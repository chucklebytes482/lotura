package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Satu akun Google Drive. Token disimpan terpisah per email di SecureTokenStore. */
@Entity(tableName = "drive_accounts")
data class DriveAccountEntity(
    @PrimaryKey val email: String,
    val displayName: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** Satu akun GitHub. PAT disimpan terenkripsi di SecureTokenStore. */
@Entity(tableName = "github_accounts")
data class GithubAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    val username: String,
    val avatarUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** Repositori GitHub yang tersedia untuk akun ini. */
@Entity(
    tableName = "github_repos",
    foreignKeys = [
        ForeignKey(
            entity = GithubAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["accountId"])]
)
data class GithubRepoEntity(
    @PrimaryKey val fullName: String,
    val accountId: Long,
    val owner: String,
    val name: String,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val archived: Boolean,
    val localPath: String
)
