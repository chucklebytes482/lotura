package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SyncStatus
import com.example.ui.FolderDetail
import com.example.ui.FolderStats
import com.example.data.SyncSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Layar detail folder root Drive atau repo GitHub. Tombol kembali ada di top bar. */
@Composable
fun FolderDetailScreen(
    detail: FolderDetail,
    stats: FolderStats,
    summary: SyncSummary?,
    busy: Boolean,
    onDownload: () -> Unit,
    onUpload: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        Text(detail.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDownload, enabled = !busy) {
                Icon(Icons.Rounded.CloudDownload, contentDescription = "Download")
            }
            IconButton(onClick = onUpload, enabled = !busy) {
                Icon(Icons.Rounded.CloudUpload, contentDescription = "Unggah")
            }
        }

        HorizontalDivider()

        InfoLine("Path relatif", detail.relPath.ifEmpty { "/" })
        InfoLine("Path lokal", detail.localPath)

        HorizontalDivider()

        Text("Isi", fontWeight = FontWeight.SemiBold)
        when {
            stats.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            stats.error != null -> Text(
                "Gagal menghitung: ${stats.error}",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
            else -> {
                InfoLine("Jumlah file", stats.fileCount.toString())
                InfoLine("Total ukuran", formatBytes(stats.totalBytes))
                if (stats.unknownSizeCount > 0) {
                    NoteText("${stats.unknownSizeCount} file tidak diketahui ukurannya (dokumen Google) dan tidak ikut total.")
                }
                if (stats.partial) {
                    NoteText("Hitungan berhenti di batas panggilan API, angka ini belum lengkap.")
                }
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
        Spacer(Modifier.height(8.dp))
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

private fun formatTime(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(millis))

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return String.format(Locale("id", "ID"), "%.1f %s", value, units[index])
}
