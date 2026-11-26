# AGENTS.md

Panduan umum (berlaku untuk semua repo) ada di repo `encrypt`, folder `panduan/`:
https://github.com/chucklebytes482/encrypt/tree/main/panduan

Baca dan ikuti berkas berikut, dalam urutan ini:
- `00-prinsip-kerja-ai.md`
- `01-build-ui-sync.md`
- `02-standar-ui-tema-google.md`

Berkas lain di sana (`03-format-vxenc.md`, `04-kredensial-veracrypt.md`) tidak berlaku untuk repo ini.

## Pengecualian untuk repo ini

Keputusan pemilik repo. Mengalahkan panduan di atas:

- **Online, bukan luring.** Fitur Google Drive dan GitHub tetap dipakai, jadi aplikasi memakai `INTERNET` dan `ACCESS_NETWORK_STATE`. Akses Drive lewat Google Sign-In (`GoogleAuthManager`, `DriveApiClient`). Akses GitHub lewat PAT (`GithubApiClient`). OkHttp dipakai untuk panggilan HTTP ini dan unduh APK.
- **Tanpa tab Panduan.** Beranda hanya punya tab Google Drive dan GitHub. Kartu "Cadangan Perangkat" dan "Web Panduan" tidak dibuat.
- **Kartu pembaruan APK** tampil di atas beranda (`ui/update/ApkUpdateSection.kt`).
- **Paket.** `namespace` tetap `com.example`. `applicationId` tetap `com.aistudio.lotura`. Tidak diganti, dan tidak perlu OAuth client baru. Panduan 01 meminta `com.aistudio.<kata>` untuk paket; penyimpangan ini disengaja.
- **Logo dan ikon launcher tidak diubah.** Ikon sudah memakai busur empat warna Google. Huruf di tengah belum ada, itu perubahan terpisah.
- **Panduan pengguna** ada di `docs/index.html` (GitHub Pages). Tidak ada tautan ke repo privat dari sana.
