package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.remote.PickedFolder
import com.example.ui.FolderUploadQueue

@Composable
internal fun FolderUploadDialog(
    queue: FolderUploadQueue,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (PickedFolder) -> Unit,
    onUpload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unggah folder") },
        text = {
            Column {
                if (queue.folders.isEmpty()) Text("Belum ada folder yang dipilih.")
                else Text("${queue.folders.size} folder dipilih. Subfolder ikut diunggah.")
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 144.dp)) {
                    items(queue.folders, key = { it.tree.toString() }) { folder ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(folder.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { onRemove(folder) }, enabled = !queue.picking) {
                                Icon(Icons.Default.Close, contentDescription = "Keluarkan ${folder.name} dari pilihan")
                            }
                        }
                    }
                }
                queue.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onAdd, enabled = !queue.picking, modifier = Modifier.testTag("add_upload_folder")) {
                    Text("Tambah folder")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpload, enabled = queue.folders.isNotEmpty() && !queue.picking,
                modifier = Modifier.testTag("confirm_upload_folders")) {
                Text("Unggah")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !queue.picking) { Text("Batal") } }
    )
}
