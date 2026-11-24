@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.sync.SkipDetail
import com.example.sync.SkipReason
import com.example.sync.SourceKeys
import com.example.sync.TreeItem
import com.example.ui.tree.rootVisibleItems
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HomeTab(val title: String) {
    DRIVE("Google Drive"),
    GITHUB("GitHub"),
    LOG("Log standar")
}

/** Keadaan satu sumber: menunggu giliran, sedang sync, atau sedang memuat pohon. */
private enum class RowActivity { IDLE, LOADING, QUEUED, RUNNING }

private fun activityOf(sourceKey: String, loading: Set<String>, queued: Set<String>, busy: Set<String>): RowActivity =
    when {
        sourceKey in busy -> RowActivity.RUNNING
        sourceKey in queued -> RowActivity.QUEUED
        sourceKey in loading -> RowActivity.LOADING
        else -> RowActivity.IDLE
    }

/**
 * Beranda dengan tiga tab: Google Drive, GitHub, dan Log standar (semua sumber).
 * Tab bisa diganti dengan geser ke kiri atau kanan, atau dengan mengetuk judul tab.
 * Ikon ↓ (download) dan ↑ (unggah) di tiap folder dasar dan tiap repo langsung menjalankan sinkron.
 * Sync berjalan satu per satu; sumber yang sedang antre atau berjalan bisa dibatalkan.
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
    queuedSources: Set<String>,
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
    onSyncRepo: (GithubRepoEntity, Boolean) -> Unit,
    onCancelSync: (String) -> Unit
) {
    // Disimpan saat rotasi. PAT sengaja tidak disimpan di state yang tersimpan.
    var showAddGithub by rememberSaveable { mutableStateOf(false) }
    val busy = busySources.isNotEmpty()
    val pagerState = rememberPagerState(pageCount = { HomeTab.entries.size })
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize()) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        TabRow(selectedTabIndex = pagerState.currentPage) {
            HomeTab.entries.forEachIndexed { index, t ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(t.title, fontSize = 13.sp, maxLines = 1) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { page ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                noticeItems(
                    isOnline = isOnline,
                    hasStoragePermission = hasStoragePermission,
                    onRequestStoragePermission = onRequestStoragePermission,
                    onOpenWebGuide = onOpenWebGuide
                )

                when (HomeTab.entries[page]) {
                    HomeTab.DRIVE -> driveSection(
                        driveAccounts = driveAccounts,
                        trees = trees,
                        loading = loading,
                        busySources = busySources,
                        queuedSources = queuedSources,
                        isConnectingDrive = isConnectingDrive,
                        driveAuthError = driveAuthError,
                        onConnectDrive = onConnectDrive,
                        onReloadDrive = onReloadDrive,
                        onRemoveDrive = onRemoveDrive,
                        onSyncFolder = onSyncFolder,
                        onCancelSync = onCancelSync
                    )
                    HomeTab.GITHUB -> githubSection(
                        githubAccounts = githubAccounts,
                        repos = repos,
                        loading = loading,
                        busySources = busySources,
                        queuedSources = queuedSources,
                        onAddClick = { showAddGithub = true },
                        onRefreshRepos = onRefreshRepos,
                        onDeleteGithubAccount = onDeleteGithubAccount,
                        onSyncRepo = onSyncRepo,
                        onCancelSync = onCancelSync
                    )
                    HomeTab.LOG -> logSection(logs)
                }
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

/** Pemberitahuan di atas setiap tab: offline, izin, dan tombol panduan. */
private fun LazyListScope.noticeItems(
    isOnline: Boolean,
    hasStoragePermission: Boolean,
    onRequestStoragePermission: () -> Unit,
    onOpenWebGuide: () -> Unit
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
}

// ---------- Tab Google Drive ----------

