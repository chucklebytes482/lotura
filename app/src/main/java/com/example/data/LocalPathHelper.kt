package com.example.data

import java.io.File

/**
 * Folder lokal hasil koneksi akun.
 *  Drive:  /storage/emulated/0/google drive/<email>
 *  GitHub: /storage/emulated/0/github/<username>/<repo>
 */
object LocalPathHelper {
    const val BASE_DIR = "/storage/emulated/0"
    const val DRIVE_DIR = "google drive"
    const val GITHUB_DIR = "github"

    private val illegalCharsRegex = Regex("""[/\\:*?"<>|]""")

    fun sanitize(raw: String): String {
        val cleaned = raw.trim().replace(illegalCharsRegex, "_").trim()
        return cleaned.ifEmpty { "_" }
    }

    fun driveRoot(email: String): File =
        File(File(BASE_DIR, DRIVE_DIR), sanitize(email))

    fun githubRoot(username: String, repo: String): File =
        File(File(File(BASE_DIR, GITHUB_DIR), sanitize(username)), sanitize(repo))
}
