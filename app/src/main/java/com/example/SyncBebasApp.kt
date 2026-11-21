package com.example

import android.app.Application
import com.example.data.SyncRepository
import com.example.data.db.SyncDatabase
import com.example.data.secure.SecureTokenStore
import com.example.drive.DriveApiClient
import com.example.github.GithubApiClient
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
        database = SyncDatabase.getInstance(this)
        secureTokenStore = SecureTokenStore(this)
        repository = SyncRepository(database, secureTokenStore)
        driveApiClient = DriveApiClient()
        githubApiClient = GithubApiClient()
        syncExecutor = SyncExecutor(repository, driveApiClient, githubApiClient)
    }
}
