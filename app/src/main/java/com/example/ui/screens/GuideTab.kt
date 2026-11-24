package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.guide.MdBlock
import com.example.ui.guide.MdStyle
import com.example.ui.guide.parseInline
import com.example.ui.guide.parseMarkdownLite
import com.example.update.ApkState
import com.example.update.ApkUpdate

/** Panduan pengguna yang disalin ke assets saat build (lihat app/build.gradle.kts). */
const val GUIDE_ASSET = "panduan/01_Panduan_Lotura.md"

/** Null bila file panduan tidak bisa dibaca dari assets. */
fun loadGuideBlocks(context: Context): List<MdBlock>? = runCatching {
    context.assets.open(GUIDE_ASSET).bufferedReader().use { parseMarkdownLite(it.readText()) }
}.getOrNull()

/** Ringkasan tataletak yang dipakai Lotura; sumbernya ada di panduan/umum/04_Standar_UI_Tema_Google.md bagian 9. */
private val layoutReference = listOf(
    "Tiga tab tetap: Google Drive, GitHub, Panduan. Geser kiri atau kanan, atau ketuk nama tab.",
    "Judul sumber (akun Drive atau kartu repo) membuka log sumber itu di lembar bawah.",
    "Ikon aksi (↓, ↑, muat ulang, hapus, batal) berdiri sendiri dan tidak membuka log.",
    "Jarak layar 16 dp, jarak antarkartu 10 dp, sudut kartu 10 sampai 12 dp.",
    "Teks isi 14 sp, keterangan 11 sampai 12 sp. Status sync ditampilkan di kartu sumbernya."
)

/** Isi tab Panduan: unduh APK, panduan dari assets, referensi tataletak, dan tautan web. */
fun LazyListScope.guideSection(
    blocks: List<MdBlock>?,
    apkState: ApkState,
    onDownloadApk: () -> Unit,
    onRetryInstall: () -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    onDismissPermissionPrompt: () -> Unit,
    onDismissApkError: () -> Unit,
    onOpenWebGuide: () -> Unit
) {
    item { ApkCard(apkState, onDownloadApk, onDismissApkError) }

    if (apkState is ApkState.NeedsInstallPermission) {
        item {
            AlertDialog(
                onDismissRequest = onDismissPermissionPrompt,
                title = { Text("Izinkan pasang aplikasi") },
                text = {
                    Text(
                        "Lotura perlu izin \"Pasang aplikasi tidak dikenal\" untuk membuka APK yang baru diunduh. " +
                            "Aktifkan izin itu di pengaturan, lalu kembali dan ketuk \"Sudah diizinkan\".",
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = onOpenUnknownSourcesSettings) { Text("Buka pengaturan") }
                },
                dismissButton = {
                    TextButton(onClick = onRetryInstall) { Text("Sudah diizinkan") }
                }
            )
        }
    }

    item {
        Text("Panduan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp))
    }
    if (blocks == null) {
        item { Text("Panduan tidak bisa dimuat dari aplikasi.", fontSize = 13.sp) }
    } else {
        blocks.forEach { block -> item { MdBlockView(block) } }
    }

    item { Spacer(Modifier.height(8.dp)) }
    item {
        Text("Referensi tataletak", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
    layoutReference.forEach { line ->
        item { Text("• $line", fontSize = 13.sp) }
    }

    item {
        TextButton(onClick = onOpenWebGuide) { Text("Buka halaman web") }
    }
}

@Composable
private fun ApkCard(apkState: ApkState, onDownload: () -> Unit, onDismissError: () -> Unit) {
    val downloading = apkState is ApkState.Downloading
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Unduh aplikasi terbaru", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Mengunduh ${ApkUpdate.ASSET_NAME} dari rilis GitHub terbaru, lalu membuka installer. Butuh internet.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onDownload, enabled = !downloading) {
                Text(if (downloading) "Mengunduh..." else "Unduh dan pasang")
            }
            if (apkState is ApkState.Failed) {
                Spacer(Modifier.height(8.dp))
                Text(apkState.message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                TextButton(onClick = onDismissError) { Text("Tutup") }
            }
        }
    }
}

@Composable
private fun MdBlockView(block: MdBlock) {
    when (block) {
        is MdBlock.Heading -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }
            Text(
                text = inlineText(block.text),
                style = style,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        is MdBlock.Paragraph -> Text(
            text = inlineText(block.text),
            style = MaterialTheme.typography.bodyMedium
        )
        is MdBlock.Bullet -> Row(Modifier.fillMaxWidth().padding(start = (block.indent * 16).dp)) {
            Text("•", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
            Text(text = inlineText(block.text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        is MdBlock.Numbered -> Row(Modifier.fillMaxWidth()) {
            Text("${block.number}.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
            Text(text = inlineText(block.text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        MdBlock.Divider -> HorizontalDivider(Modifier.padding(vertical = 6.dp))
    }
}

private fun inlineText(s: String): AnnotatedString = buildAnnotatedString {
    for (run in parseInline(s)) {
        val style = when (run.style) {
            MdStyle.PLAIN -> SpanStyle()
            MdStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            MdStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            MdStyle.CODE -> SpanStyle(fontFamily = FontFamily.Monospace)
        }
        withStyle(style) { append(run.text) }
    }
}
