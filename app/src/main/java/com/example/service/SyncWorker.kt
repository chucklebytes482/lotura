package com.example.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.SyncBebasApp
import com.example.data.db.SyncPairEntity
import com.example.drive.GoogleAuthManager
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for scheduled periodic GitHub auto-commits (default 15 minutes)
 * and background Google Drive catch-up sync.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_PAIR_ID = "pair_id"
        private const val UNIQUE_WORK_PREFIX = "sync_bebas_periodic_"

        fun workNameFor(pairId: Long) = "$UNIQUE_WORK_PREFIX$pairId"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as? SyncBebasApp ?: return Result.failure()
        val pairId = inputData.getLong(KEY_PAIR_ID, -1L)
        if (pairId <= 0L) return Result.success()

        val pair = app.repository.getPairById(pairId) ?: return Result.success()
        val prefs = app.repository.getAppPrefsSnapshot()

        // 1. Optional Drive catch-up before GitHub commit so Drive changes also flow into GitHub
        if (prefs.autoSyncEnabled && prefs.driveConnected && pair.autoSyncDrive && pair.driveFolderId.isNotBlank()) {
            val token = GoogleAuthManager.tryGetSilentAccessToken(applicationContext)
                ?: app.repository.getDriveAccessToken()
            if (!token.isNullOrBlank()) {
                app.syncExecutor.syncPairWithDrive(pairId, token)
            }
        }

        // 2. Scheduled atomic GitHub sync & auto-commit
        if (pair.githubEnabled && pair.githubAccountId != null) {
            val ghResult = app.syncExecutor.syncPairWithGithub(pairId)
            if (!ghResult.success) {
                return Result.retry()
            }
        }

        return Result.success()
    }
}

object SyncScheduler {

    fun scheduleForPair(
        context: Context,
        pair: SyncPairEntity,
        overrideIntervalMinutes: Int? = null
    ) {
        val wm = WorkManager.getInstance(context)
        val workName = SyncWorker.workNameFor(pair.id)

        if (!pair.githubEnabled && !pair.autoSyncDrive) {
            wm.cancelUniqueWork(workName)
            return
        }

        val chosen = overrideIntervalMinutes ?: pair.githubIntervalMinutes
        val intervalMinutes = chosen.coerceAtLeast(15).toLong()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(workDataOf(SyncWorker.KEY_PAIR_ID to pair.id))
            .build()

        wm.enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelForPair(context: Context, pairId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(SyncWorker.workNameFor(pairId))
    }
}
