# Lotura

Aplikasi Android untuk sinkronisasi manual antara perangkat, Google Drive, dan GitHub. Pilih file, lalu unduh atau unggah. Tidak ada server perantara.

## Dokumen

- Panduan umum (berlaku untuk proyek Android lain):
- Situs panduan dan unduhan: https://chucklebytes482.github.io/lotura/

## Build

Syarat: JDK 21 (sama dengan CI), Android SDK dengan platform 36, dan `local.properties` berisi `sdk.dir`.

```bash
gradle assembleDebug
gradle testDebugUnitTest
```

Repo ini tidak memakai Gradle wrapper. Pasang Gradle 9.x secara lokal.

Rilis dibuat oleh workflow `.github/workflows/release.yml` dan diunggah sebagai GitHub Release.
