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

/** Data GitHub yang dibutuhkan saat eksekusi: commit dan tree yang dipakai saat scan. */
internal data class GithubTarget(
    val pat: String,
    val owner: String,
    val repo: String,
    val branch: String,
    val headCommit: String,
    val treeSha: String
)

/**
 * Hasil fase scan: rencana per path, tanpa menulis apa pun.
 * Tombol "Sinkron" menampilkan [plan] sebagai dry-run, lalu [SyncEngine.execute] menjalankan rencana yang sama.
 */
class SyncScan internal constructor(
    val sourceKey: String,
    val rootDir: File,
    val mode: SyncMode,
    val base: Map<String, PathState>,
    val actions: List<PlannedAction>,
    /** Jumlah file lokal dalam ruang lingkup sync (sebelum planner). */
    val localFileCount: Int,
    internal val driveEmail: String?,
    internal val github: GithubTarget?
) {
    val plan: SyncPlan = SyncPlan(actions)

    /** Ada base, tapi folder lokal tidak punya satu file pun. Bisa berarti file terhapus tidak sengaja. */
    val localEmptyWithBase: Boolean get() = mode == SyncMode.SYNC && base.isNotEmpty() && localFileCount == 0

    /** Trash melewati batas: lebih dari [TRASH_CONFIRM_MIN] file, atau lebih dari [TRASH_CONFIRM_PERCENT] persen base. */
    val trashOverLimit: Boolean
        get() = plan.trashTotal > TRASH_CONFIRM_MIN ||
            (base.isNotEmpty() && plan.trashTotal * 100 > base.size * TRASH_CONFIRM_PERCENT)

    /** Konfirmasi tambahan wajib: trash melewati batas, atau folder lokal kosong padahal base ada. */
    val needsStrongConfirm: Boolean get() = trashOverLimit || localEmptyWithBase

    companion object {
        const val TRASH_CONFIRM_MIN = 10
        const val TRASH_CONFIRM_PERCENT = 20
    }
}

