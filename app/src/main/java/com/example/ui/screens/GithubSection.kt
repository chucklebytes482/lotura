@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// Bagian tab GitHub: akun, tombol tambah PAT, dan daftar repo.

internal fun LazyListScope.githubSection(
    githubAccounts: List<GithubAccountEntity>,
    repos: List<GithubRepoEntity>,
    loading: Set<String>,
    busySources: Set<String>,
    queuedSources: Set<String>,
    onAddClick: () -> Unit,
    onRefreshRepos: (GithubAccountEntity) -> Unit,
    onDeleteGithubAccount: (GithubAccountEntity) -> Unit,
    armedSources: Set<String>,
    onAddBothSides: (sourceKey: String, label: String) -> Unit,
    onPreviewSync: (sourceKey: String, label: String) -> Unit,
    onCancelSync: (String) -> Unit,
    onOpenLog: (String) -> Unit
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
                onCancel = {},
                onOpenLog = null
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
                    onAddBoth = { onAddBothSides(sourceKey, repo.fullName) },
                    onSync = { onPreviewSync(sourceKey, repo.fullName) },
                    onCancel = { onCancelSync(sourceKey) },
                    onOpenLog = { onOpenLog(sourceKey) }
                )
            }
        }
    }
}

