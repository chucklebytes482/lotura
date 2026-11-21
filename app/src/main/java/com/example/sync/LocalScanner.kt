package com.example.sync

import java.io.File
import java.security.MessageDigest

data class LocalFileSnapshot(
    val relativePath: String,
    val modifiedAtMillis: Long,
    val size: Long,
    val contentHash: String,
    val gitBlobSha: String
)

object LocalScanner {

    fun scanDirectory(rootDir: File, ignoreRules: IgnoreRules): Map<String, LocalFileSnapshot> {
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyMap()
        val result = linkedMapOf<String, LocalFileSnapshot>()
        val rootPath = rootDir.canonicalPath.trimEnd(File.separatorChar)

        fun walk(dir: File) {
            val children = dir.listFiles() ?: return
            for (child in children) {
                val childPath = child.canonicalPath
                if (!childPath.startsWith(rootPath)) continue
                val rel = childPath
                    .removePrefix(rootPath)
                    .trimStart(File.separatorChar)
                    .replace(File.separatorChar, '/')
                if (rel.isEmpty()) continue
                if (ignoreRules.isIgnored(rel, isDirectory = child.isDirectory)) continue

                if (child.isDirectory) {
                    walk(child)
                } else if (child.isFile) {
                    val hashes = computeHashes(child)
                    result[rel] = LocalFileSnapshot(
                        relativePath = rel,
                        modifiedAtMillis = child.lastModified(),
                        size = child.length(),
                        contentHash = hashes.first,
                        gitBlobSha = hashes.second
                    )
                }
            }
        }

        walk(rootDir)
        return result
    }

    /**
     * Computes Pair(md5Hex, gitBlobSha1Hex) in a single file read pass.
     * MD5 matches Google Drive's md5Checksum; Git blob SHA-1 matches GitHub's Git Tree blob sha.
     */
    fun computeHashes(file: File): Pair<String, String> {
        val md5 = MessageDigest.getInstance("MD5")
        val sha1 = MessageDigest.getInstance("SHA-1")
        val header = "blob ${file.length()}\u0000".toByteArray(Charsets.UTF_8)
        sha1.update(header)

        val buffer = ByteArray(16 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                md5.update(buffer, 0, read)
                sha1.update(buffer, 0, read)
            }
        }
        return md5.digest().toHex() to sha1.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
