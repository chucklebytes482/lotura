package com.example

import android.app.Application
import com.example.data.SyncRepository
import com.example.data.db.SyncDatabase
import com.example.data.secure.SecureTokenStore
import com.example.drive.DriveApiClient
import com.example.drive.GoogleAuthManager
import com.example.github.GithubApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SyncBebasApp : Application() {

    lateinit var database: SyncDatabase
        private set

    lateinit var secureTokenStore: SecureTokenStore
        private set

    lateinit var repository: SyncRepository
        private set

    lateinit var driveApiClient: DriveApiClient
        private set

    lateinit var githubApiClient: GithubApiClient
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        database = SyncDatabase.getInstance(this)
        secureTokenStore = SecureTokenStore(this)
        repository = SyncRepository(database, secureTokenStore)
        driveApiClient = DriveApiClient()
        githubApiClient = GithubApiClient()
        // Hapus sisa format akun Drive lama (satu akun global) sekali saat aplikasi diperbarui.
        appScope.launch {
            repository.clearLegacyDriveKeys()
        }
    }

    /** Token silent dulu; kalau gagal, pakai token tersimpan. */
    suspend fun resolveDriveToken(email: String): String {
        val fresh = GoogleAuthManager.tryGetSilentAccessToken(this, email)
        if (fresh != null) {
            repository.saveDriveToken(email, fresh)
            return fresh
        }
        return repository.getDriveToken(email)
            ?: throw IllegalStateException("Token Drive untuk $email tidak ada. Hubungkan ulang akun.")
    }
}
