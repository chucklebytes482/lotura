# Template Source Code UI & Sinkronisasi Otomatis Perangkat (Umum)

Kumpulan contoh kode Kotlin siap salin. Ganti `com.contoh.aplikasi` dengan `com.aistudio.<kata_euskara>`.

---

## 1. Tema Otomatis Sistem (`ui/theme/Theme.kt`)

```kotlin
package com.contoh.aplikasi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AppDarkScheme = darkColorScheme(
    primary = Color(0xFF67D4E4),
    onPrimary = Color(0xFF032D35),
    primaryContainer = Color(0xFF0E3A44),
    onPrimaryContainer = Color(0xFFB3F1FA),
    secondary = Color(0xFF4ADE80),
    background = Color(0xFF0B1319),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF121C24),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1B2732),
    onSurfaceVariant = Color(0xFF9FB0BE),
    outline = Color(0xFF2F4150),
    error = Color(0xFFFF8A80)
)

private val AppLightScheme = lightColorScheme(
    primary = Color(0xFF006978),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCF2F8),
    onPrimaryContainer = Color(0xFF002025),
    secondary = Color(0xFF15803D),
    background = Color(0xFFF4F7F9),
    onBackground = Color(0xFF0F1B24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F1B24),
    surfaceVariant = Color(0xFFE7EFF4),
    onSurfaceVariant = Color(0xFF4B5E6B),
    outline = Color(0xFFC8D6E0),
    error = Color(0xFFC62828)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) AppDarkScheme else AppLightScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        shapes = AppShapes,
        content = content
    )
}
```

---

## 2. Prioritas Beranda: #1 Cadangan Perangkat & #2 Web Panduan (`ui/MainScaffold.kt`)

```kotlin
package com.contoh.aplikasi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardAppScaffold(
    title: String,
    content: @Composable () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            content()
        }
    }
}

@Composable
fun AdaptiveMenuContainer(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = modifier
            .widthIn(max = 640.dp)
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
fun StandardMenuCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
    trailingIcon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(trailingIcon, contentDescription = "Buka $title", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

fun LazyListScope.standardHomePriorityCards(
    backupStatusText: String,
    backupIcon: ImageVector,
    guideIcon: ImageVector,
    openExternalIcon: ImageVector,
    onOpenBackupScreen: () -> Unit,
    onOpenWebGuide: () -> Unit
) {
    item(key = "priority_1_device_backup") {
        StandardMenuCard(
            icon = backupIcon,
            title = "Cadangan Perangkat",
            subtitle = backupStatusText,
            onClick = onOpenBackupScreen,
            testTag = "home_device_backup_card"
        )
    }
    item(key = "priority_2_web_guide") {
        StandardMenuCard(
            icon = guideIcon,
            title = "Web Panduan",
            subtitle = "Panduan & Arti Ikon",
            trailingIcon = openExternalIcon,
            onClick = onOpenWebGuide,
            testTag = "home_web_guide_card"
        )
    }
}

/**
 * Pola kartu minimalis berbasis ikon (icon-first):
 * Jika ikon sudah jelas mewakili sumber/metrik/status, tampilkan ikon berukuran lega (20.dp–28.dp)
 * dengan jarak renggang (18.dp) tanpa label teks berulang. Penjelasan lengkap tiap ikon diletakkan
 * di Web Panduan (<slug>-site), sementara TalkBack tetap terdukung lewat contentDescription.
 */
@Composable
fun MinimalMetricIconItem(
    icon: ImageVector,
    count: Int,
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = tint
        )
    }
}
```

---

## 3. Sinkronisasi Otomatis ke Perangkat (`data/DeviceAutoSyncManager.kt`)

Satu berkas JSON tunggal konsisten di `/storage/emulated/0/Documents/<NamaApp>/<slug>_data.json`: langsung menimpa berkas perangkat setiap ada perubahan data/folder di aplikasi (agar hapus/ubah folder tidak kembali lagi), serta memulihkan otomatis saat aplikasi dibuka dengan pengaman anti-timpa file kosong dan perlindungan jumlah entri aktif terbanyak.