/**
 * Sinkron untuk satu sumber (akun Drive atau repo GitHub), dibagi dua fase:
 *  1. scan*: baca lokal, remote, dan base lalu hasilkan [SyncScan]. Tidak menulis apa pun.
 *  2. [execute]: jalankan rencana dari scan. Dipakai sama untuk mode tambah dan sinkron.
 *
 * Eksekusi:
 *  - Fase lokal: unduh, pindah ke trash lokal, salinan konflik, catat state.
 *  - Fase remote: unggah dan hapus ke trash remote. GitHub memakai satu commit untuk semuanya.
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

    suspend fun scanDrive(
        email: String,
        rootDir: File,
        ignoreRules: IgnoreRules,
        items: List<TreeItem>,
        stateStore: SyncStateStore,
        sourceKey: String,
        mode: SyncMode
    ): SyncScan = withContext(Dispatchers.IO) {
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

        SyncScan(
            sourceKey = sourceKey,
            rootDir = rootDir,
            mode = mode,
            base = base,
            actions = planFor(mode, local, remote, base),
            localFileCount = local.size,
            driveEmail = email,
            github = null
        )
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

    suspend fun scanGithub(
        pat: String,
        owner: String,
        repo: String,
        branch: String,
        rootDir: File,
        ignoreRules: IgnoreRules,
        items: List<TreeItem>,
        stateStore: SyncStateStore,
        sourceKey: String,
        mode: SyncMode
    ): SyncScan = withContext(Dispatchers.IO) {
        val base = scopedBase(stateStore, sourceKey, items)
        val scanned = scanLocal(rootDir, items, ignoreRules, ::isTrackedRepoPath)
        val local = scanned.mapValues { LocalFile(LocalFileOps.gitBlobSha(it.value)) }
        guardLocal(rootDir, items, base)

        val (headCommit, treeSha) = github.getBranchHeadCommit(pat, owner, repo, branch)
        val remote = github.listBlobsInSubfolder(pat, owner, repo, treeSha, "")
            .filter { isTrackedRepoPath(it.repoPath) }
            .filter { blob -> items.any { it.covers(blob.repoPath) } && !ignoreRules.isIgnored(blob.repoPath) }
            .associate { it.repoPath to RemoteFile(it.repoPath, it.sha, null) }

        SyncScan(
            sourceKey = sourceKey,
            rootDir = rootDir,
            mode = mode,
            base = base,
            actions = planFor(mode, local, remote, base),
            localFileCount = local.size,
            driveEmail = null,
            github = GithubTarget(pat, owner, repo, branch, headCommit, treeSha)
        )
    }

    // ---------- Eksekusi ----------

    /**
     * Menjalankan rencana dari [scan]. Dipanggil setelah user melihat dry-run dari scan yang sama.
     * Hasil scan tidak dihitung ulang di sini.
     */
    suspend fun execute(scan: SyncScan, stateStore: SyncStateStore): OpResult = withContext(Dispatchers.IO) {
        val stamp = stampNow()
        val gh = scan.github
        if (gh != null) executeGithub(scan, gh, stateStore, stamp)
        else executeDrive(scan, stateStore, stamp)
    }

    private suspend fun executeDrive(scan: SyncScan, stateStore: SyncStateStore, stamp: String): OpResult {
        val email = scan.driveEmail ?: error("Scan Drive tanpa email")
        val token = driveTokenProvider(email)
        val phase1 = applyLocalSide(
            sourceKey = scan.sourceKey,
            rootDir = scan.rootDir,
            stateStore = stateStore,
            actions = scan.actions,
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
                            sourceFile = File(scan.rootDir, action.path),
                            nativeWorkspaceMimeType = action.remote?.nativeMime
                        )
                        val r = driveRemote(uploaded)
                        stateStore.put(scan.sourceKey, action.path, PathState(action.local.hash, r.hash, r.ref))
                        result += OpResult(transferred = 1)
                    }
                    is PlannedAction.TrashRemote -> {
                        drive.trashFile(token, action.remote.ref)
                        stateStore.delete(scan.sourceKey, action.path)
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
        return result
    }

    private suspend fun executeGithub(
        scan: SyncScan,
        gh: GithubTarget,
        stateStore: SyncStateStore,
        stamp: String
    ): OpResult {
        val phase1 = applyLocalSide(
            sourceKey = scan.sourceKey,
            rootDir = scan.rootDir,
            stateStore = stateStore,
            actions = scan.actions,
            stamp = stamp,
            hashOf = LocalFileOps::gitBlobSha
        ) { r, dest ->
            val sha = r.hash ?: error("Blob ${r.ref} tidak punya sha")
            github.downloadBlobToFile(gh.pat, gh.owner, gh.repo, sha, dest)
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
                        val file = File(scan.rootDir, action.path)
                        if (file.length() > GithubApiClient.MAX_GITHUB_FILE_BYTES) {
                            result += OpResult.skip(SkipReason.TOO_LARGE_FOR_GITHUB, action.path)
                        } else {
                            val blobSha = github.createBlobFromFile(gh.pat, gh.owner, gh.repo, file)
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
                    pat = gh.pat,
                    owner = gh.owner,
                    repo = gh.repo,
                    branch = gh.branch,
                    parentCommitSha = gh.headCommit,
                    baseTreeSha = gh.treeSha,
                    changes = changes,
                    message = "Sinkron Lotura: ${uploaded.size} unggah, ${removed.size} pindah ke trash"
                )
                // State hanya dicatat setelah commit berhasil.
                uploaded.forEach { (path, state) -> stateStore.put(scan.sourceKey, path, state) }
                removed.forEach { stateStore.delete(scan.sourceKey, it) }
                result += OpResult(transferred = count)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result += OpResult(failed = count)
            }
        }
        return result
    }

    // ---------- Bersama ----------

    private fun planFor(
        mode: SyncMode,
        local: Map<String, LocalFile>,
        remote: Map<String, RemoteFile>,
        base: Map<String, PathState>
    ): List<PlannedAction> =
        if (mode == SyncMode.ADD_BOTH) TwoWayPlanner.planAddAll(local, remote, base)
        else TwoWayPlanner.planAll(local, remote, base)

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
