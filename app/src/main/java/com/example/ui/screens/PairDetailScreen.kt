package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.DeletePolicy
import com.example.data.db.FileStateEntity
import com.example.data.db.SyncLogEntity
import com.example.data.db.SyncPairEntity
import com.example.ui.components.StatusPill
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.AmberPending
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun PairDetailScreen(
    pair: SyncPairEntity?,
    trackedFiles: List<FileStateEntity>,
    logs: List<SyncLogEntity>,
    isSyncing: Boolean,
    onSyncNow: (includeGithub: Boolean) -> Unit,
    onOpenExplorer: () -> Unit,
    onEditPair: () -> Unit,
    onDeletePair: (SyncPairEntity) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (pair == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Pasangan folder tidak ditemukan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "summary_card") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(pair.name, style = MaterialTheme.typography.headlineSmall)
                            StatusPill(
                                label = if (pair.resolvedDeletePolicy == DeletePolicy.SAFE_NO_DELETE) "Mode Aman" else "2 Arah",
                                dotColor = if (pair.lastError.isNullOrBlank()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                            )
                        }

                        DetailMetaRow("Folder Lokal", pair.localPath, monospace = true)
                        DetailMetaRow("Google Drive", pair.driveFolderName.ifBlank { "-" })
                        if (pair.githubEnabled && pair.githubRepo.isNotBlank()) {
                            val sub = if (pair.githubSubfolder.isNotBlank()) "/${pair.githubSubfolder}" else ""
                            DetailMetaRow(
                                "GitHub",
                                "${pair.githubOwner}/${pair.githubRepo}@${pair.githubBranch}$sub • ${pair.githubIntervalMinutes} mnt",
                                monospace = true
                            )
                        }
                        DetailMetaRow("Berkas", "${trackedFiles.size} file")
                        DetailMetaRow("Drive", formatRelativeTime(pair.lastDriveSyncAt))
                        if (pair.githubEnabled) {
                            DetailMetaRow("Commit GitHub Terakhir", formatRelativeTime(pair.lastGithubSyncAt))
                        }

                        if (!pair.lastError.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = pair.lastError,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            item(key = "actions_grid") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onSyncNow(true) },
                            enabled = !isSyncing,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .testTag("detail_sync_now_button")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (isSyncing) "Menyinkron..." else "Sinkron Sekarang", fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = onOpenExplorer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .testTag("detail_open_explorer_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Penjelajah File")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (pair.githubEnabled) {
                            OutlinedButton(
                                onClick = { onSyncNow(true) },
                                enabled = !isSyncing,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("detail_commit_github_button")
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Commit GitHub")
                            }
                        }

                        OutlinedButton(
                            onClick = onEditPair,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("detail_edit_pair_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit")
                        }

                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("detail_delete_pair_button")
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Hapus Pasangan",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item(key = "logs_header") {
                Text(
                    text = "Aktivitas Sinkronisasi Terbaru",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (logs.isEmpty()) {
                item(key = "no_pair_logs") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    ) {
                        Text(
                            text = "Belum ada riwayat sinkronisasi untuk pasangan ini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(logs.take(30), key = { it.id }) { log ->
                    LogEntryRow(log = log)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Hapus Pasangan?") },
            text = {
                Text("Hapus \"${pair.name}\" dari daftar?")
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Batal") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeletePair(pair)
                    }
                ) { Text("Hapus") }
            }
        )
    }
}

@Composable
private fun DetailMetaRow(
    label: String,
    value: String,
    monospace: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (monospace) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun LogEntryRow(log: SyncLogEntity, modifier: Modifier = Modifier) {
    val badgeColor = when (log.status) {
        "OK" -> MaterialTheme.colorScheme.secondary
        "CONFLICT" -> AmberPending
        "ERROR" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(label = "${log.target} • ${log.action}", dotColor = badgeColor)
                    Text(
                        text = log.pairName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatRelativeTime(log.timestampMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (log.relativePath.isNotBlank()) {
                Text(
                    text = log.relativePath,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
