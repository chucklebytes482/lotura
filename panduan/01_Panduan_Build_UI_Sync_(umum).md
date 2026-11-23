# 01 — Panduan Build, UI Minimalis, & Sinkronisasi Perangkat (Umum)

Aturan inti pembuatan aplikasi Android (**Kotlin + Jetpack Compose + Material 3 + MVVM**) ringan (`< 10 MB`). Kirim dokumen ini (dan/atau dokumen lain di `panduan/`) lalu ketik **"jalankan"** atau **"jalankan yang sesuai saja"**.

### Daftar Dokumen `panduan/`
1. `01_Panduan_Build_UI_Sync_(umum).md` — Aturan inti, UI minimalis, source code wajib (Cadangan Perangkat & Web Panduan), & prompt singkat.
2. `02_Setup_Rilis_CI_DualRepo_(umum).md` — Gradle R8, anti-429, GitHub Secrets 1-PAT, CI Dual-Repo, & `docs/index.html`.
3. `03_Generator_Kredensial_VeraCrypt_(umum).md` — Generator 3 berkas rahasia → VeraCrypt v5 (1–2 MiB) → 1 HTML pengunduh.

---

## 1. Aturan Wajib vs Dilarang

| Aspek | Wajib | Dilarang |
|---|---|---|
| **Identitas** | 1 kata Euskara 3 suku kata (4–8 huruf Latin tanpa diakritik) → `com.aistudio.<kata>`, ikon adaptif kustom. | Nama > 2 kata, diakritik, akhiran acak. |
| **Tema & Aksesibilitas** | Otomatis ikut sistem (`isSystemInDarkTheme()`), edge-to-edge (`WindowInsets.safeDrawing`), sentuh ≥ `48.dp`, `testTag` `snake_case`, `contentDescription`. | Pemilih tema Gelap/Terang, ukuran layar statis. |
| **UI Minimalis & Stabil** | Icon-first (ikon `20–28.dp`, jarak lega, tanpa teks redundan). **Dimensi kartu wajib konsisten sejak awal** dan **1 kanal status saja** (spinner di tombol + 1 `Snackbar` bawah). Legenda ikon & penjelasan panjang hanya di situs publik `docs/index.html`. | Label berulang di samping ikon jelas, `AnimatedVisibility`/teks kondisional di dalam kartu yang mengubah tinggi kotak, atau status duplikat di kartu & `Snackbar`. |
| **Prioritas Beranda** | `#1 Cadangan Perangkat`, `#2 Web Panduan` (subjudul non-teknis, mis. *Panduan & Arti Ikon*), lalu konten utama + bilah pencarian & mode pilih dengan `BackHandler`. | Menu berlapis untuk cadangan/panduan, istilah teknis (*GitHub Pages*, dll.) di UI. |
| **Privasi & Ukuran** | **100% luring** untuk app offline (tanpa `INTERNET`/`ACCESS_NETWORK_STATE`, tanpa Firebase/Auth/OkHttp). Release `< 10 MB` (`R8`, `isShrinkResources`, `arm64-v8a`), komentari dependensi tak terpakai. | Izin/SDK jaringan tak terpakai, commit `.apk`/`.aab`/cache/rahasia mentah ke Git. |
| **Zona Folder** | `panduan/` = panduan umum; `docs/` = khusus situs publik `<slug>-site` (ramah HP, tanggal & `HH:mm` rilis di bawah tombol unduh, tanpa tautan GitHub Releases); root = hanya `<slug_app>_secrets_download.html` sementara (jangan blokir di `.gitignore`, hapus setelah diunduh). | Campur panduan ke `docs/`, simpan `.hc`/`.jks` mentah di Git. |

---

## 2. Kontrak Skema Data & Sinkronisasi Otomatis Perangkat

- **Berkas tunggal:** `/storage/emulated/0/Documents/<NamaApp>/<slug>_data.json` (tulis langsung; fallback Android 10+ via `MediaStore` mode `"wt"` agar tidak membuat duplikat `(1)`).
- **Field wajib tiap entri JSON:** `id`, `updatedAtMillis` (epoch ms), `deleted` (boolean). Resolusi konflik: **Last-Write-Wins** berdasarkan `updatedAtMillis`.
- **Saat ada ubah/hapus data atau folder di app:** langsung timpa berkas perangkat (agar folder/entri yang dihapus tidak muncul kembali), dengan pagar jika belum pernah sinkron dan berkas perangkat punya entri aktif lebih banyak.
- **Saat app dibuka:** cek berkas perangkat otomatis dengan pagar **anti-timpa file kosong & prioritas entri aktif terbanyak** (pulihkan penuh bila lokal kosong atau file perangkat lebih baru dengan entri ≥ lokal; gabung aman bila file perangkat lebih sedikit).
- **Tanpa:** login cloud (kecuali diminta), `WorkManager`, layanan latar belakang, atau file backup bertanggal ganda.

---

## 3. Source Code Wajib: Cadangan Perangkat & Web Panduan

### A. Sinkronisasi 1 Berkas Perangkat (`data/DeviceAutoSyncManager.kt`)

