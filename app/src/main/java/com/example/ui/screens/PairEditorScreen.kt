package com.example.ui.screens

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.PairPathHelper
import com.example.data.db.DeletePolicy
import com.example.data.db.GithubAccountEntity
import com.example.data.db.SyncPairEntity
import com.example.data.db.SyncSourceType
import com.example.ui.DriveBrowserUiState
import com.example.ui.GithubBrowserUiState
import com.example.ui.GithubFolderBrowserUiState
import com.example.ui.theme.JetBrainsMonoFamily
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PairEditorScreen(
    existingPair: SyncPairEntity?,
    githubAccounts: List<GithubAccountEntity>,
    driveBrowser: DriveBrowserUiState,
    githubBrowser: GithubBrowserUiState,
    githubFolderBrowser: GithubFolderBrowserUiState,
    onOpenDriveFolder: (parentId: String, folderName: String?) -> Unit,
    onCreateDriveFolder: (String) -> Unit,
    onLoadGithubRepos: (Long) -> Unit,
    onLoadGithubFolders: (accountId: Long, owner: String, repo: String, branch: String) -> Unit,
    onOpenGithubFolderPath: (String) -> Unit,
    onSavePair: (SyncPairEntity) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val defaultBaseDir = remember {
        runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
        }.getOrDefault("/storage/emulated/0/Documents")
    }

    var sourceType by remember(existingPair) {
        mutableStateOf(existingPair?.sourceType ?: SyncSourceType.DRIVE)
    }

    // Drive source state
    var driveFolderId by remember(existingPair) { mutableStateOf(existingPair?.driveFolderId ?: "") }
    var driveFolderName by remember(existingPair) { mutableStateOf(existingPair?.driveFolderName ?: "") }

    // GitHub source state
    var githubAccountId by remember(existingPair, githubAccounts) {
        mutableStateOf(existingPair?.githubAccountId ?: githubAccounts.firstOrNull()?.id)
    }
    var githubOwner by remember(existingPair) { mutableStateOf(existingPair?.githubOwner ?: "") }
    var githubRepo by remember(existingPair) { mutableStateOf(existingPair?.githubRepo ?: "") }
    var githubBranch by remember(existingPair) { mutableStateOf(existingPair?.githubBranch ?: "main") }
    var githubSubfolder by remember(existingPair) { mutableStateOf(existingPair?.githubSubfolder ?: "") }

    // Local device base directory (folder name is auto-matched & auto-created from source)
    var localBasePath by remember(existingPair) {
        val initial = if (existingPair != null) {
            val existingSourceFolder = if (existingPair.isGithubPair) {
                PairPathHelper.sourceFolderNameForGithub(existingPair.githubSubfolder)
            } else {
                PairPathHelper.sourceFolderNameForDrive(existingPair.driveFolderName)
            }
            PairPathHelper.extractBaseDirForEditing(existingPair.localPath, existingSourceFolder)
        } else {
            defaultBaseDir
        }
        mutableStateOf(initial)
    }

    val sourceFolderName = remember(sourceType, driveFolderName, githubSubfolder) {
        if (sourceType == SyncSourceType.DRIVE) {
            PairPathHelper.sourceFolderNameForDrive(driveFolderName)
        } else {
            PairPathHelper.sourceFolderNameForGithub(githubSubfolder)
        }
    }

    val autoPairName = remember(sourceType, driveFolderName, githubRepo, githubSubfolder) {
        if (sourceType == SyncSourceType.DRIVE) {
            PairPathHelper.pairNameForDrive(driveFolderName)
        } else {
            PairPathHelper.pairNameForGithub(githubRepo, githubSubfolder)
        }
    }

    val resolvedLocalPath = remember(localBasePath, sourceFolderName) {
        PairPathHelper.resolveLocalTargetPath(localBasePath, sourceFolderName)
    }

    val localFolderExists = remember(resolvedLocalPath) {
        resolvedLocalPath.isNotBlank() && File(resolvedLocalPath).exists()
    }

    var validationError by remember { mutableStateOf<String?>(null) }
    var showLocalPicker by remember { mutableStateOf(false) }
    var showDrivePicker by remember { mutableStateOf(false) }
    var showGithubRepoPicker by remember { mutableStateOf(false) }
    var showGithubFolderPicker by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Langkah 1: Pilih Sumber (Google Drive atau 1 Folder GitHub)
            EditorSectionCard(title = "1. Pilih Sumber") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SourceTypeChoiceCard(
                        title = "Google Drive",
                        icon = Icons.Default.CloudQueue,
                        selected = sourceType == SyncSourceType.DRIVE,
                        onClick = {
                            sourceType = SyncSourceType.DRIVE
                            validationError = null
                        },
                        testTag = "source_type_drive_card",
                        modifier = Modifier.weight(1f)
                    )
                    SourceTypeChoiceCard(
                        title = "GitHub",
                        icon = Icons.Default.Code,
                        selected = sourceType == SyncSourceType.GITHUB,
                        onClick = {
                            sourceType = SyncSourceType.GITHUB
                            validationError = null
                        },
                        testTag = "source_type_github_card",
                        modifier = Modifier.weight(1f)
                    )
                }

                if (sourceType == SyncSourceType.DRIVE) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "Folder Google Drive",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = driveFolderName.ifBlank { "Folder Drive belum dipilih" },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (driveFolderId.isNotBlank()) {
                                    Text(
                                        text = driveFolderId,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onOpenDriveFolder("root", "Drive Saya")
                            showDrivePicker = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("pair_pick_drive_folder_button")
                    ) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (driveFolderId.isBlank()) "Pilih Folder Google Drive" else "Ganti Folder Google Drive")
                    }
                } else {
                    if (githubAccounts.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Tambahkan akun GitHub di tab Akun.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            githubAccounts.forEach { acc ->
                                FilterChip(
                                    selected = githubAccountId == acc.id,
                                    onClick = {
                                        if (githubAccountId != acc.id) {
                                            githubAccountId = acc.id
                                            githubOwner = ""
                                            githubRepo = ""
                                            githubSubfolder = ""
                                        }
                                    },
                                    label = { Text("@${acc.username}") },
                                    modifier = Modifier.testTag("select_gh_acc_${acc.id}")
                                )
                            }
                        }

                        if (githubAccountId != null) {
                            OutlinedButton(
                                onClick = {
                                    onLoadGithubRepos(githubAccountId!!)
                                    showGithubRepoPicker = true
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("pair_pick_github_repo_button")
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (githubRepo.isBlank()) {
                                        "Pilih Repositori GitHub"
                                    } else {
                                        "$githubOwner/$githubRepo ($githubBranch)"
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (githubAccountId != null && githubOwner.isNotBlank() && githubRepo.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "Folder GitHub",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = githubSubfolder.ifBlank { "Folder repo belum dipilih" },
                                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "$githubOwner/$githubRepo",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    onLoadGithubFolders(githubAccountId!!, githubOwner, githubRepo, githubBranch)
                                    showGithubFolderPicker = true
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("pair_pick_github_folder_button")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (githubSubfolder.isBlank()) "Pilih 1 Folder di Repo" else "Ganti Folder di Repo")
                            }
                        }
                    }
                }

                if (autoPairName.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Nama Pasangan",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = autoPairName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("auto_pair_name_preview")
                        )
                    }
                }
            }

            // Langkah 2: Pilih Jalur di Perangkat (nama folder disamakan & dibuat otomatis)
            EditorSectionCard(title = "2. Jalur di Perangkat") {
                OutlinedButton(
                    onClick = { showLocalPicker = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("pair_browse_local_button")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pilih Jalur di Perangkat")
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (sourceFolderName.isNotBlank()) "Folder Tujuan" else "Jalur Dasar",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = resolvedLocalPath.ifBlank { localBasePath },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("pair_resolved_local_path")
                        )
                        if (sourceFolderName.isNotBlank()) {
                            Text(
                                text = if (localFolderExists) "Folder sudah ada" else "Folder dibuat otomatis saat disimpan",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (localFolderExists) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (!validationError.isNullOrBlank()) {
                Text(
                    text = validationError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("pair_validation_error")
                )
            }

            Button(
                onClick = {
                    val isDrive = sourceType == SyncSourceType.DRIVE
                    when {
                        isDrive && (driveFolderId.isBlank() || driveFolderName.isBlank()) -> {
                            validationError = "Pilih folder Google Drive terlebih dahulu."
                        }
                        !isDrive && githubAccountId == null -> {
                            validationError = "Pilih akun GitHub terlebih dahulu."
                        }
                        !isDrive && (githubOwner.isBlank() || githubRepo.isBlank()) -> {
                            validationError = "Pilih repositori GitHub terlebih dahulu."
                        }
                        !isDrive && githubSubfolder.trim().trim('/').isBlank() -> {
                            validationError = "Pilih 1 folder di dalam repositori GitHub."
                        }
                        localBasePath.isBlank() || resolvedLocalPath.isBlank() -> {
                            validationError = "Pilih jalur folder di perangkat terlebih dahulu."
                        }
                        else -> {
                            validationError = null
                            val cleanSubfolder = githubSubfolder.trim().trim('/')
                            val baseEntity = existingPair ?: SyncPairEntity(
                                name = autoPairName,
                                localPath = resolvedLocalPath,
                                driveFolderId = if (isDrive) driveFolderId else "",
                                driveFolderName = if (isDrive) driveFolderName else ""
                            )
                            val updated = baseEntity.copy(
                                name = autoPairName,
                                localPath = resolvedLocalPath,
                                driveFolderId = if (isDrive) driveFolderId else "",
                                driveFolderName = if (isDrive) driveFolderName else "",
                                deletePolicy = DeletePolicy.TWO_WAY.name,
                                autoSyncDrive = false,
                                githubEnabled = !isDrive,
                                githubAccountId = if (!isDrive) githubAccountId else null,
                                githubOwner = if (!isDrive) githubOwner.trim() else "",
                                githubRepo = if (!isDrive) githubRepo.trim() else "",
                                githubBranch = if (!isDrive) githubBranch.trim().ifBlank { "main" } else "main",
                                githubSubfolder = if (!isDrive) cleanSubfolder else "",
                                githubIntervalMinutes = 0
                            )
                            onSavePair(updated)
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .testTag("save_pair_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (existingPair == null) "Simpan Pasangan Folder" else "Perbarui Pasangan Folder",
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.heightIn(min = 24.dp))
        }
    }

    if (showLocalPicker) {
        LocalFolderPickerDialog(
            initialPath = localBasePath,
            onDismiss = { showLocalPicker = false },
            onSelectPath = { chosen ->
                localBasePath = chosen
                showLocalPicker = false
            }
        )
    }

    if (showDrivePicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDrivePicker = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DriveFolderPickerSheet(
                state = driveBrowser,
                onOpenFolder = onOpenDriveFolder,
                onCreateFolder = onCreateDriveFolder,
                onSelectCurrent = { id, folderName ->
                    driveFolderId = id
                    driveFolderName = folderName
                    validationError = null
                    showDrivePicker = false
                }
            )
        }
    }

    if (showGithubRepoPicker) {
        GithubRepoPickerDialog(
            state = githubBrowser,
            onDismiss = { showGithubRepoPicker = false },
            onSelectRepo = { repo ->
                githubOwner = repo.owner
                githubRepo = repo.name
                githubBranch = repo.defaultBranch
                githubSubfolder = ""
                validationError = null
                showGithubRepoPicker = false
                githubAccountId?.let { accId ->
                    onLoadGithubFolders(accId, repo.owner, repo.name, repo.defaultBranch)
                    showGithubFolderPicker = true
                }
            }
        )
    }

    if (showGithubFolderPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showGithubFolderPicker = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            GithubFolderPickerSheet(
                state = githubFolderBrowser,
                onOpenPath = onOpenGithubFolderPath,
                onSelectFolder = { chosenFolder ->
                    githubSubfolder = chosenFolder
                    validationError = null
                    showGithubFolderPicker = false
                }
            )
        }
    }
}

@Composable
private fun EditorSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun SourceTypeChoiceCard(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
