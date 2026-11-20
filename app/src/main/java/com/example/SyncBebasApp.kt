package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.SyncRepository
import com.example.data.db.SyncDatabase
import com.example.data.secure.SecureTokenStore
import com.example.drive.DriveApiClient
import com.example.github.GithubApiClient
import com.example.service.SyncForegroundService
import com.example.sync.SyncExecutor

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

    lateinit var syncExecutor: SyncExecutor
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        database = SyncDatabase.getInstance(this)
        secureTokenStore = SecureTokenStore(this)
        repository = SyncRepository(database, secureTokenStore)
        driveApiClient = DriveApiClient()
        githubApiClient = GithubApiClient()
        syncExecutor = SyncExecutor(repository, driveApiClient, githubApiClient)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SyncForegroundService.CHANNEL_ID,
                getString(R.string.notification_channel_sync_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_sync_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }
}
