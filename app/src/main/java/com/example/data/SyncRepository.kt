package com.example.data

import com.example.data.db.DriveAccountEntity
import com.example.data.db.GithubAccountEntity
import com.example.data.db.GithubRepoEntity
import com.example.data.db.MirrorJobEntity
import com.example.data.db.SyncDatabase
import com.example.data.secure.SecureTokenStore
import kotlinx.coroutines.flow.Flow

class SyncRepository(
    private val db: SyncDatabase,
    private val secureStore: SecureTokenStore
) {
    val driveAccounts: Flow<List<DriveAccountEntity>> = db.driveAccountDao().observeAll()
    val githubAccounts: Flow<List<GithubAccountEntity>> = db.githubAccountDao().observeAll()
    val githubRepos: Flow<List<GithubRepoEntity>> = db.githubRepoDao().observeAll()
    val mirrorJobs: Flow<List<MirrorJobEntity>> = db.mirrorJobDao().observeAll()

    suspend fun getMirrorJob(jobKey: String): MirrorJobEntity? = db.mirrorJobDao().getByKey(jobKey)
    suspend fun saveMirrorJob(job: MirrorJobEntity) = db.mirrorJobDao().upsert(job)

    /** Mencatat hasil satu putaran auto sync. Membaca ulang dulu supaya pengaturan Hentikan dari UI tidak tertimpa. */
    suspend fun recordMirrorRun(jobKey: String, result: String, stop: Boolean, nowMillis: Long) {
        val job = db.mirrorJobDao().getByKey(jobKey) ?: return
        db.mirrorJobDao().upsert(job.copy(lastRunAt = nowMillis, lastResult = result, enabled = job.enabled && !stop))
    }

    suspend fun stopMirrorJob(jobKey: String) {
        val job = db.mirrorJobDao().getByKey(jobKey) ?: return
        db.mirrorJobDao().upsert(job.copy(enabled = false, lastResult = "Dihentikan manual."))
    }

    suspend fun getDriveAccounts(): List<DriveAccountEntity> = db.driveAccountDao().getAll()

    suspend fun addOrUpdateDriveAccount(email: String, displayName: String, accessToken: String) {
        db.driveAccountDao().upsert(DriveAccountEntity(email = email, displayName = displayName))
        secureStore.saveDriveToken(email, accessToken)
    }

    suspend fun saveDriveToken(email: String, accessToken: String) = secureStore.saveDriveToken(email, accessToken)
    suspend fun getDriveToken(email: String): String? = secureStore.getDriveToken(email)

    suspend fun removeDriveAccount(email: String) {
        secureStore.deleteDriveToken(email)
        db.driveAccountDao().deleteByEmail(email)
    }

    suspend fun clearLegacyDriveKeys() = secureStore.clearLegacyDriveKeys()

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

    suspend fun replaceGithubRepos(accountId: Long, repos: List<GithubRepoEntity>) {
        db.githubRepoDao().replaceForAccount(accountId, repos)
    }
}
