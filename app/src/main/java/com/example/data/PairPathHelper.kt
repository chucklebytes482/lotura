package com.example.data

import java.io.File

object PairPathHelper {

    const val DEFAULT_LOCAL_BASE_DIR = "/storage/emulated/0/Documents"
    const val DRIVE_SOURCE_DIR = "Google Drive"
    const val GITHUB_SOURCE_DIR = "GitHub"

    private val illegalCharsRegex = Regex("""[/\\:*?"<>|]""")

    fun sanitizeFolderName(raw: String): String {
        return raw.trim()
            .replace(illegalCharsRegex, "_")
            .trim('.', ' ')
    }

    fun normalizeGithubFolderPath(raw: String): String {
        return raw.trim()
            .replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("/")
    }

    fun rootGithubFolderName(raw: String): String {
        val cleanPath = normalizeGithubFolderPath(raw)
        if (cleanPath.isEmpty()) return ""
        return cleanPath.substringBefore('/')
    }

    fun sourceFolderNameForDrive(driveFolderName: String): String {
        return sanitizeFolderName(driveFolderName)
    }

    fun sourceFolderNameForGithub(githubSubfolder: String): String {
        val cleanPath = normalizeGithubFolderPath(githubSubfolder)
        if (cleanPath.isEmpty()) return ""
        val leaf = cleanPath.substringAfterLast('/')
        return sanitizeFolderName(leaf)
    }

    fun pairNameForDrive(driveFolderName: String): String {
        return driveFolderName.trim()
    }

    fun pairNameForGithub(repoName: String, githubSubfolder: String): String {
        val cleanRepo = repoName.trim()
        val cleanSub = normalizeGithubFolderPath(githubSubfolder)
        return when {
            cleanRepo.isNotEmpty() && cleanSub.isNotEmpty() -> "$cleanRepo/$cleanSub"
            cleanSub.isNotEmpty() -> cleanSub
            else -> cleanRepo
        }
    }

    fun resolveDefaultLocalTargetPath(sourceFolderName: String): String {
        return resolveLocalTargetPath(DEFAULT_LOCAL_BASE_DIR, sourceFolderName)
    }

    /**
     * Nama folder tingkat atas: "Google Drive <email>" atau "GitHub <username>".
     * Identitas kosong hanya memakai nama sumber.
     */
    fun sourceAccountDirName(source: String, identity: String): String {
        val cleanIdentity = sanitizeFolderName(identity)
        return if (cleanIdentity.isEmpty()) source else "$source $cleanIdentity"
    }

    /**
     * Folder lokal pasangan Google Drive:
     * `/storage/emulated/0/Documents/Google Drive <email>/<FolderRoot>`.
     */
    fun resolveDefaultLocalPathForDrive(email: String, driveFolderName: String): String {
        val sourceRoot = File(DEFAULT_LOCAL_BASE_DIR, sourceAccountDirName(DRIVE_SOURCE_DIR, email))
        val cleanFolder = sourceFolderNameForDrive(driveFolderName)
        return if (cleanFolder.isEmpty()) {
            sourceRoot.path
        } else {
            File(sourceRoot, cleanFolder).path
        }
    }

    /**
     * Folder lokal pasangan GitHub:
     * `/storage/emulated/0/Documents/GitHub <username>/<RepoName>/<FolderPath>`.
     */
    fun resolveDefaultLocalPathForGithub(username: String, repoName: String, githubSubfolder: String): String {
        val cleanRepo = sanitizeFolderName(repoName)
        val folderSegments = normalizeGithubFolderPath(githubSubfolder)
            .split('/')
            .map { sanitizeFolderName(it) }
            .filter { it.isNotEmpty() }

        var current = File(DEFAULT_LOCAL_BASE_DIR, sourceAccountDirName(GITHUB_SOURCE_DIR, username))
        if (cleanRepo.isNotEmpty()) {
            current = File(current, cleanRepo)
        }
        folderSegments.forEach { segment ->
            current = File(current, segment)
        }
        return current.path
    }

    /**
     * Resolves the final local device directory path so its folder name always matches the remote
     * source folder name. If the chosen directory already ends with [sourceFolderName], avoids
     * duplicating the folder segment.
     */
    fun resolveLocalTargetPath(basePath: String, sourceFolderName: String): String {
        val cleanBase = basePath.trim().trimEnd('/', '\\')
        val cleanFolder = sanitizeFolderName(sourceFolderName)
        if (cleanBase.isEmpty()) return cleanFolder
        if (cleanFolder.isEmpty()) return cleanBase
        val baseFile = File(cleanBase)
        return if (baseFile.name.equals(cleanFolder, ignoreCase = true)) {
            baseFile.path
        } else {
            File(baseFile, cleanFolder).path
        }
    }
}
