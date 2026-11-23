package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LocalPathHelper
import com.example.data.SyncSummary
import com.example.data.SyncStatus
import com.example.data.db.DriveAccountEntity
import com.example.data.db.GithubAccountEntity
import com.example.data.db.GithubRepoEntity
import com.example.sync.SourceKeys
import com.example.sync.TreeItem
import com.example.ui.components.StoragePermissionHelper
import com.example.ui.tree.rootVisibleItems

/**
 * Beranda: setiap akun (Drive atau GitHub) menampilkan folder dasarnya.
 * Ikon download/unggah di tiap folder dasar (dan di tiap repo) langsung menjalankan sinkron.
 */
@Composable
fun HomeScreen(
    driveAccounts: List<DriveAccountEntity>,
    githubAccounts: List<GithubAccountEntity>,
    repos: List<GithubRepoEntity>,
    trees: Map<String, List<TreeItem>>,
    loading: Set<String>,
    summaries: Map<String, SyncSummary>,
    busySources: Set<String>,
    isConnectingDrive: Boolean,
    driveAuthError: String?,
    isAddingGithub: Boolean,
    githubAccountError: String?,
    hasStoragePermission: Boolean,
    isOnline: Boolean,
    onRequestStoragePermission: () -> Unit,
    onOpenWebGuide: () -> Unit,
    onConnectDrive: () -> Unit,
    onRemoveDrive: (String) -> Unit,
    onReloadDrive: (String) -> Unit,
    onAddGithubAccount: (String, () -> Unit) -> Unit,
    onRefreshRepos: (GithubAccountEntity) -> Unit,
    onDeleteGithubAccount: (GithubAccountEntity) -> Unit,
    onOpenDriveFolder: (TreeItem) -> Unit,
    onOpenRepo: (GithubRepoEntity) -> Unit,
    onSyncFolder: (TreeItem, Boolean) -> Unit,
    onSyncRepo: (GithubRepoEntity, Boolean) -> Unit
) {
    var showAddGithub by remember { mutableStateOf(false) }
    val busy = busySources.isNotEmpty()

    Column(Modifier.fillMaxWidth()) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!isOnline) {
                item { Notice("Tidak ada koneksi internet.") }
            }
            if (!hasStoragePermission) {
                item {
                    Notice("Izin akses semua file belum diberikan. Lotura butuh izin ini untuk membaca dan menulis folder lokal.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRequestStoragePermission) { Text("Beri izin") }
                }
            }
            item {
                TextButton(onClick = onOpenWebGuide) { Text("Buka panduan") }
            }

            // ---------- Google Drive ----------
            item { SectionTitle("Google Drive", "Folder lokal: ${LocalPathHelper.BASE_DIR}/${LocalPathHelper.DRIVE_PREFIX} <email>/") }
            item {
                Button(onClick = onConnectDrive, enabled = !isConnectingDrive) {
                    Text(if (isConnectingDrive) "Menghubungkan..." else "Hubungkan akun Google Drive")
                }
            }
            driveAuthError?.let { item { ErrorText(it) } }
            if (driveAccounts.isEmpty()) {
                item { EmptyText("Belum ada akun Drive.") }
            }
            driveAccounts.forEach { account ->
                val sourceKey = SourceKeys.drive(account.email)
                item(key = "drive-head:${account.email}") {
                    AccountHeader(
                        title = account.email,
                        subtitle = null,
                        path = null,
                        onReload = { onReloadDrive(account.email) },
                        onRemove = { onRemoveDrive(account.email) },
                        isLoading = sourceKey in loading
                    )
                }
                rootVisibleItems(trees[sourceKey].orEmpty()).forEach { folder ->
                    item(key = folder.key) {
                        FolderRow(
                            item = folder,
                            summary = summaries[folder.key],
                            enabled = !busy,
                            onOpen = { onOpenDriveFolder(folder) },
                            onDownload = { onSyncFolder(folder, false) },
                            onUpload = { onSyncFolder(folder, true) }
                        )
                    }
                }
            }

            // ---------- GitHub ----------
            item { Spacer(Modifier.height(8.dp)) }
            item { SectionTitle("GitHub", null) }
            item {
                Button(onClick = { showAddGithub = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Tambah akun GitHub")
                }
            }
            if (githubAccounts.isEmpty()) {
                item { EmptyText("Belum ada akun GitHub.") }
            }
            githubAccounts.forEach { account ->
                item(key = "gh-head:${account.id}") {
                    AccountHeader(
                        title = account.username,
                        subtitle = null,
                        path = null,
                        onReload = { onRefreshRepos(account) },
                        onRemove = { onDeleteGithubAccount(account) },
                        isLoading = false
                    )
                }
                val accountRepos = repos.filter { it.accountId == account.id }
                    .sortedWith(compareBy<GithubRepoEntity> { it.archived }.thenBy { it.fullName.lowercase() })
                if (accountRepos.isEmpty()) {
                    item(key = "gh-empty:${account.id}") { EmptyText("Tidak ada repo.") }
                }
                accountRepos.forEach { repo ->
                    repoCard(
                        repo = repo,
                        loading = loading,
                        summary = summaries[SourceKeys.github(repo.fullName)],
                        busy = busy,
                        onOpenRepo = onOpenRepo,
                        onSyncRepo = onSyncRepo
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

    }

    if (showAddGithub) {
        AddGithubDialog(
            isAdding = isAddingGithub,
            error = githubAccountError,
            onDismiss = { showAddGithub = false },
            onSubmit = { pat ->
                onAddGithubAccount(pat) { showAddGithub = false }
            }
        )
    }
}

// ---------- Bagian repo GitHub ----------

private fun LazyListScope.repoCard(
    repo: GithubRepoEntity,
    loading: Set<String>,
    summary: SyncSummary?,
    busy: Boolean,
    onOpenRepo: (GithubRepoEntity) -> Unit,
    onSyncRepo: (GithubRepoEntity, Boolean) -> Unit
) {
    val sourceKey = SourceKeys.github(repo.fullName)
    item(key = "repo:${repo.fullName}") {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { onOpenRepo(repo) },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SyncIcons(
                    enabled = !busy,
                    onDownload = { onSyncRepo(repo, false) },
                    onUpload = { onSyncRepo(repo, true) }
                )
                Row(
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = repo.name,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (repo.archived) {
                        Spacer(Modifier.width(6.dp))
                        Tag("Archived")
                    }
                    if (repo.isPrivate) {
                        Spacer(Modifier.width(6.dp))
                        Tag("Private")
                    }
                }
                if (sourceKey in loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    StatusIcon(summary)
                }
            }
        }
    }
}

// ---------- Komponen ----------

@Composable
private fun SectionTitle(title: String, hint: String?) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        hint?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun AccountHeader(
    title: String,
    subtitle: String?,
    path: String?,
    onReload: () -> Unit,
    onRemove: () -> Unit,
    isLoading: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let {
                    Text(it, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                path?.let {
                    Text(it, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onReload) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Muat ulang")
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Hapus akun")
            }
        }
    }
}

@Composable
private fun SyncIcons(enabled: Boolean, onDownload: () -> Unit, onUpload: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDownload, enabled = enabled) {
            Icon(Icons.Rounded.CloudDownload, contentDescription = "Download")
        }
        IconButton(onClick = onUpload, enabled = enabled) {
            Icon(Icons.Rounded.CloudUpload, contentDescription = "Unggah")
        }
    }
}

