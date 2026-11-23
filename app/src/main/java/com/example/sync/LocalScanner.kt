package com.example.sync

import java.io.File

/**
 * Memindai folder lokal hanya untuk ukuran file. Tidak ada hash:
 * perbandingan antara lokal dan remote memakai `size` saja.
 */
object LocalScanner {

    /** Map relativePath (pakai '/') -> File, untuk semua file di bawah [rootDir] yang tidak diabaikan. */
    fun scanFiles(rootDir: File, ignoreRules: IgnoreRules): Map<String, File> {
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyMap()
        val result = linkedMapOf<String, File>()
        walk(rootDir, rootDir, ignoreRules, result)
        return result
    }

    private fun walk(root: File, dir: File, ignoreRules: IgnoreRules, out: MutableMap<String, File>) {
        val children = dir.listFiles() ?: return
        for (child in children.sortedBy { it.name.lowercase() }) {
            val rel = child.relativeTo(root).invariantSeparatorsPath
            if (rel.isEmpty()) continue
            if (ignoreRules.isIgnored(rel, isDirectory = child.isDirectory)) continue
            when {
                child.isDirectory -> walk(root, child, ignoreRules, out)
                child.isFile -> out[rel] = child
            }
        }
    }
}
