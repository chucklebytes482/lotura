package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.SyncPairEntity
import com.example.ui.components.CompactSearchBar
import com.example.ui.components.ExternalFileOpener
import com.example.ui.components.SelectionActionBar
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.JetBrainsMonoFamily
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileExplorerScreen(
    pair: SyncPairEntity?,
    onShowMessage: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (pair == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Folder pasangan tidak ditemukan.")
        }
        return
    }

    val rootDir = remember(pair.localPath) { File(pair.localPath).apply { mkdirs() } }
    var currentRelPath by remember(pair.id) { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPaths by remember { mutableStateOf(emptySet<String>()) }
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
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "breadcrumb_bar") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (currentRelPath.isEmpty()) pair.name else "${pair.name} / $currentRelPath",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showCreateFileDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("explorer_new_file_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Catatan .md")
                            }
                            OutlinedButton(
                                onClick = { showCreateFolderDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("explorer_new_folder_button")
                            ) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
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

    if (showCreateFileDialog) {
        var fileName by remember { mutableStateOf("catatan-baru.md") }
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("Buat Berkas Baru") },
            text = {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Nama file (mis. catatan.md, ide.txt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) { Text("Batal") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = fileName.trim()
                        if (clean.isNotEmpty()) {
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
                    }
                ) { Text("Buat & Edit") }
            }
        )
    }

    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Buat Subfolder Baru") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Nama folder") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Batal") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = folderName.trim()
                        if (clean.isNotEmpty()) {
                            val dir = File(currentDir, clean)
                            if (dir.mkdirs() || dir.exists()) {
                                refreshTick++
                                showCreateFolderDialog = false
                                onShowMessage("Folder \"$clean\" dibuat")
                            }
                        }
                    }
                ) { Text("Buat") }
            }
        )
    }

    if (showMoveDialog) {
        var targetSubfolder by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Pindahkan ${selectedPaths.size} Item") },
            text = {
                OutlinedTextField(
                    value = targetSubfolder,
                    onValueChange = { targetSubfolder = it },
                    label = { Text("Subfolder tujuan relatif (kosong = root)") },
                    placeholder = { Text("arsip/2026") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) { Text("Batal") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val destDir = if (targetSubfolder.trim().isEmpty()) {
                            rootDir
                        } else {
                            File(rootDir, targetSubfolder.trim().trim('/')).apply { mkdirs() }
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
                ) { Text("Pindahkan") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileEntryCard(
    file: File,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val ext = file.extension.lowercase()
    val icon = when {
        isSelected -> Icons.Default.CheckCircle
        file.isDirectory -> Icons.Default.Folder
        ext in setOf("md", "txt", "docx", "pdf") -> Icons.Default.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                if (isSelected) 1.5.dp else 1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = file.name,
                    tint = if (file.isDirectory || isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (file.isDirectory) {
                        "Folder • ${formatRelativeTime(file.lastModified())}"
                    } else {
                        "${formatFileSize(file.length())} • ${formatRelativeTime(file.lastModified())}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!file.isDirectory) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Buka di editor eksternal",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
