# Panduan Personality & Aturan Build APK Android (< 10 MB)

**Kotlin + Jetpack Compose + Material 3 | MVVM | Rilis APK & Situs Publik via GitHub Actions**

### Daftar Dokumen di `panduan/`
1. `Panduan_Personality_Build_APK_(umum).md` — Aturan inti, alur kerja, template prompt, dan checklist rilis.
2. `Template_Source_Code_UI_Sync_(umum).md` — Contoh kode Kotlin siap salin (tema sistem, Beranda, sinkronisasi otomatis perangkat, mode pilih, & test).
3. `apk_Release_Setup_Guide_(umum).md` — Setup keystore, GitHub Secrets, `.gitignore`, mirror Maven anti-429, `gradle.properties`, Gradle R8, ProGuard, `.github/workflows/release.yml`, dan `.github/workflows/publish-site.yml`.
4. `Prompt_Generator_Kredensial_Rilis_(umum).md` — Prompt generator kredensial rilis permanen ke 1 file HTML pengunduh kontainer VeraCrypt (`.hc`) di root.
5. `Alur_Mindmap_Build_APK_(umum).json` — Ringkasan visual alur build.

---

## Aturan Wajib vs Dilarang

| Aspek | Wajib | Dilarang |
|---|---|---|
| **Penamaan & ID** | 1 kata asli Euskara 3 suku kata (4–8 huruf Latin tanpa diakritik) → `com.aistudio.<kata>`, ikon adaptif kustom. | Nama > 2 kata, diakritik, atau akhiran acak. |
| **Tema & UI Minimalis** | Otomatis mengikuti sistem (`isSystemInDarkTheme()`), edge-to-edge (`WindowInsets.safeDrawing`), target sentuh ≥ 48dp. Jika suatu keterangan sudah diwakili ikon yang jelas, cukup tampilkan ikon lebih besar dengan jarak renggang (tanpa label teks redundan); legenda arti ikon & penjelasan lengkap ditempatkan di situs publik. **Dimensi kartu/kotak wajib konsisten sejak awal** (tanpa blok teks kondisional di dalam kartu yang membuat kotak membesar/menyusut saat status berubah) dan **anti-duplikat keterangan** (pilih 1 kanal saja, mis. spinner di tombol + 1 Snackbar bawah). | Pemilih tema (Gelap/Terang), label teks berulang di samping ikon yang sudah jelas, paragraf penjelas panjang di aplikasi, kotak pemberitahuan `AnimatedVisibility` di dalam kartu yang membuat ukuran kotak berubah-ubah, atau menampilkan pesan status yang sama ganda di kartu dan Snackbar bersamaan. |
| **Prioritas Beranda** | **#1 Cadangan Perangkat** di paling atas, **#2 Web Panduan** (subjudul ramah non-teknis, mis. *Panduan & Arti Ikon*) di bawahnya, lalu daftar konten utama. | Menyembunyikan status cadangan atau panduan di menu berlapis, atau menampilkan istilah teknis seperti *GitHub Pages* di UI pengguna. |
| **Sinkronisasi** | **Sinkronisasi otomatis ke perangkat** ke 1 berkas tunggal konsisten di `/storage/emulated/0/Documents/<NamaApp>/<slug>_data.json`: **langsung menimpa berkas perangkat** setiap ada perubahan di aplikasi (agar hapus/ubah folder tidak kembali lagi) + **otomatis saat aplikasi dibuka** dengan **pengaman anti-timpa file kosong & prioritas entri terbanyak** (pulihkan penuh bila lokal kosong atau file perangkat lebih baru dengan entri ≥ lokal; gabung aman bila file perangkat lebih sedikit). | Google Drive/cloud login (kecuali aplikasi memang khusus sinkronisasi cloud), layanan latar belakang/WorkManager, jadwal periodik, salinan berkas ganda bertanggal, atau union folder buta yang menghidupkan lagi folder terhapus. |
| **Privasi & Ukuran APK** | **100% luring** untuk aplikasi offline (tanpa izin `INTERNET`/`ACCESS_NETWORK_STATE`, tanpa Firebase/Google Auth/OkHttp), `< 10 MB` (`isMinifyEnabled = true`, `isShrinkResources = true`, `abiFilters += "arm64-v8a"`), komentari/hapus dependensi tak terpakai. | Izin jaringan/SDK cloud tak terpakai, atau menyimpan `.apk`, `.aab`, biner cache, serta rahasia mentah (`.jks`, `.env`, `keystore.properties`) di Git. |
| **Kecepatan Build & Anti-429** | Aktifkan `org.gradle.parallel`, `org.gradle.caching`, `org.gradle.configuration-cache`, `android.nonTransitiveRClass`, dan `kotlin.compiler.execution.strategy=in-process` di `gradle.properties`; pasang mirror Google Maven Central sebelum `mavenCentral()` di `settings.gradle.kts`; gunakan cache & pre-fetch atomik JAR Robolectric di CI serta `configurations.create` (kompatibel Gradle 9). | Menaruh file biner cache di dalam folder proyek (cache harus berada di sistem container/CI), atau hanya mengandalkan `repo.maven.apache.org` tanpa mirror/cache di GitHub Actions. |
| **Dual-Repo Privat & Situs Publik** | Jaga repositori kode sumber tetap **Private**. Publikasikan Web Panduan (`docs/`) dan tautan unduh langsung APK (`<slug>-latest-arm64-v8a.apk`) secara otomatis ke repositori publik terpisah **`<slug>-site`** menggunakan Fine-grained PAT (`PUBLIC_DIST_TOKEN` khusus 1 repo publik, izin `Contents: Read and write`, autentikasi header sementara). Halaman publik wajib ramah HP & non-teknis (tanpa tautan *Semua Rilis & Checksum* atau jargon teknis), dan atur `retention-days: 7` pada artefak repo privat agar hemat kuota 500 MB. | Mengubah repo kode sumber ke Public hanya demi GitHub Pages, memakai Classic PAT seluruh akun, menaruh jargon teknis/tautan GitHub Releases di halaman situs publik, atau mencampur panduan ke `docs/`. |
| **Zona Folder** | `panduan/` = panduan & template; `docs/` = **khusus halaman situs publik (`<slug>-site`)**; root `<slug_app>_secrets_download.html` = 1 berkas HTML pengunduh kontainer `.hc` (**tidak disembunyikan di `.gitignore`**) agar langsung diunduh lewat browser lalu dihapus pengguna. | Mencampur panduan ke `docs/`, menaruh biner `.hc` mentah yang gagal diunduh editor, atau membiarkan rahasia mentah tidak terenkripsi masuk Git. |

