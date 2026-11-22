package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.BrandVisualColors
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileEntryCard(
    file: File,
    isSelected: Boolean,
    brand: BrandVisualColors,
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
        color = if (isSelected) brand.container.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                if (isSelected) 1.5.dp else 1.dp,
                if (isSelected) brand.accent else MaterialTheme.colorScheme.outline,
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
                    .background(if (file.isDirectory || isSelected) brand.container else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = file.name,
                    tint = if (file.isDirectory || isSelected) brand.accent
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

@Composable
internal fun CreateFileDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var fileName by remember { mutableStateOf("catatan-baru.md") }
    AlertDialog(
        onDismissRequest = onDismiss,
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
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = fileName.trim()
                    if (clean.isNotEmpty()) onCreate(clean)
                }
            ) { Text("Buat & Edit") }
        }
    )
}

@Composable
internal fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
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
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = folderName.trim()
                    if (clean.isNotEmpty()) onCreate(clean)
                }
            ) { Text("Buat") }
        }
    )
}

@Composable
internal fun MoveFilesDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit
) {
    var targetSubfolder by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pindahkan $selectedCount Item") },
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
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
        confirmButton = {
            Button(onClick = { onMove(targetSubfolder.trim().trim('/')) }) {
                Text("Pindahkan")
            }
        }
    )
}
