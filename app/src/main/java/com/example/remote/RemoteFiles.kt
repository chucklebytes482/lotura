package com.example.remote

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
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

    suspend fun upload(location: Location, uris: List<Uri>): String = withContext(Dispatchers.IO) {
        check(location.writable && uris.isNotEmpty())
        val files = uris.distinct().map { it to uploadName(app, it) }
        requireAvailable(emptyList(), files.map { it.second })
        // The picker name is validated before reading or sending any content.
        val scratch = mutableListOf<File>()
        try {
            val staged = files.map { (uri, name) ->
                ensureActive()
                val temp = File.createTempFile("lotura-upload-", ".tmp", app.cacheDir).also { scratch += it }
                val limit = if (location.source is Source.Github) GithubUploadLimit else Long.MAX_VALUE
                checkNotNull(app.contentResolver.openInputStream(uri)) { "File $name tidak bisa dibaca." }.use { input ->
                    temp.outputStream().use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var count = 0L
                        while (true) {
                            ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            count += n
                            check(count <= limit) { "$name melebihi batas unggah GitHub (45 MiB)." }
                            out.write(buffer, 0, n)
                        }
                    }
                }
                name to temp
            }
            when (val source = location.source) {
                is Source.Drive -> {
                    val token = app.resolveDriveToken(source.email)
                    requireAvailable(driveEntries(token, location.ref).map { it.name }, staged.map { it.first })
                    var completed = 0
                    try {
                        for ((name, file) in staged) {
                            ensureActive()
                            drive.uploadFile(token, location.ref, name, file)
                            completed++
                        }
                    } catch (e: Exception) {
                        ensureActive()
                        error("$completed dari ${staged.size} file terunggah. ${e.message.orEmpty()}")
                    }
                }
                is Source.Github -> with(git(source)) {
                    var snapshot = folder(location)
                    requireAvailable(snapshot.entries.map { it.name }, staged.map { it.first })
                    var remaining = staged
                    if (snapshot.head == null) {
                        val (name, file) = staged.first()
                        github.createInitialFile(token, repo.owner, repo.name, name, "Lotura: Unggah file pertama", file)
                        remaining = staged.drop(1)
                    }
                    if (remaining.isNotEmpty()) {
                        try {
                            if (snapshot.head == null) snapshot = folder(location)
                            requireAvailable(snapshot.entries.map { it.name }, remaining.map { it.first })
                            val changes = remaining.map { (name, file) ->
                                val sha = github.createBlobFromFile(token, repo.owner, repo.name, file)
                                GithubTreeChange(childPath(location.ref, name), sha)
                            }
                            commit(snapshot, changes, "Lotura: Unggah ${changes.size} file")
                        } catch (e: Exception) {
                            ensureActive()
                            if (remaining.size == staged.size) throw e
                            error("1 dari ${staged.size} file terunggah. ${e.message.orEmpty()}")
                        }
                    }
                }
            }
            "${staged.size} file terunggah."
        } finally {
            scratch.forEach { it.delete() }
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
        fun uploadName(context: Context, uri: Uri): String {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            require(name != null && RemoteNames.validNewName(name)) {
                "Pilihan berisi nama tersembunyi, tidak diketahui, atau tidak valid. Tidak ada file yang diunggah."
            }
            return name
        }
    }
}
