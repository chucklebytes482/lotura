package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SyncStatus
import com.example.data.SyncSummary
import com.example.ui.FolderDetail
import com.example.ui.FolderStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kartu detail folder. Menampilkan tombol download dan unggah, tombol jelajah file
 * (repo GitHub saja), dan status sync terakhir. Tombol top bar tetap ada untuk kembali.
 */
@Composable
fun FolderDetailScreen(
    detail: FolderDetail,
    summary: SyncSummary?,
    busy: Boolean,
    onDownload: () -> Unit,
    onUpload: () -> Unit,
    stats: FolderStats = FolderStats(),
    onOpenFiles: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onDownload, enabled = !busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Download")
            }
            OutlinedButton(onClick = onUpload, enabled = !busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Unggah")
            }
        }

        if (onOpenFiles != null) {
            OutlinedButton(onClick = onOpenFiles, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Jelajahi file")
            }
        }

        HorizontalDivider()

        Text("Isi", fontWeight = FontWeight.SemiBold)
        when {
            stats.loading -> NoteText("Memuat isi...")
            stats.error != null -> {
                Text(
                    "Gagal memuat isi: ${stats.error}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
                if (onRetry != null) {
                    OutlinedButton(onClick = onRetry, enabled = !busy) { Text("Coba lagi") }
                }
            }
            else -> {
                InfoLine("Jumlah file", stats.fileCount.toString())
                InfoLine("Total ukuran", formatBytes(stats.totalBytes))
            }
        }

        HorizontalDivider()

        Text("Sync terakhir", fontWeight = FontWeight.SemiBold)
        if (summary == null) {
            NoteText("Belum pernah di-sync.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when (summary.status) {
                    SyncStatus.OK -> Icons.Filled.Check to MaterialTheme.colorScheme.primary
                    SyncStatus.SEBAGIAN -> Icons.Filled.Warning to MaterialTheme.colorScheme.tertiary
                    SyncStatus.GAGAL -> Icons.Filled.Close to MaterialTheme.colorScheme.error
                }
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(end = 6.dp))
                Text(statusText(summary.status), fontWeight = FontWeight.Medium)
            }
            InfoLine("Waktu", formatTime(summary.lastSyncAtMillis))
            InfoLine("Berhasil / sudah sama", summary.okCount.toString())
            InfoLine("Gagal", summary.failCount.toString())
            InfoLine("Item tersembunyi dilewati", summary.hiddenSkipped.toString())
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp)
    }
}

@Composable
private fun NoteText(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun statusText(status: SyncStatus): String = when (status) {
    SyncStatus.OK -> "OK"
    SyncStatus.SEBAGIAN -> "Sebagian"
    SyncStatus.GAGAL -> "Gagal"
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(millis))
