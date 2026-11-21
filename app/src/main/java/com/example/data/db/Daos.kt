package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncPairDao {
    @Query("SELECT * FROM sync_pairs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SyncPairEntity>>

    @Query("SELECT * FROM sync_pairs ORDER BY createdAt DESC")
    suspend fun getAll(): List<SyncPairEntity>

    @Query("SELECT * FROM sync_pairs WHERE id = :id")
    fun observeById(id: Long): Flow<SyncPairEntity?>

    @Query("SELECT * FROM sync_pairs WHERE id = :id")
    suspend fun getById(id: Long): SyncPairEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pair: SyncPairEntity): Long

    @Update
    suspend fun update(pair: SyncPairEntity)

    @Query("DELETE FROM sync_pairs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        UPDATE sync_pairs
        SET lastDriveSyncAt = :syncedAt, lastStatus = :status, lastError = :error
        WHERE id = :pairId
        """
    )
    suspend fun updateDriveSyncStatus(
        pairId: Long,
        syncedAt: Long,
        status: String,
        error: String?
    )

    @Query(
        """
        UPDATE sync_pairs
        SET lastGithubSyncAt = :syncedAt, lastStatus = :status, lastError = :error
        WHERE id = :pairId
        """
    )
    suspend fun updateGithubSyncStatus(
        pairId: Long,
        syncedAt: Long,
        status: String,
        error: String?
    )
}

@Dao
interface FileStateDao {
    @Query("SELECT * FROM file_states WHERE pairId = :pairId")
    suspend fun getForPair(pairId: Long): List<FileStateEntity>

    @Query("SELECT * FROM file_states WHERE pairId = :pairId ORDER BY relativePath ASC")
    fun observeForPair(pairId: Long): Flow<List<FileStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: FileStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(states: List<FileStateEntity>)

    @Query("DELETE FROM file_states WHERE pairId = :pairId AND relativePath = :relativePath")
    suspend fun delete(pairId: Long, relativePath: String)

    @Query("DELETE FROM file_states WHERE pairId = :pairId")
    suspend fun deleteAllForPair(pairId: Long)
}

@Dao
interface GithubAccountDao {
    @Query("SELECT * FROM github_accounts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GithubAccountEntity>>

    @Query("SELECT * FROM github_accounts ORDER BY createdAt DESC")
    suspend fun getAll(): List<GithubAccountEntity>

    @Query("SELECT * FROM github_accounts WHERE id = :id")
    suspend fun getById(id: Long): GithubAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: GithubAccountEntity): Long

    @Query("DELETE FROM github_accounts WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_logs ORDER BY timestampMillis DESC LIMIT 400")
    fun observeRecent(): Flow<List<SyncLogEntity>>

    @Query("SELECT * FROM sync_logs WHERE pairId = :pairId ORDER BY timestampMillis DESC LIMIT 150")
    fun observeForPair(pairId: Long): Flow<List<SyncLogEntity>>

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