```kotlin
package com.contoh.aplikasi.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SyncRecord(
    val id: String,
    val title: String,
    val value: String,
    val folder: String,
    val updatedAtMillis: Long,
    val deleted: Boolean = false
)

class DeviceAutoSyncManager(
    private val context: Context,
    private val appFolderName: String = "NamaApp",
    private val fileName: String = "app_data.json"
) {
    val displayPath: String = "/storage/emulated/0/Documents/$appFolderName/$fileName"

    fun mergeLastWriteWins(
        localItems: List<SyncRecord>,
        deviceItems: List<SyncRecord>
    ): List<SyncRecord> {
        val merged = linkedMapOf<String, SyncRecord>()
        (localItems + deviceItems).forEach { item ->
            val current = merged[item.id]
            if (current == null || item.updatedAtMillis >= current.updatedAtMillis) {
                merged[item.id] = item
            }
        }
        return merged.values.toList()
    }

    suspend fun readSingleBackupFile(): String? = withContext(Dispatchers.IO) {
        val directFile = directTargetFile()
        if (directFile.exists() && directFile.canRead()) {
            return@withContext runCatching { directFile.readText(Charsets.UTF_8) }.getOrNull()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = queryExistingMediaStoreUri() ?: return@withContext null
            return@withContext runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
        }
        null
    }

    suspend fun writeReplacingSingleFile(jsonContent: String): Boolean = withContext(Dispatchers.IO) {
        val directFile = directTargetFile()
        val wroteDirect = runCatching {
            directFile.parentFile?.mkdirs()
            directFile.writeText(jsonContent, Charsets.UTF_8)
            true
        }.getOrDefault(false)
        if (wroteDirect) return@withContext true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val existingUri = queryExistingMediaStoreUri()
            val targetUri = existingUri ?: resolver.insert(
                MediaStore.Files.getContentUri("external"),
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$appFolderName")
                }
            ) ?: return@withContext false

            return@withContext runCatching {
                // Mode "wt" memastikan berkas tunggal ditimpa penuh tanpa membuat duplikat (1), (2)
                resolver.openOutputStream(targetUri, "wt")?.use { out ->
                    out.write(jsonContent.toByteArray(Charsets.UTF_8))
                } != null
            }.getOrDefault(false)
        }
        false
    }

    private fun directTargetFile(): File {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(File(docsDir, appFolderName), fileName)
    }

    private fun queryExistingMediaStoreUri() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relPath = "${Environment.DIRECTORY_DOCUMENTS}/$appFolderName/"
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(fileName, relPath),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    android.content.ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
                } else null
            }
        } else null
}
```

Panggil `writeReplacingSingleFile` langsung setiap kali ada perubahan data/folder di aplikasi (dengan pagar pengaman bila belum pernah sinkron dan berkas perangkat punya entri lebih banyak), serta jalankan pemeriksaan pemulihan otomatis saat aplikasi dibuka (tolak menimpa lokal bila berkas perangkat kosong atau punya entri aktif lebih sedikit; gabungkan aman agar entri terbanyak tetap selamat).

---

## 4. Bilah Pencarian, Mode Pilih & `BackHandler` (`ui/components/SelectionComponents.kt`)

```kotlin
package com.contoh.aplikasi.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CompactSearchBar(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = placeholder) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.testTag("clear_search_button")) {
                    Icon(Icons.Default.Close, contentDescription = "Bersihkan pencarian")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth().testTag("search_input")
    )
}

@Composable
fun SelectionActionBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onMoveSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .testTag("selection_action_bar")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag("exit_selection_button")) {
                Icon(Icons.Default.Close, contentDescription = "Keluar mode pilih")
            }
            Text("$selectedCount dipilih", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onSelectAll, modifier = Modifier.testTag("select_all_button")) {
                Text("Pilih Semua")
            }
            IconButton(onClick = onMoveSelected, enabled = selectedCount > 0, modifier = Modifier.size(48.dp).testTag("move_selected_button")) {
                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Pindahkan")
            }
            IconButton(onClick = onDeleteSelected, enabled = selectedCount > 0, modifier = Modifier.size(48.dp).testTag("delete_selected_button")) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
```

---

## 5. Template Unit Test & Robolectric (`AppVerificationTest.kt`)

Jalankan `gradle :app:testDebugUnitTest --no-daemon` dengan cache lokal Robolectric offline-first + mirror Google Maven Central (`apk_Release_Setup_Guide_(umum).md`).

```kotlin
package com.contoh.aplikasi

import androidx.test.core.app.ApplicationProvider
import com.contoh.aplikasi.data.DeviceAutoSyncManager
import com.contoh.aplikasi.data.SyncRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppVerificationTest {
    @Test
    fun mergeLastWriteWins_prefersNewerTimestampAndPreservesTombstone() {
        val manager = DeviceAutoSyncManager(ApplicationProvider.getApplicationContext())
        val local = listOf(
            SyncRecord(id = "1", title = "Lama", value = "A", folder = "", updatedAtMillis = 100L),
            SyncRecord(id = "2", title = "Hapus", value = "B", folder = "", updatedAtMillis = 300L, deleted = true)
        )
        val device = listOf(
            SyncRecord(id = "1", title = "Baru", value = "A2", folder = "", updatedAtMillis = 200L),
            SyncRecord(id = "2", title = "Ada", value = "B", folder = "", updatedAtMillis = 150L, deleted = false)
        )

        val merged = manager.mergeLastWriteWins(local, device).associateBy { it.id }
        assertEquals("Baru", merged["1"]?.title)
        assertTrue(merged["2"]?.deleted == true)
    }
}
```
