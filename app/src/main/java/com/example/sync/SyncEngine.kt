package com.example.sync

import com.example.data.LocalFileOps
import com.example.drive.DriveChild
import com.example.drive.DriveFileItem
import com.example.drive.DriveMimeTypes
import com.example.drive.DriveRemote
import com.example.drive.RawDriveEntry
import com.example.github.GithubApiClient
import com.example.github.GithubRemote
import com.example.github.GithubTreeChange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
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
    /** Jumlah item lokal dalam ruang lingkup sync (sebelum planner). */
    val localItemCount: Int,
    internal val driveEmail: String?,
    internal val driveFolders: Map<String, String> = emptyMap(),
    internal val github: GithubTarget?
) {
    val plan: SyncPlan = SyncPlan(actions)

    /** Ada base, tapi folder lokal tidak punya satu file pun. Bisa berarti file terhapus tidak sengaja. */
    val localEmptyWithBase: Boolean get() = mode == SyncMode.SYNC && base.isNotEmpty() && localItemCount == 0

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
 *  - Fase lokal: unduh, pindah ke trash lokal (termasuk lokal yang kalah konflik), catat state.
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
        stateStore: SyncStateStore,
        sourceKey: String,
        mode: SyncMode
    ): SyncScan = withContext(Dispatchers.IO) {
        val token = driveTokenProvider(email)
        val saved = stateStore.getAll(sourceKey)
        val scanned = LocalScanner.scan(rootDir, ignoreRules, saved.isNotEmpty())
        val driveTree = scanDriveTree(token, ignoreRules)
        val local = scanned.files.mapValues { LocalFile(LocalFileOps.md5Hex(it.value)) } +
            scanned.folders.associateWith { LocalFile(FOLDER_STATE) }
        val remote = driveTree.entries
        verifyNoTypeCollision(scanned.files.keys, scanned.folders, remote)
        val base = eligibleBase(saved, ignoreRules, scanned.blocked + driveTree.blocked).toMutableMap()
        inferLegacyFolders(local, remote, base)
        val actions = planFor(mode, local, remote, base)
        val safeActions = guardFolderDeletes(
            actions.filterNot { isBlocked(it.path, scanned.blocked) || isBlocked(it.path, driveTree.blocked) },
            local, remote, scanned.blocked, driveTree.blocked
        )

        SyncScan(
            sourceKey = sourceKey,
            rootDir = rootDir,
            mode = mode,
            base = base,
            actions = safeActions,
            localItemCount = local.size,
            driveEmail = email,
            driveFolders = remote.filterValues { it.isFolder }.mapValues { it.value.ref },
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

    private data class DriveTree(val entries: Map<String, RemoteFile>, val blocked: Set<String>)

    private suspend fun scanDriveTree(token: String, ignoreRules: IgnoreRules): DriveTree {
        val entries = linkedMapOf<String, RemoteFile>()
        val blocked = linkedSetOf<String>()
        suspend fun walk(parentId: String, prefix: String) {
            val children = drive.listSyncChildren(token, parentId)
            // Drive permits '/' inside one name; it cannot be mapped to one local segment.
            if (prefix.isNotEmpty() && children.any { '/' in it.name }) blocked += "$prefix/.lotura-unmapped"
            val byPath = children.filterNot { '/' in it.name }.groupBy { entry ->
                SourceTreeRules.childPath(prefix, DriveMimeTypes.localFileNameForDriveItem(entry.name, entry.mimeType))
            }
            for ((path, group) in byPath) {
                val entry = group.first()
                val folder = entry.mimeType == DriveMimeTypes.FOLDER
                val native = DriveMimeTypes.exportSpecFor(entry.mimeType) != null
                when {
                    group.size != 1 || !isSyncPath(path) || ignoreRules.isIgnored(path, folder) ||
                        (!folder && !native && entry.mimeType.startsWith("application/vnd.google-apps.")) -> blocked += path
                    folder -> {
                        entries[path] = RemoteFile(entry.id, FOLDER_STATE, null)
                        walk(entry.id, path)
                    }
                    else -> entries[path] = RemoteFile(
                        entry.id,
                        if (native) null else (entry.md5Checksum ?: "size:${entry.size}"),
                        entry.mimeType.takeIf { native }
                    )
                }
            }
        }
        walk("root", "")
        return DriveTree(entries, blocked)
    }

    // ---------- GitHub ----------

    suspend fun scanGithub(
        pat: String,
        owner: String,
        repo: String,
        branch: String,
        rootDir: File,
        ignoreRules: IgnoreRules,
        stateStore: SyncStateStore,
        sourceKey: String,
        mode: SyncMode
    ): SyncScan = withContext(Dispatchers.IO) {
        val saved = stateStore.getAll(sourceKey)
        val scanned = LocalScanner.scan(rootDir, ignoreRules, saved.isNotEmpty())
        val local = scanned.files.mapValues { LocalFile(LocalFileOps.gitBlobSha(it.value)) }

        val (headCommit, treeSha) = github.getBranchHeadCommit(pat, owner, repo, branch)
        val blobs = github.listBlobsInSubfolder(pat, owner, repo, treeSha, "")
        val remote = blobs.asSequence()
            .filter { it.isRegularFile && isSyncPath(it.repoPath) && !ignoreRules.isIgnored(it.repoPath) }
            .associate { it.repoPath to RemoteFile(it.repoPath, it.sha, null, it.mode) }
        val allRemoteDirs = trackedDirPaths(blobs.map { it.repoPath })
        check(scanned.files.keys.none { it in allRemoteDirs } &&
            blobs.none { it.repoPath in scanned.folders && isSyncPath(it.repoPath) }) {
            "Sinkron dibatalkan: satu jalur dipakai sebagai file dan folder."
        }
        val blocked = scanned.blocked + (blobs.map { it.repoPath }.toSet() - remote.keys)
        val base = eligibleBase(saved, ignoreRules, blocked)

        SyncScan(
            sourceKey = sourceKey,
            rootDir = rootDir,
            mode = mode,
            base = base,
            actions = planFor(mode, local, remote, base)
                .filterNot { isBlocked(it.path, blocked) },
            localItemCount = local.size,
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
        if (scan.base.isNotEmpty() && scan.plan.trashTotal > 0) {
            check(checkedLocalPath(scan.rootDir, "").list() != null) { "Folder sumber lokal tidak dapat dibaca." }
        }
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
            base = scan.base,
            stamp = stamp,
            hashOf = LocalFileOps::md5Hex
        ) { r, dest -> downloadDriveRemote(token, r, dest) }
        val folderIds = scan.driveFolders.toMutableMap()
        val checkedChildren = mutableMapOf<String, Map<String, RawDriveEntry>>()
        suspend fun currentChildren(path: String): Map<String, RawDriveEntry> {
            checkedChildren[path]?.let { return it }
            val id = if (path.isEmpty()) "root" else folderIds.getValue(path).also { id ->
                val actual = currentChildren(path.substringBeforeLast('/', ""))[id]
                check(actual?.mimeType == DriveMimeTypes.FOLDER && actual.name == path.substringAfterLast('/')) {
                    "Folder Drive berubah setelah scan."
                }
            }
            return drive.listSyncChildren(token, id).associateBy { it.id }.also { checkedChildren[path] = it }
        }

        var result = phase1.result
        for (action in phase1.remoteWork) {
            if (!currentCoroutineContext().isActive) break
            try {
                when (action) {
                    is PlannedAction.Upload -> {
                        val src = checkedLocalPath(scan.rootDir, action.path)
                        val parent = action.path.substringBeforeLast('/', "")
                        val parentId = if (parent.isEmpty()) "root" else folderIds[parent]
                            ?: error("Folder induk $parent belum tersedia di Drive.")
                        val r = if (action.local.isFolder) {
                            check(src.isDirectory) { "Folder lokal berubah setelah scan." }
                            val id = drive.createFolder(token, parentId, src.name).id
                            folderIds[action.path] = id
                            RemoteFile(id, FOLDER_STATE, null)
                        } else {
                            check(src.isFile && LocalFileOps.md5Hex(src) == action.local.hash) { "File lokal berubah setelah scan." }
                            driveRemote(drive.uploadFile(token, parentId, action.path, action.remote?.ref, src, action.remote?.nativeMime))
                        }
                        stateStore.put(scan.sourceKey, action.path, PathState(action.local.hash, r.hash, r.ref))
                        result += OpResult(transferred = 1)
                    }
                    is PlannedAction.TrashRemote -> {
                        check(!checkedLocalPath(scan.rootDir, action.path).exists()) { "Jalur lokal muncul lagi setelah scan." }
                        val parent = action.path.substringBeforeLast('/', "")
                        val fresh = currentChildren(parent)[action.remote.ref]
                        check(fresh != null && fresh.name == action.path.substringAfterLast('/') &&
                            (if (action.remote.isFolder) fresh.mimeType == DriveMimeTypes.FOLDER
                            else fresh.md5Checksum == action.remote.hash && fresh.md5Checksum != null)) {
                            "Item Drive berubah setelah scan."
                        }
                        if (action.remote.isFolder) check(drive.listSyncChildren(token, action.remote.ref).isEmpty()) {
                            "Folder Drive tidak kosong."
                        }
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
            base = scan.base,
            stamp = stamp,
            hashOf = LocalFileOps::gitBlobSha,
            pruneEmptyParents = true
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
                        val file = checkedLocalPath(scan.rootDir, action.path)
                        check(file.isFile) { "File lokal berubah setelah scan." }
                        if (file.length() > GithubApiClient.MAX_GITHUB_FILE_BYTES) {
                            result += OpResult.skip(SkipReason.TOO_LARGE_FOR_GITHUB, action.path)
                        } else {
                            val blobSha = github.createBlobFromFile(gh.pat, gh.owner, gh.repo, file)
                            check(blobSha == action.local.hash) { "File lokal berubah setelah scan." }
                            changes += GithubTreeChange(action.path, blobSha, action.remote?.gitMode ?: "100644")
                            uploaded += action.path to PathState(blobSha, blobSha, action.path)
                        }
                    }
                    is PlannedAction.TrashRemote -> {
                        check(!checkedLocalPath(scan.rootDir, action.path).exists()) { "Jalur lokal muncul lagi setelah scan." }
                        val sha = action.remote.hash ?: error("Blob ${action.path} tidak punya sha")
                        changes += GithubTreeChange(action.path, null, action.remote.gitMode)
                        changes += GithubTreeChange(".trash/$stamp/${action.path}", sha, action.remote.gitMode)
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
        /** Aksi yang harus dijalankan di fase remote: Upload dan TrashRemote. */
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
        base: Map<String, PathState>,
        stamp: String,
        hashOf: (File) -> String,
        pruneEmptyParents: Boolean = false,
        download: suspend (RemoteFile, File) -> Unit
    ): Phase1Outcome {
        var result = OpResult()
        val deferred = mutableListOf<PlannedAction>()
        val ordered = actions.sortedWith(compareBy<PlannedAction> {
            when (it) {
                is PlannedAction.TrashLocal, is PlannedAction.TrashRemote -> 1
                else -> 0
            }
        }.thenBy { if (it is PlannedAction.TrashLocal || it is PlannedAction.TrashRemote) -it.path.count { c -> c == '/' } else 0 })
        for (action in ordered) {
            if (!currentCoroutineContext().isActive) break
            try {
                when (action) {
                    is PlannedAction.Download -> {
                        val dest = checkedLocalPath(rootDir, action.path)
                        if (action.remote.isFolder) {
                            check(dest.isDirectory || dest.mkdirs()) { "Folder lokal tidak dapat dibuat." }
                        } else {
                            dest.parentFile?.mkdirs()
                            download(action.remote, dest)
                        }
                        stateStore.put(
                            sourceKey,
                            action.path,
                            PathState(if (action.remote.isFolder) FOLDER_STATE else hashOf(dest), action.remote.hash, action.remote.ref)
                        )
                        result += OpResult(transferred = 1)
                    }
                    is PlannedAction.TrashLocal -> {
                        val src = checkedLocalPath(rootDir, action.path)
                        if (action.isFolder) {
                            // Do not overwrite the matching folder already created in .trash for child files.
                            check(!src.exists() || (src.list()?.isEmpty() == true && src.delete())) {
                                "Folder lokal tidak kosong atau tidak dapat dihapus."
                            }
                        } else {
                            if (src.exists()) check(src.isFile && hashOf(src) == base.getValue(action.path).localHash) {
                                "File lokal berubah setelah scan."
                            }
                            moveToTrash(rootDir, action.path, stamp)
                            if (pruneEmptyParents) pruneParents(rootDir, src.parentFile)
                        }
                        stateStore.delete(sourceKey, action.path)
                        result += OpResult(transferred = 1)
                    }
                    is PlannedAction.Conflict -> {
                        // Remote menang: lokal ke trash (tidak permanen), remote diunduh ke path asli.
                        val src = checkedLocalPath(rootDir, action.path)
                        check(src.isFile && hashOf(src) == action.local.hash) { "File lokal berubah setelah scan." }
                        moveToTrash(rootDir, action.path, stamp)
                        download(action.remote, File(rootDir, action.path).also { it.parentFile?.mkdirs() })
                        stateStore.put(
                            sourceKey,
                            action.path,
                            PathState(hashOf(File(rootDir, action.path)), action.remote.hash, action.remote.ref)
                        )
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
        return Phase1Outcome(result, deferred)
    }

    /** Pindah berkas lokal ke `.trash/<stamp>/<path>`. Folder .trash tidak ikut sync (nama diawali titik). */
    private fun moveToTrash(rootDir: File, path: String, stamp: String) {
        val src = File(rootDir, path)
        if (!src.exists()) return
        val dest = checkedLocalPath(rootDir, ".trash/$stamp/$path")
        dest.parentFile?.mkdirs()
        if (!src.renameTo(dest)) {
            src.copyTo(dest, overwrite = true)
            check(src.delete()) { "File lokal tidak dapat dipindahkan ke trash." }
        }
    }

    private fun checkedLocalPath(root: File, path: String): File {
        val parent = root.absoluteFile.parentFile ?: error("Folder sumber tidak valid.")
        val canonicalRoot = File(parent.canonicalFile, root.name)
        val dest = File(root, path)
        check(root.canonicalFile == canonicalRoot && dest.canonicalFile == File(canonicalRoot, path)) {
            "Jalur lokal tidak aman: $path"
        }
        return dest
    }

    private fun pruneParents(root: File, start: File?) {
        var dir = start
        while (dir != null && dir != root && dir.list()?.isEmpty() == true && dir.delete()) dir = dir.parentFile
    }

    private fun eligibleBase(base: Map<String, PathState>, rules: IgnoreRules, blocked: Set<String>) =
        base.filterKeys { isSyncPath(it) && !rules.isIgnored(it) && !isBlocked(it, blocked) }

    private fun isBlocked(path: String, blocked: Set<String>): Boolean {
        var current = path
        while (current.isNotEmpty()) {
            if (current in blocked) return true
            current = current.substringBeforeLast('/', "")
        }
        return false
    }

    private fun verifyNoTypeCollision(files: Set<String>, folders: Set<String>, remote: Map<String, RemoteFile>) {
        check(files.none { remote[it]?.isFolder == true } && folders.none { remote[it]?.isFolder == false }) {
            "Sinkron dibatalkan: satu jalur dipakai sebagai file dan folder."
        }
    }

    /** Older versions recorded only files. Infer deleted parents from known, unchanged children. */
    private fun inferLegacyFolders(
        local: Map<String, LocalFile>,
        remote: Map<String, RemoteFile>,
        base: MutableMap<String, PathState>
    ) {
        for ((path, state) in base.toMap()) {
            if (state.localHash == FOLDER_STATE || state.remoteHash == FOLDER_STATE) continue
            val action = TwoWayPlanner.plan(path, local[path], remote[path], state)
            if (action !is PlannedAction.TrashRemote && action !is PlannedAction.TrashLocal) continue
            var parent = path.substringBeforeLast('/', "")
            while (parent.isNotEmpty()) {
                if (action is PlannedAction.TrashRemote && parent !in local && remote[parent]?.isFolder == true) {
                    base.putIfAbsent(parent, PathState(FOLDER_STATE, FOLDER_STATE, remote.getValue(parent).ref))
                } else if (action is PlannedAction.TrashLocal && parent !in remote && local[parent]?.isFolder == true) {
                    base.putIfAbsent(parent, PathState(FOLDER_STATE, FOLDER_STATE, "legacy-folder"))
                }
                parent = parent.substringBeforeLast('/', "")
            }
        }
    }

    /** Only remove a folder when all children will be removed on that side. */
    private fun guardFolderDeletes(
        actions: List<PlannedAction>,
        local: Map<String, LocalFile>,
        remote: Map<String, RemoteFile>,
        blockedLocal: Set<String>,
        blockedRemote: Set<String>
    ): List<PlannedAction> {
        val localChildren = (local.keys + blockedLocal).groupBy { it.substringBeforeLast('/', "") }
        val remoteChildren = (remote.keys + blockedRemote).groupBy { it.substringBeforeLast('/', "") }
        val safe = actions.associateBy { it.path }.toMutableMap()
        for (action in actions.sortedByDescending { it.path.count { c -> c == '/' } }) {
            val path = action.path
            if (action is PlannedAction.TrashLocal && action.isFolder) {
                val children = localChildren[path].orEmpty()
                if (children.all { safe[it] is PlannedAction.TrashLocal }) continue
                if (children.any { safe[it] is PlannedAction.Upload }) {
                    // An edited or newly added local child survived a remote folder deletion.
                    safe[path] = PlannedAction.Upload(path, local.getValue(path), null)
                } else safe.remove(path)
            }
            if (action is PlannedAction.TrashRemote && action.remote.isFolder) {
                val children = remoteChildren[path].orEmpty()
                if (children.all { safe[it] is PlannedAction.TrashRemote }) continue
                if (children.any { safe[it] is PlannedAction.Download }) {
                    safe[path] = PlannedAction.Download(path, action.remote)
                } else safe.remove(path)
            }
        }
        return actions.mapNotNull { safe[it.path] }
    }

    private fun stampNow(): String = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
}
