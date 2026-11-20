# Panduan Personality & Alur Build Aplikasi Android Ringan (< 10 MB)

**Standar Pengembangan Ringan | Kotlin + Jetpack Compose + Material 3 | Arsitektur MVVM | Distribusi APK GitHub Release**

Dokumen ini adalah panduan umum (*personality* / pola standar pengembangan) sekaligus referensi **source code siap salin** untuk membangun aplikasi Android yang cepat, responsif, berukuran rilis di bawah **10 MB**, mendukung tata letak standar edge-to-edge dan sinkronisasi Google Drive 2 arah opsional, serta siap dirilis otomatis sebagai signed APK di GitHub Releases.

---

## Prinsip Utama (Wajib Diterapkan)

1. **Fokus pada Fitur Inti & Pangkas Fitur Opsional**
   - Bangun hanya fitur yang benar-benar dibutuhkan pengguna.
   - Tolak atau hapus fitur tambahan yang membengkakkan ukuran APK dan menambah beban UI (seperti aset media berat, SDK iklan/analitik tak perlu, menu berlapis, atau kamus/data bawaan raksasa yang tidak diminta).
   - Nonaktifkan atau hapus dependensi Gradle yang tidak digunakan.
2. **Target Ukuran APK Rilis < 10 MB**
   - Aktifkan **R8 / ProGuard** (`isMinifyEnabled = true`), **Resource Shrinking** (`isShrinkResources = true`), dan **filter ABI `arm64-v8a`** khusus pada varian `release` (biarkan `debug` mendukung multi-ABI untuk pengujian emulator/perangkat).
3. **Antarmuka Ringkas, Responsif & Intuitif**
   - Hindari tombol aksi berulang yang memakan ruang layar. Gunakan gestur langsung (*tap* untuk buka/edit, tekan tahan untuk mode pilih banyak / aksi lanjutan, *swipe* jika relevan).
   - Sediakan bilah pencarian atau penyaringan cepat di bagian atas bila mengelola daftar item/folder.
   - Pastikan seluruh target sentuh minimal **48dp × 48dp** dengan aksesibilitas (`contentDescription`) yang jelas.
4. **Kepatuhan Keamanan & Tanpa Data Palsu**
   - Jangan menanamkan API key, password keystore, atau kredensial pribadi di dalam kode sumber maupun repositori Git.
   - Gunakan `BuildConfig` / environment secrets atau Google Sign-In (`CredentialManager`) sesuai kebutuhan.

---

## Fase 1 — Konsep, Identitas & Format Data Terstruktur

1. **Identitas Aplikasi Unik**
   - Tetapkan `applicationId` unik (misal: `com.organisasi.namaapp`), nama launcher pendek (1–2 kata agar tidak terpotong di beranda Android), dan ikon adaptif kustom (`res/mipmap-*`).
2. **Arsitektur Modern**
   - Gunakan **Kotlin** + **Jetpack Compose** + **Material Design 3** (`Theme.kt` terpusat, `enableEdgeToEdge()`, dan penanganan `WindowInsets`).
   - Terapkan pola **MVVM** dengan `ViewModel`, `StateFlow` / `collectAsStateWithLifecycle`, serta `kotlinx.serialization` atau `org.json` untuk parsing data yang konsisten.
3. **Skema Data Bersih & Seragam**
   - Rancang skema JSON / Room / DataStore dengan field seragam (tanpa field opsional yang membingungkan jika file juga diedit manual/AI), dilengkapi `id`, `updatedAtMillis`, dan penanda `deleted` (*soft-delete*) bila mendukung sinkronisasi dua arah.
4. **Pisahkan Metadata Teknis dari UI**
   - Jangan tampilkan ID internal, metadata mentah, atau log teknis di tampilan utama pengguna.

---

## Fase 2 — Strategi Penyimpanan & Sinkronisasi Cloud (Metode Ganda)

Sesuaikan mekanisme persistensi dengan sifat data aplikasi:

