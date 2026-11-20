# Lotura

**Aplikasi Android native ringan (< 10 MB) untuk sinkronisasi 2 arah multi-folder perangkat dengan Google Drive dan GitHub.**

- **Application ID:** `com.aistudio.lotura`
- **Teknologi:** Kotlin • Jetpack Compose • Material 3 (*Precision Slate*) • Room • Android Keystore AES-256-GCM • WorkManager • OkHttp
- **Edit File Bebas:** Seluruh berkas disimpan di folder nyata penyimpanan perangkat (`MANAGE_EXTERNAL_STORAGE`) dan dapat langsung dibuka/diedit menggunakan aplikasi pihak ketiga seperti **Pilcrow MD** (Markdown), **Google Docs** (`.docx`), maupun editor teks/kode/PDF favorit Anda.

---

## Fitur Utama

1. **Multi-Pasangan Folder Perangkat ↔ Google Drive (2 Arah Real-Time)**
   - Hubungkan banyak folder lokal (mis. `/storage/emulated/0/Documents/Catatan`, `/storage/emulated/0/Documents/Proyek`) ke folder Google Drive masing-masing.
   - Menggunakan **1 akun Google utama** (`AuthorizationClient`, scope penuh `https://www.googleapis.com/auth/drive` untuk pemakaian pribadi / OAuth mode *Testing*).
   - `SyncForegroundService` (`foregroundServiceType="dataSync"`) + `RecursiveFileObserver` mendeteksi perubahan lokal secara real-time saat Pilcrow atau Google Docs menyimpan file.

2. **Ekspor Otomatis Google Docs / Sheets / Slides**
   - File native Google Docs, Sheets, dan Slides di Google Drive otomatis diekspor saat sinkronisasi menjadi `.docx`, `.xlsx`, dan `.pptx` di perangkat dan repositori GitHub.
   - Suntingan lokal pada berkas tersebut otomatis diunggah balik ke berkas Google Drive yang sama.

3. **Multi-Sinkronisasi GitHub (1 Akun PAT + 1 Repo per Pasangan Folder)**
   - Dukung banyak akun GitHub dengan **Fine-grained Personal Access Token (PAT)** yang dienkripsi menggunakan **Android Keystore (AES-256-GCM)** dan disimpan terpisah di DataStore.
   - Setiap pasangan folder dapat ditautkan secara opsional ke 1 akun GitHub + `owner/repo` + `branch` + `subfolder`.
   - **Auto-Commit Terjadwal Berkala** melalui `WorkManager` (default tiap 15 menit, dapat diatur 15/30/60/180 menit atau dipicu manual) menggabungkan semua perubahan menjadi **1 commit atomik** melalui GitHub Git Data API.

4. **Keamanan Data, Kebijakan Hapus & Resolusi Konflik**
   - **Kebijakan Hapus per Pasangan Folder:** pilih **Dua Arah Penuh** (Drive → Trash, lokal/GitHub ikut hapus) atau **Mode Aman** (tidak pernah menghapus file secara otomatis).
   - **Konflik Tanpa Kehilangan Data:** versi dengan waktu modifikasi terbaru menang, sedangkan versi yang kalah otomatis diamankan sebagai `nama (konflik YYYYMMDD-HHmmss).ext`.
   - **Aturan Pengecualian (*Ignore Rules*):** mendukung pola glob (`.git/`, `*.tmp`, `*.swp`, `.DS_Store`, dll.) per pasangan.

---

## Panduan Setup Singkat

### 1. Setup OAuth Google Drive (Pemakaian Pribadi)
Ikuti langkah lengkap di [`docs/SETUP_GOOGLE_CLOUD.md`](docs/SETUP_GOOGLE_CLOUD.md) untuk:
- Mengaktifkan **Google Drive API** di Google Cloud Console.
- Membuat **OAuth 2.0 Client ID (Android)** dengan package `com.aistudio.lotura` dan sidik jari **SHA-1** keystore debug/release Anda.
- Menambahkan email Google Anda ke daftar **Test users** pada OAuth Consent Screen (*Testing mode*).

### 2. Setup Fine-grained PAT GitHub
1. Buka **GitHub Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token**.
2. Pilih repositori target (*Only select repositories*).
3. Pada **Repository permissions**, berikan **Contents: Read and write** dan **Metadata: Read-only**.
4. Salin token (`github_pat_...`) lalu masukkan di menu **Akun → Tambah Akun GitHub** di dalam Lotura.

### 3. Build Lokal & Rilis Otomatis GitHub Actions
Jalankan pengujian Unit & Robolectric:
```bash
gradle :app:testDebugUnitTest --no-daemon
```
Bangun APK R8 `arm64-v8a` ringan:
```bash
gradle :app:assembleRelease --no-daemon
```
Untuk rilis otomatis via `.github/workflows/release.yml`, tambahkan GitHub Secrets berikut:
- `RELEASE_KEYSTORE_BASE64`
- `STORE_PASSWORD`
- `KEY_PASSWORD`
- `KEY_ALIAS` (default `upload`)

---

## Batasan yang Perlu Diketahui
- **Distribusi Pribadi / Sideload APK:** Izin `MANAGE_EXTERNAL_STORAGE` dan scope penuh `https://www.googleapis.com/auth/drive` dirancang khusus untuk penggunaan pribadi (APK via GitHub Releases), bukan distribusi publik Google Play Store.
- **Batas Ukuran File GitHub:** Berkas lokal di atas **45 MB** otomatis dilewati saat commit ke GitHub (namun tetap disinkronkan penuh ke Google Drive) agar mematuhi batas GitHub REST API.
- **Penghemat Baterai OEM:** Pada beberapa perangkat Android (MIUI/HyperOS, ColorOS, OneUI), nonaktifkan pembatasan baterai untuk *Lotura* agar `SyncForegroundService` dan `WorkManager` berjalan konsisten di latar belakang.