---

## Alur Ringkas (Fase 1–5)

1. **Identitas & Skema Data:** Tentukan nama Euskara (`com.aistudio.<kata>`), arsitektur MVVM (`StateFlow`), dan format JSON seragam dengan `id`, `updatedAtMillis`, serta `deleted`.
2. **Penyimpanan & Sinkronisasi Perangkat:** Gunakan penyimpanan lokal (DataStore/Room) dengan 1 berkas tunggal di `/storage/emulated/0/Documents/<NamaApp>/`: **langsung timpa berkas perangkat** setiap ada perubahan di aplikasi (folder terhapus tidak kembali) dan **sinkron otomatis saat aplikasi dibuka** dengan pengaman anti-timpa file kosong/entri lebih sedikit.
3. **UI Ringkas, Luring & Cepat Dibuild:** Pangkas fitur opsional, teks redundan (utamakan ikon besar berjarak renggang + legenda di situs), izin jaringan, dan dependensi berat tak terpakai. Pastikan `gradle.properties` memakai parallel/build-cache/configuration-cache serta `settings.gradle.kts` memakai mirror Google Maven Central sebelum `mavenCentral()`.
4. **Pengujian & Build R8 Anti-429:** Gunakan cache lokal Robolectric offline-first (`length() > 0L`) + `actions/cache` & pre-fetch mirror di CI agar `gradle :app:testDebugUnitTest --no-daemon` lulus 100% tanpa HTTP 429 dan bebas peringatan Gradle 9, lalu verifikasi APK rilis R8 `arm64-v8a` dengan `apksigner`.
5. **Rilis Dual-Repo (Privat → `<slug>-site`):** Publikasikan APK & halaman panduan `docs/index.html` otomatis ke repo publik `<slug>-site` lewat `.github/workflows/release.yml` (serta `.github/workflows/publish-site.yml` khusus suntingan cepat `docs/**`). Halaman publik wajib ramah HP (`overflow-x: clip`, `min-width: 0`, `overflow-wrap: anywhere`), menampilkan tombol unduh langsung APK terbaru dan legenda ikon visual, tanpa keterangan teknis maupun tautan ke halaman rilis GitHub.

---

## Template Prompt Memulai Proyek Baru

