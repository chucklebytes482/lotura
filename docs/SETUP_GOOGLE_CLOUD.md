# Setup Google Cloud Console & Rilis — Lotura

Seluruh konfigurasi Google Cloud OAuth, kunci penandatanganan rilis permanen, dan GitHub Pages untuk **Lotura** (`com.aistudio.lotura`) telah selesai disiapkan. Dokumen ini berfungsi sebagai catatan referensi dan troubleshooting.

---

## Ringkasan Status & Identitas Proyek

| Komponen | Status | Nilai |
|---|---|---|
| **Google Cloud Project** | ✅ Selesai | `gen-lang-client-0224561583` (`1000578483438`) |
| **Package Name (`applicationId`)** | ✅ Selesai | `com.aistudio.lotura` *(namespace kode: `com.example`)* |
| **Google Drive API** | ✅ Aktif | `drive.googleapis.com` |
| **OAuth Brand & Scope Drive** | ✅ Selesai | Brand `Lotura` • Scope `https://www.googleapis.com/auth/drive` |
| **Audience / Test Users** | ✅ Selesai | Akun Gmail pemilik terdaftar |
| **Debug OAuth Client (Android)** | ✅ Selesai | `1000578483438-kr5lrgrhndrd4i838bqt7kmtnlsffcdj.apps.googleusercontent.com` |
| **Debug SHA-1** | ✅ Terdaftar | `06:E9:FF:98:CE:CF:2D:FC:62:4C:79:50:34:9B:2B:1A:6F:72:46:EC` |
| **Debug SHA-256** | ✅ Terdaftar | `F6:68:E2:A0:AF:12:5C:23:AC:1C:24:BE:74:61:AD:BB:0E:86:2A:27:77:80:D6:7C:A1:1C:35:32:D3:86:DE:88` |
| **Release SHA-1 (Permanen)** | ✅ Terdaftar | `1A:95:1D:E4:FB:08:58:63:FD:C5:39:AC:88:E3:15:5E:9D:D7:AB:8D` |
| **Release SHA-256 (Permanen)** | ✅ Terverifikasi | `64:22:47:D9:76:99:BF:DB:0E:E4:C1:93:69:BF:50:10:81:CA:3F:B4:24:DE:D7:1F:E6:39:97:89:20:19:94:8E` |
| **GitHub Signing Secrets** | ✅ Aktif | `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS=upload` |
| **GitHub Actions Release** | ✅ Aktif | `.github/workflows/release.yml` — otomatis saat `push` ke `main` bila ada perubahan berkas aplikasi (`app/**`, Gradle, `.env.example`) + manual via **Run workflow** |
| **`GOOGLE_SERVICES_JSON_BASE64`** | ℹ️ Tidak diperlukan | Lotura memakai `AuthorizationClient` + Drive REST API v3 langsung (tanpa Firebase SDK) |
| **Web Panduan (GitHub Pages)** | ✅ Aktif | `https://chucklebytes482.github.io/lotura/` |

---

## Tautan Langsung Google Cloud Console (Ramah HP)

> **Tips HP:** Aktifkan **Situs Desktop / Mode Desktop** di browser jika menu Google Cloud Console terpotong di layar kecil.

- **Google Drive API:** [Buka Library Drive API](https://console.cloud.google.com/apis/library/drive.googleapis.com?project=gen-lang-client-0224561583)
- **Audience / Test Users:** [Buka Audience](https://console.cloud.google.com/auth/audience?project=gen-lang-client-0224561583)
- **OAuth 2.0 Credentials:** [Buka Credentials](https://console.cloud.google.com/apis/credentials?project=gen-lang-client-0224561583)

---

## Troubleshooting OAuth Android

| Pesan / Gejala | Penyebab | Solusi |
|---|---|---|
| `ApiException: 10 (DEVELOPER_ERROR)` | Package name atau SHA-1 APK terpasang tidak cocok | Pastikan APK rilis ditandatangani kunci permanen (`SHA-1: 1A:95:...` / `SHA-256: 64:22:...`) dan cocok dengan OAuth Client Android `com.aistudio.lotura`. |
| `Access blocked: has not completed Google verification` | Email Gmail belum masuk daftar *Test users* | Tambahkan email Gmail di **Audience → Test users** pada proyek `gen-lang-client-0224561583`. |
| `403 accessNotConfigured` | Google Drive API belum aktif | Buka tautan **Library Drive API** di atas dan pastikan status **Enabled**. |
| `403 insufficientPermissions` | Izin Drive belum dicentang saat layar persetujuan | Putuskan akun di tab **Akun**, lalu hubungkan ulang dan centang izin Google Drive. |
