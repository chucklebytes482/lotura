package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.SyncBebasApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? SyncBebasApp ?: return@launch
                val prefs = app.repository.getAppPrefsSnapshot()
                val pairs = app.repository.getAllPairs()
                pairs.forEach { pair ->
                    SyncScheduler.scheduleForPair(context.applicationContext, pair)
                }
                if (prefs.realtimeWatcherEnabled && pairs.any { it.autoSyncDrive }) {
                    SyncForegroundService.start(context.applicationContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
