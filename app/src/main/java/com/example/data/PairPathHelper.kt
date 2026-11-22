package com.example.data

import java.io.File

object PairPathHelper {

    const val DEFAULT_LOCAL_BASE_DIR = "/storage/emulated/0/Documents"

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

    fun resolveDefaultLocalPathForDrive(driveFolderName: String): String {
        val cleanFolder = sourceFolderNameForDrive(driveFolderName)
        return resolveLocalTargetPath(DEFAULT_LOCAL_BASE_DIR, cleanFolder)
    }

    /**
     * Resolves a nested device directory for a GitHub pair so repositories with identically named
     * folders never collide: `/storage/emulated/0/Documents/<RepoName>/<SubfolderPath>`.
     */
    fun resolveDefaultLocalPathForGithub(repoName: String, githubSubfolder: String): String {
        val cleanRepo = sanitizeFolderName(repoName)
        val folderSegments = normalizeGithubFolderPath(githubSubfolder)
            .split('/')
            .map { sanitizeFolderName(it) }
            .filter { it.isNotEmpty() }

        var current = File(DEFAULT_LOCAL_BASE_DIR)
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