private fun LazyListScope.driveSection(
    driveAccounts: List<DriveAccountEntity>,
    trees: Map<String, List<TreeItem>>,
    loading: Set<String>,
    busySources: Set<String>,
    queuedSources: Set<String>,
    isConnectingDrive: Boolean,
    driveAuthError: String?,
    onConnectDrive: () -> Unit,
    onReloadDrive: (String) -> Unit,
    onRemoveDrive: (String) -> Unit,
    onSyncFolder: (TreeItem, Boolean) -> Unit,
    onCancelSync: (String) -> Unit
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
        val activity = activityOf(sourceKey, loading, queuedSources, busySources)
        item(key = "drive-head:${account.email}") {
            AccountHeader(
                title = account.email,
                activity = activity,
                onReload = { onReloadDrive(account.email) },
                onRemove = { onRemoveDrive(account.email) },
                onCancel = { onCancelSync(sourceKey) }
            )
        }
        rootVisibleItems(trees[sourceKey].orEmpty()).forEach { folder ->
            item(key = folder.key) {
                FolderRow(
                    item = folder,
                    enabled = activity == RowActivity.IDLE,
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
    busySources: Set<String>,
    queuedSources: Set<String>,
    onAddClick: () -> Unit,
    onRefreshRepos: (GithubAccountEntity) -> Unit,
    onDeleteGithubAccount: (GithubAccountEntity) -> Unit,
    onSyncRepo: (GithubRepoEntity, Boolean) -> Unit,
    onCancelSync: (String) -> Unit
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
                activity = RowActivity.IDLE,
                onReload = { onRefreshRepos(account) },
                onRemove = { onDeleteGithubAccount(account) },
                onCancel = {}
            )
        }
        val accountRepos = repos.filter { it.accountId == account.id }
            .sortedWith(compareBy<GithubRepoEntity> { it.archived }.thenBy { it.fullName.lowercase() })
        if (accountRepos.isEmpty()) {
            item(key = "gh-empty:${account.id}") { EmptyText("Tidak ada repo.") }
        }
        accountRepos.forEach { repo ->
            val sourceKey = SourceKeys.github(repo.fullName)
            item(key = "repo:${repo.fullName}") {
                RepoCard(
                    repo = repo,
                    activity = activityOf(sourceKey, loading, queuedSources, busySources),
                    onSync = { upload -> onSyncRepo(repo, upload) },
                    onCancel = { onCancelSync(sourceKey) }
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
            "Semua sumber, terbaru di atas. Menyimpan ${SyncLogStore.MAX_ENTRIES} entri terakhir. Ketuk entri untuk melihat item yang dilewati."
        )
    }
    if (logs.isEmpty()) {
        item { EmptyText("Belum ada aktivitas sync.") }
    }
    items(logs) { entry -> LogRow(entry) }
}

@Composable
private fun LogRow(entry: SyncLogEntry) {
    val skippedTotal = entry.skipped + entry.hidden
    val hasDetail = entry.skipDetails.isNotEmpty()
    var expanded by rememberSaveable(entry.atMillis, entry.source, entry.label) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = { if (hasDetail) expanded = !expanded }
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
                    SyncStatus.DIBATALKAN -> Icons.Filled.Close to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${statusText(entry.status)} · ${entry.transferred} berhasil, $skippedTotal dilewati, ${entry.failed} gagal",
                    fontSize = 13.sp
                )
            }
            if (skippedTotal > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Dilewati: ${skipCountsText(entry.skipCounts, entry.hidden)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            entry.error?.let {
                Spacer(Modifier.height(2.dp))
                ErrorText(it)
            }
            if (hasDetail) {
                Spacer(Modifier.height(4.dp))
                if (expanded) {
                    entry.skipDetails.forEach { detail ->
                        Text(
                            "${detail.path} — ${skipReasonLabel(detail.reason)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val notShown = skippedTotal - entry.skipDetails.size
                    if (notShown > 0) {
                        Text("…dan $notShown item lain tidak ditampilkan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Ketuk untuk menutup", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("Ketuk untuk lihat daftar item yang dilewati", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ---------- Kartu repo ----------

@Composable
private fun RepoCard(
    repo: GithubRepoEntity,
    activity: RowActivity,
    onSync: (Boolean) -> Unit,
    onCancel: () -> Unit
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
            if (activity == RowActivity.IDLE) {
                SyncArrows(enabled = true, onDownload = { onSync(false) }, onUpload = { onSync(true) })
            } else {
                ActivityStatus(activity, onCancel)
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
    activity: RowActivity,
    onReload: () -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit
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
            if (activity == RowActivity.IDLE) {
                IconButton(onClick = onReload) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Muat ulang")
                }
            } else {
                ActivityStatus(activity, onCancel)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Hapus akun")
            }
        }
    }
}

/** Status aktivitas di tempat ikon sinkron: spinner, teks menunggu/berjalan, dan tombol batal. */
@Composable
private fun ActivityStatus(activity: RowActivity, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (activity == RowActivity.QUEUED) {
            Text("Menunggu giliran", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (activity == RowActivity.RUNNING) {
            Text("Sync berjalan", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(6.dp))
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        if (activity == RowActivity.QUEUED || activity == RowActivity.RUNNING) {
            IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Batalkan sync", modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Ikon kecil di pojok kanan kartu: ↓ = download (sumber ke perangkat), ↑ = unggah (perangkat ke sumber). */
@Composable
private fun SyncArrows(enabled: Boolean, onDownload: () -> Unit, onUpload: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDownload, enabled = enabled, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = "Download", modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onUpload, enabled = enabled, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = "Unggah", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun FolderRow(
    item: TreeItem,
    enabled: Boolean,
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
            // Dinonaktifkan selama sumber ini antre atau berjalan; statusnya ada di header akun.
            SyncArrows(enabled = enabled, onDownload = { onSync(false) }, onUpload = { onSync(true) })
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
    SyncStatus.DIBATALKAN -> "Dibatalkan"
}

/** Label pendek untuk satu alasan skip. */
private fun skipReasonLabel(reason: SkipReason): String = when (reason) {
    SkipReason.SAME_SIZE -> "ukuran sama, dianggap sudah sinkron"
    SkipReason.NOT_A_LOCAL_FILE -> "tidak ada sebagai file di perangkat"
    SkipReason.TOO_LARGE_FOR_GITHUB -> "lebih dari 45 MB, melewati batas aman GitHub"
    SkipReason.HIDDEN -> "nama diawali titik, tersembunyi"
}

/** Ringkasan jumlah per alasan, mis. "7 ukuran sama, 3 lebih dari 45 MB". */
private fun skipCountsText(counts: Map<SkipReason, Int>, hidden: Int): String {
    val parts = mutableListOf<String>()
    counts[SkipReason.SAME_SIZE]?.takeIf { it > 0 }?.let { parts += "$it ukuran sama" }
    counts[SkipReason.NOT_A_LOCAL_FILE]?.takeIf { it > 0 }?.let { parts += "$it tidak ada di perangkat" }
    counts[SkipReason.TOO_LARGE_FOR_GITHUB]?.takeIf { it > 0 }?.let { parts += "$it lebih dari 45 MB" }
    val hiddenCount = counts[SkipReason.HIDDEN] ?: hidden
    if (hiddenCount > 0) parts += "$hiddenCount tersembunyi"
    return parts.joinToString(", ").ifEmpty { "-" }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(millis))
