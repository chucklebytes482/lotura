package com.example.sync

import com.example.drive.DriveChild
import com.example.drive.DriveFileItem
import com.example.drive.DriveMimeTypes
import com.example.drive.DriveRemote
import com.example.github.GithubApiClient
import com.example.github.GithubRemote
import com.example.github.GithubTreeBlob
import com.example.github.GithubTreeChange
import com.example.data.LocalFileOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Menjalankan unduh/unggah untuk item yang dicentang saja.
 * Perbandingan hanya memakai ukuran (lihat [SyncDecisions]).
 */
class SyncEngine(
    private val drive: DriveRemote,
    private val github: GithubRemote,
    /** Mengembalikan access token Drive yang masih berlaku untuk akun [email]. */
    private val driveTokenProvider: suspend (email: String) -> String
) {

    // ---------- Pohon Drive ----------

    suspend fun driveChildren(email: String, folderId: String): List<DriveChild> =
        drive.listChildren(driveTokenProvider(email), folderId)

    // ---------- Google Drive ----------

    suspend fun downloadDrive(
        email: String,
        rootDir: File,
        items: List<TreeItem>
    ): OpResult = withContext(Dispatchers.IO) {
        val token = driveTokenProvider(email)
        var result = OpResult()
        for (item in items) {
            if (!currentCoroutineContext().isActive) break
            val remoteId = item.remoteId
            if (remoteId == null) {
                result += OpResult(failed = 1)
                continue
            }
            if (item.isFolder) {
                val folderDir = File(rootDir, item.path)
                folderDir.mkdirs()
                val listing = try {
                    drive.listFilesRecursive(token, remoteId)
                } catch (e: Exception) {
                    result += OpResult(failed = 1)
                    null
                }
                listing?.let { all ->
                    all.filter { isHiddenPath(it.relativePath) }.forEach {
                        result += OpResult.skip(SkipReason.HIDDEN, "${item.path}/${it.relativePath}")
                    }
                }
                listing?.filterNot { isHiddenPath(it.relativePath) }?.forEach { remote ->
                    result += downloadDriveFile(
                        token = token,
                        dest = File(folderDir, remote.relativePath),
                        relPath = "${item.path}/${remote.relativePath}",
                        id = remote.id,
                        size = remote.remoteSize(),
                        nativeMime = remote.nativeWorkspaceMimeType
                    )
                }
            } else {
                result += downloadDriveFile(
                    token = token,
                    dest = File(rootDir, item.path),
                    relPath = item.path,
                    id = remoteId,
                    size = item.size,
                    nativeMime = item.nativeMime
                )
            }
        }
        result
    }

    private suspend fun downloadDriveFile(
        token: String,
        dest: File,
        relPath: String,
        id: String,
        size: Long?,
        nativeMime: String?
    ): OpResult {
        if (!currentCoroutineContext().isActive) return OpResult()
        val localSize = dest.takeIf { it.isFile }?.length()
        if (!SyncDecisions.shouldTransfer(sourceSize = size, targetSize = localSize)) {
            return OpResult.skip(SkipReason.SAME_SIZE, relPath)
        }
        return try {
            dest.parentFile?.mkdirs()
            if (nativeMime != null) {
                val spec = DriveMimeTypes.exportSpecFor(nativeMime)
                    ?: error("Tipe Google tidak didukung: $nativeMime")
                drive.exportGoogleWorkspaceFile(token, id, spec.exportMimeType, dest)
            } else {
                drive.downloadFile(token, id, dest)
            }
            OpResult(transferred = 1)
        } catch (e: Exception) {
            OpResult(failed = 1)
        }
    }

    suspend fun uploadDrive(
        email: String,
        rootDir: File,
        ignoreRules: IgnoreRules,
        items: List<TreeItem>
    ): OpResult = withContext(Dispatchers.IO) {
        val token = driveTokenProvider(email)
        var result = OpResult()
        for (item in items) {
            if (!currentCoroutineContext().isActive) break
            // Pasangan (path relatif ke root Drive, file lokal, referensi remote) untuk item ini
            val jobs = mutableListOf<Triple<String, File, RemoteRef?>>()
            if (item.isFolder) {
                val folderDir = File(rootDir, item.path)
                val remoteMap = item.remoteId?.let { folderId ->
                    runCatching { drive.listFilesRecursive(token, folderId) }
                        .getOrDefault(emptyList())
                        .associate { it.relativePath to it.toRemoteRef() }
                } ?: emptyMap()
                LocalScanner.scanFiles(folderDir, ignoreRules).forEach { (rel, file) ->
                    jobs += Triple("${item.path}/$rel", file, remoteMap[rel])
                }
            } else {
                val file = File(rootDir, item.path)
                if (file.isFile) {
                    jobs += Triple(item.path, file, RemoteRef(item.remoteId, item.size, item.nativeMime))
                } else {
                    result += OpResult.skip(SkipReason.NOT_A_LOCAL_FILE, item.path)
                }
            }
            for ((relPath, file, remote) in jobs) {
                if (!currentCoroutineContext().isActive) break
                val remoteSize = remote?.size
                val exists = remote?.id != null
                if (exists && !SyncDecisions.shouldTransfer(sourceSize = file.length(), targetSize = remoteSize)) {
                    result += OpResult.skip(SkipReason.SAME_SIZE, relPath)
                    continue
                }
                try {
                    drive.uploadFile(
                        accessToken = token,
                        rootFolderId = "root",
                        relativePath = relPath,
                        existingFileId = remote?.id,
                        sourceFile = file,
                        nativeWorkspaceMimeType = remote?.nativeMime
                    )
                    result += OpResult(transferred = 1)
                } catch (e: Exception) {
                    result += OpResult(failed = 1)
                }
            }
        }
        result
    }

    // ---------- GitHub ----------

    suspend fun downloadGithub(
        pat: String,
        owner: String,
        repo: String,
        branch: String,
        rootDir: File,
        items: List<TreeItem>,
        onSynced: (path: String, sha: String) -> Unit = { _, _ -> }
    ): OpResult = withContext(Dispatchers.IO) {
        val (_, treeSha) = github.getBranchHeadCommit(pat, owner, repo, branch)
        val blobs = github.listBlobsInSubfolder(pat, owner, repo, treeSha, "")
        var result = OpResult()
        for (blob in blobs) {
            if (!currentCoroutineContext().isActive) break
            if (items.none { it.covers(blob.repoPath) }) continue
            if (isHiddenPath(blob.repoPath)) {
                result += OpResult.skip(SkipReason.HIDDEN, blob.repoPath)
                continue
            }
            if (!isTrackedRepoPath(blob.repoPath)) continue
            val dest = File(rootDir, blob.repoPath)
            if (!SyncDecisions.shouldTransfer(sourceSize = blob.size, targetSize = dest.takeIf { it.isFile }?.length())) {
                result += OpResult.skip(SkipReason.SAME_SIZE, blob.repoPath)
                // Ukuran sama: hanya dianggap sinkron kalau isinya memang identik.
                if (dest.isFile && LocalFileOps.gitBlobSha(dest) == blob.sha) onSynced(blob.repoPath, blob.sha)
                continue
            }
            try {
                github.downloadBlobToFile(pat, owner, repo, blob.sha, dest)
                result += OpResult(transferred = 1)
                onSynced(blob.repoPath, blob.sha)
            } catch (e: Exception) {
                result += OpResult(failed = 1)
            }
        }
        result
    }

    suspend fun uploadGithub(
        pat: String,
        owner: String,
        repo: String,
        branch: String,
        rootDir: File,
        ignoreRules: IgnoreRules,
        items: List<TreeItem>,
        onSynced: (path: String, sha: String) -> Unit = { _, _ -> }
    ): OpResult = withContext(Dispatchers.IO) {
        val (headCommit, treeSha) = github.getBranchHeadCommit(pat, owner, repo, branch)
        val remoteBlobs: Map<String, GithubTreeBlob> =
            github.listBlobsInSubfolder(pat, owner, repo, treeSha, "")
                .filter { isTrackedRepoPath(it.repoPath) }
                .associateBy { it.repoPath }

        var result = OpResult()
        val changes = mutableListOf<GithubTreeChange>()

        val candidates = linkedMapOf<String, File>()
        for (item in items) {
            if (item.isFolder) {
                LocalScanner.scanFiles(File(rootDir, item.path), ignoreRules).forEach { (rel, file) ->
                    candidates["${item.path}/$rel"] = file
                }
            } else {
                val file = File(rootDir, item.path)
                if (file.isFile) candidates[item.path] = file
            }
        }

        for ((repoPath, file) in candidates) {
            if (!currentCoroutineContext().isActive) break
            if (!isTrackedRepoPath(repoPath)) continue
            val remote = remoteBlobs[repoPath]
            if (!SyncDecisions.shouldTransfer(sourceSize = file.length(), targetSize = remote?.size)) {
                result += OpResult.skip(SkipReason.SAME_SIZE, repoPath)
                if (remote != null && LocalFileOps.gitBlobSha(file) == remote.sha) onSynced(repoPath, remote.sha)
                continue
            }
            if (file.length() > GithubApiClient.MAX_GITHUB_FILE_BYTES) {
                result += OpResult.skip(SkipReason.TOO_LARGE_FOR_GITHUB, repoPath)
                continue
            }
            try {
                val blobSha = github.createBlobFromFile(pat, owner, repo, file)
                changes += GithubTreeChange(repoPath = repoPath, blobSha = blobSha)
            } catch (e: Exception) {
                result += OpResult(failed = 1)
            }
        }

        // Dibatalkan sebelum commit: tidak ada yang dikirim ke repo.
        if (changes.isNotEmpty() && currentCoroutineContext().isActive) {
            try {
                github.createAtomicCommit(
                    pat = pat,
                    owner = owner,
                    repo = repo,
                    branch = branch,
                    parentCommitSha = headCommit,
                    baseTreeSha = treeSha,
                    changes = changes,
                    message = "Upload ${changes.size} file dari Lotura"
                )
                result += OpResult(transferred = changes.size)
                changes.forEach { change -> change.blobSha?.let { onSynced(change.repoPath, it) } }
            } catch (e: Exception) {
                result += OpResult(failed = changes.size)
            }
        }
        result
    }


    private data class RemoteRef(val id: String?, val size: Long?, val nativeMime: String?)

    private fun DriveFileItem.toRemoteRef() = RemoteRef(id, remoteSize(), nativeWorkspaceMimeType)

    private fun DriveFileItem.remoteSize(): Long? = if (isNativeGoogleWorkspace) null else size
}