### A. Sinkronisasi Cloud (Opsional / Dengan Akun Google)
- Gunakan **Google Sign-In** via Jetpack `CredentialManager` (`GetSignInWithGoogleOption`) — jangan gunakan login anonim bila aplikasi memakai layanan Google/Firebase.
- Gunakan OAuth scope minimal sesuai kebutuhan (misal `https://www.googleapis.com/auth/drive.file` untuk menyimpan data cadangan JSON di folder aplikasi Google Drive).
- Daftarkan sidik jari sertifikat **SHA-1** dan **SHA-256** (baik varian *debug* maupun *release*) ke Firebase Console / Google Cloud Console agar autentikasi tetap berjalan pada APK rilis.
- Simpan data dan pengaturan sebagai file JSON terpisah di folder Google Drive dengan **sinkronisasi 2 arah berbasis timestamp** (`updatedAtMillis`).

### B. Mode Tanpa Login / Offline-First
- Aplikasi harus tetap dapat langsung dipakai tanpa memaksa pengguna login di layar pertama (*no mandatory login gate*).
- Untuk data lokal perangkat atau preferensi UI, gunakan **DataStore** atau **Room** (jika tidak memerlukan sinkronisasi cloud). Jika mendukung ekspor/impor lokal, gunakan Android **Storage Access Framework** (`CreateDocument` / `OpenDocument`) tanpa izin penyimpanan berbahaya (`READ_EXTERNAL_STORAGE`).

---

## Fase 3 — Kurasi Fitur, Pemangkasan UI & Efisiensi Memori

1. **Audit Dependensi Rutin**
   - Hapus library yang tidak dipakai dari `gradle/libs.versions.toml` dan `app/build.gradle.kts`.
2. **UI Bersih & Tidak Redundan**
   - Hilangkan tab navigasi kosong, tombol aksi duplikat di setiap baris, dan kartu pengaturan yang jarang terpakai.
   - Kelompokkan aksi massal (Pilih Semua, Pindahkan, Hapus) ke dalam satu bilah aksi kontekstual saat mode pilih aktif.
3. **Efisiensi Performa & Memori**
   - Gunakan `remember` dan `derivedStateOf` untuk meminimalkan rekomposisi.
   - Hindari IPC berulang atau operasi I/O di main thread; gunakan cache in-memory untuk jalur akses cepat dan Kotlin Coroutines (`Dispatchers.IO`) untuk pembacaan penyimpanan.

---

## Fase 4 — Pengujian Otomatis, Kompresi R8 & Build APK Release

### 1. Pengujian Sebelum Build Rilis
- Tulis dan jalankan **Unit Test** untuk logika bisnis, sinkronisasi/merge JSON, dan edge case.
- Tulis dan jalankan **Robolectric UI Test** untuk memvalidasi layar Compose, navigasi, tata letak responsif, dan alur pengguna utama (tanpa memerlukan emulator fisik).
- Perintah verifikasi:
  ```bash
  gradle :app:testDebugUnitTest --no-daemon
  ```

### 2. Konfigurasi Build Release (`app/build.gradle.kts`)
```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        ndk {
            abiFilters += "arm64-v8a"
        }
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```
Pastikan `proguard-rules.pro` mempertahankan kelas model serialisasi / Kotlin metadata yang dibutuhkan agar R8 tidak menghapus field penting.

### 3. Target Metrik Lolos Rilis
- [ ] Seluruh Unit Test & Robolectric Test lulus (0 gagal, 0 error).
- [ ] Ukuran APK `release` **< 10 MB**.
- [ ] Folder `lib/` di dalam APK `release` hanya berisi arsitektur `arm64-v8a` (atau tanpa native lib yang tidak perlu).
- [ ] Tanda tangan APK lolos verifikasi `apksigner verify --print-certs`.
- [ ] Sidik jari SHA-1 & SHA-256 sertifikat rilis terdaftar di Google Cloud / Firebase Console (jika memakai Google Sign-In / API Google).

---

## Fase 5 — Otomasi GitHub Release & Dokumentasi (Tanpa Menyimpan APK di Repo)

1. **Jangan Simpan File APK di Repositori Git**
   - File `.apk`, `.aab`, dan `.jks` tidak boleh di-commit ke folder mana pun di dalam repositori (termasuk folder `releases/`) agar ukuran repositori tetap ringan dan bebas kebocoran kredensial.
2. **Publikasi Otomatis via GitHub Actions**
   - Gunakan workflow `.github/workflows/release.yml` yang menjalankan test, membangun `:app:assembleRelease` ke direktori sementara `dist/`, menghitung `CHECKSUMS.txt` & `CERT_FINGERPRINT.txt`, lalu mengunggahnya langsung ke **GitHub Releases** dan **Workflow Artifacts**.
