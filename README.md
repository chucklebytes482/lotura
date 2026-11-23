# Lotura

Aplikasi Android untuk sinkronisasi manual antara perangkat, Google Drive, dan GitHub. Pilih file, lalu unduh atau unggah. Tidak ada server perantara.

## Dokumen

- Panduan pengguna: [`panduan/pribadi/01_Panduan_Lotura.md`](panduan/pribadi/01_Panduan_Lotura.md)
- Panduan umum (berlaku untuk proyek Android lain):
  - [`02_Setup_Rilis_CI.md`](panduan/umum/02_Setup_Rilis_CI.md): APK rilis, Gradle R8, dan CI
  - [`03_Generator_Kredensial_VeraCrypt.md`](panduan/umum/03_Generator_Kredensial_VeraCrypt.md): kredensial rilis
  - [`04_Standar_UI_Tema_Google.md`](panduan/umum/04_Standar_UI_Tema_Google.md): tema Material 3 dan ikon adaptif
- Situs panduan dan unduhan: https://chucklebytes482.github.io/lotura/

## Build

Syarat: JDK 21 (sama dengan CI), Android SDK dengan platform 36, dan `local.properties` berisi `sdk.dir`.

```bash
gradle assembleDebug
gradle testDebugUnitTest
```

Repo ini tidak memakai Gradle wrapper. Pasang Gradle 9.x secara lokal.

Rilis dibuat oleh workflow `.github/workflows/release.yml` dan diunggah sebagai GitHub Release. Lihat `panduan/umum/02_Setup_Rilis_CI.md`.
