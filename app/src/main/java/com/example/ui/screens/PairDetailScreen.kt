package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.FileStateEntity
import com.example.data.db.SyncPairEntity
import com.example.ui.components.BrandIcons
import com.example.ui.components.CompactSearchBar
import com.example.ui.components.ExternalFileOpener
import com.example.ui.components.SelectionActionBar
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.providerBrandColors
import java.io.File

@Composable
fun PairDetailScreen(
    pair: SyncPairEntity?,
    trackedFiles: List<FileStateEntity>,
    onDeletePair: (SyncPairEntity) -> Unit,
    onShowMessage: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (pair == null) {
        BackHandler { onNavigateBack() }
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Pasangan folder tidak ditemukan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val isGithub = pair.isGithubPair
    val brand = providerBrandColors(isGithub = isGithub)
    val latestSyncAt = maxOf(pair.lastDriveSyncAt, pair.lastGithubSyncAt)

    // Inline File Explorer state
    val rootDir = remember(pair.localPath) { File(pair.localPath).apply { mkdirs() } }
    var currentRelPath by remember(pair.id) { mutableStateOf("") }
    var searchQuery by remember(pair.id) { mutableStateOf("") }
    var selectedPaths by remember(pair.id) { mutableStateOf(emptySet<String>()) }
    var refreshTick by remember { mutableIntStateOf(0) }

    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }

    val selectionMode = selectedPaths.isNotEmpty()

    BackHandler {
        when {
            selectionMode -> selectedPaths = emptySet()
            currentRelPath.isNotEmpty() -> {
                currentRelPath = currentRelPath.substringBeforeLast('/', "")
            }
            else -> onNavigateBack()
        }
    }

    val currentDir = remember(rootDir, currentRelPath) {
        if (currentRelPath.isEmpty()) rootDir else File(rootDir, currentRelPath)
    }

    val entries = remember(currentDir, searchQuery, refreshTick) {
        val all = currentDir.listFiles()
            ?.filter { !it.name.startsWith(".syncbebas_") }
            ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .orEmpty()
        val q = searchQuery.trim()
        if (q.isEmpty()) all else all.filter { it.name.contains(q, ignoreCase = true) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .testTag("detail_content_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Bagian Atas: Kartu Ringkasan Pasangan beraksen kontras + Tombol Hapus Pasangan
            item(key = "summary_card") {
                val cardShape = RoundedCornerShape(20.dp)
                Surface(
                    shape = cardShape,
                    color = brand.cardSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, brand.border, cardShape)
                        .clip(cardShape)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .fillMaxHeight()
                                .background(brand.accent)
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(brand.container)
                                        .border(1.dp, brand.accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isGithub) BrandIcons.GitHub else BrandIcons.GoogleDrive,
                                        contentDescription = if (isGithub) "GitHub" else "Google Drive",
                                        tint = brand.accent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = pair.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = brand.container,
                                        modifier = Modifier.border(
                                            1.dp,
                                            brand.accent.copy(alpha = 0.45f),
                                            RoundedCornerShape(999.dp)
                                        )
                                    ) {
                                        Text(
                                            text = if (isGithub) "GitHub" else "Google Drive",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = brand.onContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (isGithub) {
                                DetailMetaRow(
                                    label = "Sumber GitHub",
                                    value = "${pair.githubOwner}/${pair.githubRepo}@${pair.githubBranch} / ${pair.githubSubfolder}",
                                    monospace = true
                                )
                            } else {
                                DetailMetaRow("Sumber Google Drive", pair.driveFolderName.ifBlank { "-" })
                            }

                            DetailMetaRow("Folder Perangkat", pair.localPath, monospace = true)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    DetailMetaRow("Berkas", "${trackedFiles.size} file")
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    DetailMetaRow("Sinkron Terakhir", formatRelativeTime(latestSyncAt))
                                }
                            }

                            if (!pair.lastError.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = pair.lastError,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { showDeleteConfirm = true },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("detail_delete_pair_button")
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Hapus Pasangan",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Hapus Pasangan", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Bagian Bawah: Penjelajah File Langsung
            item(key = "explorer_header_bar") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (currentRelPath.isEmpty()) pair.name else "${pair.name} / $currentRelPath",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                color = brand.accent,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("explorer_breadcrumb")
                            )
                            if (currentRelPath.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        currentRelPath = currentRelPath.substringBeforeLast('/', "")
                                    },
                                    modifier = Modifier.testTag("explorer_up_button")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Naik folder",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Naik")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showCreateFileDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("explorer_new_file_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.NoteAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Catatan .md")
                            }
                            OutlinedButton(
                                onClick = { showCreateFolderDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("explorer_new_folder_button")
                            ) {
                                Icon(
                                    Icons.Default.CreateNewFolder,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Folder Baru")
                            }
                        }
                    }
                }
            }

            if (selectionMode) {
                item(key = "explorer_selection_bar") {
                    SelectionActionBar(
                        selectedCount = selectedPaths.size,
                        onSelectAll = { selectedPaths = entries.map { it.absolutePath }.toSet() },
                        onMoveSelected = { showMoveDialog = true },
                        onDeleteSelected = {
                            var deletedCount = 0
                            selectedPaths.forEach { p ->
                                val f = File(p)
                                if (f.exists() && f.deleteRecursively()) deletedCount++
                            }
                            selectedPaths = emptySet()
                            refreshTick++
                            onShowMessage("$deletedCount item dihapus dari perangkat")
                        },
                        onClose = { selectedPaths = emptySet() }
                    )
                }
            } else {
                item(key = "explorer_search") {
                    CompactSearchBar(
                        query = searchQuery,
                        placeholder = "Cari file...",
                        onQueryChange = { searchQuery = it },
                        testTag = "explorer_search_input"
                    )
                }
            }

            if (entries.isEmpty()) {
                item(key = "explorer_empty") {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                            .testTag("explorer_empty_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Folder kosong", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            } else {
                items(entries, key = { it.absolutePath }) { file ->
                    val isSelected = file.absolutePath in selectedPaths
                    FileEntryCard(
                        file = file,
                        isSelected = isSelected,
                        brand = brand,
                        onClick = {
                            if (selectionMode) {
                                selectedPaths = if (isSelected) selectedPaths - file.absolutePath else selectedPaths + file.absolutePath
                            } else if (file.isDirectory) {
                                currentRelPath = if (currentRelPath.isEmpty()) file.name else "$currentRelPath/${file.name}"
                            } else {
                                ExternalFileOpener.openInExternalEditor(context, file)
                                    .onFailure { err ->
                                        onShowMessage("Tidak ada aplikasi editor untuk ${file.name}: ${err.localizedMessage}")
                                    }
                            }
                        },
                        onLongClick = {
                            selectedPaths = if (isSelected) selectedPaths - file.absolutePath else selectedPaths + file.absolutePath
                        }
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Hapus Pasangan?") },
            text = { Text("Hapus \"${pair.name}\" dari daftar?") },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Batal") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeletePair(pair)
                    }
                ) { Text("Hapus") }
            }
        )
    }

    if (showCreateFileDialog) {
        CreateFileDialog(
            onDismiss = { showCreateFileDialog = false },
            onCreate = { clean ->
                val target = File(currentDir, clean)
                runCatching {
                    target.parentFile?.mkdirs()
                    if (!target.exists()) target.writeText("# ${clean.substringBeforeLast('.')}\n\n")
                    refreshTick++
                    showCreateFileDialog = false
                    ExternalFileOpener.openInExternalEditor(context, target)
                }.onFailure {
                    onShowMessage("Gagal membuat file: ${it.localizedMessage}")
                }
            }
        )
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onCreate = { clean ->
                val dir = File(currentDir, clean)
                if (dir.mkdirs() || dir.exists()) {
                    refreshTick++
                    showCreateFolderDialog = false
                    onShowMessage("Folder \"$clean\" dibuat")
                }
            }
        )
    }

    if (showMoveDialog) {
        MoveFilesDialog(
            selectedCount = selectedPaths.size,
            onDismiss = { showMoveDialog = false },
            onMove = { targetSubfolder ->
                val destDir = if (targetSubfolder.isEmpty()) {
                    rootDir
                } else {
                    File(rootDir, targetSubfolder).apply { mkdirs() }
                }
                var moved = 0
                selectedPaths.forEach { srcPath ->
                    val src = File(srcPath)
                    val dst = File(destDir, src.name)
                    if (src.exists() && src.canonicalPath != dst.canonicalPath && src.renameTo(dst)) {
                        moved++
                    }
                }
                selectedPaths = emptySet()
                refreshTick++
                showMoveDialog = false
                onShowMessage("$moved item dipindahkan")
            }
        )
    }
}

@Composable
private fun DetailMetaRow(
    label: String,
    value: String,
    monospace: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (monospace) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
