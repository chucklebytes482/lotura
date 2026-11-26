package com.example.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.update.ApkState
import com.example.update.ApkUpdate

/** Kartu unduh pembaruan APK, dipasang di atas konten beranda. Tinggi tetap; status galat tampil di kartu ini saja. */
fun LazyListScope.apkUpdateSection(
    apkState: ApkState,
    onDownload: () -> Unit,
    onRetryInstall: () -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    onDismissPermissionPrompt: () -> Unit,
    onDismissError: () -> Unit
) {
    item(key = "apk-update-card") { ApkUpdateCard(apkState, onDownload, onDismissError) }

    if (apkState is ApkState.NeedsInstallPermission) {
        item(key = "apk-install-permission") {
            AlertDialog(
                onDismissRequest = onDismissPermissionPrompt,
                title = { Text("Izinkan pasang aplikasi") },
                text = {
                    Text(
                        "Lotura perlu izin \"Pasang aplikasi tidak dikenal\" untuk membuka APK yang baru diunduh. " +
                            "Aktifkan izin itu di pengaturan, lalu kembali dan ketuk \"Sudah diizinkan\".",
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = onOpenUnknownSourcesSettings) { Text("Buka pengaturan") }
                },
                dismissButton = {
                    TextButton(onClick = onRetryInstall) { Text("Sudah diizinkan") }
                }
            )
        }
    }
}

@Composable
private fun ApkUpdateCard(apkState: ApkState, onDownload: () -> Unit, onDismissError: () -> Unit) {
    val downloading = apkState is ApkState.Downloading
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Unduh aplikasi terbaru", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Mengunduh ${ApkUpdate.ASSET_NAME} dari rilis GitHub terbaru, lalu membuka installer. Butuh internet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onDownload, enabled = !downloading) {
                Text(if (downloading) "Mengunduh..." else "Unduh dan pasang")
            }
            if (apkState is ApkState.Failed) {
                Spacer(Modifier.height(8.dp))
                Text(apkState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onDismissError) { Text("Tutup") }
            }
        }
    }
}