3. **Dokumentasi Publik di GitHub Pages**
   - Gunakan folder `docs/` dengan halaman HTML/Markdown ringan untuk panduan penggunaan, format data/skema, dan tombol unduh yang mengarah langsung ke `https://github.com/<username>/<repo>/releases/latest`.

---

## Kumpulan Source Code Siap Salin (Standar UI, Tata Letak & Sinkronisasi)

Seluruh contoh di bawah menggunakan namespace contoh `com.contoh.aplikasi` agar langsung bisa disesuaikan dan disalin ke proyek Android mana pun.

### 1. Standar Tema, Palet Warna & Bentuk (`ui/theme/Color.kt` & `Theme.kt`)

Palet *Precision Slate* (gelap & terang) dengan warna semantik status sinkronisasi (`MintSynced`, `AmberPending`, `CoralAlert`).

```kotlin
// app/src/main/java/com/contoh/aplikasi/ui/theme/Color.kt
package com.contoh.aplikasi.ui.theme

import androidx.compose.ui.graphics.Color

// Mode Gelap (Precision Slate & Cyan-Teal)
val SlateVoid = Color(0xFF0B1319)
val SlateSurface = Color(0xFF121C24)
val SlateSurfaceVariant = Color(0xFF1B2732)
val SlateCardElevated = Color(0xFF212F3C)
val SlateOutline = Color(0xFF2F4150)

val CyanAccent = Color(0xFF67D4E4)
val CyanOnAccent = Color(0xFF032D35)
val CyanContainer = Color(0xFF0E3A44)
val CyanOnContainer = Color(0xFFB3F1FA)

// Warna status semantik (Sinkronisasi & Peringatan)
val MintSynced = Color(0xFF4ADE80)
val AmberPending = Color(0xFFFBBF24)
val CoralAlert = Color(0xFFFF8A80)
val CoralOnAlert = Color(0xFF4A0E0E)

val TextPrimaryDark = Color(0xFFF1F5F9)
val TextSecondaryDark = Color(0xFF9FB0BE)

// Mode Terang
val LightBg = Color(0xFFF4F7F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE7EFF4)
val LightOutline = Color(0xFFC8D6E0)
val TealPrimaryLight = Color(0xFF006978)
val TealContainerLight = Color(0xFFCCF2F8)
val TealOnContainerLight = Color(0xFF002025)
val TextPrimaryLight = Color(0xFF0F1B24)
val TextSecondaryLight = Color(0xFF4B5E6B)
```

```kotlin
// app/src/main/java/com/contoh/aplikasi/ui/theme/Theme.kt
package com.contoh.aplikasi.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val AppDarkScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = CyanOnAccent,
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanOnContainer,
    secondary = MintSynced,
    onSecondary = SlateVoid,
    secondaryContainer = SlateCardElevated,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = AmberPending,
    background = SlateVoid,
    onBackground = TextPrimaryDark,
    surface = SlateSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = SlateOutline,
    error = CoralAlert,
    onError = CoralOnAlert
)

private val AppLightScheme = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = LightSurface,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = TealOnContainerLight,
    secondary = Color(0xFF15803D),
    onSecondary = LightSurface,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightOutline,
    error = Color(0xFFC62828),
    onError = LightSurface
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AppDarkScheme
        else -> AppLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        shapes = AppShapes,
        content = content
    )
}
```

---

### 2. Standar Tata Letak Utama Edge-to-Edge & Kartu Menu (`MainScaffold.kt`)

Pola standar layar beranda adaptif (`widthIn(max = 640.dp)` untuk tablet/foldable), `enableEdgeToEdge()`, `WindowInsets.safeDrawing`, kartu menu 1 baris dengan ikon 42dp, serta pemicu `ModalBottomSheet` untuk Sinkronisasi Google Drive.

```kotlin
// app/src/main/java/com/contoh/aplikasi/ui/MainScaffold.kt
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    canNavigateBack: Boolean,
    onNavigateBack: () -> Unit,
    showSyncSheet: Boolean,
    onDismissSyncSheet: () -> Unit,
    syncSheetContent: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("navigate_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
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

    if (showSyncSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissSyncSheet,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            syncSheetContent()
        }
    }
}

@Composable
fun AdaptiveMenuContainer(
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun StandardMenuCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
    trailingIcon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
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
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = trailingIcon,
                contentDescription = "Buka $title",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
```

