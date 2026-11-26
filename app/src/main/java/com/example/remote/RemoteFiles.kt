package com.example.remote

import android.net.Uri
import android.os.Environment
import com.example.SyncBebasApp
import com.example.data.db.GithubRepoEntity
import com.example.drive.DriveMimeTypes
import com.example.github.GithubEmptyRepositoryException
import com.example.github.GithubTreeChange
import com.example.github.GithubTreeEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

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

    suspend fun pickFolder(uri: Uri): PickedFolder = withContext(Dispatchers.IO) { picker.folder(uri) }

    suspend fun uploadFiles(location: Location, uris: List<Uri>): String = withContext(Dispatchers.IO) {
        upload(location, scanUploadTrees(uris.distinct().map(picker::file), picker::children))
    }

    suspend fun uploadFolders(location: Location, folders: List<PickedFolder>): String = withContext(Dispatchers.IO) {
        val roots = folders.map { picker.folder(it.tree) }.map {
            UploadDocument(it.name, picker.rootUri(it.tree), isFolder = true)
        }
        upload(location, scanUploadTrees(roots, picker::children))
    }

    private suspend fun upload(location: Location, tree: UploadTree<Uri>): String {
        check(location.writable && tree.items.isNotEmpty())
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
