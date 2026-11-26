package com.example.sync

import com.example.data.LocalFileOps
import com.example.drive.DriveChild
import com.example.drive.DriveFileItem
import com.example.drive.DriveMimeTypes
import com.example.drive.DriveRemote
import com.example.github.GithubApiClient
import com.example.github.GithubRemote
import com.example.github.GithubTreeChange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sinkron dua arah untuk satu sumber (akun Drive atau repo GitHub).
 *
 * Alur tiap sync:
 *  1. Scan lokal, ambil daftar remote, baca base (state sync terakhir) dari [SyncStateStore].
 *  2. [TwoWayPlanner] memutuskan aksi per path.
 *  3. Fase lokal: unduh, pindah ke trash lokal, salinan konflik, catat state.
 *  4. Fase remote: unggah dan hapus ke trash remote. GitHub memakai satu commit untuk semuanya.
 *
 * Hapus tidak pernah permanen: file dipindah ke `.trash/<waktu>/<path>` di kedua sisi.
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

    suspend fun syncDrive(
        email: String,
        rootDir: File,
        ignoreRules: IgnoreRules,
        items: List<TreeItem>,
        stateStore: SyncStateStore,
        sourceKey: String
    ): OpResult = withContext(Dispatchers.IO) {
        val token = driveTokenProvider(email)
        val base = scopedBase(stateStore, sourceKey, items)
        val scanned = scanLocal(rootDir, items, ignoreRules) { !isHiddenPath(it) }
        val local = scanned.mapValues { LocalFile(LocalFileOps.md5Hex(it.value)) }
        guardLocal(rootDir, items, base)

        val remote = linkedMapOf<String, RemoteFile>()
        for (item in items) {
            if (item.isFolder) {
                val folderId = item.remoteId ?: error("Folder ${item.path} tidak punya id Drive.")
                for (f in drive.listFilesRecursive(token, folderId)) {
                    if (isHiddenPath(f.relativePath)) continue
                    val path = "${item.path}/${f.relativePath}"
                    if (ignoreRules.isIgnored(path)) continue
                    remote[path] = driveRemote(f)
                }
            } else {
                val fileId = item.remoteId ?: continue
                val fresh = fetchDriveFileOrNull(token, fileId) ?: continue
                if (!fresh.trashed && !isHiddenPath(item.path) && !ignoreRules.isIgnored(item.path)) {
                    remote[item.path] = driveRemote(fresh)
                }
            }
        }

        val actions = TwoWayPlanner.planAll(local, remote, base)
        val stamp = stampNow()
        val phase1 = applyLocalSide(
            sourceKey = sourceKey,
            rootDir = rootDir,
            stateStore = stateStore,
            actions = actions,
            stamp = stamp,
            hashOf = LocalFileOps::md5Hex
        ) { r, dest -> downloadDriveRemote(token, r, dest) }

        var result = phase1.result
        for (action in phase1.remoteWork) {
            if (!currentCoroutineContext().isActive) break
            try {
                when (action) {
                    is PlannedAction.Upload -> {
                        val uploaded = drive.uploadFile(
                            accessToken = token,
                            rootFolderId = "root",
                            relativePath = action.path,
                            existingFileId = action.remote?.ref,
                            sourceFile = File(rootDir, action.path),
                            nativeWorkspaceMimeType = action.remote?.nativeMime
                        )
                        val r = driveRemote(uploaded)
                        stateStore.put(sourceKey, action.path, PathState(action.local.hash, r.hash, r.ref))
                        result += OpResult(transferred = 1)
                    }
                    is PlannedAction.TrashRemote -> {
                        drive.trashFile(token, action.remote.ref)
                        stateStore.delete(sourceKey, action.path)
                        result += OpResult(transferred = 1)
                    }
                    else -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result += OpResult(failed = 1)
            }
        }
        result
    }

    private suspend fun downloadDriveRemote(token: String, remote: RemoteFile, dest: File) {
        val nativeMime = remote.nativeMime
        if (nativeMime != null) {
            val spec = DriveMimeTypes.exportSpecFor(nativeMime) ?: error("Tipe Google tidak didukung: $nativeMime")
            drive.exportGoogleWorkspaceFile(token, remote.ref, spec.exportMimeType, dest)
        } else {
            drive.downloadFile(token, remote.ref, dest)
        }
    }

    /** Hash remote Drive: md5 untuk file biasa, null untuk native (isinya tidak bisa dibandingkan). */
    private fun driveRemote(f: DriveFileItem): RemoteFile {
        val native = f.nativeWorkspaceMimeType
        val hash = if (native != null) null else (f.md5Checksum ?: "size:${f.size}")
        return RemoteFile(ref = f.id, hash = hash, nativeMime = native)
    }

    /** File Drive yang sudah tidak ada (404) dianggap tidak ada di remote. Error lain tetap dilempar. */
    private suspend fun fetchDriveFileOrNull(token: String, fileId: String): DriveFileItem? =
        try {
            drive.getFile(token, fileId)
        } catch (e: IllegalStateException) {
            if (e.message?.contains("(404)") == true) null else throw e
        }

    // ---------- GitHub ----------

    suspend fun syncGithub(
        pat: String,
        owner: String,
        repo: String,
        branch: String,
        rootDir: File,
        ignoreRules: IgnoreRules,
        items: List<TreeItem>,
        stateStore: SyncStateStore,
        sourceKey: String
    ): OpResult = withContext(Dispatchers.IO) {
        val base = scopedBase(stateStore, sourceKey, items)
        val scanned = scanLocal(rootDir, items, ignoreRules, ::isTrackedRepoPath)
        val local = scanned.mapValues { LocalFile(LocalFileOps.gitBlobSha(it.value)) }
        guardLocal(rootDir, items, base)

        val (headCommit, treeSha) = github.getBranchHeadCommit(pat, owner, repo, branch)
        val remote = github.listBlobsInSubfolder(pat, owner, repo, treeSha, "")
            .filter { isTrackedRepoPath(it.repoPath) }
            .filter { blob -> items.any { it.covers(blob.repoPath) } && !ignoreRules.isIgnored(blob.repoPath) }
            .associate { it.repoPath to RemoteFile(it.repoPath, it.sha, null) }

        val actions = TwoWayPlanner.planAll(local, remote, base)
        val stamp = stampNow()
        val phase1 = applyLocalSide(
            sourceKey = sourceKey,
            rootDir = rootDir,
            stateStore = stateStore,
            actions = actions,
            stamp = stamp,
            hashOf = LocalFileOps::gitBlobSha
        ) { r, dest ->
            val sha = r.hash ?: error("Blob ${r.ref} tidak punya sha")
            github.downloadBlobToFile(pat, owner, repo, sha, dest)
        }

        var result = phase1.result
        val changes = mutableListOf<GithubTreeChange>()
        val uploaded = mutableListOf<Pair<String, PathState>>()
        val removed = mutableListOf<String>()
        for (action in phase1.remoteWork) {
            if (!currentCoroutineContext().isActive) break
            try {
                when (action) {
                    is PlannedAction.Upload -> {
                        val file = File(rootDir, action.path)
                        if (file.length() > GithubApiClient.MAX_GITHUB_FILE_BYTES) {
                            result += OpResult.skip(SkipReason.TOO_LARGE_FOR_GITHUB, action.path)
                        } else {
                            val blobSha = github.createBlobFromFile(pat, owner, repo, file)
                            changes += GithubTreeChange(repoPath = action.path, blobSha = blobSha)
                            uploaded += action.path to PathState(action.local.hash, blobSha, action.path)
                        }
                    }
                    is PlannedAction.TrashRemote -> {
                        val sha = action.remote.hash ?: error("Blob ${action.path} tidak punya sha")
                        changes += GithubTreeChange(repoPath = action.path, blobSha = null)
                        changes += GithubTreeChange(repoPath = ".trash/$stamp/${action.path}", blobSha = sha)
                        removed += action.path
                    }
                    else -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result += OpResult(failed = 1)
            }
        }

        // Dibatalkan sebelum commit: tidak ada yang dikirim ke repo, state tidak berubah.
        if (changes.isNotEmpty() && currentCoroutineContext().isActive) {
            val count = uploaded.size + removed.size
            try {
                github.createAtomicCommit(
                    pat = pat,
                    owner = owner,
                    repo = repo,
                    branch = branch,
                    parentCommitSha = headCommit,
                    baseTreeSha = treeSha,
                    changes = changes,
                    message = "Sinkron Lotura: ${uploaded.size} unggah, ${removed.size} pindah ke trash"
                )
                // State hanya dicatat setelah commit berhasil.
                uploaded.forEach { (path, state) -> stateStore.put(sourceKey, path, state) }
                removed.forEach { stateStore.delete(sourceKey, it) }
                result += OpResult(transferred = count)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result += OpResult(failed = count)
            }
        }
        result
    }

    // ---------- Bersama ----------

    private data class Phase1Outcome(
        val result: OpResult,
        /** Aksi yang harus dijalankan di fase remote: Upload dan TrashRemote, termasuk salinan konflik. */
        val remoteWork: List<PlannedAction>
    )

    /**
     * Fase lokal. Unduhan dan pemindahan dilakukan di sini; unggahan dan hapus remote ditunda ke fase berikutnya.
     */
    private suspend fun applyLocalSide(
        sourceKey: String,
        rootDir: File,
        stateStore: SyncStateStore,
        actions: List<PlannedAction>,
        stamp: String,
        hashOf: (File) -> String,
        download: suspend (RemoteFile, File) -> Unit
    ): Phase1Outcome {
        var result = OpResult()
        val deferred = mutableListOf<PlannedAction>()
        val copies = mutableListOf<PlannedAction>()
        for (action in actions) {
            if (!currentCoroutineContext().isActive) break
            try {
                when (action) {
                    is PlannedAction.Download -> {
                        val dest = File(rootDir, action.path)
                        dest.parentFile?.mkdirs()
                        download(action.remote, dest)
                        stateStore.put(
                            sourceKey,
                            action.path,
                            PathState(hashOf(dest), action.remote.hash, action.remote.ref)
                        )
                        result += OpResult(transferred = 1)
                    }
                    is PlannedAction.TrashLocal -> {
                        moveToTrash(rootDir, action.path, stamp)
                        stateStore.delete(sourceKey, action.path)
                        result += OpResult(transferred = 1)
                    }
                    is PlannedAction.Conflict -> {
                        // Lokal dipindah jadi salinan konflik (lalu diunggah), remote diunduh ke path asli.
                        val src = File(rootDir, action.path)
                        val copyPath = TwoWayPlanner.conflictCopyPath(action.path, stamp)
                        val copy = File(rootDir, copyPath)
                        copy.parentFile?.mkdirs()
                        if (!src.renameTo(copy)) throw IOException("Gagal memindah ${src.name} ke salinan konflik.")
                        download(action.remote, src)
                        stateStore.put(
                            sourceKey,
                            action.path,
                            PathState(hashOf(src), action.remote.hash, action.remote.ref)
                        )
                        copies += PlannedAction.Upload(copyPath, action.local, null)
                        result += OpResult(transferred = 1)
                    }
                    is PlannedAction.Record -> {
                        stateStore.put(
                            sourceKey,
                            action.path,
                            PathState(action.local.hash, action.remote.hash, action.remote.ref)
                        )
                    }
                    is PlannedAction.Forget -> stateStore.delete(sourceKey, action.path)
                    is PlannedAction.Upload, is PlannedAction.TrashRemote -> deferred += action
                    is PlannedAction.Unchanged -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result += OpResult(failed = 1)
            }
        }
        return Phase1Outcome(result, deferred + copies)
    }

    /** Pindah berkas lokal ke `.trash/<stamp>/<path>`. Folder .trash tidak ikut sync (nama diawali titik). */
    private fun moveToTrash(rootDir: File, path: String, stamp: String) {
        val src = File(rootDir, path)
        if (!src.exists()) return
        val dest = File(rootDir, ".trash/$stamp/$path")
        dest.parentFile?.mkdirs()
        if (!src.renameTo(dest)) {
            src.copyTo(dest, overwrite = true)
            src.delete()
        }
    }

    /** Base untuk sumber ini, dibatasi ke path yang dicakup item terpilih. */
    private suspend fun scopedBase(
        stateStore: SyncStateStore,
        sourceKey: String,
        items: List<TreeItem>
    ): Map<String, PathState> =
        stateStore.getAll(sourceKey).filterKeys { path -> items.any { it.covers(path) } }

    /** Hasil scan lokal untuk item terpilih. [accept] menyaring path yang boleh ikut (mis. hanya .md di GitHub). */
    private fun scanLocal(
        rootDir: File,
        items: List<TreeItem>,
        ignoreRules: IgnoreRules,
        accept: (String) -> Boolean
    ): Map<String, File> {
        val out = linkedMapOf<String, File>()
        for (item in items) {
            if (item.isFolder) {
                LocalScanner.scanFiles(File(rootDir, item.path), ignoreRules).forEach { (rel, file) ->
                    val path = "${item.path}/$rel"
                    if (accept(path) && !ignoreRules.isIgnored(path)) out[path] = file
                }
            } else {
                val file = File(rootDir, item.path)
                if (file.isFile && accept(item.path) && !ignoreRules.isIgnored(item.path)) out[item.path] = file
            }
        }
        return out
    }

    /**
     * Pengaman: kalau base ada tapi folder lokal hilang, jangan anggap semua file remote sudah dihapus lokal.
     * Sync dibatalkan. Folder kosong (yang masih ada) tetap diproses seperti biasa.
     */
    private fun guardLocal(rootDir: File, items: List<TreeItem>, base: Map<String, PathState>) {
        if (base.isEmpty()) return
        if (!rootDir.isDirectory) {
            throw IllegalStateException("Folder ${rootDir.name} tidak ada. Sync dibatalkan supaya file di sumber tidak terhapus.")
        }
        items.filter { item -> item.isFolder && base.keys.any { item.covers(it) } }.forEach { item ->
            if (!File(rootDir, item.path).isDirectory) {
                throw IllegalStateException("Folder ${item.path} tidak ada di perangkat. Sync dibatalkan supaya file di sumber tidak terhapus.")
            }
        }
    }

    private fun stampNow(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}
