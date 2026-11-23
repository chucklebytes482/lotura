package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.example.ui.components.BrandIcons
import com.example.ui.theme.BrandVisualColors
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.providerBrandColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PairEditorScreen(
    existingPairs: List<SyncPairEntity>,
    githubAccounts: List<GithubAccountEntity>,
    driveBrowser: DriveBrowserUiState,
    githubBrowser: GithubBrowserUiState,
    githubFolderBrowser: GithubFolderBrowserUiState,
    driveEmail: String = "",
    onLoadDriveRootFolders: () -> Unit,
    onCreateDriveFolder: (String) -> Unit,
    onLoadGithubRepos: (Long) -> Unit,
    onLoadGithubFolders: (accountId: Long, owner: String, repo: String, branch: String) -> Unit,
    onSavePair: (SyncPairEntity) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    var sourceType by remember { mutableStateOf(SyncSourceType.DRIVE) }

    // Drive source state
    var driveFolderId by remember { mutableStateOf("") }
    var driveFolderName by remember { mutableStateOf("") }

    // GitHub source state
    var githubAccountId by remember(githubAccounts) {
        mutableStateOf(githubAccounts.firstOrNull()?.id)
    }
    var githubOwner by remember { mutableStateOf("") }
    var githubRepo by remember { mutableStateOf("") }
    var githubBranch by remember { mutableStateOf("main") }
    var githubSubfolder by remember { mutableStateOf("") }

    val driveBrand = providerBrandColors(isGithub = false)
    val githubBrand = providerBrandColors(isGithub = true)
    val activeBrand = if (sourceType == SyncSourceType.GITHUB) githubBrand else driveBrand

    val pairedDriveFolderIds = remember(existingPairs) {
        existingPairs
            .filter { !it.isGithubPair && it.driveFolderId.isNotBlank() }
            .map { it.driveFolderId }
            .toSet()
    }

    val pairedGithubPathsForRepo = remember(existingPairs, githubOwner, githubRepo, githubBranch) {
        val normOwner = githubOwner.trim()
        val normRepo = githubRepo.trim()
        val normBranch = githubBranch.trim().ifBlank { "main" }
        existingPairs
            .filter {
                it.isGithubPair &&
                    it.githubOwner.equals(normOwner, ignoreCase = true) &&
                    it.githubRepo.equals(normRepo, ignoreCase = true) &&
                    it.githubBranch.trim().ifBlank { "main" }.equals(normBranch, ignoreCase = true)
            }
            .map { PairPathHelper.rootGithubFolderName(it.githubSubfolder) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    val autoPairName = remember(sourceType, driveFolderName, githubRepo, githubSubfolder) {
        if (sourceType == SyncSourceType.DRIVE) {
            PairPathHelper.pairNameForDrive(driveFolderName)
        } else {
            PairPathHelper.pairNameForGithub(githubRepo, githubSubfolder)
        }
    }

    val githubUsername = githubAccounts.firstOrNull { it.id == githubAccountId }?.username.orEmpty()

    val resolvedLocalPath = remember(sourceType, driveFolderName, driveEmail, githubRepo, githubSubfolder, githubUsername) {
        if (sourceType == SyncSourceType.DRIVE) {
            PairPathHelper.resolveDefaultLocalPathForDrive(driveEmail, driveFolderName)
        } else {
            PairPathHelper.resolveDefaultLocalPathForGithub(githubUsername, githubRepo, githubSubfolder)
        }
    }

    var validationError by remember { mutableStateOf<String?>(null) }
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
            EditorSectionCard(title = "Pilih Sumber", accent = activeBrand) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SourceTypeChoiceCard(
                        title = "Google Drive",
                        icon = BrandIcons.GoogleDrive,
                        brand = driveBrand,
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
                        icon = BrandIcons.GitHub,
                        brand = githubBrand,
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
                        color = driveBrand.cardSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, driveBrand.border, RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(driveBrand.container),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = BrandIcons.GoogleDrive,
                                    contentDescription = "Folder Google Drive",
                                    tint = driveBrand.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
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
                                        color = driveBrand.accent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onLoadDriveRootFolders()
                            showDrivePicker = true
                        },
                        border = BorderStroke(1.dp, driveBrand.accent),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = driveBrand.accent),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("pair_pick_drive_folder_button")
                    ) {
                        Icon(BrandIcons.GoogleDrive, contentDescription = null, modifier = Modifier.size(18.dp))
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
                                border = BorderStroke(1.dp, githubBrand.accent),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = githubBrand.accent),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("pair_pick_github_repo_button")
                            ) {
                                Icon(BrandIcons.GitHub, contentDescription = null, modifier = Modifier.size(18.dp))
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
                                color = githubBrand.cardSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, githubBrand.border, RoundedCornerShape(14.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(githubBrand.container),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = "Folder GitHub",
                                            tint = githubBrand.accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
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
                                            color = githubBrand.accent,
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
                                border = BorderStroke(1.dp, githubBrand.accent),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = githubBrand.accent),
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
                    val cleanSubfolder = PairPathHelper.normalizeGithubFolderPath(githubSubfolder)
                    when {
                        isDrive && (driveFolderId.isBlank() || driveFolderName.isBlank()) -> {
                            validationError = "Pilih folder Google Drive terlebih dahulu."
                        }
                        isDrive && driveFolderId in pairedDriveFolderIds -> {
                            validationError = "Folder Google Drive ini sudah dipasangkan."
                        }
                        !isDrive && githubAccountId == null -> {
                            validationError = "Pilih akun GitHub terlebih dahulu."
                        }
                        !isDrive && (githubOwner.isBlank() || githubRepo.isBlank()) -> {
                            validationError = "Pilih repositori GitHub terlebih dahulu."
                        }
                        !isDrive && cleanSubfolder.isBlank() -> {
                            validationError = "Pilih 1 folder di dalam repositori GitHub."
                        }
                        !isDrive && PairPathHelper.rootGithubFolderName(cleanSubfolder) in pairedGithubPathsForRepo -> {
                            validationError = "Folder GitHub ini sudah dipasangkan."
                        }
                        resolvedLocalPath.isBlank() -> {
                            validationError = "Jalur folder perangkat tidak valid."
                        }
                        else -> {
                            validationError = null
                            val created = SyncPairEntity(
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
                            onSavePair(created)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeBrand.accent,
                    contentColor = activeBrand.onAccent
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .testTag("save_pair_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Simpan Pasangan Folder",
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.heightIn(min = 24.dp))
        }
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
                pairedDriveFolderIds = pairedDriveFolderIds,
                onCreateFolder = onCreateDriveFolder,
                onSelectFolder = { id, folderName ->
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
                pairedGithubRootFolders = pairedGithubPathsForRepo,
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
    accent: BrandVisualColors,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accent.border, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = accent.accent,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun SourceTypeChoiceCard(
    title: String,
    icon: ImageVector,
    brand: BrandVisualColors,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) brand.container else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) brand.accent else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) brand.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) brand.onContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
