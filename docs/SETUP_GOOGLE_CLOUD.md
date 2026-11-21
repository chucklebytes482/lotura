# Setup Google Cloud Console untuk Lotura

Lotura menggunakan **Google Play Services `AuthorizationClient`** langsung dengan **Google Drive REST API v3** pada proyek Google Cloud **`gen-lang-client-0224561583`**.

---

## Ringkasan Identitas & Sidik Jari Sertifikat (Siap Salin)

| Komponen | Status | Nilai |
|---|---|---|
| **Google Cloud Project** | ✅ Terhubung | `gen-lang-client-0224561583` (`1000578483438`) |
| **Package Name (`applicationId`)** | ✅ Final | `com.aistudio.lotura` *(bukan `com.example`)* |
| **OAuth Brand & Scope Drive** | ✅ Dikonfigurasi | Brand `Lotura` • Scope `https://www.googleapis.com/auth/drive` |
| **Debug OAuth Client (Android)** | ✅ Otomatis terdaftar | `1000578483438-kr5lrgrhndrd4i838bqt7kmtnlsffcdj.apps.googleusercontent.com` |
| **Debug SHA-1** | ✅ Terdaftar | `06:E9:FF:98:CE:CF:2D:FC:62:4C:79:50:34:9B:2B:1A:6F:72:46:EC` |
| **Debug SHA-256** | ✅ Terdaftar | `F6:68:E2:A0:AF:12:5C:23:AC:1C:24:BE:74:61:AD:BB:0E:86:2A:27:77:80:D6:7C:A1:1C:35:32:D3:86:DE:88` |
| **Release SHA-1 (Permanen)** | 🔑 Daftarkan di Console | `1A:95:1D:E4:FB:08:58:63:FD:C5:39:AC:88:E3:15:5E:9D:D7:AB:8D` |
| **Release SHA-256 (Permanen)** | ✅ Diverifikasi di CI | `64:22:47:D9:76:99:BF:DB:0E:E4:C1:93:69:BF:50:10:81:CA:3F:B4:24:DE:D7:1F:E6:39:97:89:20:19:94:8E` |
| **GitHub Signing Secrets** | ✅ Aktif (*fail-closed*) | `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS=upload` |

---

## Langkah Singkat di Google Cloud Console (Ramah HP)

> **Tips HP:** Jika menu atau tombol tidak tampil utuh di layar HP, aktifkan **Situs Desktop / Mode Desktop** pada browser Anda.

### 1. Aktifkan Google Drive API
- **1 Klik:** [Buka Google Drive API — gen-lang-client-0224561583](https://console.cloud.google.com/apis/library/drive.googleapis.com?project=gen-lang-client-0224561583) → klik **Enable** jika belum aktif.

### 2. Pastikan Gmail Pribadi Masuk Test Users
- **1 Klik:** [Buka Audience / Test Users — gen-lang-client-0224561583](https://console.cloud.google.com/auth/audience?project=gen-lang-client-0224561583) → jika status masih **Testing**, tambahkan email Gmail yang Anda pakai login di aplikasi pada bagian **Test users**.

### 3. Daftarkan OAuth Client Android Release (Permanen)
- **1 Klik:** [Buat OAuth Client Android — gen-lang-client-0224561583](https://console.cloud.google.com/apis/credentials/oauthclient?project=gen-lang-client-0224561583)
- Isi persis nilai berikut:
  - **Application type:** `Android`
  - **Name:** `Lotura Android Release`
  - **Package name:** `com.aistudio.lotura`
  - **SHA-1 certificate fingerprint:**
    ```text
    1A:95:1D:E4:FB:08:58:63:FD:C5:39:AC:88:E3:15:5E:9D:D7:AB:8D
    ```
- Klik **Create**. *(Jangan menempel SHA-256 pada kolom SHA-1.)*

---

## Verifikasi Build Release
Workflow `.github/workflows/release.yml` berjalan dalam mode **fail-closed** dengan kunci rilis permanen dan otomatis memverifikasi bahwa setiap APK rilis cocok dengan **Release SHA-256** (`64:22:47:D9:...`).

---

## Troubleshooting OAuth Android
| Pesan / Gejala | Penyebab | Solusi |
|---|---|---|
| `ApiException: 10 (DEVELOPER_ERROR)` | Package name atau SHA-1 APK terpasang belum terdaftar | Pastikan OAuth Client Android `com.aistudio.lotura` memiliki **Release SHA-1** `1A:95:1D:E4:FB:08:58:63:FD:C5:39:AC:88:E3:15:5E:9D:D7:AB:8D`. |
| `Access blocked: has not completed Google verification` | Email Gmail belum masuk daftar *Test users* | Tambahkan email Gmail Anda di **Audience → Test users** pada proyek `gen-lang-client-0224561583`. |
| `403 accessNotConfigured` | Google Drive API belum aktif | Klik tautan **Enable Google Drive API** pada Langkah 1 di atas. |
| `403 insufficientPermissions` | Izin Drive belum dicentang saat layar persetujuan | Putuskan akun di tab **Akun**, lalu hubungkan ulang dan centang akses Google Drive. |
