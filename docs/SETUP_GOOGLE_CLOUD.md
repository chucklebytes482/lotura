# Setup Google Cloud Console untuk Lotura

Lotura menggunakan **Google Play Services `AuthorizationClient`** langsung dengan **Google Drive REST API v3** (tanpa memerlukan Firebase atau file `google-services.json`). Anda cukup mendaftarkan **OAuth 2.0 Client ID (Android)** untuk `com.aistudio.lotura` di Google Cloud Console milik Anda sendiri.

---

## Langkah 1 — Buat Proyek & Aktifkan Google Drive API
1. Buka [Google Cloud Console](https://console.cloud.google.com/).
2. Buat proyek baru (mis. `Lotura Pribadi`).
3. Masuk ke **APIs & Services → Library**, cari **Google Drive API**, lalu klik **Enable**.

---

## Langkah 2 — Konfigurasi OAuth Consent Screen (Mode Testing)
1. Buka **Google Auth Platform → Branding / Audience** (atau **APIs & Services → OAuth consent screen**).
2. Pilih tipe **External** (untuk akun Gmail pribadi) dan biarkan status publikasi tetap **Testing** — *tidak perlu verifikasi Google*.
3. Isi nama aplikasi: `Lotura` dan email dukungan Anda.
4. Pada bagian **Data Access / Scopes**, tambahkan scope:
   - `https://www.googleapis.com/auth/drive`
5. Pada bagian **Audience / Test users**, klik **Add users** dan masukkan alamat Gmail pribadi Anda.

---

## Langkah 3 — Dapatkan SHA-1 Keystore (Debug & Release)

### Keystore Release Anda
```bash
keytool -list -v -keystore release-upload.jks -alias upload
```
Salin nilai **SHA1** (format `AA:BB:CC:...`).

### Keystore Debug (untuk uji lokal)
```bash
keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android
```
*(Jika memakai GitHub Actions release, Anda juga bisa menyalin SHA-1 langsung dari file `CERT_FINGERPRINT.txt` pada lampiran GitHub Release.)*

---

## Langkah 4 — Buat OAuth 2.0 Client ID (Android)
1. Buka **APIs & Services → Credentials → Create Credentials → OAuth client ID**.
2. Pilih **Application type: Android**.
3. Isi:
   - **Name:** `Lotura Android Release`
   - **Package name:** `com.aistudio.lotura`
   - **SHA-1 certificate fingerprint:** tempel SHA-1 dari Langkah 3.
4. Klik **Create**. *(Ulangi sekali lagi untuk SHA-1 debug jika Anda juga menguji build debug.)*

---

## Troubleshooting OAuth Android
| Pesan / Gejala | Penyebab | Solusi |
|---|---|---|
| `ApiException: 10 (DEVELOPER_ERROR)` | Package name atau SHA-1 tidak cocok dengan APK yang terpasang | Cocokkan SHA-1 dari `CERT_FINGERPRINT.txt` di GitHub Release dengan OAuth Client Android `com.aistudio.lotura`. |
| `Access blocked: has not completed Google verification` | Email Anda belum masuk daftar *Test users* | Tambahkan email Gmail Anda di **OAuth Consent Screen → Test users**. |
| `403 insufficientPermissions` | Scope Drive belum dicentang saat layar persetujuan | Putuskan akun di tab **Akun**, lalu klik **Hubungkan Google** lagi dan centang izin Google Drive. |
