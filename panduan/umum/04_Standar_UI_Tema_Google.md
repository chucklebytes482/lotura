# 04 — Standar UI: Tema Google (Umum)

Acuan Android Material 3, palet Google, font Roboto. Ganti `<slug_app>` dan `<package>` sesuai proyek.

## 1. Prinsip

- Netral: latar putih atau abu-hitam. Aksen hanya untuk aksi dan status.
- Empat warna Google punya makna: biru aksi utama, hijau sukses, kuning menunggu, merah galat.
- Satu keluarga font: Roboto. Roboto Mono hanya untuk log dan nilai teknis.
- Wajib mode terang dan gelap (`isSystemInDarkTheme()`).

## 2. Token warna

Definisi di `app/src/main/java/<package>/ui/theme/Color.kt`.

| Token | Terang | Gelap | Pakai untuk |
|---|---|---|---|
| Google Blue | `#4285F4` | `#8AB4F8` | Aksi utama, tautan |
| Google Red | `#EA4335` | `#F28B82` | Galat, hapus |
| Google Yellow | `#FBBC05` | `#FBBC05` | Menunggu, pending |
| Google Green | `#34A853` | `#81C995` | Sukses, tersinkron |
| Background | `#FFFFFF` | `#202124` | Latar layar |
| Surface | `#FFFFFF` | `#292A2D` | Kartu, panel |
| Surface variant | `#F1F3F4` | `#3C4043` | Chip, input |
| Outline | `#DADCE0` | `#5F6368` | Garis, batas |
| Text | `#202124` | `#E8EAED` | Teks utama |
| Text secondary | `#5F6368` | `#9AA0A6` | Teks pendukung |

Kuning dan merah bukan dekorasi.

## 3. Skema Material 3

```kotlin
private val GoogleLightScheme = lightColorScheme(
    primary = GoogleBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FE),
    onPrimaryContainer = Color(0xFF0B57D0),
    secondary = GoogleGreen,
    tertiary = GoogleYellow,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF202124),
    surface = Color.White,
    onSurface = Color(0xFF202124),
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFFDADCE0),
    error = GoogleRed,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) GoogleDarkScheme else GoogleLightScheme
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
```

Warna khusus penyedia (Drive, GitHub) lewat `BrandVisualColors` (`accent`, `onAccent`, `container`, `onContainer`, `cardSurface`, `border`) via `CompositionLocal`. Jangan hardcode warna di layar.

## 4. Font dan tipografi

Berkas di `app/src/main/res/font/`: `roboto_regular.ttf`, `roboto_medium.ttf`, `roboto_bold.ttf`, `roboto_mono_regular.ttf`, `roboto_mono_medium.ttf`.

```kotlin
val RobotoFamily = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_medium, FontWeight.Medium),
    Font(R.font.roboto_bold, FontWeight.Bold)
)
val RobotoMonoFamily = FontFamily(
    Font(R.font.roboto_mono_regular, FontWeight.Normal),
    Font(R.font.roboto_mono_medium, FontWeight.Medium)
)

val AppTypography = Typography(
    headlineSmall = TextStyle(fontFamily = RobotoFamily, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = RobotoFamily, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = RobotoFamily, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = RobotoFamily, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = RobotoMonoFamily, fontSize = 11.sp, lineHeight = 16.sp)
)
```

Judul tidak bold penuh. Pakai `Normal` atau `Medium`.

## 5. Bentuk dan jarak

- Sudut: kartu 16dp, tombol pill atau 8dp, dialog 28dp.
- Kelipatan 4dp (8, 12, 16, 24). Margin layar 16dp.
- Kartu tanpa bayangan tebal. Cukup garis `outline` 1dp.

## 6. Pola komponen

- Tombol utama: satu per layar, `Button` primary.
- Sekunder: `OutlinedButton`. Hapus: teks warna `error`.
- Status: ikon plus warna plus label. Jangan hanya warna. Di daftar padat cukup ikon; teks lengkap ada di log sumber.
- Aksi berulang di daftar: ikon (download, unggah, muat ulang, hapus), bukan teks. Beri `contentDescription`.
- Nilai teknis (path, ID, angka): `labelSmall` (Roboto Mono) dalam `SelectionContainer`.

## 7. Ikon launcher adaptif

Tiga berkas di `res/`: latar, depan, monokrom. Dirujuk `mipmap-anydpi-v26/ic_launcher.xml`.

```xml
<!-- res/mipmap-anydpi-v26/ic_launcher.xml -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

Kanvas 108dp, area aman 72dp di tengah. Di luar itu terpotong pada lingkaran atau squircle.
PNG lama (`mipmap-*dpi/`): render dari SVG dengan `rsvg-convert`, ukuran 48, 72, 96, 144, 192 px.

## 8. Checklist rilis

- [ ] Mode terang dan gelap terbaca.
- [ ] Tidak ada warna hardcode di layar.
- [ ] `res/font/` hanya Roboto dan Roboto Mono.
- [ ] Tidak ada berkas sisa generator.
- [ ] Ikon adaptif benar di lingkaran dan squircle.
- [ ] Font scale 200% tidak memotong teks.
- [ ] Unit test lolos (`gradle testDebugUnitTest` bila tanpa wrapper).

## 9. Tataletak tab

- Tiga tab tetap: Google Drive, GitHub, Panduan. Geser kiri atau kanan, atau ketuk nama tab.
- Judul sumber (akun Drive atau kartu repo) membuka log sumber itu di lembar bawah.
- Ikon aksi (↓, ↑, muat ulang, hapus, batal) berdiri sendiri dan tidak membuka log.
- Jarak layar 16 dp, jarak antarkartu 10 dp, sudut kartu 10 sampai 12 dp.
- Teks isi 14 sp, keterangan 11 sampai 12 sp. Status sync ditampilkan di kartu sumbernya.