/** Ikon status sync terakhir: centang (OK), peringatan (SEBAGIAN), silang (GAGAL). Kosong jika belum pernah sync. */
@Composable
private fun StatusIcon(summary: SyncSummary?) {
    if (summary == null) return
    val (icon, tint, desc) = when (summary.status) {
        SyncStatus.OK -> Triple(Icons.Filled.Check, MaterialTheme.colorScheme.primary, "Sync terakhir OK")
        SyncStatus.SEBAGIAN -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.tertiary, "Sync terakhir sebagian")
        SyncStatus.GAGAL -> Triple(Icons.Filled.Close, MaterialTheme.colorScheme.error, "Sync terakhir gagal")
    }
    Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.padding(end = 6.dp))
}

@Composable
private fun FolderRow(
    item: TreeItem,
    summary: SyncSummary?,
    enabled: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onUpload: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SyncIcons(enabled = enabled, onDownload = onDownload, onUpload = onUpload)
            Row(
                modifier = Modifier.weight(1f).padding(start = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.archived) {
                    Spacer(Modifier.width(6.dp))
                    Tag("Archived")
                }
            }
            StatusIcon(summary)
        }
    }
    HorizontalDivider()
}

@Composable
private fun Tag(label: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun AddGithubDialog(
    isAdding: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var pat by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!isAdding) onDismiss() },
        title = { Text("Tambah akun GitHub") },
        text = {
            Column {
                Text(
                    "Pakai fine-grained PAT dengan akses read/write Contents ke repo yang ingin disinkronkan. Semua repo di akun itu akan terhubung.",
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pat,
                    onValueChange = { pat = it },
                    label = { Text("Fine-grained PAT") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isAdding,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    ErrorText(it)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(pat) }, enabled = !isAdding && pat.isNotBlank()) {
                Text(if (isAdding) "Memverifikasi..." else "Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isAdding) { Text("Batal") }
        }
    )
}

@Composable
private fun Notice(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(text, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
}

@Composable
private fun EmptyText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
}
