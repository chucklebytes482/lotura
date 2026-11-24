package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.example.data.SyncLogEntry
import com.example.data.SyncLogStore
import com.example.data.SyncStatus
import com.example.data.db.DriveAccountEntity
import com.example.data.db.GithubAccountEntity
import com.example.data.db.GithubRepoEntity
import com.example.sync.SourceKeys
import com.example.sync.TreeItem
import com.example.ui.tree.rootVisibleItems
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HomeTab(val title: String) {
    DRIVE("Google Drive"),
    GITHUB("GitHub"),
    LOG("Log standar")
}

/**
 * Beranda dengan tiga tab: Google Drive, GitHub, dan Log standar (semua sumber).
 * Ikon ↓ (download) dan ↑ (unggah) di tiap folder dasar dan tiap repo langsung menjalankan sinkron.
 * Ketuk kartu tidak melakukan apa pun.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    driveAccounts: List<DriveAccountEntity>,
    githubAccounts: List<GithubAccountEntity>,
    repos: List<GithubRepoEntity>,
    trees: Map<String, List<TreeItem>>,
    loading: Set<String>,
    logs: List<SyncLogEntry>,
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
    onSyncFolder: (TreeItem, Boolean) -> Unit,
    onSyncRepo: (GithubRepoEntity, Boolean) -> Unit
) {
    var showAddGithub by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(HomeTab.DRIVE) }
    val busy = busySources.isNotEmpty()

    Column(modifier.fillMaxSize()) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        TabRow(selectedTabIndex = tab.ordinal) {
            HomeTab.entries.forEach { t ->
                Tab(
                    selected = tab == t,
                    onClick = { tab = t },
                    text = { Text(t.title, fontSize = 13.sp, maxLines = 1) }
                )
            }
        }

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

            when (tab) {
                HomeTab.DRIVE -> driveSection(
                    driveAccounts = driveAccounts,
                    trees = trees,
                    loading = loading,
                    isConnectingDrive = isConnectingDrive,
                    driveAuthError = driveAuthError,
                    onConnectDrive = onConnectDrive,
                    onReloadDrive = onReloadDrive,
                    onRemoveDrive = onRemoveDrive,
                    onSyncFolder = onSyncFolder
                )
                HomeTab.GITHUB -> githubSection(
                    githubAccounts = githubAccounts,
                    repos = repos,
                    loading = loading,
                    onAddClick = { showAddGithub = true },
                    onRefreshRepos = onRefreshRepos,
                    onDeleteGithubAccount = onDeleteGithubAccount,
                    onSyncRepo = onSyncRepo
                )
                HomeTab.LOG -> logSection(logs)
            }
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

// ---------- Tab Google Drive ----------

private fun LazyListScope.driveSection(
    driveAccounts: List<DriveAccountEntity>,
    trees: Map<String, List<TreeItem>>,
    loading: Set<String>,
    isConnectingDrive: Boolean,
    driveAuthError: String?,
    onConnectDrive: () -> Unit,
    onReloadDrive: (String) -> Unit,
    onRemoveDrive: (String) -> Unit,
    onSyncFolder: (TreeItem, Boolean) -> Unit
) {
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
                onReload = { onReloadDrive(account.email) },
                onRemove = { onRemoveDrive(account.email) },
                isLoading = sourceKey in loading
            )
        }
        rootVisibleItems(trees[sourceKey].orEmpty()).forEach { folder ->
            item(key = folder.key) {
                FolderRow(
                    item = folder,
                    busy = sourceKey in loading,
                    onSync = { upload -> onSyncFolder(folder, upload) }
                )
            }
        }
    }
}

// ---------- Tab GitHub ----------

private fun LazyListScope.githubSection(
    githubAccounts: List<GithubAccountEntity>,
    repos: List<GithubRepoEntity>,
    loading: Set<String>,
    onAddClick: () -> Unit,
    onRefreshRepos: (GithubAccountEntity) -> Unit,
    onDeleteGithubAccount: (GithubAccountEntity) -> Unit,
    onSyncRepo: (GithubRepoEntity, Boolean) -> Unit
) {
    item { SectionTitle("GitHub", null) }
    item {
        Button(onClick = onAddClick) {
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
            item(key = "repo:${repo.fullName}") {
                RepoCard(
                    repo = repo,
                    busy = SourceKeys.github(repo.fullName) in loading,
                    onSync = { upload -> onSyncRepo(repo, upload) }
                )
            }
        }
    }
}

// ---------- Tab Log standar ----------

private fun LazyListScope.logSection(logs: List<SyncLogEntry>) {
    item {
        SectionTitle(
            "Log standar",
            "Semua sumber, terbaru di atas. Menyimpan ${SyncLogStore.MAX_ENTRIES} entri terakhir."
        )
    }
    if (logs.isEmpty()) {
        item { EmptyText("Belum ada aktivitas sync.") }
    }
    items(logs) { entry -> LogRow(entry) }
}

@Composable
private fun LogRow(entry: SyncLogEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (entry.upload) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = if (entry.upload) "Unggah" else "Download",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = entry.label,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(formatTime(entry.atMillis), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = entry.source,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when (entry.status) {
                    SyncStatus.OK -> Icons.Filled.Check to MaterialTheme.colorScheme.primary
                    SyncStatus.SEBAGIAN -> Icons.Filled.Warning to MaterialTheme.colorScheme.tertiary
                    SyncStatus.GAGAL -> Icons.Filled.Close to MaterialTheme.colorScheme.error
                }
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${statusText(entry.status)} · ${entry.transferred} ditransfer, ${entry.skipped} dilewati, ${entry.failed} gagal",
                    fontSize = 13.sp
                )
            }
            entry.error?.let {
                Spacer(Modifier.height(2.dp))
                ErrorText(it)
            }
        }
    }
}

// ---------- Kartu repo ----------

@Composable
private fun RepoCard(
    repo: GithubRepoEntity,
    busy: Boolean,
    onSync: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
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
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                SyncArrows(onDownload = { onSync(false) }, onUpload = { onSync(true) })
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
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
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

/** Ikon kecil di pojok kanan kartu: ↓ = download (sumber ke perangkat), ↑ = unggah (perangkat ke sumber). */
@Composable
private fun SyncArrows(onDownload: () -> Unit, onUpload: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = "Download", modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onUpload, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = "Unggah", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun FolderRow(
    item: TreeItem,
    busy: Boolean,
    onSync: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
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
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                SyncArrows(onDownload = { onSync(false) }, onUpload = { onSync(true) })
            }
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

private fun statusText(status: SyncStatus): String = when (status) {
    SyncStatus.OK -> "OK"
    SyncStatus.SEBAGIAN -> "Sebagian"
    SyncStatus.GAGAL -> "Gagal"
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(millis))