---

### 3. Standar Tampilan Sinkronisasi Google Drive (`ui/screens/DriveSyncSheet.kt`)

Tampilan **ModalBottomSheet** siap salin untuk dua kondisi: **Belum Login** (tanpa memaksa login di awal) dan **Terhubung** (indikator status *online/pending/synced*, toggle **Sinkronisasi Otomatis**, tombol **Putuskan Akun**, serta tombol **Sinkronkan Sekarang**).

```kotlin
// app/src/main/java/com/contoh/aplikasi/ui/screens/DriveSyncSheet.kt
package com.contoh.aplikasi.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contoh.aplikasi.ui.theme.AmberPending
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DriveSyncUnauthenticatedSheetContent(
    driveFolderName: String,
    isLoading: Boolean,
    errorMessage: String?,
    onConnectGoogleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = "Auto-Sinkronisasi Google Drive",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-Sinkronisasi Google Drive",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Opsional • Belum terhubung ke akun Google",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "Simpan dan sinkronkan otomatis ke folder \"$driveFolderName\" di Google Drive Anda agar berkas cadangan JSON mudah diakses lintas perangkat.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Button(
            onClick = onConnectGoogleClick,
            enabled = !isLoading,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .testTag("drive_connect_google_button")
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Masuk dengan Google (Drive Sync)", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun DriveSyncConnectedSheetContent(
    userEmail: String,
    driveFolderName: String,
    autoSyncEnabled: Boolean,
    isOnline: Boolean,
    pendingChanges: Boolean,
    lastSyncedAtMillis: Long,
    fallbackStatusMessage: String,
    isSyncing: Boolean,
    onToggleAutoSync: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusDotColor: Color = when {
        !isOnline -> MaterialTheme.colorScheme.error
        pendingChanges -> AmberPending
        lastSyncedAtMillis > 0L -> MaterialTheme.colorScheme.secondary
        else -> AmberPending
    }

    val statusHeadline = when {
        isSyncing -> "Menyinkronkan data & pengaturan..."
        lastSyncedAtMillis > 0L -> "Tersinkronisasi • ${formatRelativeTime(lastSyncedAtMillis)}"
        else -> fallbackStatusMessage
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = "Auto-Sinkronisasi Google Drive",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-Sinkronisasi Google Drive",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = userEmail.ifBlank { "Akun Google Terhubung" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Kartu Ringkasan Status Sinkronisasi
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(18.dp)
                )
                .testTag("drive_sync_status_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Text(
                        text = statusHeadline,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isOnline) "Jaringan: Online" else "Jaringan: Offline",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (pendingChanges) "Menunggu sinkron" else "Semua tersimpan",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Folder Drive: $driveFolderName (Sinkron 2 arah)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Toggle Auto-Sinkronisasi
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onToggleAutoSync(!autoSyncEnabled) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = "Sinkronisasi Otomatis",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Unggah otomatis setiap ada perubahan atau saat kembali online",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = autoSyncEnabled,
                onCheckedChange = onToggleAutoSync,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("drive_auto_sync_switch")
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

        // Tombol Aksi Bawah (Putuskan Akun & Sinkronkan Sekarang)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDisconnect,
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 54.dp)
                    .testTag("drive_disconnect_button")
            ) {
                Text(
                    text = "Putuskan Akun",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            Button(
                onClick = onSyncNow,
                enabled = !isSyncing,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                modifier = Modifier
                    .weight(1.1f)
                    .heightIn(min = 54.dp)
                    .testTag("drive_sync_now_button")
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (isSyncing) "Menyinkron..." else "Sinkronkan Sekarang",
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

fun formatRelativeTime(timestampMillis: Long): String {
    val diffSec = ((System.currentTimeMillis() - timestampMillis) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "Baru saja"
        diffSec < 3600L -> "${diffSec / 60} menit lalu"
        diffSec < 86400L -> "${diffSec / 3600} jam lalu"
        else -> SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(timestampMillis))
    }
}
```

---

### 4. Autentikasi Google Sign-In & OAuth Google Drive (`ui/AuthHelper.kt`)

Menggunakan Jetpack `CredentialManager` (`GetSignInWithGoogleOption`) + Firebase Auth + `Identity.getAuthorizationClient(...)` untuk meminta scope `drive.file`.

