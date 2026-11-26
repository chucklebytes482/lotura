package com.example.sync

import com.example.data.LocalFileOps
import com.example.drive.DriveApiClient
import com.example.drive.DriveFileItem
import com.example.drive.DriveFolderItem
import com.example.drive.DriveMimeTypes
import com.example.drive.DriveRemote
import com.example.drive.RawDriveEntry
import com.example.github.GithubApiClient
import com.example.github.GithubRemote
import com.example.github.GithubTreeBlob
import com.example.github.GithubTreeChange
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SyncEngineTest {
    @get:Rule val temp = TemporaryFolder()
    private val rules = IgnoreRules(IgnoreRules.DEFAULT_PATTERNS)
    private val state = MemoryState()
    private val drive = TestDrive()
    private val github = TestGithub()
    private val engine = SyncEngine(drive, github) { "test-token" }
    private val driveKey = SourceKeys.drive("test")
    private val gitKey = SourceKeys.github("o/r")

    private suspend fun scanDrive(root: File, mode: SyncMode) = engine.scanDrive("test", root, rules, state, driveKey, mode)
    private suspend fun scanGit(root: File, mode: SyncMode) = engine.scanGithub("test", "o", "r", "main", root, rules, state, gitKey, mode)
    private suspend fun apply(scan: SyncScan) { assertEquals(0, engine.execute(scan, state).failed) }

    private fun file(root: File, path: String, content: String = "data"): File = File(root, path).also {
        it.parentFile!!.mkdirs()
        it.writeBytes(byteArrayOf(0) + content.toByteArray())
    }

    @Test fun drive_localOnlyFilesAndEmptyFolderThenNestedDeletion() = runBlocking {
        val root = temp.newFolder()
        file(root, "new/nested/data.tmp")
        file(root, "new/README")
        file(root, "new/.hidden/key")
        file(root, ".secret")
        File(root, "new/empty").mkdir()
        apply(scanDrive(root, SyncMode.ADD_BOTH))
        assertEquals(setOf("new", "new/nested", "new/nested/data.tmp", "new/README", "new/empty"), drive.items.keys)
        assertTrue(File(root, "new").deleteRecursively())
        apply(scanDrive(root, SyncMode.ADD_BOTH))
        assertFalse(File(root, "new").exists())
        val scan = scanDrive(root, SyncMode.SYNC)
        assertTrue(scan.needsStrongConfirm)
        assertEquals(5, scan.plan.trashRemote)
        apply(scan)
        assertTrue(drive.items.isEmpty())
        assertEquals("new", drive.trashed.last())
        assertTrue(state.getAll(driveKey).isEmpty())
    }

    @Test fun drive_remoteFolderDeletionAndRenamePropagateToLocal() = runBlocking {
        val root = temp.newFolder()
        file(root, "previous/deep/data.bin")
        File(root, "previous/deep/empty").mkdir()
        apply(scanDrive(root, SyncMode.ADD_BOTH))
        drive.renameTree("previous", "next")
        apply(scanDrive(root, SyncMode.ADD_BOTH))
        val scan = scanDrive(root, SyncMode.SYNC)
        assertEquals(4, scan.plan.trashLocal)
        apply(scan)
        assertFalse(File(root, "previous").exists())
        assertTrue(File(root, "next/deep/data.bin").isFile)
        assertTrue(File(root, "next/deep/empty").isDirectory)
        assertTrue(File(root, ".trash").walkTopDown().any { it.name == "data.bin" })
    }

    @Test fun drive_upgradeFromFileOnlyHistoryDoesNotRestoreDeletedFolder() = runBlocking {
        val root = temp.newFolder()
        file(root, "old/data.bin")
        apply(scanDrive(root, SyncMode.ADD_BOTH))
        state.delete(driveKey, "old")
        assertTrue(File(root, "old").deleteRecursively())
        apply(scanDrive(root, SyncMode.ADD_BOTH))
        assertFalse(File(root, "old").exists())
        apply(scanDrive(root, SyncMode.SYNC))
        assertTrue(drive.items.isEmpty())
    }

    @Test fun drive_doesNotTrashFolderContainingHiddenOrUntrackedRemoteItems() = runBlocking {
        val root = temp.newFolder()
        file(root, "folder/file.bin")
        apply(scanDrive(root, SyncMode.ADD_BOTH))
        drive.add("folder/.hidden", "application/octet-stream")
        drive.add("folder/shortcut", DriveMimeTypes.SHORTCUT)
        assertTrue(File(root, "folder").deleteRecursively())
        val scan = scanDrive(root, SyncMode.SYNC)
        assertEquals(1, scan.plan.trashRemote)
        apply(scan)
        assertEquals(setOf("folder", "folder/.hidden", "folder/shortcut"), drive.items.keys)
    }

    @Test fun drive_localDeletionDoesNotTrashFolderWithRemotelyEditedFile() = runBlocking {
        val root = temp.newFolder()
        file(root, "folder/file.bin")
        apply(scanDrive(root, SyncMode.ADD_BOTH))
        drive.edit("folder/file.bin", file(root, "changed", "new data"))
        assertTrue(File(root, "changed").delete())
        assertTrue(File(root, "folder").deleteRecursively())
        val scan = scanDrive(root, SyncMode.SYNC)
        assertEquals(0, scan.plan.trashRemote)
        apply(scan)
        assertTrue(File(root, "folder/file.bin").isFile)
    }

    @Test fun drive_remoteDeletionPreservesLocalEditsAndRecreatesTheirParents() = runBlocking {
        val root = temp.newFolder()
        file(root, "gone/nested/keep.bin")
        file(root, "gone/nested/remove.bin")
        apply(scanDrive(root, SyncMode.ADD_BOTH))
        drive.items.clear()
        file(root, "gone/nested/keep.bin", "edited locally")
        apply(scanDrive(root, SyncMode.SYNC))
        assertEquals(setOf("gone", "gone/nested", "gone/nested/keep.bin"), drive.items.keys)
        assertTrue(File(root, "gone/nested/keep.bin").isFile)
        assertFalse(File(root, "gone/nested/remove.bin").exists())
    }

    @Test fun drive_doesNotTrashAFileMovedRemotelyAfterPreview() = runBlocking {
        val root = temp.newFolder()
        file(root, "old/file.bin")
        apply(scanDrive(root, SyncMode.ADD_BOTH))
        File(root, "old").deleteRecursively()
        val scan = scanDrive(root, SyncMode.SYNC)
        assertEquals(2, scan.plan.trashRemote)
        drive.renameTree("old", "moved")
        assertEquals(2, engine.execute(scan, state).failed)
        assertEquals(setOf("moved", "moved/file.bin"), drive.items.keys)
        assertTrue(drive.trashed.isEmpty())
    }

    @Test fun github_uploadsAnyExtensionAndDeletesEntireTrackedFolderButNotHiddenPaths() = runBlocking {
        val root = temp.newFolder()
        file(root, "new/README")
        file(root, "new/photos/image.png")
        file(root, "new/.local/not-uploaded")
        github.blobs["new/.remote/keep"] = GithubTreeBlob("new/.remote/keep", "new/.remote/keep", "sha", 1)
        apply(scanGit(root, SyncMode.ADD_BOTH))
        assertEquals(setOf("new/README", "new/photos/image.png", "new/.remote/keep"), github.blobs.keys)
        assertTrue(File(root, "new").deleteRecursively())
        apply(scanGit(root, SyncMode.ADD_BOTH))
        apply(scanGit(root, SyncMode.SYNC))
        assertEquals(setOf("new/.remote/keep"), github.blobs.keys.filter { isSyncPath(it) || it == "new/.remote/keep" }.toSet())
        assertEquals(2, github.blobs.keys.count { it.startsWith(".trash/") })
    }

    @Test fun github_remoteDeletionPrunesEmptyLocalFoldersWithoutTouchingHiddenItems() = runBlocking {
        val root = temp.newFolder()
        file(root, "tree/removed/deep/README")
        file(root, "tree/keep/file.png")
        file(root, "tree/keep/.secret")
        apply(scanGit(root, SyncMode.ADD_BOTH))
        github.blobs.clear()
        apply(scanGit(root, SyncMode.SYNC))
        assertFalse(File(root, "tree/removed").exists())
        assertFalse(File(root, "tree/keep/file.png").exists())
        assertTrue(File(root, "tree/keep/.secret").isFile)
    }

    @Test fun missingLocalSourceWithHistoryAbortsRatherThanTrashingRemote() = runBlocking {
        val root = temp.newFolder()
        file(root, "file.pdf")
        apply(scanDrive(root, SyncMode.ADD_BOTH))
        root.deleteRecursively()
        assertTrue(runCatching { scanDrive(root, SyncMode.SYNC) }.isFailure)
        assertEquals(setOf("file.pdf"), drive.items.keys)
    }

    private class MemoryState : SyncStateStore {
        private val entries = mutableMapOf<String, MutableMap<String, PathState>>()
        override suspend fun getAll(sourceKey: String) = entries[sourceKey].orEmpty().toMap()
        override suspend fun put(sourceKey: String, path: String, state: PathState) { entries.getOrPut(sourceKey) { mutableMapOf() }[path] = state }
        override suspend fun delete(sourceKey: String, path: String) { entries[sourceKey]?.remove(path) }
        override suspend fun deleteSource(sourceKey: String) { entries.remove(sourceKey) }
    }

    private class TestDrive : DriveRemote by DriveApiClient(noNetwork) {
        val items = linkedMapOf<String, RawDriveEntry>()
        val trashed = mutableListOf<String>()
        private val payload = mutableMapOf<String, ByteArray>()
        private var nextId = 0
        fun add(path: String, mime: String, file: File? = null): RawDriveEntry {
            val id = "id-${nextId++}"
            val entry = RawDriveEntry(id, path.substringAfterLast('/'), mime, 0, file?.length() ?: 0,
                file?.let(LocalFileOps::md5Hex), false, null, null)
            items[path] = entry
            if (file != null) payload[id] = file.readBytes()
            return entry
        }
        fun edit(path: String, file: File) {
            val entry = items.getValue(path)
            items[path] = entry.copy(size = file.length(), md5Checksum = LocalFileOps.md5Hex(file))
            payload[entry.id] = file.readBytes()
        }
        fun renameTree(from: String, to: String) {
            val tree = items.filterKeys { it == from || it.startsWith("$from/") }
            for ((path, entry) in tree) {
                items.remove(path)
                items[to + path.removePrefix(from)] = if (path == from) entry.copy(name = to) else entry
            }
        }
        private fun path(id: String): String = if (id == "root") "" else items.entries.single { it.value.id == id }.key
        override suspend fun listSyncChildren(accessToken: String, folderId: String) =
            items.filterKeys { it.substringBeforeLast('/', "") == path(folderId) }.values.toList()
        override suspend fun createFolder(accessToken: String, parentId: String, name: String): DriveFolderItem {
            val entry = add(SourceTreeRules.childPath(path(parentId), name), DriveMimeTypes.FOLDER)
            return DriveFolderItem(entry.id, entry.name)
        }
        override suspend fun uploadFile(accessToken: String, parentFolderId: String, relativePath: String,
                                        existingFileId: String?, sourceFile: File, nativeWorkspaceMimeType: String?): DriveFileItem {
            check(path(parentFolderId) == relativePath.substringBeforeLast('/', ""))
            val entry = add(relativePath, "application/octet-stream", sourceFile)
            return DriveFileItem(entry.id, entry.name, relativePath, entry.mimeType, 0, entry.size, entry.md5Checksum)
        }
        override suspend fun downloadFile(accessToken: String, fileId: String, destFile: File) {
            destFile.writeBytes(payload.getValue(fileId))
        }
        override suspend fun trashFile(accessToken: String, fileId: String) {
            val path = path(fileId)
            check(items.keys.none { it.startsWith("$path/") })
            items.remove(path)
            trashed += path
        }
    }

    private class TestGithub : GithubRemote by GithubApiClient(noNetwork) {
        val blobs = linkedMapOf<String, GithubTreeBlob>()
        override suspend fun getBranchHeadCommit(pat: String, owner: String, repo: String, branch: String) = "head" to "tree"
        override suspend fun listBlobsInSubfolder(pat: String, owner: String, repo: String, treeSha: String, subfolder: String) = blobs.values.toList()
        override suspend fun createBlobFromFile(pat: String, owner: String, repo: String, file: File) = LocalFileOps.gitBlobSha(file)
        override suspend fun createAtomicCommit(pat: String, owner: String, repo: String, branch: String,
                                                parentCommitSha: String, baseTreeSha: String, changes: List<GithubTreeChange>, message: String): String {
            for ((path, sha, mode) in changes) {
                if (sha == null) blobs.remove(path) else blobs[path] = GithubTreeBlob(path, path, sha, 1, mode)
            }
            return "new-head"
        }
    }

    companion object {
        private val noNetwork = OkHttpClient.Builder().addInterceptor { error("Unexpected network call") }.build()
    }
}
