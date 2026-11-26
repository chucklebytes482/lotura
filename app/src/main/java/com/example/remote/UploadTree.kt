package com.example.remote

import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal sealed interface UploadItem<out T> {
    val name: String
    data class File<T>(override val name: String, val content: T) : UploadItem<T>
    data class Folder<T>(override val name: String, val children: List<UploadItem<T>>) : UploadItem<T>
}

internal data class UploadDocument<T>(val name: String, val content: T, val isFolder: Boolean)
internal data class UploadTree<T>(val items: List<UploadItem<T>>, val skipped: Int)
internal data class GitUploadFile<T>(val path: String, val content: T?)

internal suspend fun <T> scanUploadTrees(
    roots: List<UploadDocument<T>>,
    children: (T) -> List<UploadDocument<T>>
): UploadTree<T> {
    var skipped = 0
    val ancestors = mutableSetOf<T>()
    suspend fun scan(documents: List<UploadDocument<T>>, skipHidden: Boolean): List<UploadItem<T>> {
        val seen = mutableSetOf<String>()
        return documents.mapNotNull { doc ->
            coroutineContext.ensureActive()
            if (skipHidden && doc.name.trimStart().startsWith('.')) {
                skipped++ // Do not read or descend into hidden folders.
                return@mapNotNull null
            }
            require(RemoteNames.validNewName(doc.name)) { "Pilihan berisi nama tersembunyi atau tidak valid." }
            require(seen.add(doc.name)) { "Ada nama ganda dalam folder yang sama: ${doc.name}." }
            if (!doc.isFolder) UploadItem.File(doc.name, doc.content)
            else {
                check(ancestors.add(doc.content)) { "Siklus folder terdeteksi: ${doc.name}." }
                try {
                    UploadItem.Folder(doc.name, scan(children(doc.content), skipHidden = true))
                } finally {
                    ancestors.remove(doc.content)
                }
            }
        }
    }
    return UploadTree(scan(roots, skipHidden = false), skipped)
}

internal suspend fun <T, R> List<UploadItem<T>>.mapUploadFiles(
    transform: suspend (UploadItem.File<T>) -> R
): List<UploadItem<R>> = map { item ->
    when (item) {
        is UploadItem.File -> UploadItem.File(item.name, transform(item))
        is UploadItem.Folder -> UploadItem.Folder(item.name, item.children.mapUploadFiles(transform))
    }
}

internal fun <T> List<UploadItem<T>>.gitUploadFiles(): List<GitUploadFile<T>> = buildList {
    fun visit(item: UploadItem<T>, parent: String) {
        val path = "$parent${item.name}"
        when (item) {
            is UploadItem.File -> add(GitUploadFile(path, item.content))
            is UploadItem.Folder -> if (item.children.isEmpty()) {
                add(GitUploadFile("$path/${RemoteNames.EMPTY_FOLDER_MARKER}", null))
            } else {
                item.children.forEach { visit(it, "$path/") }
            }
        }
    }
    this@gitUploadFiles.forEach { visit(it, "") }
}
