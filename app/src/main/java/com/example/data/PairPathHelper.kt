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

    fun sourceFolderNameForDrive(driveFolderName: String): String {
        return sanitizeFolderName(driveFolderName)
    }

    fun sourceFolderNameForGithub(githubSubfolder: String): String {
        val cleanPath = githubSubfolder.trim().trim('/')
        if (cleanPath.isEmpty()) return ""
        val leaf = cleanPath.substringAfterLast('/')
        return sanitizeFolderName(leaf)
    }

    fun pairNameForDrive(driveFolderName: String): String {
        return driveFolderName.trim()
    }

    fun pairNameForGithub(repoName: String, githubSubfolder: String): String {
        val cleanRepo = repoName.trim()
        val cleanSub = githubSubfolder.trim().trim('/')
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
