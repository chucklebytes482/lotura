package com.example.remote

import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.example.SyncBebasApp
import com.example.data.db.GithubRepoEntity
import com.example.data.db.MirrorJobEntity
import com.example.drive.DriveMimeTypes
import com.example.github.GithubEmptyRepositoryException
import com.example.github.GithubTreeChange
import com.example.github.GithubTreeEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** Nama folder yang dipakai bersama: di perangkat (/storage/emulated/0/autosync) dan di root Google Drive. */
internal const val AUTOSYNC_NAME = "autosync"

/** Satu-satunya folder perangkat yang bisa dijelajahi dan dijadwalkan untuk auto sync. */
internal fun deviceRoot(): File = File(Environment.getExternalStorageDirectory(), AUTOSYNC_NAME)

internal sealed interface Source {
    val title: String
    data class Drive(val email: String) : Source { override val title = email }
    data class Github(val repo: GithubRepoEntity) : Source { override val title = repo.fullName }
}

internal data class Folder(val name: String, val ref: String)
internal data class Location(val source: Source, val folders: List<Folder> = emptyList()) {
    val ref: String get() = folders.lastOrNull()?.ref ?: if (source is Source.Drive) "root" else ""
    val title: String get() = folders.lastOrNull()?.name ?: source.title
    val writable: Boolean get() = (source as? Source.Github)?.repo?.archived != true
    fun open(item: RemoteEntry) = copy(folders = folders + Folder(item.name, item.ref))
}

internal data class RemoteEntry(
    val name: String,
    val ref: String,
    val revision: String,
    val kind: Kind,
    val mime: String = ""
) {
    enum class Kind { FOLDER, FILE, LINK, UNSUPPORTED }
    val isFolder get() = kind == Kind.FOLDER
    val downloadable get() = kind == Kind.FILE || isFolder
}

internal data class DownloadResult(val file: File, val skipped: Int)

internal enum class UploadMode { ADD, MIRROR }

/** Auto sync berhenti karena folder perangkat tidak bisa dipakai lagi. Job dinonaktifkan. */
internal class MirrorStoppedException(message: String) : Exception(message)

internal class RemoteFiles(private val app: SyncBebasApp) {
    private val github = app.githubApiClient
    private val drive = app.driveApiClient

