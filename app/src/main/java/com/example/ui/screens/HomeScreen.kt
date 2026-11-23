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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import com.example.data.db.DriveAccountEntity
import com.example.data.db.GithubAccountEntity
import com.example.data.db.GithubRepoEntity
import com.example.sync.SourceKeys
import com.example.sync.TreeItem
import com.example.ui.components.StoragePermissionHelper

/**
 * Beranda: setiap akun (Drive atau GitHub) tampil sebagai pohon yang bisa dibuka.
 * Centang file/folder, lalu pakai Download atau Upload di bawah.
 */
@Composable
fun HomeScreen(
    driveAccounts: List<DriveAccountEntity>,
    githubAccounts: List<GithubAccountEntity>,
    repos: List<GithubRepoEntity>,
    trees: Map<String, List<TreeItem>>,
    loading: Set<String>,
    expanded: Set<String>,
    selected: Set<String>,
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
    onToggleRepo: (GithubRepoEntity) -> Unit,
    onReloadRepo: (GithubRepoEntity) -> Unit,
    onToggleFolder: (TreeItem) -> Unit,
    onToggleSelect: (TreeItem) -> Unit,
    onDownload: () -> Unit,
    onUpload: () -> Unit,
    onClearSelection: () -> Unit
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
                        title = account.displayName.ifBlank { account.email },
                        subtitle = account.email,
                        path = LocalPathHelper.driveRoot(account.email).absolutePath,
                        onReload = { onReloadDrive(account.email) },
                        onRemove = { onRemoveDrive(account.email) },
                        isLoading = sourceKey in loading
                    )
                }
                visibleTreeItems(trees[sourceKey].orEmpty(), expanded).forEach { treeItem ->
                    item(key = treeItem.key) {
                        TreeRow(
                            item = treeItem,
                            selected = treeItem.key in selected,
                            isLoading = treeItem.key in loading,
                            isExpanded = treeItem.key in expanded,
                            onToggleFolder = { onToggleFolder(treeItem) },
                            onToggleSelect = { onToggleSelect(treeItem) }
                        )
                    }
                }
            }

            // ---------- GitHub ----------
            item { Spacer(Modifier.height(8.dp)) }
            item { SectionTitle("GitHub", "Setiap repo jadi folder sendiri: ${LocalPathHelper.BASE_DIR}/${LocalPathHelper.GITHUB_PREFIX} <username>/<repo>/") }
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
                        title = account.label,
                        subtitle = "@${account.username}",
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
                    repoSection(
                        repo = repo,
                        trees = trees,
                        loading = loading,
                        expanded = expanded,
                        selected = selected,
                        onToggleRepo = onToggleRepo,
                        onReloadRepo = onReloadRepo,
                        onToggleFolder = onToggleFolder,
                        onToggleSelect = onToggleSelect
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (selected.isNotEmpty()) {
            ActionBar(
                count = selected.size,
                enabled = !busy,
                onDownload = onDownload,
                onUpload = onUpload,
                onClear = onClearSelection
            )
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

private fun LazyListScope.repoSection(
    repo: GithubRepoEntity,
    trees: Map<String, List<TreeItem>>,
    loading: Set<String>,
    expanded: Set<String>,
    selected: Set<String>,
    onToggleRepo: (GithubRepoEntity) -> Unit,
    onReloadRepo: (GithubRepoEntity) -> Unit,
    onToggleFolder: (TreeItem) -> Unit,
    onToggleSelect: (TreeItem) -> Unit
) {
    val sourceKey = SourceKeys.github(repo.fullName)
    val isOpen = sourceKey in expanded
    item(key = "repo:${repo.fullName}") {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { onToggleRepo(repo) },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isOpen) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null
                )
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Text(
                        text = LocalPathHelper.githubRoot(repo.owner, repo.name).absolutePath,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (sourceKey in loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else if (isOpen) {
                    IconButton(onClick = { onReloadRepo(repo) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Muat ulang repo")
                    }
                }
            }
        }
    }
    if (isOpen) {
        visibleTreeItems(trees[sourceKey].orEmpty(), expanded).forEach { treeItem ->
            item(key = treeItem.key) {
                TreeRow(
                    item = treeItem,
                    selected = treeItem.key in selected,
                    isLoading = treeItem.key in loading,
                    isExpanded = treeItem.key in expanded,
                    onToggleFolder = { onToggleFolder(treeItem) },
                    onToggleSelect = { onToggleSelect(treeItem) }
                )
            }
        }
    }
}

// ---------- Komponen ----------

/** Baris pohon yang terlihat: anak folder hanya muncul kalau induknya terbuka. */
private fun visibleTreeItems(items: List<TreeItem>, expanded: Set<String>): List<TreeItem> {
    if (items.isEmpty()) return emptyList()
    val byParent = items.groupBy { it.path.substringBeforeLast('/', "") }
    val out = mutableListOf<TreeItem>()
    fun walk(parentPath: String) {
        val children = byParent[parentPath].orEmpty()
            .sortedWith(compareByDescending<TreeItem> { it.isFolder }.thenBy { it.name.lowercase() })
        for (child in children) {
            out += child
            if (child.isFolder && child.key in expanded) walk(child.path)
        }
    }
    walk("")
    return out
}

@Composable
private fun SectionTitle(title: String, hint: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccountHeader(
    title: String,
    subtitle: String,
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
                Text(subtitle, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun TreeRow(
    item: TreeItem,
    selected: Boolean,
    isLoading: Boolean,
    isExpanded: Boolean,
    onToggleFolder: () -> Unit,
    onToggleSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (item.depth * 16).dp)
            .clickable { if (item.isFolder) onToggleFolder() else onToggleSelect() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
        if (item.isFolder) {
            Icon(
                imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Spacer(Modifier.width(18.dp))
        }
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    fontSize = 14.sp,
                    fontWeight = if (item.isFolder) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.archived && item.depth == 0) {
                    Spacer(Modifier.width(6.dp))
                    Tag("Archived")
                }
            }
            Text(
                text = rowSubtitle(item),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = (item.depth * 16).dp))
}

private fun rowSubtitle(item: TreeItem): String = when {
    item.isFolder -> "Folder"
    item.nativeMime != null || item.size == null -> "Dokumen Google · selalu ditransfer"
    else -> formatBytes(item.size)
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
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
private fun ActionBar(
    count: Int,
    enabled: Boolean,
    onDownload: () -> Unit,
    onUpload: () -> Unit,
    onClear: () -> Unit
) {
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("$count item dicentang", fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onDownload, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Download")
                }
                OutlinedButton(onClick = onUpload, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Upload")
                }
                TextButton(onClick = onClear, enabled = enabled) { Text("Batal") }
            }
        }
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
