package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveAccountDao {
    @Query("SELECT * FROM drive_accounts ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<DriveAccountEntity>>

    @Query("SELECT * FROM drive_accounts ORDER BY createdAt ASC")
    suspend fun getAll(): List<DriveAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: DriveAccountEntity)

    @Query("DELETE FROM drive_accounts WHERE email = :email")
    suspend fun deleteByEmail(email: String)
}

@Dao
interface GithubAccountDao {
    @Query("SELECT * FROM github_accounts ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<GithubAccountEntity>>

    @Query("SELECT * FROM github_accounts WHERE id = :id")
    suspend fun getById(id: Long): GithubAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: GithubAccountEntity): Long

    @Query("DELETE FROM github_accounts WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface GithubRepoDao {
    @Query("SELECT * FROM github_repos ORDER BY fullName ASC")
    fun observeAll(): Flow<List<GithubRepoEntity>>

    @Query("SELECT * FROM github_repos WHERE fullName = :fullName")
    suspend fun getByFullName(fullName: String): GithubRepoEntity?

    @Query("DELETE FROM github_repos WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(repos: List<GithubRepoEntity>)

    @Transaction
    suspend fun replaceForAccount(accountId: Long, repos: List<GithubRepoEntity>) {
        deleteForAccount(accountId)
        insertAll(repos)
    }
}

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_logs ORDER BY timestampMillis DESC LIMIT 400")
    fun observeRecent(): Flow<List<SyncLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SyncLogEntity): Long

    @Query("DELETE FROM sync_logs")
    suspend fun clearAll()

    @Query(
        """
        DELETE FROM sync_logs
        WHERE id NOT IN (SELECT id FROM sync_logs ORDER BY timestampMillis DESC LIMIT 500)
        """
    )
    suspend fun pruneOld()
}
