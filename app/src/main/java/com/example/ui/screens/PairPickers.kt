package com.example.ui.screens

import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.PairPathHelper
import com.example.github.GithubRepoSummary
import com.example.ui.DriveBrowserUiState
import com.example.ui.GithubBrowserUiState
import com.example.ui.GithubFolderBrowserUiState
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.providerBrandColors

@Composable
internal fun DriveFolderPickerSheet(
    state: DriveBrowserUiState,
    pairedDriveFolderIds: Set<String> = emptySet(),
    onOpenFolder: (parentId: String, folderName: String?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onSelectCurrent: (id: String, name: String) -> Unit
) {
    val brand = providerBrandColors(isGithub = false)
    var newFolderName by remember { mutableStateOf("") }
    val currentCrumb = state.breadcrumbs.lastOrNull() ?: ("root" to "Drive Saya")
    val isInSubfolder = state.breadcrumbs.size > 1 && currentCrumb.first != "root"
    val isCurrentPaired = currentCrumb.first in pairedDriveFolderIds

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
            color = brand.accent
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = brand.accent,
                    contentColor = brand.onAccent
                ),
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
                ) { CircularProgressIndicator(color = brand.accent) }
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
                        val isPaired = folder.id in pairedDriveFolderIds
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isPaired) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                            } else {
                                brand.cardSurface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (isPaired) MaterialTheme.colorScheme.outline.copy(alpha = 0.45f) else brand.border,
                                    RoundedCornerShape(12.dp)
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenFolder(folder.id, folder.name) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (isPaired) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else brand.accent
                                )
                                Text(
                                    text = folder.name,
                                    color = if (isPaired) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isPaired) {
                                    PairedFolderBadge(testTag = "drive_folder_paired_${folder.id}")
                                } else {
                                    TextButton(
                                        onClick = { onSelectCurrent(folder.id, folder.name) },
                                        modifier = Modifier.testTag("pick_drive_folder_${folder.id}")
                                    ) {
                                        Text("Pilih", color = brand.accent, fontWeight = FontWeight.SemiBold)
                                    }
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
                enabled = !isCurrentPaired,
                colors = ButtonDefaults.buttonColors(
                    containerColor = brand.accent,
                    contentColor = brand.onAccent
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("confirm_drive_folder_button")
            ) {
                Text(
                    if (isCurrentPaired) {
                        "\"${currentCrumb.second}\" sudah terpasang"
                    } else {
                        "Gunakan \"${currentCrumb.second}\""
                    }
                )
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
    val brand = providerBrandColors(isGithub = true)
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
                        CircularProgressIndicator(color = brand.accent)
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
                                color = brand.cardSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, brand.border, RoundedCornerShape(12.dp))
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
    pairedGithubPaths: Set<String> = emptySet(),
    onOpenPath: (String) -> Unit,
    onSelectFolder: (String) -> Unit
) {
    val brand = providerBrandColors(isGithub = true)
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

    val normalizedCurrentPath = PairPathHelper.normalizeGithubFolderPath(state.currentPath)
    val isCurrentPaired = normalizedCurrentPath.isNotEmpty() && normalizedCurrentPath in pairedGithubPaths

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
            color = brand.accent
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
                ) { CircularProgressIndicator(color = brand.accent) }
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
                        val normalizedFullPath = PairPathHelper.normalizeGithubFolderPath(fullPath)
                        val isPaired = normalizedFullPath in pairedGithubPaths
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isPaired) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                            } else {
                                brand.cardSurface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (isPaired) MaterialTheme.colorScheme.outline.copy(alpha = 0.45f) else brand.border,
                                    RoundedCornerShape(12.dp)
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenPath(fullPath) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (isPaired) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else brand.accent
                                )
                                Text(
                                    text = childName,
                                    color = if (isPaired) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isPaired) {
                                    PairedFolderBadge(testTag = "gh_folder_paired_$normalizedFullPath")
                                } else {
                                    TextButton(
                                        onClick = { onSelectFolder(fullPath) },
                                        modifier = Modifier.testTag("pick_gh_folder_$fullPath")
                                    ) {
                                        Text("Pilih", color = brand.accent, fontWeight = FontWeight.SemiBold)
                                    }
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
                enabled = !isCurrentPaired,
                colors = ButtonDefaults.buttonColors(
                    containerColor = brand.accent,
                    contentColor = brand.onAccent
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("confirm_gh_folder_button")
            ) {
                Text(
                    if (isCurrentPaired) {
                        "\"${state.currentPath}\" sudah terpasang"
                    } else {
                        "Gunakan \"${state.currentPath}\""
                    }
                )
            }
        }
        Spacer(Modifier.heightIn(min = 12.dp))
    }
}

@Composable
private fun PairedFolderBadge(testTag: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "Terpasang",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
