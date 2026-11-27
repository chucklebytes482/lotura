package com.example.remote

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.SyncBebasApp
import com.example.data.db.MirrorJobEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val KEY_JOB = "job_key"

/** Jumlah percobaan per putaran jika terjadi error biasa (bukan dihentikan sistem atau izin dicabut). */
private const val MAX_ATTEMPTS = 3

/** Batas panjang catatan hasil terakhir per job. Hanya satu catatan yang disimpan, tidak ada riwayat. */
const val MAX_LOG_CHARS = 160

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
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val manager = WorkManager.getInstance(context)
        // Putaran pertama langsung jalan sekarang. Periodik mulai setelah satu interval supaya tidak dobel.
        val now = OneTimeWorkRequestBuilder<MirrorSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setInputData(workDataOf(KEY_JOB to job.jobKey))
            .build()
        manager.enqueueUniqueWork(uniqueName(job.jobKey) + ":now", ExistingWorkPolicy.REPLACE, now)
        val periodic = PeriodicWorkRequestBuilder<MirrorSyncWorker>(job.intervalHours.toLong(), TimeUnit.HOURS)
            .setInitialDelay(job.intervalHours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setInputData(workDataOf(KEY_JOB to job.jobKey))
            .build()
        manager.enqueueUniquePeriodicWork(
            uniqueName(job.jobKey), ExistingPeriodicWorkPolicy.UPDATE, periodic
        )
    }

    fun cancel(context: Context, jobKey: String) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(uniqueName(jobKey))
            cancelUniqueWork(uniqueName(jobKey) + ":now")
        }
    }
}

/** Satu putaran auto sync untuk satu job. Penyimpanan hasil selalu dijalankan di NonCancellable supaya tidak terpotong. */
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
        val attempt = runAttemptCount + 1
        return try {
            val summary = RemoteFiles(app).autoMirror(job)
            withContext(NonCancellable) { record(repo, key, summary, stop = false) }
            Result.success()
        } catch (e: CancellationException) {
            // Sistem menghentikan worker (misalnya batas waktu). Catat hanya jika job masih aktif, lalu teruskan pembatalan.
            withContext(NonCancellable) {
                if (repo.getMirrorJob(key)?.enabled == true) {
                    record(repo, key, "Tertunda: dihentikan sistem, dicoba lagi.", stop = false)
                }
            }
            throw e
        } catch (e: MirrorStoppedException) {
            // Catat dulu, baru batalkan jadwal. Pembatalan bisa menghentikan worker ini.
            withContext(NonCancellable) {
                record(repo, key, "Dihentikan: ${e.message}", stop = true)
                MirrorAutoSync.cancel(applicationContext, key)
            }
            Result.success()
        } catch (e: Exception) {
            withContext(NonCancellable) {
                record(repo, key, "Gagal (percobaan $attempt): ${e.message ?: "tidak diketahui"}", stop = false)
            }
            if (attempt < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    private suspend fun record(repo: com.example.data.SyncRepository, key: String, result: String, stop: Boolean) =
        repo.recordMirrorRun(key, result, stop = stop, nowMillis = System.currentTimeMillis())
}