```text
Buat aplikasi Android (Kotlin + Jetpack Compose + Material 3 + MVVM):
- Nama & applicationId: 1 kata Euskara 3 suku kata tanpa diakritik ([Kata]) | com.aistudio.[kata] + ikon adaptif kustom
- Fitur Inti:
  1. [Fitur 1]
  2. [Fitur 2]
  3. [Fitur 3]
- Standar UI & Ringan (< 10 MB):
  - Tema otomatis sistem (isSystemInDarkTheme(), tanpa pemilih tema).
  - UI kartu ringkas berbasis ikon besar & jarak antar ikon lega tanpa label teks redundan; ukuran setiap kotak/kartu wajib konsisten sejak awal tanpa teks kondisional yang membuat kotak membesar/menyusut, dan dilarang menampilkan keterangan status duplikat di dua tempat sekaligus (cukup 1 Snackbar bawah + spinner di tempat); legenda arti ikon dan panduan lengkap hanya di situs publik (docs/index.html).
  - Prioritas Beranda: #1 Cadangan Perangkat, #2 Web Panduan (subjudul non-teknis).
  - Sinkronisasi otomatis ke perangkat ke 1 berkas tunggal di /storage/emulated/0/Documents/[NamaApp]/ (langsung menimpa berkas perangkat saat ada perubahan data/folder di aplikasi agar folder terhapus tidak kembali, serta sinkron saat aplikasi dibuka dengan pengaman anti-timpa file kosong & perlindungan entri terbanyak; tanpa cloud/login/layanan latar belakang).
  - 100% luring: tanpa izin INTERNET/ACCESS_NETWORK_STATE dan tanpa dependensi Firebase/Google Auth/OkHttp; R8 + shrink + arm64-v8a pada release.
- Pengujian, Kecepatan Build & Rilis Dual-Repo:
  - Aktifkan optimasi cache/parallel/configuration-cache di gradle.properties dan tambahkan mirror Google Maven Central sebelum mavenCentral() di settings.gradle.kts.
  - Sertakan Unit Test & Robolectric UI Test dengan cache offline-first + pre-fetch mirror anti-429 di CI (kompatibel Gradle 9).
  - Siapkan distribusi Dual-Repo: repo kode utama tetap Private, publikasi otomatis docs/ dan APK langsung (<slug>-latest-arm64-v8a.apk) ke repo publik <slug>-site via secret PUBLIC_DIST_TOKEN + workflow ringan publish-site.yml + retention-days: 7 untuk artefak repo privat.
  - Simpan panduan di panduan/, jaga docs/ khusus situs publik (bebas horizontal overflow di HP, bebas istilah teknis & tanpa tautan halaman rilis GitHub), dan sediakan kontainer .hc dalam 1 file HTML di root (<slug_app>_secrets_download.html, tidak disembunyikan di .gitignore) agar mudah diunduh lewat browser lalu dihapus pengguna.
```

---

## Checklist Audit Rilis

- [ ] 100% luring (untuk aplikasi offline) tanpa izin `INTERNET`/`ACCESS_NETWORK_STATE` dan tanpa dependensi tak terpakai.
- [ ] APK rilis `< 10 MB` (R8 + shrink + `arm64-v8a`) dan lolos `apksigner verify`.
- [ ] Optimasi `gradle.properties` aktif, mirror Google Maven Central terpasang di `settings.gradle.kts`, serta Unit Test & Robolectric 100% lulus lokal dan di CI (cache + pre-fetch anti-429, tanpa peringatan Gradle 9).
- [ ] Tema otomatis sistem, prioritas Beranda `#1 Cadangan Perangkat` & `#2 Web Panduan`, kartu UI ringkas berbasis ikon dengan dimensi kotak konsisten sejak awal (tanpa perubahan ukuran dinamis) dan bebas keterangan status duplikat.
- [ ] Sinkronisasi perangkat memakai 1 berkas tunggal di `Documents/<NamaApp>/`: langsung menimpa saat edit/hapus di aplikasi (folder terhapus tidak kembali) dan dilindungi dari timpa file kosong/entri lebih sedikit.
- [ ] Distribusi Dual-Repo siap: repo sumber **Private**, secret `PUBLIC_DIST_TOKEN` hanya untuk repo publik `<slug>-site`, `retention-days: 7` aktif, dan workflow ringan `publish-site.yml` tersedia untuk pembaruan cepat `docs/`.
- [ ] Folder `docs/` khusus situs publik (ramah HP, memuat tombol unduh langsung APK & legenda ikon, tanpa jargon teknis atau tautan halaman rilis GitHub), `panduan/` berisi dokumentasi umum, `<slug_app>_secrets_download.html` di root sudah dihapus setelah `.hc` diunduh, dan tidak ada rahasia mentah (`.jks`, `.env`, `keystore.properties`) atau `.apk` di Git.
