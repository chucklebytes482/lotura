package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.remote.MirrorAutoSync
import com.example.remote.PickedFolder
import com.example.ui.FolderUploadQueue

@Composable
internal fun FolderUploadDialog(
    queue: FolderUploadQueue,
    allowMirror: Boolean,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (PickedFolder) -> Unit,
    onMirrorChange: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit,
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
                if (allowMirror) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = queue.mirror,
                            onCheckedChange = onMirrorChange,
                            enabled = !queue.picking,
                            modifier = Modifier.testTag("upload_mirror_toggle")
                        )
                        Text("Mirror: samakan dengan Drive")
                    }
                    if (queue.mirror) {
                        Text(
                            "Folder Drive dengan nama sama akan dibuat ulang sesuai perangkat. " +
                                "Item Drive yang tidak ada di perangkat dipindah ke Sampah. " +
                                "File dengan isi berbeda diganti.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text("Auto sync", style = MaterialTheme.typography.labelLarge)
                        val options = listOf(0 to "Tidak otomatis") + MirrorAutoSync.OPTIONS.map { it to MirrorAutoSync.label(it) }
                        options.forEach { (hours, label) ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .selectable(
                                        selected = queue.autoHours == hours,
                                        enabled = !queue.picking,
                                        role = Role.RadioButton,
                                        onClick = { onIntervalChange(hours) }
                                    )
                                    .testTag("mirror_interval_$hours"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = queue.autoHours == hours, onClick = null, enabled = !queue.picking)
                                Text(label)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpload, enabled = queue.folders.isNotEmpty() && !queue.picking,
                modifier = Modifier.testTag("confirm_upload_folders")) {
                Text(if (queue.mirror) "Mirror" else "Unggah")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !queue.picking) { Text("Batal") } }
    )
}
