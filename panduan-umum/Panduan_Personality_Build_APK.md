# Panduan Personality & Aturan Wajib Build APK Android (< 10 MB)

**Kotlin + Jetpack Compose + Material 3 | Arsitektur MVVM | Distribusi APK GitHub Release**

Dokumen ini adalah panduan inti (*personality* & aturan wajib) untuk membangun aplikasi Android ringan (**< 10 MB**) secara konsisten lintas proyek.

### Paket 4 Dokumen Panduan
1. **`Panduan_Personality_Build_APK.md`** *(dokumen ini)* — Prinsip wajib, penamaan Euskara, alur Fase 1–5, templat PRD, dan checklist rilis.
2. **`Template_Source_Code_UI_Sync.md`** — Kumpulan source code Kotlin siap salin (tema otomatis sistem, urutan prioritas Beranda, modal sinkronisasi terjadwal, auth, Drive JSON sync, selection bar, & Robolectric test).
3. **`apk_Release_Setup_Guide.md`** — Setup keystore release, GitHub Secrets, `.gitignore`, Gradle R8, ProGuard, SHA-1/SHA-256, dan workflow GitHub Actions.
4. **`Alur_Mindmap_Build_APK.json`** — Peta visual ringkas seluruh alur pembuatan hingga rilis APK.

---

## Ringkasan Aturan Wajib vs Dilarang

| Aspek | Wajib Diterapkan | Dilarang di Dalam Aplikasi |
|---|---|---|
| **Tema** | Otomatis mengikuti sistem (`isSystemInDarkTheme()`). | Menu/opsi pemilihan tema (*Gelap / Terang / Sistem*) atau penyimpanan preferensi tema di DataStore. |
| **Prioritas Beranda** | **#1 Sinkronisasi** di paling atas, **#2 Web Panduan (GitHub Pages)** tepat di bawahnya. | Menyembunyikan sinkronisasi atau panduan di menu berlapis. |
| **Keterangan & Panduan** | Label singkat dan data inti saja di UI aplikasi; **seluruh keterangan rinci hanya di GitHub Pages** (`docs/index.html`). | Paragraf penjelas, instruksi teknis OAuth/PAT, atau subteks repetitif di dalam layar aplikasi. |
| **Sinkronisasi Drive** | Status ringkas **1 baris** (`Tersinkronisasi • 14 jam lalu`) + pemilih jadwal **`5 menit`**, **`15 menit` (default)**, **`4 jam`**, **`24 jam`**. | Sakelar on/off *Sinkronisasi Otomatis*, status jaringan/antrean mentah, atau nama folder berlebih di kartu status. |
| **Penamaan & ID** | **1 kata asli Euskara** 3 suku kata (4–8 huruf Latin tanpa diakritik) → `com.aistudio.<kata>`. | Nama panjang > 2 kata, karakter diakritik, atau akhiran acak tanpa makna. |
| **Ukuran & Rilis APK** | R8 + resource shrinking + ABI `arm64-v8a` (**< 10 MB**), publikasi via GitHub Actions Releases. | Menyimpan `.apk`, `.aab`, `.jks`, atau API key di repositori Git. |
| **Otomasi Teknis (Ramah HP)** | AI **otomatiskan & kerjakan semua hal teknis** yang bisa dieksekusi (OAuth, Firebase, SHA-1/SHA-256 debug, Gradle R8, CI/CD, skrip, test) + beri **deep link langsung** untuk klik wajib akun pribadi. | Menyuruh pengguna menjalankan perintah teknis manual atau mencari menu Google Cloud Console di layar HP yang sempit (harus Mode Desktop). |

---

## Prinsip Utama (Wajib Diterapkan)

1. **Fokus Fitur Inti & Hapus Fitur Opsional**
   - Bangun hanya fitur yang diminta; hapus fitur opsional (termasuk pemilih tema), aset media berat, SDK iklan/analitik tak perlu, dan dependensi Gradle yang tidak dipakai.
