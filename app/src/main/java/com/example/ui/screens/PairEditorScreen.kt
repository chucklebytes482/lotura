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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.DeletePolicy
import com.example.data.db.GithubAccountEntity
import com.example.data.db.SyncPairEntity
import com.example.ui.DriveBrowserUiState
import com.example.ui.GithubBrowserUiState
import com.example.ui.theme.JetBrainsMonoFamily
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PairEditorScreen(
    existingPair: SyncPairEntity?,
    githubAccounts: List<GithubAccountEntity>,
    driveBrowser: DriveBrowserUiState,
    githubBrowser: GithubBrowserUiState,
    onOpenDriveFolder: (parentId: String, folderName: String?) -> Unit,
    onCreateDriveFolder: (String) -> Unit,
    onLoadGithubRepos: (Long) -> Unit,
    onSavePair: (SyncPairEntity) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val defaultBaseDir = remember {
        runCatching {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Lotura").absolutePath
        }.getOrDefault("/storage/emulated/0/Documents/Lotura")
    }

    var name by remember(existingPair) { mutableStateOf(existingPair?.name ?: "") }
    var localPath by remember(existingPair) { mutableStateOf(existingPair?.localPath ?: defaultBaseDir) }
    var driveFolderId by remember(existingPair) { mutableStateOf(existingPair?.driveFolderId ?: "") }
    var driveFolderName by remember(existingPair) { mutableStateOf(existingPair?.driveFolderName ?: "") }
    var deletePolicy by remember(existingPair) { mutableStateOf(existingPair?.resolvedDeletePolicy ?: DeletePolicy.TWO_WAY) }
    var autoSyncDrive by remember(existingPair) { mutableStateOf(existingPair?.autoSyncDrive ?: true) }
    var ignorePatterns by remember(existingPair) {
        mutableStateOf(existingPair?.ignorePatterns ?: SyncPairEntity.DEFAULT_IGNORE_PATTERNS)
    }

    var githubEnabled by remember(existingPair) { mutableStateOf(existingPair?.githubEnabled ?: false) }
    var githubAccountId by remember(existingPair) {
        mutableStateOf(existingPair?.githubAccountId ?: githubAccounts.firstOrNull()?.id)
    }
    var githubOwner by remember(existingPair) { mutableStateOf(existingPair?.githubOwner ?: "") }
    var githubRepo by remember(existingPair) { mutableStateOf(existingPair?.githubRepo ?: "") }
    var githubBranch by remember(existingPair) { mutableStateOf(existingPair?.githubBranch ?: "main") }
    var githubSubfolder by remember(existingPair) { mutableStateOf(existingPair?.githubSubfolder ?: "") }
    var githubInterval by remember(existingPair) { mutableIntStateOf(existingPair?.githubIntervalMinutes ?: 15) }

    var validationError by remember { mutableStateOf<String?>(null) }
    var showLocalPicker by remember { mutableStateOf(false) }
    var showDrivePicker by remember { mutableStateOf(false) }
    var showGithubRepoPicker by remember { mutableStateOf(false) }

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
            // Section 1: Folder Perangkat
            EditorSectionCard(title = "1. Folder Perangkat") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Pasangan") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pair_name_input")
                )

                OutlinedTextField(
                    value = localPath,
                    onValueChange = { localPath = it },
                    label = { Text("Path Folder") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pair_local_path_input")
                )

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
                    Text("Pilih Folder")
                }
            }

            // Section 2: Google Drive
            EditorSectionCard(title = "2. Google Drive") {
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
                            contentDescription = "Folder Drive",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = driveFolderName.ifBlank { "Belum dipilih" },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (driveFolderId.isNotBlank()) {
                                Text(
                                    text = driveFolderId,
                                    style = MaterialTheme.typography.labelSmall,
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
                    Text("Pilih Folder Drive")
                }

                OutlinedTextField(
                    value = driveFolderId,
                    onValueChange = {
                        driveFolderId = it.trim()
                        if (driveFolderName.isBlank() && it.isNotBlank()) {
                            driveFolderName = "Folder Drive ($it)"
                        }
                    },
                    label = { Text("Folder ID Drive") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pair_drive_id_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pantau Real-Time",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoSyncDrive,
                        onCheckedChange = { autoSyncDrive = it },
                        modifier = Modifier.testTag("pair_auto_drive_switch")
                    )
                }
            }

            // Section 3: Kebijakan Penghapusan & Ignore Rules
            EditorSectionCard(title = "3. Kebijakan Hapus") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PolicyChoiceCard(
                        title = "Dua Arah",
                        selected = deletePolicy == DeletePolicy.TWO_WAY,
                        onClick = { deletePolicy = DeletePolicy.TWO_WAY },
                        testTag = "policy_two_way_card",
                        modifier = Modifier.weight(1f)
                    )
                    PolicyChoiceCard(
                        title = "Mode Aman",
                        selected = deletePolicy == DeletePolicy.SAFE_NO_DELETE,
                        onClick = { deletePolicy = DeletePolicy.SAFE_NO_DELETE },
                        testTag = "policy_safe_card",
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = ignorePatterns,
                    onValueChange = { ignorePatterns = it },
                    label = { Text("Pengecualian") },
                    minLines = 3,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pair_ignore_input")
                )
            }

            // Section 4: GitHub Opsional
            EditorSectionCard(title = "4. GitHub") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tautkan Repositori",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = githubEnabled,
                        onCheckedChange = { githubEnabled = it },
                        modifier = Modifier.testTag("pair_github_switch")
                    )
                }

                if (githubEnabled) {
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
                        Text(
                            text = "Pilih Akun GitHub",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            githubAccounts.forEach { acc ->
                                FilterChip(
                                    selected = githubAccountId == acc.id,
                                    onClick = {
                                        githubAccountId = acc.id
                                        if (githubOwner.isBlank()) githubOwner = acc.username
                                    },
                                    label = { Text("@${acc.username} (${acc.label})") },
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
                                Text("Muat & Pilih Repositori dari GitHub")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = githubOwner,
                            onValueChange = { githubOwner = it.trim() },
                            label = { Text("Owner") },
                            placeholder = { Text("username") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pair_github_owner_input")
                        )
                        OutlinedTextField(
                            value = githubRepo,
                            onValueChange = { githubRepo = it.trim() },
                            label = { Text("Nama Repo") },
                            placeholder = { Text("catatan-repo") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pair_github_repo_input")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = githubBranch,
                            onValueChange = { githubBranch = it.trim() },
                            label = { Text("Branch") },
                            placeholder = { Text("main") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pair_github_branch_input")
                        )
                        OutlinedTextField(
                            value = githubSubfolder,
                            onValueChange = { githubSubfolder = it.trim() },
                            label = { Text("Subfolder (Opsional)") },
                            placeholder = { Text("docs/catatan") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pair_github_subfolder_input")
                        )
                    }

                    Text(
                        text = "Interval Commit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 60, 180).forEach { mins ->
                            FilterChip(
                                selected = githubInterval == mins,
                                onClick = { githubInterval = mins },
                                label = { Text(if (mins < 60) "$mins menit" else "${mins / 60} jam") },
                                modifier = Modifier.testTag("gh_interval_$mins")
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
                    val trimmedName = name.trim()
                    val trimmedPath = localPath.trim()
                    when {
                        trimmedName.isEmpty() -> validationError = "Nama pasangan folder wajib diisi."
                        trimmedPath.isEmpty() -> validationError = "Path folder lokal wajib diisi."
                        githubEnabled && githubAccountId == null -> validationError = "Pilih akun GitHub terlebih dahulu."
                        githubEnabled && (githubOwner.isBlank() || githubRepo.isBlank()) -> {
                            validationError = "Owner dan nama repositori GitHub wajib diisi."
                        }
                        else -> {
                            validationError = null
                            val updated = (existingPair ?: SyncPairEntity(
                                name = trimmedName,
                                localPath = trimmedPath,
                                driveFolderId = driveFolderId,
                                driveFolderName = driveFolderName
                            )).copy(
                                name = trimmedName,
                                localPath = trimmedPath,
                                driveFolderId = driveFolderId,
                                driveFolderName = driveFolderName.ifBlank {
                                    if (driveFolderId.isNotBlank()) "Folder Drive" else ""
                                },
                                deletePolicy = deletePolicy.name,
                                autoSyncDrive = autoSyncDrive,
                                ignorePatterns = ignorePatterns,
                                githubEnabled = githubEnabled,
                                githubAccountId = if (githubEnabled) githubAccountId else null,
                                githubOwner = if (githubEnabled) githubOwner.trim() else "",
                                githubRepo = if (githubEnabled) githubRepo.trim() else "",
                                githubBranch = if (githubEnabled) githubBranch.trim().ifBlank { "main" } else "main",
                                githubSubfolder = if (githubEnabled) githubSubfolder.trim().trim('/') else "",
                                githubIntervalMinutes = githubInterval
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
            initialPath = localPath,
            onDismiss = { showLocalPicker = false },
            onSelectPath = { chosen ->
                localPath = chosen
                if (name.isBlank()) {
                    name = File(chosen).name.ifBlank { "Folder Lokal" }
                }
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
                    showDrivePicker = false
                }
            )
        }
    }

    if (showGithubRepoPicker) {
        AlertDialog(
            onDismissRequest = { showGithubRepoPicker = false },
            title = { Text("Pilih Repositori GitHub") },
            text = {
                when {
                    githubBrowser.isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    !githubBrowser.error.isNullOrBlank() -> {
                        Text(githubBrowser.error, color = MaterialTheme.colorScheme.error)
                    }
                    githubBrowser.repos.isEmpty() -> {
                        Text("Tidak ada repositori yang dapat diakses oleh token PAT ini.")
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(githubBrowser.repos, key = { it.fullName }) { repo ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            githubOwner = repo.owner
                                            githubRepo = repo.name
                                            githubBranch = repo.defaultBranch
                                            showGithubRepoPicker = false
                                        }
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
                TextButton(onClick = { showGithubRepoPicker = false }) { Text("Tutup") }
            }
        )
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
private fun PolicyChoiceCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LocalFolderPickerDialog(
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
                Text("Pilih Folder Lokal", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Button(onClick = { onSelectPath(currentDir.absolutePath) }) {
                Text("Pilih Folder Ini")
            }
        }
    )
}

@Composable
private fun DriveFolderPickerSheet(
    state: DriveBrowserUiState,
    onOpenFolder: (parentId: String, folderName: String?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onSelectCurrent: (id: String, name: String) -> Unit
) {
    var newFolderName by remember { mutableStateOf("") }
    val currentCrumb = state.breadcrumbs.lastOrNull() ?: ("root" to "Drive Saya")

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
                                TextButton(onClick = { onSelectCurrent(folder.id, folder.name) }) {
                                    Text("Pilih")
                                }
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = { onSelectCurrent(currentCrumb.first, currentCrumb.second) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
        ) {
            Text("Gunakan \"${currentCrumb.second}\"")
        }
        Spacer(Modifier.heightIn(min = 12.dp))
    }
}