```kotlin
// app/src/main/java/com/contoh/aplikasi/ui/AuthHelper.kt
package com.contoh.aplikasi.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.contoh.aplikasi.R
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

object AuthHelper {
    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    fun signInWithGoogle(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        val clientId = runCatching { context.getString(R.string.default_web_client_id) }.getOrNull()
        if (clientId.isNullOrBlank()) {
            onError("Konfigurasi default_web_client_id tidak ditemukan.")
            return
        }
        val activity = context.findActivity()
        if (activity == null) {
            onError("Activity context diperlukan untuk login Google.")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val cred = credentialManager.getCredential(activity, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    onSuccess()
                } else {
                    onError("Tipe kredensial Google tidak dikenali.")
                }
            } catch (_: GetCredentialCancellationException) {
                onCancelled()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Gagal masuk dengan Google.")
            }
        }
    }

    fun signOut(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onComplete: () -> Unit = {}
    ) {
        scope.launch {
            withContext(NonCancellable) {
                runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
                Firebase.auth.signOut()
                onComplete()
            }
        }
    }

    fun authorizeGoogleDrive(
        activity: Activity,
        resolutionLauncher: ActivityResultLauncher<IntentSenderRequest>,
        onAccessToken: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val authorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()

        Identity.getAuthorizationClient(activity)
            .authorize(authorizationRequest)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent != null) {
                        resolutionLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } else {
                        onError("Otorisasi Google Drive memerlukan konfirmasi ulang.")
                    }
                } else {
                    val token = result.accessToken
                    if (!token.isNullOrBlank()) {
                        onAccessToken(token)
                    } else {
                        onError("Token akses Google Drive kosong.")
                    }
                }
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Gagal meminta izin Google Drive.")
            }
    }
}
```

---

### 5. Klien Sinkronisasi JSON 2 Arah Google Drive & Merge LWW (`data/DriveSyncClient.kt`)

Implementasi ringan menggunakan `OkHttp` + `org.json` untuk membaca/menulis file JSON ke folder Google Drive (termasuk dukungan shortcut folder) serta fungsi penggabungan dua arah **Last-Write-Wins (LWW)** berbasis `updatedAtMillis` dan penanda `deleted`.

