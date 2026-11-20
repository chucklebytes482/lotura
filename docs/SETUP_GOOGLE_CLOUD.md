# Setup Google Cloud Console untuk Lotura

Lotura menggunakan **Google Play Services `AuthorizationClient`** langsung dengan **Google Drive REST API v3** pada proyek Google Cloud **`gen-lang-client-0224561583`**.

---

## Status Otomatisasi (Sudah Dikerjakan)

Bagian berikut **sudah otomatis dikonfigurasi** di Google Cloud Console untuk proyek Anda:

| Komponen | Status | Nilai Terdaftar |
|---|---|---|
| **Google Cloud Project** | ✅ Selesai | `gen-lang-client-0224561583` (`1000578483438`) |
| **OAuth Brand & Scope Drive** | ✅ Selesai | Brand `Lotura` • Scope `https://www.googleapis.com/auth/drive` |
| **Android OAuth 2.0 Client (Debug)** | ✅ Selesai | `1000578483438-kr5lrgrhndrd4i838bqt7kmtnlsffcdj.apps.googleusercontent.com` |
| **Package Name** | ✅ Selesai | `com.aistudio.lotura` |
| **Debug SHA-1 Terdaftar** | ✅ Selesai | `06:E9:FF:98:CE:CF:2D:FC:62:4C:79:50:34:9B:2B:1A:6F:72:46:EC` |
| **Debug SHA-256 Terdaftar** | ✅ Selesai | `F6:68:E2:A0:AF:12:5C:23:AC:1C:24:BE:74:61:AD:BB:0E:86:2A:27:77:80:D6:7C:A1:1C:35:32:D3:86:DE:88` |

> **Artinya:** Build **Debug / Preview** sudah memiliki OAuth Client Android + SHA-1/SHA-256 terdaftar otomatis.

---

## Sisa Langkah Singkat (Hanya dari Akun / Komputer Anda)

### 1. Pastikan Google Drive API Aktif & Email Masuk Test Users
- **Aktifkan Google Drive API (1 klik):**
  [Buka Google Drive API — gen-lang-client-0224561583](https://console.cloud.google.com/apis/library/drive.googleapis.com?project=gen-lang-client-0224561583) → klik **Enable** *(atau jalankan `docs/setup_google_cloud.sh`)*.
- **Tambahkan Test User (mode Testing):**
  [Buka Audience / Test Users — gen-lang-client-0224561583](https://console.cloud.google.com/auth/audience?project=gen-lang-client-0224561583) → pastikan alamat Gmail pribadi Anda terdaftar di **Test users**.

---

### 2. Daftarkan SHA-1 Keystore Release (Khusus APK Release / GitHub Releases)
Keystore rilis (`.jks`) bersifat pribadi dan tidak pernah disimpan di repositori. Jalankan skrip pembantu di komputer lokal Anda untuk membuat keystore rilis (jika belum ada), mencetak **SHA-1 / SHA-256**, dan mengaktifkan Drive API otomatis:

```bash
bash docs/setup_google_cloud.sh
```

Lalu daftarkan **SHA-1 Release** (atau salin dari lampiran `CERT_FINGERPRINT.txt` di GitHub Release) ke:
- [Buat OAuth Client Android Release — gen-lang-client-0224561583](https://console.cloud.google.com/apis/credentials/oauthclient?project=gen-lang-client-0224561583)
  - **Application type:** `Android`
  - **Name:** `Lotura Android Release`
  - **Package name:** `com.aistudio.lotura`
  - **SHA-1 certificate fingerprint:** tempel SHA-1 release dari output skrip / `CERT_FINGERPRINT.txt`.

---

## Troubleshooting OAuth Android
| Pesan / Gejala | Penyebab | Solusi |
|---|---|---|
| `ApiException: 10 (DEVELOPER_ERROR)` | Package name atau SHA-1 tidak cocok dengan APK yang terpasang | Pastikan OAuth Client Android `com.aistudio.lotura` memuat SHA-1 debug (`06:E9:...`) dan SHA-1 release dari `CERT_FINGERPRINT.txt`. |
| `Access blocked: has not completed Google verification` | Email Anda belum masuk daftar *Test users* | Tambahkan email Gmail Anda di **Audience → Test users** pada proyek `gen-lang-client-0224561583`. |
| `403 accessNotConfigured` / `Drive API has not been used` | Google Drive API belum diaktifkan di proyek | Klik tautan **Enable Google Drive API** di atas atau jalankan `gcloud services enable drive.googleapis.com --project=gen-lang-client-0224561583`. |
| `403 insufficientPermissions` | Scope Drive belum dicentang saat layar persetujuan | Putuskan akun di tab **Akun**, lalu klik **Hubungkan Google** lagi dan centang izin Google Drive. |
