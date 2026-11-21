package com.example.ui.screens

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.github.GithubRepoSummary
import com.example.ui.DriveBrowserUiState
import com.example.ui.GithubBrowserUiState
import com.example.ui.GithubFolderBrowserUiState
import com.example.ui.theme.JetBrainsMonoFamily
import java.io.File

@Composable
internal fun LocalFolderPickerDialog(
    initialPath: String,
    onDismiss: () -> Unit,
    onSelectPath: (String) -> Unit
) {
    val rootStorage = remember {
        runCatching { Environment.getExternalStorageDirectory() }.getOrElse { File("/storage/emulated/0") }
    }
    var currentDir by remember {
        val init = File(initialPath)
        mutableStateOf(if (init.exists() && init.isDirectory) init else rootStorage)
    }
    var newFolderName by remember { mutableStateOf("") }
    var refreshTick by remember { mutableIntStateOf(0) }

    val subdirs = remember(currentDir, refreshTick) {
        currentDir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentDir.parentFile != null && currentDir.absolutePath != rootStorage.absolutePath) {
                    IconButton(onClick = { currentDir = currentDir.parentFile!! }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Naik folder")
                    }
                }
                Text("Pilih Jalur di Perangkat", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = currentDir.absolutePath,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        placeholder = { Text("Folder baru...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val clean = newFolderName.trim()
                            if (clean.isNotEmpty()) {
                                val target = File(currentDir, clean)
                                if (target.mkdirs() || target.exists()) {
                                    newFolderName = ""
                                    currentDir = target
                                    refreshTick++
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Buat folder lokal")
                    }
                }

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier.heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (subdirs.isEmpty()) {
                        item {
                            Text(
                                text = "Tidak ada subfolder di direktori ini.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(subdirs, key = { it.absolutePath }) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { currentDir = folder }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(folder.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
        confirmButton = {
            Button(
                onClick = { onSelectPath(currentDir.absolutePath) },
                modifier = Modifier.testTag("confirm_local_path_button")
            ) {
                Text("Pilih Jalur Ini")
            }
        }
    )
}

@Composable
internal fun DriveFolderPickerSheet(
    state: DriveBrowserUiState,
    onOpenFolder: (parentId: String, folderName: String?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onSelectCurrent: (id: String, name: String) -> Unit
) {
    var newFolderName by remember { mutableStateOf("") }
    val currentCrumb = state.breadcrumbs.lastOrNull() ?: ("root" to "Drive Saya")
    val isInSubfolder = state.breadcrumbs.size > 1 && currentCrumb.first != "root"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Pilih Folder Google Drive", style = MaterialTheme.typography.titleLarge)

        Text(
            text = state.breadcrumbs.joinToString(" / ") { it.second },
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newFolderName,
                onValueChange = { newFolderName = it },
                placeholder = { Text("Buat folder Drive baru...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (newFolderName.isNotBlank()) {
                        onCreateFolder(newFolderName.trim())
                        newFolderName = ""
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "Buat folder")
            }
        }

        if (state.breadcrumbs.size > 1) {
            TextButton(
                onClick = {
                    val parent = state.breadcrumbs[state.breadcrumbs.size - 2]
                    onOpenFolder(parent.first, parent.second)
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Naik ke ${state.breadcrumbs[state.breadcrumbs.size - 2].second}")
            }
        }

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            !state.error.isNullOrBlank() -> {
                Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            state.folders.isEmpty() -> {
                Text(
                    text = "Folder kosong",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.folders, key = { it.id }) { folder ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenFolder(folder.id, folder.name) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(folder.name, modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = { onSelectCurrent(folder.id, folder.name) },
                                    modifier = Modifier.testTag("pick_drive_folder_${folder.id}")
                                ) {
                                    Text("Pilih")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isInSubfolder) {
            Button(
                onClick = { onSelectCurrent(currentCrumb.first, currentCrumb.second) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("confirm_drive_folder_button")
            ) {
                Text("Gunakan \"${currentCrumb.second}\"")
            }
        }
        Spacer(Modifier.heightIn(min = 12.dp))
    }
}

@Composable
internal fun GithubRepoPickerDialog(
    state: GithubBrowserUiState,
    onDismiss: () -> Unit,
    onSelectRepo: (GithubRepoSummary) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Repositori GitHub") },
        text = {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                !state.error.isNullOrBlank() -> {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                }
                state.repos.isEmpty() -> {
                    Text("Tidak ada repositori yang dapat diakses oleh token PAT ini.")
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.repos, key = { it.fullName }) { repo ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectRepo(repo) }
                                    .testTag("pick_gh_repo_${repo.name}")
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(repo.fullName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "Branch: ${repo.defaultBranch} • ${if (repo.isPrivate) "Privat" else "Publik"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
internal fun GithubFolderPickerSheet(
    state: GithubFolderBrowserUiState,
    onOpenPath: (String) -> Unit,
    onSelectFolder: (String) -> Unit
) {
    val directChildren = remember(state.allFolders, state.currentPath) {
        val prefix = if (state.currentPath.isEmpty()) "" else "${state.currentPath}/"
        state.allFolders
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix).substringBefore('/') }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedBy { it.lowercase() }
            .map { childName ->
                val fullPath = if (prefix.isEmpty()) childName else "$prefix$childName"
                childName to fullPath
            }
    }

    val breadcrumbText = if (state.currentPath.isEmpty()) {
        state.repo.ifBlank { "Repositori" }
    } else {
        "${state.repo} / ${state.currentPath.replace("/", " / ")}"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Pilih 1 Folder GitHub", style = MaterialTheme.typography.titleLarge)

        Text(
            text = breadcrumbText,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
            color = MaterialTheme.colorScheme.secondary
        )

        if (state.currentPath.isNotEmpty()) {
            TextButton(
                onClick = {
                    val parent = state.currentPath.substringBeforeLast('/', "")
                    onOpenPath(parent)
                },
                modifier = Modifier.testTag("gh_folder_up_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Naik folder")
            }
        }

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            !state.error.isNullOrBlank() -> {
                Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            directChildren.isEmpty() -> {
                Text(
                    text = if (state.currentPath.isEmpty()) {
                        "Tidak ada folder di repositori ini."
                    } else {
                        "Tidak ada subfolder di dalam \"${state.currentPath}\"."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(directChildren, key = { it.second }) { (childName, fullPath) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenPath(fullPath) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Text(childName, modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = { onSelectFolder(fullPath) },
                                    modifier = Modifier.testTag("pick_gh_folder_$fullPath")
                                ) {
                                    Text("Pilih")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Root repo cannot be selected; confirm button only appears when inside a specific folder
        if (state.currentPath.isNotEmpty()) {
            Button(
                onClick = { onSelectFolder(state.currentPath) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("confirm_gh_folder_button")
            ) {
                Text("Gunakan \"${state.currentPath}\"")
            }
        }
        Spacer(Modifier.heightIn(min = 12.dp))
    }
}