```kotlin
// app/src/main/java/com/contoh/aplikasi/data/DriveSyncClient.kt
package com.contoh.aplikasi.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SyncRecord(
    val id: String,
    val title: String,
    val value: String,
    val folder: String,
    val updatedAtMillis: Long,
    val deleted: Boolean = false
)

class DriveAuthExpiredException(message: String) : Exception(message)

class DriveSyncClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val SHORTCUT_MIME = "application/vnd.google-apps.shortcut"
    }

    /**
     * Menggabungkan daftar item lokal dan remote secara deterministik (Last-Write-Wins).
     */
    fun mergeLastWriteWins(
        localItems: List<SyncRecord>,
        remoteItems: List<SyncRecord>
    ): List<SyncRecord> {
        val merged = linkedMapOf<String, SyncRecord>()
        (localItems + remoteItems).forEach { item ->
            val current = merged[item.id]
            if (current == null || item.updatedAtMillis >= current.updatedAtMillis) {
                merged[item.id] = item
            }
        }
        return merged.values.toList()
    }

    suspend fun resolveOrCreateFolder(accessToken: String, folderName: String): String =
        withContext(Dispatchers.IO) {
            val escaped = folderName.replace("'", "\\'")
            val query = "name = '$escaped' and trashed = false and " +
                "(mimeType = '$FOLDER_MIME' or mimeType = '$SHORTCUT_MIME')"
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("$DRIVE_FILES_URL?q=$encoded&fields=files(id,mimeType,shortcutDetails)&supportsAllDrives=true&includeItemsFromAllDrives=true")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val files = JSONObject(executeRequest(request)).optJSONArray("files")
            if (files != null && files.length() > 0) {
                val first = files.getJSONObject(0)
                val targetId = first.optJSONObject("shortcutDetails")?.optString("targetId")
                return@withContext if (!targetId.isNullOrBlank()) targetId else first.getString("id")
            }

            val metadata = JSONObject().apply {
                put("name", folderName)
                put("mimeType", FOLDER_MIME)
            }
            val createReq = Request.Builder()
                .url("$DRIVE_FILES_URL?fields=id")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(metadata.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            JSONObject(executeRequest(createReq)).getString("id")
        }

    suspend fun findFileIdByName(accessToken: String, folderId: String, fileName: String): String? =
        withContext(Dispatchers.IO) {
            val escaped = fileName.replace("'", "\\'")
            val query = "'$folderId' in parents and name = '$escaped' and trashed = false"
            val encoded = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("$DRIVE_FILES_URL?q=$encoded&fields=files(id)&supportsAllDrives=true&includeItemsFromAllDrives=true")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            val arr = JSONObject(executeRequest(req)).optJSONArray("files")
            if (arr != null && arr.length() > 0) arr.getJSONObject(0).getString("id") else null
        }

    suspend fun downloadJson(accessToken: String, fileId: String): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$DRIVE_FILES_URL/$fileId?alt=media&supportsAllDrives=true")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            executeRequest(req)
        }

    suspend fun upsertJsonFile(
        accessToken: String,
        folderId: String,
        existingFileId: String?,
        fileName: String,
        jsonContent: String
    ): String = withContext(Dispatchers.IO) {
        val metadata = JSONObject().apply {
            put("name", fileName)
            put("mimeType", "application/json")
            if (existingFileId == null) {
                put("parents", JSONArray().apply { put(folderId) })
            }
        }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addPart(metadata.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addPart(jsonContent.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val req = Request.Builder()
            .addHeader("Authorization", "Bearer $accessToken")
            .let { builder ->
                if (existingFileId != null) {
                    builder.url("$DRIVE_UPLOAD_URL/$existingFileId?uploadType=multipart&fields=id&supportsAllDrives=true")
                        .patch(body)
                } else {
                    builder.url("$DRIVE_UPLOAD_URL?uploadType=multipart&fields=id&supportsAllDrives=true")
                        .post(body)
                }
            }
            .build()

        JSONObject(executeRequest(req)).getString("id")
    }

    private fun executeRequest(request: Request): String {
        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) {
                throw DriveAuthExpiredException("Otorisasi Google Drive kedaluwarsa (${response.code})")
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("Google Drive API error ${response.code}: $bodyStr")
            }
            return bodyStr
        }
    }
}
```

---

### 6. Pola File Manager Ringkas: Bilah Pencarian, Mode Pilih & `BackHandler` (`SelectionActionBar.kt`)

Gunakan pola ini saat layar menampilkan daftar item atau folder agar tetap bersih tanpa tombol hapus/pindah berulang di tiap baris.

```kotlin
// app/src/main/java/com/contoh/aplikasi/ui/components/SelectionComponents.kt
package com.contoh.aplikasi.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.testTag("clear_search_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Bersihkan pencarian")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("search_input")
    )
}

@Composable
fun SelectionActionBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onMoveSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .testTag("selection_action_bar")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("exit_selection_button")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Keluar dari mode pilih")
            }
            Text(
                text = "$selectedCount dipilih",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onSelectAll,
                modifier = Modifier.testTag("select_all_button")
            ) {
                Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Pilih Semua")
            }
            IconButton(
                onClick = onMoveSelected,
                enabled = selectedCount > 0,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("move_selected_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = "Pindahkan item terpilih",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onDeleteSelected,
                enabled = selectedCount > 0,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("delete_selected_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Hapus item terpilih",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
```

Jangan lupa menambahkan `BackHandler` di layar sekunder / mode pilih agar tombol kembali sistem keluar dari mode pilih atau naik satu tingkat folder terlebih dahulu:

```kotlin
BackHandler(enabled = selectionMode || currentFolderPath.isNotEmpty()) {
    when {
        selectionMode -> clearSelection()
        currentFolderPath.isNotEmpty() -> {
            currentFolderPath = currentFolderPath.substringBeforeLast('/', missingDelimiterValue = "")
        }
    }
}
```

---

### 7. Template Unit Test & Robolectric Compose Test (`AppVerificationTest.kt`)