2. **Urutan Prioritas Beranda & Dokumentasi Terpusat di GitHub Pages**
   - **Prioritas #1:** Kartu **Sinkronisasi** (paling atas).
   - **Prioritas #2:** Kartu **Web Panduan (GitHub Pages)** tepat di bawah Sinkronisasi.
   - Semua penjelasan detail, batasan teknis, dan panduan penggunaan **hanya boleh ditempatkan di GitHub Pages** (`docs/index.html`).
3. **Target Ukuran APK Rilis < 10 MB**
   - Aktifkan `isMinifyEnabled = true`, `isShrinkResources = true`, dan `abiFilters += "arm64-v8a"` pada `buildTypes.release` (biarkan `debug` multi-ABI).
4. **Antarmuka Ringkas & Aksesibel**
   - Hindari tombol duplikat per baris; gunakan gestur langsung (*tap* untuk buka/edit, tekan tahan untuk mode pilih banyak via `SelectionActionBar`) serta bilah pencarian cepat di atas daftar.
   - Pastikan seluruh target sentuh minimal **48dp × 48dp** dengan `contentDescription` yang jelas.
5. **Keamanan & Tanpa Data Palsu**
   - Jangan pernah menanamkan API key, keystore, atau kredensial di kode/Git. Gunakan secrets lingkungan atau Google Sign-In resmi.
6. **Otomasi Teknis Penuh & Alur Ramah HP (*Mobile-First Developer*)**
   - Asumsikan pengguna bisa bekerja dari **HP**, di mana tampilan **Google Cloud Console** sulit dinavigasi tanpa mengaktifkan **Mode Desktop**.
   - **Wajib dikerjakan otomatis oleh AI:** provisioning OAuth & Firebase, pendaftaran `applicationId` + SHA-1/SHA-256 debug, pembacaan sidik jari keystore debug, konfigurasi Gradle R8/ProGuard, pembuatan workflow GitHub Actions, GitHub Pages, skrip otomasi CLI (`.sh`), kompilasi, dan Unit/Robolectric Test.
   - **Khusus langkah yang wajib diklik dari akun pribadi** *(mis. tombol Enable API, menambah Test users, atau mendaftarkan SHA-1 keystore release)*: AI **wajib** menyediakan **tautan langsung (*deep link*)** yang sudah membawa `?project=<project_id>`, daftar nilai siap salin (*package name, SHA-1, SHA-256, nama secret*), serta pengingat singkat untuk mengaktifkan **Situs/Mode Desktop** di browser HP hanya jika tampilan konsol terpotong.

---

## Fase 1 — Konsep, Identitas (Euskara) & Skema Data

1. **Identitas & Penamaan Aplikasi Unik (Bahasa Euskara)**
   - Gunakan **1 kata asli Euskara (Basque)**, **3 suku kata** (4–8 huruf Latin tanpa diakritik, akhiran vokal terbuka `-a`/`-i`/`-u`), ikon adaptif kustom (`res/mipmap-*`), dan `applicationId`: `com.aistudio.<kata>` *(contoh: `Lotura` → `com.aistudio.lotura`, `Teklatu` → `com.aistudio.teklatu`, `Arina` → `com.aistudio.arina`)*.
   - **Prompt AI singkat:**
     ```text
     Carikan 10 kata asli Euskara (Basque) untuk aplikasi [fungsi].
     Syarat: 1 kata, tepat 3 suku kata (seperti Lo-tu-ra), huruf Latin tanpa diakritik.
     Format: Kata | Pemenggalan | Arti | com.aistudio.[kata]
     ```
   - **Checklist ganti `applicationId`:** perbarui `app/build.gradle.kts` & `proguard-rules.pro`, lalu daftarkan `applicationId` baru + **SHA-1 & SHA-256** (debug & release) di Firebase / Google Cloud Console dan perbarui `google-services.json`.
