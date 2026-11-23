package com.example.data

import java.io.File

/**
 * Folder lokal hasil koneksi akun.
 *  Drive:  /storage/emulated/0/google drive <email>
 *  GitHub: /storage/emulated/0/github <username>/<repo>
 */
object LocalPathHelper {
    const val BASE_DIR = "/storage/emulated/0"
    const val DRIVE_PREFIX = "google drive"
    const val GITHUB_PREFIX = "github"

    private val illegalCharsRegex = Regex("""[/\\:*?"<>|]""")

    fun sanitize(raw: String): String {
        val cleaned = raw.trim().replace(illegalCharsRegex, "_").trim()
        return cleaned.ifEmpty { "_" }
    }

    fun driveRoot(email: String): File =
        File(BASE_DIR, "$DRIVE_PREFIX ${sanitize(email)}")

    fun githubRoot(username: String, repo: String): File =
        File(File(BASE_DIR, "$GITHUB_PREFIX ${sanitize(username)}"), sanitize(repo))
}
