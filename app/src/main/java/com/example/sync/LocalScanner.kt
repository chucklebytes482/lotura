package com.example.sync

import java.io.File
import java.io.IOException

internal data class LocalTree(
    val files: Map<String, File>,
    val folders: Set<String>,
    val blocked: Set<String>
)

internal object LocalScanner {
    fun scan(root: File, ignoreRules: IgnoreRules, hasBase: Boolean): LocalTree {
        val files = linkedMapOf<String, File>()
        val folders = linkedSetOf<String>()
        val blocked = linkedSetOf<String>()
        val parent = root.absoluteFile.parentFile ?: error("Folder sumber tidak valid.")
        if (!root.exists()) {
            check(!hasBase) { "Folder sumber ${root.name} tidak ditemukan. Sinkron dibatalkan demi keamanan." }
            // A missing parent is normal for a repo/account on its first sync.
            var existing = parent
            while (!existing.exists()) existing = existing.parentFile ?: error("Penyimpanan tidak tersedia.")
            check(existing.list() != null) { "Penyimpanan lokal tidak dapat dibaca." }
            return LocalTree(files, folders, blocked)
        }
        check(root.isDirectory && root.canonicalFile == File(parent.canonicalFile, root.name)) {
            "Folder sumber ${root.name} tidak dapat digunakan."
        }

        fun walk(dir: File, prefix: String) {
            val children = dir.listFiles() ?: throw IOException("Folder ${dir.name} tidak dapat dibaca.")
            for (child in children) {
                val path = SourceTreeRules.childPath(prefix, child.name)
                when {
                    !isSyncPath(path) || child.isHidden || child.canonicalFile != File(dir.canonicalFile, child.name) ||
                        ignoreRules.isIgnored(path, child.isDirectory) -> blocked += path
                    child.isDirectory -> {
                        folders += path
                        walk(child, path)
                    }
                    child.isFile -> files[path] = child
                    else -> blocked += path
                }
            }
        }
        walk(root, "")
        return LocalTree(files, folders, blocked)
    }
}