2. **Arsitektur Modern**
   - Gunakan **Kotlin** + **Jetpack Compose** + **Material Design 3** (`enableEdgeToEdge()`, `WindowInsets.safeDrawing`, tema otomatis sistem), serta **MVVM** (`ViewModel`, `StateFlow` / `collectAsStateWithLifecycle`).
3. **Skema Data Bersih & Seragam**
   - Gunakan field konsisten pada JSON / Room / DataStore lengkap dengan `id`, `updatedAtMillis`, dan penanda `deleted` (*soft-delete*) untuk sinkronisasi dua arah.

---

## Fase 2 — Penyimpanan & Sinkronisasi Cloud (Metode Ganda)

### A. Sinkronisasi Cloud Terjadwal (Opsional / Akun Google)
- Gunakan **Google Sign-In** via Jetpack `CredentialManager` (`GetSignInWithGoogleOption`) atau `AuthorizationClient` (tanpa login anonim).
- Gunakan scope sesuai kebutuhan (`https://www.googleapis.com/auth/drive.file`, atau `https://www.googleapis.com/auth/drive` khusus pemakaian pribadi mode *Testing*).
- Daftarkan **SHA-1 & SHA-256** (debug & release) ke Firebase / Google Cloud Console.
- Terapkan **sinkronisasi 2 arah terjadwal** dengan pilihan **`5 menit`**, **`15 menit` (default)**, **`4 jam`**, **`24 jam`** — tanpa sakelar on/off *Sinkronisasi Otomatis*. Default **15 menit** menjaga keseimbangan responsivitas dan baterai agar pengguna tidak keliru memilih **5 menit**.
- Kartu status sinkronisasi cukup **1 baris ringkas** (contoh: `Tersinkronisasi • 14 jam lalu`).

### B. Mode Tanpa Login / Offline-First
- Aplikasi wajib langsung dapat digunakan tanpa paksaan login di layar pertama.
- Gunakan **DataStore** atau **Room** untuk penyimpanan lokal; gunakan **Storage Access Framework** (`CreateDocument` / `OpenDocument`) untuk ekspor/impor lokal bila tidak membutuhkan izin akses semua file.

---

## Fase 3 — Kurasi Fitur, Pemangkasan UI & Efisiensi Memori

1. **Audit Dependensi:** Hapus library tak terpakai dari `gradle/libs.versions.toml` dan `app/build.gradle.kts`.
2. **UI Minimalis:** Tanpa pemilih tema, tanpa paragraf instruksi di dalam aplikasi, tanpa tab kosong, dan tanpa tombol aksi berulang di setiap item.
3. **Performa:** Gunakan `remember`, `derivedStateOf`, dan `Dispatchers.IO` agar main thread bebas dari operasi I/O.

---

## Fase 4 — Pengujian Otomatis, R8 & Build Release

1. Jalankan **Unit Test** (logika bisnis/merge) dan **Robolectric UI Test** (layar Compose, prioritas Beranda, jadwal sinkronisasi, dan ketiadaan pemilih tema):
   ```bash
   gradle :app:testDebugUnitTest --no-daemon
   ```
2. Aktifkan konfigurasi rilis R8 + shrink + `arm64-v8a` (lihat rincian kode di `apk_Release_Setup_Guide.md`).
3. **Target Lolos Rilis:**
   - [ ] Semua Unit Test & Robolectric Test lulus (0 gagal).
   - [ ] Ukuran APK release **< 10 MB** dengan ABI `arm64-v8a`.
   - [ ] Lolos `apksigner verify --print-certs` dan SHA-1/SHA-256 cocok dengan OAuth / Firebase.

---

## Fase 5 — Otomasi GitHub Release & GitHub Pages

1. **Tanpa Biner di Repo:** Jangan commit `.apk`, `.aab`, `.jks`, atau folder `releases/` / `dist/` ke Git.
2. **GitHub Actions Release:** Bangun, uji, verifikasi tanda tangan, dan unggah APK + `CHECKSUMS.txt` + `CERT_FINGERPRINT.txt` otomatis ke **GitHub Releases** (lihat `.github/workflows/release.yml` di `apk_Release_Setup_Guide.md`).
3. **GitHub Pages (`docs/index.html`):** Pusat seluruh keterangan rinci, panduan setup, penjelasan jadwal/kebijakan, dan tombol unduh `../releases/latest`.

