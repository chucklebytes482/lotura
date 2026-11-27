package com.example.remote

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract as Documents
import android.provider.OpenableColumns
import java.io.File

internal data class PickedFolder(val tree: Uri, val name: String)

/** Folder perangkat yang dibaca langsung lewat path file (izin Akses semua file), bukan lewat pemilih sistem. */
internal fun Uri.isDeviceFile(): Boolean = scheme == "file"

internal class UploadPicker(private val resolver: ContentResolver) {
    fun file(uri: Uri): UploadDocument<Uri> {
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
        require(name != null && RemoteNames.validNewName(name)) { "Pilihan berisi nama tersembunyi, tidak diketahui, atau tidak valid." }
        return UploadDocument(name, uri, isFolder = false)
    }

    fun folder(tree: Uri): PickedFolder {
        if (tree.isDeviceFile()) {
            val dir = File(checkNotNull(tree.path) { "Path folder tidak valid." })
            require(dir.isDirectory && RemoteNames.validNewName(dir.name)) { "Folder tersembunyi atau nama folder tidak valid." }
            return PickedFolder(tree, dir.name)
        }
        val root = rootUri(tree)
        return checkNotNull(resolver.query(root, columns, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val name = cursor.getString(1)
            require(cursor.getString(2) == Documents.Document.MIME_TYPE_DIR && name != null && RemoteNames.validNewName(name)) {
                "Folder tersembunyi atau nama folder tidak valid."
            }
            PickedFolder(tree, name)
        }) { "Folder yang dipilih tidak bisa dibaca." }
    }

    fun rootUri(tree: Uri): Uri = if (tree.isDeviceFile()) tree else Documents.buildDocumentUriUsingTree(tree, Documents.getTreeDocumentId(tree))

    fun children(parent: Uri): List<UploadDocument<Uri>> {
        if (parent.isDeviceFile()) {
            val items = File(checkNotNull(parent.path) { "Path folder tidak valid." }).listFiles()
                ?: error("Isi folder perangkat tidak dapat dibaca. Pastikan izin Akses semua file aktif.")
            return items.map { UploadDocument(it.name, Uri.fromFile(it), it.isDirectory) }
        }
        val collection = Documents.buildChildDocumentsUriUsingTree(parent, Documents.getDocumentId(parent))
        return checkNotNull(resolver.query(collection, columns, null, null, null)?.use { cursor ->
            check(!cursor.extras.getBoolean(Documents.EXTRA_LOADING) && cursor.extras.getString(Documents.EXTRA_ERROR) == null) {
                "Isi folder belum bisa dimuat sepenuhnya. Coba lagi."
            }
            buildList {
                while (cursor.moveToNext()) {
                    val name = checkNotNull(cursor.getString(1)) { "Ada item tanpa nama di folder yang dipilih." }
                    val id = checkNotNull(cursor.getString(0)) { "Ada item tanpa ID di folder yang dipilih." }
                    add(UploadDocument(name, Documents.buildDocumentUriUsingTree(parent, id),
                        cursor.getString(2) == Documents.Document.MIME_TYPE_DIR))
                }
            }
        }) { "Isi folder yang dipilih tidak dapat dibaca." }
    }

    private companion object {
        val columns = arrayOf(Documents.Document.COLUMN_DOCUMENT_ID, Documents.Document.COLUMN_DISPLAY_NAME,
            Documents.Document.COLUMN_MIME_TYPE)
    }
}
