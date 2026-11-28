package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.MarkdownEditor
import com.example.ui.RemoteViewModel

/**
 * Editor .md di GitHub. Perubahan dikumpulkan di draft dan baru dikirim sebagai satu commit saat "Selesai".
 * "Batal" atau kembali dengan perubahan yang belum dikirim meminta konfirmasi lalu membuang draft.
 */
@Composable
internal fun MarkdownEditorScreen(modifier: Modifier, editor: MarkdownEditor, viewModel: RemoteViewModel) {
    // null: tanpa dialog. true: buang lalu keluar dari editor. false: buang lalu tetap di mode baca.
    var confirmLeave by remember(editor.item.ref) { mutableStateOf<Boolean?>(null) }

    fun leaveEditor() {
        if (editor.saving) return
        if (editor.editing && editor.dirty) confirmLeave = true else viewModel.closeMarkdown()
    }

    BackHandler(enabled = !editor.saving) { leaveEditor() }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { leaveEditor() }, enabled = !editor.saving) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
            }
            Text(editor.item.name, style = MaterialTheme.typography.titleLarge, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (editor.loaded && !editor.editing && !editor.saving) {
                IconButton(onClick = viewModel::startMarkdownEdit, modifier = Modifier.testTag("markdown_edit")) {
                    Icon(Icons.Default.Edit, "Ubah ${editor.item.name}")
                }
            }
        }
        if (editor.saving) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("markdown_progress"))
        editor.error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        when {
            !editor.loaded && editor.error == null -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Memuat…", modifier = Modifier.padding(top = 12.dp))
            }
            !editor.loaded -> Unit
            editor.editing -> OutlinedTextField(
                value = editor.draft,
                onValueChange = viewModel::updateMarkdownDraft,
                enabled = !editor.saving,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).testTag("markdown_draft")
            )
            else -> SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
                Text(editor.original.orEmpty(), style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp))
            }
        }

        if (editor.editing) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        if (editor.dirty) confirmLeave = false else viewModel.discardMarkdownEdit()
                    },
                    enabled = !editor.saving,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("markdown_cancel")
                ) { Text("Batal") }
                Button(
                    onClick = viewModel::finishMarkdownEdit,
                    enabled = !editor.saving,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("markdown_done")
                ) { Text("Selesai") }
            }
        }
    }

    confirmLeave?.let { leave ->
        AlertDialog(onDismissRequest = { confirmLeave = null }, title = { Text("Buang perubahan?") },
            text = { Text("Perubahan yang belum dikirim sebagai commit akan hilang.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = null
                    viewModel.discardMarkdownEdit()
                    if (leave) viewModel.closeMarkdown()
                }) { Text("Buang", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = null }) { Text("Lanjut edit") } })
    }
}