```kotlin
// app/src/test/java/com/contoh/aplikasi/AppVerificationTest.kt
package com.contoh.aplikasi

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.contoh.aplikasi.data.DriveSyncClient
import com.contoh.aplikasi.data.SyncRecord
import com.contoh.aplikasi.ui.screens.DriveSyncConnectedSheetContent
import com.contoh.aplikasi.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppVerificationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mergeLastWriteWins_prefersNewerTimestampAndPreservesTombstone() {
        val client = DriveSyncClient()
        val local = listOf(
            SyncRecord(id = "1", title = "Lama", value = "A", folder = "", updatedAtMillis = 100L),
            SyncRecord(id = "2", title = "Dihapus", value = "B", folder = "", updatedAtMillis = 300L, deleted = true)
        )
        val remote = listOf(
            SyncRecord(id = "1", title = "Baru", value = "A2", folder = "", updatedAtMillis = 200L),
            SyncRecord(id = "2", title = "Masih Ada", value = "B", folder = "", updatedAtMillis = 150L, deleted = false)
        )

        val merged = client.mergeLastWriteWins(local, remote).associateBy { it.id }
        assertEquals("Baru", merged["1"]?.title)
        assertTrue(merged["2"]?.deleted == true)
    }

    @Test
    fun driveSyncConnectedSheet_rendersControlsAndTriggersSync() {
        var syncTriggered = false
        composeRule.setContent {
            AppTheme {
                DriveSyncConnectedSheetContent(
                    userEmail = "pengguna@contoh.com",
                    driveFolderName = "CadanganApp",
                    autoSyncEnabled = true,
                    isOnline = true,
                    pendingChanges = false,
                    lastSyncedAtMillis = System.currentTimeMillis(),
                    fallbackStatusMessage = "Siap sinkron",
                    isSyncing = false,
                    onToggleAutoSync = {},
                    onDisconnect = {},
                    onSyncNow = { syncTriggered = true }
                )
            }
        }

        composeRule.onNodeWithTag("drive_sync_status_card").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_auto_sync_switch").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_sync_now_button").performClick()
        assertTrue(syncTriggered)
    }
}
```

---

## Template Prompt / PRD Memulai Aplikasi Android Baru

Salin dan isi bagian bertanda `[...]` saat memulai proyek Android baru:

```text
Buat aplikasi Android (Kotlin + Jetpack Compose + Material 3 + MVVM):
- Nama Aplikasi (1-2 kata): [Nama Aplikasi] | applicationId unik + ikon adaptif kustom
- Fitur Inti Wajib:
  1. [Fitur utama 1]
  2. [Fitur utama 2]
  3. [Fitur utama 3]
- Kebijakan Ringan (< 10 MB):
  - Fokus hanya pada fitur inti yang diminta; JANGAN tambahkan fitur bloat, SDK iklan, atau aset media besar.
  - Nonaktifkan/hapus dependensi Gradle yang tidak dipakai.
  - Aktifkan isMinifyEnabled = true, isShrinkResources = true, dan filter ABI arm64-v8a pada buildTypes.release.
- Tata Letak & Sinkronisasi:
  - Ikuti standar Material 3 edge-to-edge (WindowInsets.safeDrawing, CenterAlignedTopAppBar, lebar adaptif max 640dp).
  - [Pilih: Firestore / Google Drive JSON sync 2 arah via CredentialManager + ModalBottomSheet tanpa wajib login di awal / DataStore & Room khusus lokal]
- Desain UI Ringkas:
  - Tanpa menu berlapis atau tombol duplikat di setiap baris; gunakan mode pilih kontekstual (SelectionActionBar) + bilah pencarian cepat.
- Pengujian & Rilis Otomatis:
  - Sertakan Unit Test & Robolectric UI Test untuk alur inti.
  - Siapkan .github/workflows/release.yml untuk build APK signed ke GitHub Releases & Artifacts (tanpa menyimpan biner APK di repositori).
```

---

## Checklist Audit Sebelum Merilis Versi Baru

- [ ] **Ukuran APK < 10 MB** setelah minifikasi R8 dan resource shrinking.
- [ ] **Tidak ada file sensitif** (`*.jks`, `*.keystore`, `keystore.properties`, API key) yang ikut ter-commit ke Git.
- [ ] **Tidak ada folder biner APK** (`releases/`, `dist/`) yang tertinggal di repositori.
- [ ] **Unit Test & Robolectric lulus 100%** untuk logika bisnis dan UI terbaru.
- [ ] **Tanda tangan APK valid** (`apksigner verify`) dan SHA-1/SHA-256 rilis cocok dengan konfigurasi OAuth / Firebase.
- [ ] **GitHub Release & GitHub Pages** mencerminkan fitur dan catatan perubahan terbaru.
