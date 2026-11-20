package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.db.GithubAccountEntity
import com.example.data.secure.AppPrefs
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberPending
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun AccountsScreen(
    appPrefs: AppPrefs,
    githubAccounts: List<GithubAccountEntity>,
    isConnectingGoogle: Boolean,
    isAddingGithubAccount: Boolean,
    googleAuthError: String?,
    githubAccountError: String?,
    onConnectGoogle: () -> Unit,
    onDisconnectGoogle: () -> Unit,
    onAddGithubAccount: (label: String, pat: String, onSuccess: () -> Unit) -> Unit,
    onDeleteGithubAccount: (GithubAccountEntity) -> Unit,
    onToggleRealtimeWatcher: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddGithubDialog by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "google_account_card") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Google Drive",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Google Drive", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (appPrefs.driveConnected) {
                                        appPrefs.googleEmail.ifBlank { "Terhubung" }
                                    } else {
                                        "Belum terhubung"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(
                                label = if (appPrefs.driveConnected) "Aktif" else "Off",
                                dotColor = if (appPrefs.driveConnected) MaterialTheme.colorScheme.secondary else AmberPending
                            )
                        }

                        if (!googleAuthError.isNullOrBlank()) {
                            Text(
                                text = googleAuthError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onConnectGoogle,
                                enabled = !isConnectingGoogle,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("accounts_connect_google_button")
                            ) {
                                if (isConnectingGoogle) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(if (appPrefs.driveConnected) "Otorisasi Ulang / Ganti" else "Hubungkan Google")
                                }
                            }
                            if (appPrefs.driveConnected) {
                                OutlinedButton(
                                    onClick = onDisconnectGoogle,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("accounts_disconnect_google_button")
                                ) {
                                    Text("Putuskan", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "github_section_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Akun GitHub",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { showAddGithubDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("add_github_account_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tambah")
                    }
                }
            }

            if (githubAccounts.isEmpty()) {
                item(key = "empty_github_accounts") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Belum ada akun GitHub",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(githubAccounts, key = { it.id }) { account ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                            .testTag("github_account_card_${account.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(account.label, style = MaterialTheme.typography.titleSmall)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "@${account.username}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        Icons.Default.Shield,
                                        contentDescription = "Terenkripsi",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Keystore",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onDeleteGithubAccount(account) },
                                modifier = Modifier.testTag("delete_gh_acc_${account.id}")
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Hapus akun GitHub",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            item(key = "preferences_card") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Layanan", style = MaterialTheme.typography.titleMedium)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pemantau Latar Belakang",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = appPrefs.realtimeWatcherEnabled,
                                onCheckedChange = onToggleRealtimeWatcher,
                                modifier = Modifier.testTag("accounts_realtime_switch")
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddGithubDialog) {
        var label by remember { mutableStateOf("") }
        var pat by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { if (!isAddingGithubAccount) showAddGithubDialog = false },
            title = { Text("Tambah Akun GitHub") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Label Akun") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_label_input")
                    )
                    OutlinedTextField(
                        value = pat,
                        onValueChange = { pat = it },
                        label = { Text("Fine-grained PAT") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_pat_input")
                    )
                    if (!githubAccountError.isNullOrBlank()) {
                        Text(
                            text = githubAccountError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isAddingGithubAccount,
                    onClick = { showAddGithubDialog = false }
                ) { Text("Batal") }
            },
            confirmButton = {
                Button(
                    enabled = !isAddingGithubAccount && pat.isNotBlank(),
                    onClick = {
                        onAddGithubAccount(label, pat) {
                            showAddGithubDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_github_button")
                ) {
                    if (isAddingGithubAccount) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Verifikasi & Simpan", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        )
    }
}
