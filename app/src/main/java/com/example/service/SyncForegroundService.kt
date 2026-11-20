package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.SyncBebasApp
import com.example.data.db.SyncPairEntity
import com.example.drive.GoogleAuthManager
import com.example.sync.IgnoreRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class SyncForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val watchers = ConcurrentHashMap<Long, RecursiveFileObserver>()
    private val debounceJobs = ConcurrentHashMap<Long, Job>()

    companion object {
        const val CHANNEL_ID = "sync_bebas_realtime"
        private const val NOTIFICATION_ID = 2041
        private const val DEBOUNCE_MILLIS = 4_000L

        fun start(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SyncForegroundService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundSafely(getString(R.string.notification_sync_running))
        observePairsAndWatch()
        observeScheduledIntervalLoop()
    }

    private fun observeScheduledIntervalLoop() {
        val app = application as SyncBebasApp
        serviceScope.launch {
            app.repository.appPrefs.collectLatest { prefs ->
                val intervalMinutes = prefs.syncIntervalMinutes.coerceAtLeast(5)
                if (!prefs.driveConnected) return@collectLatest
                while (true) {
                    delay(intervalMinutes * 60_000L)
                    val token = GoogleAuthManager.tryGetSilentAccessToken(applicationContext)
                        ?: app.repository.getDriveAccessToken()
                        ?: continue
                    val pairs = app.repository.getAllPairs()
                    for (pair in pairs) {
                        if (pair.driveFolderId.isNotBlank()) {
                            app.syncExecutor.syncPairWithDrive(pair.id, token)
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafely(getString(R.string.notification_sync_running))
        return START_STICKY
    }

    private fun startForegroundSafely(contentText: String) {
        val notification = buildNotification(contentText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observePairsAndWatch() {
        val app = application as SyncBebasApp
        serviceScope.launch {
            app.repository.syncPairs.collectLatest { pairs ->
                val activePairs = pairs.filter { it.autoSyncDrive && it.localPath.isNotBlank() }
                syncWatchersWith(activePairs)
                val statusText = if (activePairs.isEmpty()) {
                    "Belum ada folder aktif yang dipantau"
                } else {
                    "Memantau ${activePairs.size} folder secara real-time"
                }
                startForegroundSafely(statusText)
            }
        }
    }

    private fun syncWatchersWith(activePairs: List<SyncPairEntity>) {
        val activeIds = activePairs.map { it.id }.toSet()
        watchers.keys.filter { it !in activeIds }.forEach { removedId ->
            watchers.remove(removedId)?.stopWatching()
            debounceJobs.remove(removedId)?.cancel()
        }

        activePairs.forEach { pair ->
            watchers.remove(pair.id)?.stopWatching()
            val dir = File(pair.localPath)
            if (dir.exists() && dir.isDirectory) {
                val observer = RecursiveFileObserver(
                    rootDir = dir,
                    ignoreRules = IgnoreRules(pair.ignorePatterns)
                ) { _ ->
                    scheduleDebouncedDriveSync(pair.id, pair.name)
                }
                observer.startWatching()
                watchers[pair.id] = observer
            }
        }
    }

    private fun scheduleDebouncedDriveSync(pairId: Long, pairName: String) {
        debounceJobs[pairId]?.cancel()
        debounceJobs[pairId] = serviceScope.launch {
            delay(DEBOUNCE_MILLIS)
            val app = application as SyncBebasApp
            val prefs = app.repository.getAppPrefsSnapshot()
            if (!prefs.autoSyncEnabled || !prefs.driveConnected) return@launch

            val token = GoogleAuthManager.tryGetSilentAccessToken(applicationContext)
                ?: app.repository.getDriveAccessToken()
                ?: return@launch

            startForegroundSafely("Menyinkronkan $pairName ke Google Drive...")
            app.syncExecutor.syncPairWithDrive(pairId, token)
            startForegroundSafely("Memantau ${watchers.size} folder secara real-time")
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        watchers.values.forEach { it.stopWatching() }
        watchers.clear()
        debounceJobs.values.forEach { it.cancel() }
        debounceJobs.clear()
        serviceScope.cancel()
        super.onDestroy()
    }
}