```kotlin
data class SyncRecord(
    val id: String, val value: String, val folder: String = "",
    val updatedAtMillis: Long, val deleted: Boolean = false
)

class DeviceAutoSyncManager(
    private val context: Context,
    private val appFolder: String = "<NamaApp>",
    private val fileName: String = "<slug>_data.json"
) {
    val displayPath = "/storage/emulated/0/Documents/$appFolder/$fileName"

    fun mergeLastWriteWins(local: List<SyncRecord>, device: List<SyncRecord>): List<SyncRecord> =
        (local + device).fold(linkedMapOf<String, SyncRecord>()) { acc, item ->
            val cur = acc[item.id]
            if (cur == null || item.updatedAtMillis >= cur.updatedAtMillis) acc[item.id] = item
            acc
        }.values.toList()

    suspend fun readSingleBackupFile(): String? = withContext(Dispatchers.IO) {
        val f = directFile()
        if (f.exists() && f.canRead()) return@withContext runCatching { f.readText() }.getOrNull()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryUri()?.let { uri -> runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull() }
        } else null
    }

    suspend fun writeReplacingSingleFile(json: String): Boolean = withContext(Dispatchers.IO) {
        val f = directFile()
        if (runCatching { f.parentFile?.mkdirs(); f.writeText(json); true }.getOrDefault(false)) return@withContext true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val r = context.contentResolver
            val uri = queryUri() ?: r.insert(MediaStore.Files.getContentUri("external"), ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$appFolder")
            }) ?: return@withContext false
            // Mode "wt" menimpa penuh tanpa membuat duplikat (1), (2)
            return@withContext runCatching { r.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) } != null }.getOrDefault(false)
        }
        false
    }

    private fun directFile() = File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), appFolder), fileName)

    private fun queryUri() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"), arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(fileName, "${Environment.DIRECTORY_DOCUMENTS}/$appFolder/"), null
        )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), c.getLong(0)) else null }
    } else null
}
```

### B. Prioritas Beranda `#1 Cadangan Perangkat` & `#2 Web Panduan` (`ui/HomePriorityCards.kt`)

```kotlin
const val WEB_GUIDE_URL = "https://<username>.github.io/<slug>-site/"

fun openWebGuide(context: Context, url: String = WEB_GUIDE_URL): Boolean = runCatching {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
}.getOrDefault(false)

fun LazyListScope.homePriorityCards(
    backupStatus: String,
    onOpenBackup: () -> Unit,
    onOpenWebGuide: () -> Unit
) {
    item("priority_1_backup") {
        PriorityCard(
            icon = Icons.Rounded.FolderCopy,
            title = "Cadangan Perangkat",
            subtitle = backupStatus,
            trailing = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            testTag = "home_device_backup_card",
            onClick = onOpenBackup
        )
    }
    item("priority_2_web_guide") {
        PriorityCard(
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            title = "Web Panduan",
            subtitle = "Panduan & Arti Ikon",
            trailing = Icons.AutoMirrored.Rounded.OpenInNew,
            testTag = "home_web_guide_card",
            onClick = onOpenWebGuide
        )
    }
}

@Composable
private fun PriorityCard(
    icon: ImageVector, title: String, subtitle: String, trailing: ImageVector,
    testTag: String, onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).testTag(testTag)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer), Alignment.Center) {
                Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(trailing, contentDescription = "Buka $title", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
```

---

## 4. Template Prompt Singkat Proyek Baru

```text
Buat aplikasi Android (Kotlin + Jetpack Compose + Material 3 + MVVM):
- Nama & ID: [Kata Euskara 3 suku kata] | com.aistudio.[kata] + ikon adaptif kustom
- Fitur inti: 1) [...] 2) [...] 3) [...]
- Ikuti aturan di panduan/01_Panduan_Build_UI_Sync_(umum).md dan panduan/02_Setup_Rilis_CI_DualRepo_(umum).md:
  tema sistem otomatis, UI icon-first dengan tinggi kartu konsisten & anti-duplikat status,
  prioritas Beranda (#1 Cadangan Perangkat, #2 Web Panduan), sinkronisasi 1 berkas di Documents/[NamaApp]/,
  100% luring < 10 MB (R8 + arm64-v8a), Unit + Robolectric test lulus, dan rilis Dual-Repo (<slug>-site).
```

---

## 5. Checklist Audit Sebelum Rilis

- [ ] 100% luring (tanpa izin jaringan & dependensi tak terpakai); APK R8 `arm64-v8a` `< 10 MB` & lulus `apksigner verify`.
- [ ] `gradle :app:testDebugUnitTest --no-daemon` (Unit + Robolectric `@Config(sdk = [36])`) lulus 100% tanpa HTTP 429 & bebas peringatan Gradle 9.
- [ ] Tema sistem otomatis, prioritas Beranda `#1` & `#2`, kartu stabil tanpa resize dinamis, tanpa status duplikat.
- [ ] Sync 1 berkas di `Documents/<NamaApp>/` menimpa saat edit/hapus & aman dari file kosong.
- [ ] Dual-Repo siap (`PUBLIC_DIST_TOKEN` 1-PAT, `release.yml`, `publish-site.yml`, `retention-days: 7`), `docs/index.html` menampilkan tanggal & `HH:mm` rilis di bawah tombol unduh, dan tidak ada rahasia mentah atau `*_secrets_download.html` tersisa di Git.