    suspend fun list(location: Location): List<RemoteEntry> = when (val source = location.source) {
        is Source.Drive -> driveEntries(app.resolveDriveToken(source.email), location.ref)
        is Source.Github -> git(source).folder(location).entries.map { it.remote(location.ref) }
    }.filter { RemoteNames.visible(it.name) }
        .sortedWith(compareByDescending<RemoteEntry> { it.isFolder }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    suspend fun createFolder(location: Location, name: String) {
        require(location.writable && RemoteNames.validNewName(name)) { "Nama folder tidak valid atau tersembunyi." }
        when (val source = location.source) {
            is Source.Drive -> {
                val token = app.resolveDriveToken(source.email)
                requireAvailable(driveEntries(token, location.ref).map { it.name }, listOf(name))
                drive.createFolder(token, location.ref, name)
            }
            is Source.Github -> with(git(source)) {
                val snapshot = folder(location)
                requireAvailable(snapshot.entries.map { it.name }, listOf(name))
                val path = childPath(childPath(location.ref, name), RemoteNames.EMPTY_FOLDER_MARKER)
                val message = "Lotura: Buat folder $name"
                if (snapshot.head == null) {
                    github.createInitialFile(token, repo.owner, repo.name, path, message, null)
                } else {
                    val sha = github.createEmptyBlob(token, repo.owner, repo.name)
                    commit(snapshot, listOf(GithubTreeChange(path, sha)), message)
                }
            }
        }
    }

    private val picker = UploadPicker(app.contentResolver)

    suspend fun pickFolder(uri: Uri): PickedFolder = withContext(Dispatchers.IO) {
        // Simpan izin baca agar auto sync bisa membaca folder ini setelah aplikasi ditutup.
        runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        picker.folder(uri)
    }

    /** Menyimpan satu job per folder dan menjadwalkannya. Izin folder perangkat harus sudah tersimpan. */
    suspend fun enableMirrorAuto(location: Location, folders: List<PickedFolder>, hours: Int) = withContext(Dispatchers.IO) {
        val source = location.source as? Source.Drive ?: error("Auto sync hanya untuk Google Drive.")
        val persisted = persistedTrees()
        // Memastikan folder autosync di Drive ada (dibuat jika belum) dan token akun masih berlaku.
        ensureAutosyncFolder(app.resolveDriveToken(source.email))
        val base = deviceRoot().canonicalPath + File.separator
        for (folder in folders) {
            check(folder.tree.isDeviceFile() && Environment.isExternalStorageManager() || folder.tree in persisted) { "Izin akses folder ${folder.name} tidak tersimpan. Auto sync tidak diaktifkan." }
            check(!folder.tree.isDeviceFile() || File(folder.tree.path.orEmpty()).canonicalPath.startsWith(base)) {
                "Folder ${folder.name} harus ada di dalam ${deviceRoot().path}."
            }
            val job = MirrorJobEntity(
                jobKey = MirrorAutoSync.mirrorJobKey(source.email, "root", folder.tree.toString()),
                email = source.email,
                parentRef = "root",
                parentTitle = AUTOSYNC_NAME,
                folderName = folder.name,
                treeUri = folder.tree.toString(),
                intervalHours = hours,
                enabled = true,
                lastRunAt = 0L,
                lastResult = "Menunggu jadwal pertama."
            )
            app.repository.saveMirrorJob(job)
            MirrorAutoSync.schedule(app, job)
        }
    }

    /** Dipanggil worker. Melempar MirrorStoppedException jika folder perangkat tidak bisa dipakai lagi. */
    suspend fun autoMirror(job: MirrorJobEntity): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(job.treeUri)
        if (uri.isDeviceFile()) {
            if (!Environment.isExternalStorageManager()) throw MirrorStoppedException("izin Akses semua file sudah dicabut.")
        } else if (uri !in persistedTrees()) throw MirrorStoppedException("izin akses folder perangkat sudah dicabut.")
        val folder = try {
            picker.folder(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw MirrorStoppedException("folder perangkat tidak bisa dibaca (${e.message ?: "tidak diketahui"}).")
        }
        if (folder.name != job.folderName) {
            throw MirrorStoppedException("nama folder perangkat berubah dari ${job.folderName} menjadi ${folder.name}.")
        }
        val autosyncId = ensureAutosyncFolder(app.resolveDriveToken(job.email))
        val location = Location(Source.Drive(job.email), listOf(Folder(AUTOSYNC_NAME, autosyncId)))
        val roots = listOf(UploadDocument(folder.name, picker.rootUri(uri), isFolder = true))
        upload(location, scanUploadTrees(roots, picker::children), UploadMode.MIRROR)
    }

    /** Isi satu folder perangkat untuk penjelajahan. Butuh izin Akses semua file. */
    suspend fun listDevice(dir: File): List<File> = withContext(Dispatchers.IO) {
        check(Environment.isExternalStorageManager()) { "Izin Akses semua file belum aktif." }
        if (dir == deviceRoot()) dir.mkdirs()
        check(dir.isDirectory) { "Folder ${dir.path} tidak bisa dibaca." }
        (dir.listFiles() ?: emptyArray())
            .filter { RemoteNames.visible(it.name) }
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /** Id folder "autosync" di root Google Drive. Dibuat otomatis jika belum ada. */
    private suspend fun ensureAutosyncFolder(token: String): String {
        val existing = drive.listChildren(token, "root").firstOrNull { it.name == AUTOSYNC_NAME && it.mimeType == DriveMimeTypes.FOLDER }
        return existing?.id ?: drive.createFolder(token, "root", AUTOSYNC_NAME).id
    }

    private fun persistedTrees(): Set<Uri> =
        app.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }.toSet()

    suspend fun uploadFiles(location: Location, uris: List<Uri>): String = withContext(Dispatchers.IO) {
        upload(location, scanUploadTrees(uris.distinct().map(picker::file), picker::children), UploadMode.ADD)
    }

    suspend fun uploadFolders(location: Location, folders: List<PickedFolder>, mode: UploadMode): String = withContext(Dispatchers.IO) {
        val roots = folders.map { picker.folder(it.tree) }.map {
            UploadDocument(it.name, picker.rootUri(it.tree), isFolder = true)
        }
        upload(location, scanUploadTrees(roots, picker::children), mode)
    }

    private suspend fun upload(location: Location, tree: UploadTree<Uri>, mode: UploadMode): String {
        check(location.writable && tree.items.isNotEmpty())
        check(mode == UploadMode.ADD || location.source is Source.Drive) { "Mode mirror hanya untuk Google Drive." }
        val scratch = mutableListOf<File>()
        try {
            // Validate all names and stage all selected files before changing the remote.
            val limit = if (location.source is Source.Github) GithubUploadLimit else Long.MAX_VALUE
            val staged = tree.items.mapUploadFiles { item ->
                coroutineContext.ensureActive()
                val (name, uri) = item
                val temp = File.createTempFile("lotura-upload-", ".tmp", app.cacheDir).also { scratch += it }
                checkNotNull(app.contentResolver.openInputStream(uri)) { "File $name tidak bisa dibaca." }.use { input ->
                    temp.outputStream().use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var count = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            count += n
                            check(count <= limit) { "$name melebihi batas unggah GitHub (45 MiB)." }
                            out.write(buffer, 0, n)
                        }
                    }
                }
                temp
            }
            if (mode == UploadMode.MIRROR) {
                val source = location.source as? Source.Drive ?: error("Mode mirror hanya untuk Google Drive.")
                val summary = mirrorToDrive(location, source, staged)
                return summary + if (tree.skipped > 0) " ${tree.skipped} item tersembunyi dilewati." else ""
            }
            when (val source = location.source) {
                is Source.Drive -> uploadToDrive(location, source, staged)
                is Source.Github -> uploadToGithub(location, source, staged)
            }
            val folderCount = staged.count { it is UploadItem.Folder }
            val message = if (folderCount == 0) "${scratch.size} file terunggah."
                else "$folderCount folder terunggah (${scratch.size} file)."
            return message + if (tree.skipped > 0) " ${tree.skipped} item tersembunyi dilewati." else ""
        } finally {
            scratch.forEach { it.delete() }
        }
    }

    private class MirrorStats {
        var uploaded = 0
        var unchanged = 0
        var trashed = 0
        var foldersCreated = 0
    }

    /**
     * Membuat subfolder Drive dengan nama sama dengan folder perangkat menjadi salinan persis.
     * Berkas sama (MD5 cocok) dibiarkan. Berkas beda diganti. Item Drive yang tidak ada di perangkat
     * dipindah ke Sampah. Hanya isi subfolder itu yang disentuh, folder lain di lokasi aktif tidak.
     */
    private suspend fun mirrorToDrive(location: Location, source: Source.Drive, roots: List<UploadItem<File>>): String {
        val token = app.resolveDriveToken(source.email)
        val stats = MirrorStats()
        for (root in roots) {
            coroutineContext.ensureActive()
            val folder = root as? UploadItem.Folder ?: error("Mirror hanya menerima folder.")
            val same = drive.listChildren(token, location.ref).filter { it.name == folder.name }
            val existing = same.firstOrNull { it.mimeType == DriveMimeTypes.FOLDER }
            val id = existing?.id ?: drive.createFolder(token, location.ref, folder.name).id.also { stats.foldersCreated++ }
            same.filter { it.id != id }.forEach { drive.trashFile(token, it.id); stats.trashed++ }
            mirrorChildren(token, id, folder.children, stats)
        }
        return "Mirror selesai: ${stats.uploaded} file diunggah, ${stats.unchanged} tidak berubah, " +
            "${stats.trashed} item dipindah ke Sampah, ${stats.foldersCreated} folder dibuat."
    }

    private suspend fun mirrorChildren(token: String, parent: String, local: List<UploadItem<File>>, stats: MirrorStats) {
        val remote = drive.listChildren(token, parent).filter { RemoteNames.visible(it.name) }
        for (item in local) {
            coroutineContext.ensureActive()
            val same = remote.filter { it.name == item.name }
            when (item) {
                is UploadItem.File -> {
                    val unchanged = same.singleOrNull()?.takeIf {
                        it.mimeType != DriveMimeTypes.FOLDER && it.md5Checksum != null &&
                            it.md5Checksum.equals(md5Hex(item.content), ignoreCase = true)
                    }
                    if (unchanged != null) {
                        stats.unchanged++
                    } else {
                        same.forEach { drive.trashFile(token, it.id); stats.trashed++ }
                        drive.uploadFile(token, parent, item.name, item.content)
                        stats.uploaded++
                    }
                }
                is UploadItem.Folder -> {
                    val existing = same.firstOrNull { it.mimeType == DriveMimeTypes.FOLDER }
                    val id = existing?.id ?: drive.createFolder(token, parent, item.name).id.also { stats.foldersCreated++ }
                    same.filter { it.id != id }.forEach { drive.trashFile(token, it.id); stats.trashed++ }
                    mirrorChildren(token, id, item.children, stats)
                }
            }
        }
        val localNames = local.map { it.name }.toSet()
        for (extra in remote.filter { it.name !in localNames }) {
            coroutineContext.ensureActive()
            drive.trashFile(token, extra.id)
            stats.trashed++
        }
    }

    private fun md5Hex(file: File): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun uploadToDrive(location: Location, source: Source.Drive, items: List<UploadItem<File>>) {
        val token = app.resolveDriveToken(source.email)
        requireAvailable(driveEntries(token, location.ref).map { it.name }, items.map { it.name })
        var foldersCreated = 0
        var filesUploaded = 0
        suspend fun uploadItems(parent: String, entries: List<UploadItem<File>>) {
            for (item in entries) {
                coroutineContext.ensureActive()
                when (item) {
                    is UploadItem.File -> {
                        drive.uploadFile(token, parent, item.name, item.content)
                        filesUploaded++
                    }
                    is UploadItem.Folder -> {
                        val id = drive.createFolder(token, parent, item.name)
                        foldersCreated++
                        uploadItems(id.id, item.children)
                    }
                }
            }
        }
        try {
            uploadItems(location.ref, items)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            if (foldersCreated == 0 && filesUploaded == 0) throw e
            error("Unggah belum selesai: $foldersCreated folder dibuat, $filesUploaded file terunggah. ${e.message.orEmpty()}")
        }
    }

    private suspend fun uploadToGithub(location: Location, source: Source.Github, items: List<UploadItem<File>>) {
        with(git(source)) {
            var snapshot = folder(location)
            requireAvailable(snapshot.entries.map { it.name }, items.map { it.name })
            val leaves = items.gitUploadFiles()
            var initialized = false
            try {
                val remaining = if (snapshot.head == null) {
                    val first = leaves.first()
                    val initialHead = github.createInitialFile(token, repo.owner, repo.name, first.path,
                        "Lotura: Tambahkan item pertama", first.content)
                    initialized = true
                    if (leaves.size == 1) return
                    snapshot = folder(location)
                    check(snapshot.head == initialHead) { changed }
                    leaves.drop(1)
                } else leaves
                val changes = remaining.map { (path, file) ->
                    coroutineContext.ensureActive()
                    val sha = if (file == null) github.createEmptyBlob(token, repo.owner, repo.name)
                        else github.createBlobFromFile(token, repo.owner, repo.name, file)
                    GithubTreeChange(childPath(location.ref, path), sha)
                }
                commit(snapshot, changes, "Lotura: Unggah ${items.size} item")
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                if (!initialized) throw e
                error("Unggah belum selesai. Item pertama sudah disimpan di repo. ${e.message.orEmpty()}")
            }
        }
    }

    suspend fun delete(location: Location, item: RemoteEntry) {
        check(location.writable && RemoteNames.visible(item.name))
        when (val source = location.source) {
            is Source.Drive -> {
                val token = app.resolveDriveToken(source.email)
                check(driveEntries(token, location.ref).any { it == item }) { changed }
                drive.trashFile(token, item.ref)
            }
            is Source.Github -> with(git(source)) {
                val snapshot = folder(location)
                val entry = snapshot.entries.firstOrNull { it.remote(location.ref) == item } ?: error(changed)
                val changes = mutableListOf(GithubTreeChange(item.ref, null, entry.mode, entry.type))
                if (snapshot.entries.size == 1 && location.ref.isNotEmpty()) {
                    val sha = github.createEmptyBlob(token, repo.owner, repo.name)
                    changes += GithubTreeChange(childPath(location.ref, RemoteNames.EMPTY_FOLDER_MARKER), sha)
                }
                commit(snapshot, changes, "Lotura: Hapus ${item.name}")
            }
        }
    }

    suspend fun download(location: Location, item: RemoteEntry): DownloadResult = withContext(Dispatchers.IO) {
        check(item.downloadable && RemoteNames.visible(item.name))
        val base = Environment.getExternalStorageDirectory()
        val dirs = when (val source = location.source) {
            is Source.Drive -> listOf("google drive", source.email)
            is Source.Github -> listOf("github", source.repo.owner, source.repo.name)
        }
        val dest = dirs.fold(base) { parent, name ->
            // Account and repo labels are grouping only, not untrusted path components.
            val child = RemoteNames.safeChild(parent, RemoteNames.localName(name.trimStart('.').ifBlank { "_" }))
            check(child.isDirectory || child.mkdir()) { "Tidak dapat membuat ${child.absolutePath}." }
            child
        }
        val stage = RemoteNames.safeChild(dest, ".lotura-${UUID.randomUUID()}")
        check(stage.mkdir()) { "Tidak dapat membuat lokasi unduhan sementara." }
        try {
            val payload = File(stage, "payload")
            val skipped = when (val source = location.source) {
                is Source.Drive -> {
                    val token = app.resolveDriveToken(source.email)
                    check(driveEntries(token, location.ref).any { it == item }) { changed }
                    copyTree(item, payload, { driveEntries(token, it.ref) }) { entry, file ->
                        val export = DriveMimeTypes.exportSpecFor(entry.mime)
                        if (export == null) drive.downloadFile(token, entry.ref, file)
                        else drive.exportGoogleWorkspaceFile(token, entry.ref, export.exportMimeType, file)
                    }
                }
                is Source.Github -> with(git(source)) {
                    copyTree(item, payload, { parent -> tree(parent.revision).map { it.remote(parent.ref) } }) { entry, file ->
                        github.downloadBlobToFile(token, repo.owner, repo.name, entry.revision, file)
                    }
                }
            }
            val target = RemoteNames.unusedChild(dest, downloadName(item), item.isFolder)
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                java.nio.file.Files.move(payload.toPath(), target.toPath())
            } else {
                // Reserve the name first so a regular file can never overwrite an existing download.
                if (item.isFolder) check(target.mkdir()) else check(target.createNewFile())
                if (!payload.renameTo(target)) {
                    target.delete()
                    error("Tidak dapat menyimpan unduhan.")
                }
            }
            DownloadResult(target, skipped)
        } finally {
            stage.deleteRecursively()
        }
    }

    private suspend fun copyTree(
        entry: RemoteEntry,
        dest: File,
        children: suspend (RemoteEntry) -> List<RemoteEntry>,
        save: suspend (RemoteEntry, File) -> Unit
    ): Int {
        coroutineContext.ensureActive()
        if (!entry.isFolder) {
            save(entry, dest)
            return 0
        }
        check(dest.mkdir()) { "Tidak dapat membuat folder unduhan." }
        var skipped = 0
        for (item in children(entry)) {
            if (!RemoteNames.visible(item.name)) continue
            if (!item.downloadable) { skipped++; continue }
            skipped += copyTree(item, RemoteNames.unusedChild(dest, downloadName(item), item.isFolder), children, save)
        }
        return skipped
    }

    private suspend fun driveEntries(token: String, parent: String): List<RemoteEntry> =
        drive.listChildren(token, parent).filterNot { it.trashed }.map { entry ->
            val kind = when {
                entry.mimeType == DriveMimeTypes.FOLDER -> RemoteEntry.Kind.FOLDER
                entry.mimeType == DriveMimeTypes.SHORTCUT -> RemoteEntry.Kind.LINK
                entry.mimeType.startsWith("application/vnd.google-apps.") &&
                    DriveMimeTypes.exportSpecFor(entry.mimeType) == null -> RemoteEntry.Kind.UNSUPPORTED
                else -> RemoteEntry.Kind.FILE
            }
            // Use the actual entry ID, never the shortcut target, for delete.
            RemoteEntry(entry.name, entry.id, entry.id, kind, entry.mimeType)
        }

    private fun GithubTreeEntry.remote(parent: String): RemoteEntry = RemoteEntry(name, childPath(parent, name), sha,
        when {
            type == "tree" -> RemoteEntry.Kind.FOLDER
            mode == "100644" || mode == "100755" -> RemoteEntry.Kind.FILE
            else -> RemoteEntry.Kind.LINK
        })

    private data class GitFolder(val head: String?, val root: String?, val entries: List<GithubTreeEntry>)
    private inner class Git(val token: String, val repo: GithubRepoEntity) {
        suspend fun tree(sha: String) = github.listTree(token, repo.owner, repo.name, sha)
        suspend fun folder(location: Location): GitFolder {
            val (head, root) = try {
                github.getBranchHeadCommit(token, repo.owner, repo.name, repo.defaultBranch)
            } catch (_: GithubEmptyRepositoryException) {
                check(location.folders.isEmpty()) { changed }
                return GitFolder(null, null, emptyList())
            }
            var tree = tree(root)
            for (f in location.folders) {
                val next = tree.firstOrNull { it.name == f.name && it.type == "tree" } ?: error(changed)
                tree = tree(next.sha)
            }
            return GitFolder(head, root, tree)
        }
        suspend fun commit(snapshot: GitFolder, changes: List<GithubTreeChange>, message: String) =
            github.createAtomicCommit(token, repo.owner, repo.name, repo.defaultBranch,
                checkNotNull(snapshot.head), checkNotNull(snapshot.root), changes, message)
    }

    private suspend fun git(source: Source.Github) = Git(app.repository.getGithubPat(source.repo.accountId)
        ?: error("PAT tidak tersedia. Sambungkan ulang akun GitHub."), source.repo)

    private companion object {
        const val changed = "Isi folder berubah di remote. Muat ulang lalu coba lagi."
        const val GithubUploadLimit = com.example.github.GithubApiClient.MAX_GITHUB_FILE_BYTES
        fun childPath(parent: String, name: String) = if (parent.isEmpty()) name else "$parent/$name"
        fun downloadName(item: RemoteEntry) = DriveMimeTypes.localFileNameForDriveItem(item.name, item.mime)
        fun requireAvailable(existing: List<String>, names: List<String>) {
            val seen = existing.toMutableSet()
            for (name in names) require(seen.add(name)) { "Sudah ada item bernama $name. Tidak ada file yang ditimpa." }
        }
    }
}
