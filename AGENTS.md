# AGENTS.md

Panduan umum (berlaku untuk semua repo) ada di root repo `plan`: https://github.com/chucklebytes482/plan

Baca dan ikuti berkas berikut, dalam urutan ini:
- `00-prinsip-kerja.md`
- `01-penamaan.md`
- `02-struktur-proyek.md`
- `03-build-dan-kualitas.md`
- `04-ui-tema.md`
- `06-rahasia-dan-rilis.md` (untuk keystore rilis)
- `08-checklist-app-baru.md`

Berkas `05-cadangan-dan-sinkron.md` tidak berlaku kecuali repo ini menambah sinkron data lokal. Berkas `07-format-vxenc.md` tidak berlaku untuk repo ini.

## Pengecualian untuk repo ini

Keputusan pemilik repo. Mengalahkan panduan di atas:

- **Online, bukan luring.** Fitur Google Drive dan GitHub tetap dipakai, jadi aplikasi memakai `INTERNET` dan `ACCESS_NETWORK_STATE`. Akses Drive lewat Google Sign-In (`GoogleAuthManager`, `DriveApiClient`). Akses GitHub lewat PAT (`GithubApiClient`). OkHttp dipakai untuk panggilan HTTP ini. Aturan luring di `02-struktur-proyek.md` bagian 6 tidak berlaku.
- **Tanpa tab Panduan.** Beranda hanya punya tab Google Drive dan GitHub. Kartu "Cadangan Perangkat" tidak dibuat.
- **Kartu web panduan** hanya tampil di tab Google Drive, di atas daftar akun (`ui/screens/GuideWebCard.kt`). Kartu ini membuka `https://chucklebytes482.github.io/lotura/` di browser luar, bukan di WebView.
- **Tanpa update APK.** Tidak ada pengecekan atau unduhan APK di dalam aplikasi.
- **Paket.** `namespace` tetap `com.example`. `applicationId` tetap `com.aistudio.lotura`. Tidak diganti, dan tidak perlu OAuth client baru. Aturan paket di `01-penamaan.md` bagian 6 (`com.aistudio.<nama>`) dipenuhi untuk `applicationId`; `namespace` adalah penyimpangan yang disengaja.
- **Logo dan ikon launcher tidak diubah.** Ikon sudah memakai busur empat warna Google. Huruf di tengah belum ada, itu perubahan terpisah.
- **Panduan pengguna** ada di `docs/index.html` (GitHub Pages). Tidak ada tautan ke repo privat dari sana.
