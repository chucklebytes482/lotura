package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DeletePolicy {
    TWO_WAY,
    SAFE_NO_DELETE
}

enum class SyncSourceType {
    DRIVE,
    GITHUB
}

@Entity(tableName = "sync_pairs")
data class SyncPairEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val localPath: String,
    val driveFolderId: String,
    val driveFolderName: String,
    val deletePolicy: String = DeletePolicy.TWO_WAY.name,
    val ignorePatterns: String = DEFAULT_IGNORE_PATTERNS,
    val autoSyncDrive: Boolean = false,
    val githubEnabled: Boolean = false,
    val githubAccountId: Long? = null,
    val githubOwner: String = "",
    val githubRepo: String = "",
    val githubBranch: String = "main",
    val githubSubfolder: String = "",
    val githubIntervalMinutes: Int = 0,
    val lastDriveSyncAt: Long = 0L,
    val lastGithubSyncAt: Long = 0L,
    val lastStatus: String = "Siap disinkronkan",
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val resolvedDeletePolicy: DeletePolicy
        get() = DeletePolicy.TWO_WAY

    val sourceType: SyncSourceType
        get() = if (githubEnabled && githubRepo.isNotBlank()) SyncSourceType.GITHUB else SyncSourceType.DRIVE

    val isGithubPair: Boolean
        get() = sourceType == SyncSourceType.GITHUB

    val isDrivePair: Boolean
        get() = sourceType == SyncSourceType.DRIVE

    companion object {
        const val DEFAULT_IGNORE_PATTERNS = ".git/\n.DS_Store\nThumbs.db\n*.tmp\n*.swp\n*~\n.trash/"
    }
}

@Entity(
    tableName = "file_states",
    primaryKeys = ["pairId", "relativePath"],
    indices = [Index(value = ["pairId"])]
)
data class FileStateEntity(
    val pairId: Long,
    val relativePath: String,
    val localModifiedAt: Long = 0L,
    val localSize: Long = 0L,
    val localHash: String = "",
    val driveFileId: String? = null,
    val driveModifiedAt: Long = 0L,
    val driveNativeMimeType: String? = null,
    val githubBlobSha: String? = null,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "github_accounts")
data class GithubAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    val username: String,
    val avatarUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sync_logs",
    indices = [Index(value = ["pairId"]), Index(value = ["timestampMillis"])]
)
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val pairId: Long? = null,
    val pairName: String = "Sistem",
    val target: String, // DRIVE, GITHUB, SYSTEM
    val action: String, // UPLOAD, DOWNLOAD, EXPORT_DOCS, DELETE, CONFLICT, COMMIT, ERROR
    val relativePath: String = "",
    val status: String, // OK, CONFLICT, ERROR, SKIP
    val message: String,
    val timestampMillis: Long = System.currentTimeMillis()
)