---

## Template Prompt / PRD Memulai Aplikasi Android Baru

Salin dan isi bagian `[...]` saat memulai proyek baru (lampirkan bersama `Template_Source_Code_UI_Sync.md`):

```text
Buat aplikasi Android (Kotlin + Jetpack Compose + Material 3 + MVVM):
- Nama Aplikasi & applicationId: 1 kata Euskara 3 suku kata tanpa diakritik ([Kata]) | com.aistudio.[kata] + ikon adaptif kustom
- Fitur Inti Wajib:
  1. [Fitur utama 1]
  2. [Fitur utama 2]
  3. [Fitur utama 3]
- Aturan UI & Ringan (< 10 MB):
  - Wajib tanpa pemilih tema (otomatis ikut sistem via isSystemInDarkTheme()).
  - Dilarang menaruh keterangan/paragraf berlebih di dalam aplikasi; seluruh penjelasan rinci hanya di GitHub Pages (docs/index.html).
  - Nonaktifkan dependensi tak terpakai; aktifkan isMinifyEnabled = true, isShrinkResources = true, & ABI arm64-v8a pada release.
- Urutan Prioritas Beranda & Sinkronisasi:
  - Prioritas #1 (paling atas): Sinkronisasi Google Drive terjadwal (5 menit / 15 menit [default] / 4 jam / 24 jam) lewat ModalBottomSheet tanpa wajib login di awal, kartu status 1 baris ringkas (mis. "Tersinkronisasi • 14 jam lalu"), tanpa sakelar on/off auto-sync.
  - Prioritas #2 (tepat di bawah Sinkronisasi): Web Panduan (GitHub Pages) yang membuka dokumentasi lengkap di browser.
  - Edge-to-edge Material 3 (WindowInsets.safeDrawing, CenterAlignedTopAppBar, lebar adaptif max 640dp, SelectionActionBar, CompactSearchBar).
- Otomasi Teknis (Ramah HP):
  - Kerjakan dan otomatiskan semua hal teknis yang bisa dilakukan AI (OAuth, Firebase, ekstraksi & pendaftaran SHA-1/SHA-256 debug, Gradle R8, GitHub Actions, skrip CLI, kompilasi, & pengujian).
  - Jika ada langkah yang wajib dilakukan dari akun pribadi di Google Cloud Console, berikan tautan langsung (deep link berisi project_id) + nilai siap salin agar mudah diakses di HP tanpa repot mencari menu di Mode Desktop.
- Pengujian & Rilis:
  - Sertakan Unit Test & Robolectric UI Test.
  - Siapkan .github/workflows/release.yml untuk rilis APK signed ke GitHub Releases tanpa menyimpan APK di repositori.
```

---

## Checklist Audit Sebelum Rilis

- [ ] **Ukuran APK < 10 MB** (R8 + shrink + `arm64-v8a`).
- [ ] **Prioritas Beranda Wajib:** #1 Sinkronisasi, #2 Web Panduan (GitHub Pages).
- [ ] **Tanpa Pemilih Tema & Tanpa Keterangan Berlebih** di aplikasi (seluruh panduan rinci di GitHub Pages).
- [ ] **Penamaan Euskara** konsisten di `app_name`, `applicationId`, `proguard-rules.pro`, dan OAuth/Firebase.
- [ ] **Bebas file sensitif & biner APK** di repositori Git.
- [ ] **Otomasi Teknis Ramah HP:** seluruh setup teknis (OAuth/Firebase/SHA-1 debug/CI-CD) sudah dieksekusi AI; sisa langkah manual memakai *deep link* langsung + nilai siap salin.
- [ ] **Unit Test & Robolectric 100% lulus** + tanda tangan `apksigner` valid.
