package com.example.remote

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
import com.example.data.db.MirrorJobEntity
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

private const val KEY_JOB = "job_key"

/** Jadwal auto sync mirror Drive. Berjalan lewat WorkManager, tetap jalan saat aplikasi ditutup. */
internal object MirrorAutoSync {
    val OPTIONS = listOf(1, 6, 24)

    fun label(hours: Int): String = when (hours) {
        1 -> "Setiap 1 jam"
        6 -> "Setiap 6 jam"
        24 -> "Setiap 1 hari"
        else -> "Setiap $hours jam"
    }

    fun mirrorJobKey(email: String, parentRef: String, folderName: String) = "$email|$parentRef|$folderName"

    private fun uniqueName(jobKey: String) = "mirror-auto:$jobKey"

    fun schedule(context: Context, job: MirrorJobEntity) {
        val request = PeriodicWorkRequestBuilder<MirrorSyncWorker>(job.intervalHours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(KEY_JOB to job.jobKey))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            uniqueName(job.jobKey), ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    fun cancel(context: Context, jobKey: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(jobKey))
    }
}

/** Satu putaran auto sync untuk satu job. */
class MirrorSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val key = inputData.getString(KEY_JOB) ?: return Result.success()
        val app = applicationContext as SyncBebasApp
        val repo = app.repository
        val job = repo.getMirrorJob(key)
        if (job == null || !job.enabled) {
            MirrorAutoSync.cancel(applicationContext, key)
            return Result.success()
        }
        return try {
            val summary = RemoteFiles(app).autoMirror(job)
            repo.recordMirrorRun(key, summary, stop = false, nowMillis = System.currentTimeMillis())
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: MirrorStoppedException) {
            // Catat dulu. Membatalkan pekerjaan sendiri bisa menghentikan coroutine ini.
            repo.recordMirrorRun(key, "Dihentikan: ${e.message}", stop = true, nowMillis = System.currentTimeMillis())
            MirrorAutoSync.cancel(applicationContext, key)
            Result.success()
        } catch (e: Exception) {
            repo.recordMirrorRun(key, "Gagal: ${e.message ?: "tidak diketahui"}", stop = false, nowMillis = System.currentTimeMillis())
            Result.success()
        }
    }
}
