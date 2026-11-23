package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LocalFileOps
import com.example.ui.BrowseState
import com.example.ui.EditorState
import com.example.ui.SaveStatus
import kotlinx.coroutines.delay
import java.io.File

private const val AUTOSAVE_DELAY_MS = 800L

/**
 * Penjelajah file untuk repo GitHub lokal: buat, ganti nama, hapus, dan edit berkas teks.
 * Setiap perubahan disimpan ke disk, lalu auto-sync mendorongnya ke GitHub.
 */
@Composable
fun FileBrowserScreen(
    browse: BrowseState,
    onEntryClick: (LocalFileOps.Entry) -> Unit,
    onSave: (File, String) -> Unit,
    onCloseEditor: () -> Unit,
    onCreate: (String, Boolean) -> Unit,
    onRename: (LocalFileOps.Entry, String) -> Unit,
    onDelete: (LocalFileOps.Entry) -> Unit
) {
    val editor = browse.editor
    if (editor != null) {
        key(editor.file.absolutePath) {
            EditorPane(editor = editor, onSave = onSave, onClose = onCloseEditor)
        }
        return
    }

    var creatingFolder by remember { mutableStateOf<Boolean?>(null) }
    var renameTarget by remember { mutableStateOf<LocalFileOps.Entry?>(null) }
    var deleteTarget by remember { mutableStateOf<LocalFileOps.Entry?>(null) }

    val relative = browse.dir.absolutePath
        .removePrefix(browse.root.absolutePath)
        .ifEmpty { "/" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = relative,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { creatingFolder = false }) { Text("+ Berkas") }
            OutlinedButton(onClick = { creatingFolder = true }) { Text("+ Folder") }
        }

        if (browse.entries.isEmpty()) {
            Text(
                text = "Folder ini kosong.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(browse.entries, key = { it.file.absolutePath }) { entry ->
                EntryRow(
                    entry = entry,
                    onClick = { onEntryClick(entry) },
                    onRename = { renameTarget = entry },
                    onDelete = { deleteTarget = entry }
                )
            }
        }
    }

    creatingFolder?.let { isFolder ->
        NameDialog(
            title = if (isFolder) "Folder baru" else "Berkas baru",
            initial = "",
            confirmText = "Buat",
            onConfirm = { name ->
                onCreate(name, isFolder)
                creatingFolder = null
            },
            onDismiss = { creatingFolder = null }
        )
    }

    renameTarget?.let { target ->
        NameDialog(
            title = "Ganti nama",
            initial = target.name,
            confirmText = "Simpan",
            onConfirm = { name ->
                onRename(target, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Hapus ${target.name}?") },
            text = {
                Text(
                    if (target.isDir) {
                        "Folder dan seluruh isinya akan dihapus dari perangkat dan dari GitHub saat sinkron berikutnya."
                    } else {
                        "Berkas akan dihapus dari perangkat dan dari GitHub."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    deleteTarget = null
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun EntryRow(
    entry: LocalFileOps.Entry,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val hidden = entry.name.startsWith(".")
    val subtitle = when {
        entry.isDir -> "Folder"
        else -> formatSize(entry.size)
    } + if (hidden) " · tidak ikut sinkron" else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (entry.isDir) "${entry.name}/" else entry.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (entry.isDir) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            TextButton(onClick = { menuOpen = true }) { Text("Ubah") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Ganti nama") },
                    onClick = {
                        menuOpen = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Hapus") },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun EditorPane(
    editor: EditorState,
    onSave: (File, String) -> Unit,
    onClose: () -> Unit
) {
    var text by remember { mutableStateOf(editor.initialText) }
    var dirty by remember { mutableStateOf(false) }
    val latestText by rememberUpdatedState(text)
    val latestDirty by rememberUpdatedState(dirty)

    // Auto-save: 800 ms setelah ketikan terakhir.
    LaunchedEffect(text, dirty) {
        if (!dirty) return@LaunchedEffect
        delay(AUTOSAVE_DELAY_MS)
        onSave(editor.file, text)
        dirty = false
    }

    // Simpan sisa perubahan kalau editor ditutup sebelum jeda auto-save selesai.
    DisposableEffect(Unit) {
        onDispose {
            if (latestDirty) onSave(editor.file, latestText)
        }
    }

    val statusText = when {
        dirty -> "Belum disimpan"
        editor.status == SaveStatus.SAVING -> "Menyimpan..."
        editor.status == SaveStatus.ERROR -> "Gagal: ${editor.error ?: "tidak diketahui"}"
        editor.status == SaveStatus.SAVED -> "Tersimpan"
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onClose) { Text("Tutup") }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = editor.file.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                dirty = true
            },
            modifier = Modifier.fillMaxSize(),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Nama") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
