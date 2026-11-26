package com.example.remote

import java.io.File

internal object RemoteNames {
    const val EMPTY_FOLDER_MARKER = ".gitkeep"
    private val invalidLocal = Regex("""[/\\:*?"<>|\p{Cntrl}]""")

    fun visible(name: String): Boolean = name.isNotBlank() && !name.trimStart().startsWith('.')

    fun validNewName(name: String): Boolean =
        visible(name) && name.none { it == '/' || it == '\\' || it.isISOControl() } &&
            name.toByteArray(Charsets.UTF_8).size <= 255

    fun localName(name: String): String {
        check(visible(name)) { "Berkas tersembunyi tidak dapat diunduh." }
        return name.trim().replace(invalidLocal, "_").trimEnd('.').ifBlank { "_" }
            .let { cleaned ->
                // Leave room for a numeric collision suffix on common Android filesystems.
                var end = cleaned.length
                while (cleaned.substring(0, end).toByteArray(Charsets.UTF_8).size > 230) {
                    end = cleaned.offsetByCodePoints(end, -1)
                }
                cleaned.substring(0, end)
            }
    }

    fun safeChild(parent: File, name: String): File {
        val child = File(parent, name)
        check(child.canonicalFile == File(parent.canonicalFile, name)) { "Lokasi unduhan tidak aman." }
        return child
    }

    fun unusedChild(parent: File, name: String, folder: Boolean): File {
        val safe = localName(name)
        val dot = safe.lastIndexOf('.').takeIf { it > 0 && !folder } ?: safe.length
        val stem = safe.substring(0, dot)
        val ext = safe.substring(dot)
        var suffix = 1
        while (true) {
            val candidate = safeChild(parent, if (suffix == 1) safe else "$stem ($suffix)$ext")
            if (!candidate.exists()) return candidate
            suffix++
        }
    }
}
