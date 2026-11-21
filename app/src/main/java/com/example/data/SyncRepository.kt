package com.example.data

import com.example.data.db.FileStateEntity
import com.example.data.db.GithubAccountEntity
import com.example.data.db.SyncDatabase
import com.example.data.db.SyncLogEntity
import com.example.data.db.SyncPairEntity
import com.example.data.secure.AppPrefs
import com.example.data.secure.SecureTokenStore
import kotlinx.coroutines.flow.Flow

class SyncRepository(
    private val db: SyncDatabase,
    private val secureStore: SecureTokenStore
) {
    val syncPairs: Flow<List<SyncPairEntity>> = db.syncPairDao().observeAll()
    val githubAccounts: Flow<List<GithubAccountEntity>> = db.githubAccountDao().observeAll()
    val recentLogs: Flow<List<SyncLogEntity>> = db.syncLogDao().observeRecent()
    val appPrefs: Flow<AppPrefs> = secureStore.appPrefs

    fun observePair(pairId: Long): Flow<SyncPairEntity?> = db.syncPairDao().observeById(pairId)
    fun observeFileStates(pairId: Long): Flow<List<FileStateEntity>> = db.fileStateDao().observeForPair(pairId)
    fun observeLogsForPair(pairId: Long): Flow<List<SyncLogEntity>> = db.syncLogDao().observeForPair(pairId)

    suspend fun getAllPairs(): List<SyncPairEntity> = db.syncPairDao().getAll()
    suspend fun getPairById(pairId: Long): SyncPairEntity? = db.syncPairDao().getById(pairId)

    suspend fun savePair(pair: SyncPairEntity): Long {
        return if (pair.id == 0L) {
            db.syncPairDao().insert(pair)
        } else {
            db.syncPairDao().update(pair)
            pair.id
        }
    }

    suspend fun deletePair(pairId: Long) {
        db.fileStateDao().deleteAllForPair(pairId)
        db.syncPairDao().deleteById(pairId)
    }

    suspend fun updateDriveStatus(pairId: Long, syncedAt: Long, status: String, error: String?) {
        db.syncPairDao().updateDriveSyncStatus(pairId, syncedAt, status, error)
    }

    suspend fun updateGithubStatus(pairId: Long, syncedAt: Long, status: String, error: String?) {
        db.syncPairDao().updateGithubSyncStatus(pairId, syncedAt, status, error)
    }

    suspend fun getFileStates(pairId: Long): List<FileStateEntity> = db.fileStateDao().getForPair(pairId)
    suspend fun upsertFileState(state: FileStateEntity) = db.fileStateDao().upsert(state)
    suspend fun deleteFileState(pairId: Long, relativePath: String) = db.fileStateDao().delete(pairId, relativePath)

    suspend fun addGithubAccount(label: String, username: String, avatarUrl: String, pat: String): Long {
        val id = db.githubAccountDao().insert(
            GithubAccountEntity(
                label = label.trim().ifBlank { username },
                username = username.trim(),
                avatarUrl = avatarUrl
            )
        )
        secureStore.saveGithubPat(id, pat)
        return id
    }

    suspend fun deleteGithubAccount(accountId: Long) {
        secureStore.deleteGithubPat(accountId)
        db.githubAccountDao().deleteById(accountId)
    }

    suspend fun getGithubAccount(accountId: Long): GithubAccountEntity? = db.githubAccountDao().getById(accountId)
    suspend fun getGithubPat(accountId: Long): String? = secureStore.getGithubPat(accountId)

    suspend fun saveGoogleAccount(email: String, displayName: String, accessToken: String?) {
        secureStore.saveGoogleAccount(email, displayName, accessToken)
    }

    suspend fun saveDriveAccessToken(accessToken: String) = secureStore.saveDriveAccessToken(accessToken)
    suspend fun getDriveAccessToken(): String? = secureStore.getDriveAccessToken()
    suspend fun clearGoogleAccount() = secureStore.clearGoogleAccount()
    suspend fun getAppPrefsSnapshot(): AppPrefs = secureStore.getAppPrefsSnapshot()

    suspend fun addLog(
        pairId: Long? = null,
        pairName: String = "Sistem",
        target: String,
        action: String,
        relativePath: String = "",
        status: String,
        message: String
    ) {
        db.syncLogDao().insert(
            SyncLogEntity(
                pairId = pairId,
                pairName = pairName,
                target = target,
                action = action,
                relativePath = relativePath,
                status = status,
                message = message
            )
        )
        db.syncLogDao().pruneOld()
    }

    suspend fun clearLogs() = db.syncLogDao().clearAll()
}
