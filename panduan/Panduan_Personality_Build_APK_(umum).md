# Panduan Personality & Aturan Build APK Android (< 10 MB)

**Kotlin + Jetpack Compose + Material 3 | MVVM | Rilis APK via GitHub Actions**

### Daftar Dokumen di `panduan/`
1. `Panduan_Personality_Build_APK_(umum).md` — Aturan inti, alur kerja, template prompt, dan checklist rilis.
2. `Template_Source_Code_UI_Sync_(umum).md` — Contoh kode Kotlin siap salin (tema sistem, Beranda, sinkronisasi otomatis perangkat, mode pilih, & test).
3. `apk_Release_Setup_Guide_(umum).md` — Setup keystore, GitHub Secrets, `.gitignore`, Gradle R8, ProGuard, dan `.github/workflows/release.yml`.
4. `Prompt_Generator_Kredensial_Rilis_(umum).md` — Prompt generator kredensial rilis permanen ke 1 file HTML pengunduh kontainer VeraCrypt (`.hc`) di root.
5. `Alur_Mindmap_Build_APK_(umum).json` — Ringkasan visual alur build.

---

## Aturan Wajib vs Dilarang

| Aspek | Wajib | Dilarang |
|---|---|---|
| **Penamaan & ID** | 1 kata asli Euskara 3 suku kata (4–8 huruf Latin tanpa diakritik) → `com.aistudio.<kata>`, ikon adaptif kustom. | Nama > 2 kata, diakritik, atau akhiran acak. |
| **Tema & UI** | Otomatis mengikuti sistem (`isSystemInDarkTheme()`), edge-to-edge (`WindowInsets.safeDrawing`), target sentuh ≥ 48dp, label singkat saja. | Pemilih tema (Gelap/Terang), paragraf penjelas berlebih di aplikasi (semua panduan rinci hanya di GitHub Pages `docs/index.html`). |
| **Prioritas Beranda** | **#1 Cadangan Perangkat** di paling atas, **#2 Web Panduan (GitHub Pages)** di bawahnya, lalu daftar konten utama. | Menyembunyikan status cadangan atau panduan di menu berlapis. |
| **Sinkronisasi** | **Sinkronisasi otomatis ke perangkat** ke 1 berkas tunggal konsisten di `/storage/emulated/0/Documents/<NamaApp>/<slug>_data.json`: **langsung menimpa berkas perangkat** setiap ada perubahan di aplikasi (agar hapus/ubah folder tidak kembali lagi) + **otomatis saat aplikasi dibuka** dengan **pengaman anti-timpa file kosong & prioritas entri terbanyak** (pulihkan penuh bila lokal kosong atau file perangkat lebih baru dengan entri ≥ lokal; gabung aman bila file perangkat lebih sedikit). | Google Drive/cloud login, layanan latar belakang/WorkManager, jadwal periodik, salinan berkas ganda bertanggal, atau union folder buta yang menghidupkan lagi folder terhapus. |
| **Privasi & Ukuran APK** | **100% luring** (tanpa izin `INTERNET`/`ACCESS_NETWORK_STATE`, tanpa Firebase/Google Auth/OkHttp), `< 10 MB` (`isMinifyEnabled = true`, `isShrinkResources = true`, `abiFilters += "arm64-v8a"`), hapus dependensi tak terpakai. | Izin jaringan/SDK cloud tak terpakai, atau menyimpan `.apk`, `.aab`, serta rahasia mentah (`.jks`, `.env`, `keystore.properties`) di Git. |
| **Zona Folder** | `panduan/` = panduan & template; `docs/` = **khusus GitHub Pages**; root `<slug_app>_secrets_download.html` = 1 berkas HTML pengunduh kontainer `.hc` (**tidak disembunyikan di `.gitignore`**) agar langsung diunduh lewat browser lalu dihapus pengguna. | Mencampur panduan ke `docs/`, menaruh biner `.hc` mentah yang gagal diunduh editor, atau membiarkan rahasia mentah tidak terenkripsi masuk Git. |

---

## Alur Ringkas (Fase 1–5)

1. **Identitas & Skema Data:** Tentukan nama Euskara (`com.aistudio.<kata>`), arsitektur MVVM (`StateFlow`), dan format JSON seragam dengan `id`, `updatedAtMillis`, serta `deleted`.
2. **Penyimpanan & Sinkronisasi Perangkat:** Gunakan penyimpanan lokal (DataStore/Room) dengan 1 berkas tunggal di `/storage/emulated/0/Documents/<NamaApp>/`: **langsung timpa berkas perangkat** setiap ada perubahan di aplikasi (folder terhapus tidak kembali) dan **sinkron otomatis saat aplikasi dibuka** dengan pengaman anti-timpa file kosong/entri lebih sedikit.
3. **Kurasi Ringan & Luring:** Pangkas fitur opsional, izin jaringan (`INTERNET`/`ACCESS_NETWORK_STATE`), dan dependensi berat tak terpakai (Firebase/Google Auth/OkHttp). Gunakan `remember`, `derivedStateOf`, dan `Dispatchers.IO`.
4. **Pengujian & Build R8:** Gunakan cache lokal Robolectric offline-first agar `gradle :app:testDebugUnitTest --no-daemon` lulus 100% tanpa mengunduh ulang dari Maven, lalu verifikasi APK rilis R8 `arm64-v8a` dengan `apksigner`.
5. **Rilis & GitHub Pages:** Publikasikan APK via `.github/workflows/release.yml` ke GitHub Releases dan tempatkan panduan lengkap pengguna di `docs/index.html` (wajib ramah HP tanpa teks/path/kode keluar jalur: `overflow-x: clip`, `min-width: 0`, dan `overflow-wrap: anywhere`, tanpa teks hash mentah panjang).

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
  - Tanpa paragraf instruksi panjang di aplikasi; panduan rinci hanya di GitHub Pages (docs/index.html).
  - Prioritas Beranda: #1 Cadangan Perangkat, #2 Web Panduan (GitHub Pages).
  - Sinkronisasi otomatis ke perangkat ke 1 berkas tunggal di /storage/emulated/0/Documents/[NamaApp]/ (langsung menimpa berkas perangkat saat ada perubahan data/folder di aplikasi agar folder terhapus tidak kembali, serta sinkron saat aplikasi dibuka dengan pengaman anti-timpa file kosong & perlindungan entri terbanyak; tanpa cloud/login/layanan latar belakang).
  - 100% luring: tanpa izin INTERNET/ACCESS_NETWORK_STATE dan tanpa dependensi Firebase/Google Auth/OkHttp; R8 + shrink + arm64-v8a pada release.
- Pengujian & Rilis:
  - Sertakan Unit Test & Robolectric UI Test dengan konfigurasi cache lokal Robolectric offline-first.
  - Simpan panduan di panduan/, jaga docs/ khusus GitHub Pages (wajib bebas horizontal overflow di HP: overflow-x: clip, min-width: 0, overflow-wrap: anywhere pada code/pre/kartu), dan sediakan kontainer .hc dalam 1 file HTML di root (<slug_app>_secrets_download.html, tidak disembunyikan di .gitignore) agar mudah diunduh lewat browser lalu dihapus pengguna.
```

---

## Checklist Audit Rilis

- [ ] 100% luring tanpa izin `INTERNET`/`ACCESS_NETWORK_STATE` dan tanpa dependensi Firebase/Google Auth/OkHttp.
- [ ] APK rilis `< 10 MB` (R8 + shrink + `arm64-v8a`) dan lolos `apksigner verify`.
- [ ] Unit Test & Robolectric 100% lulus lokal (offline cache) dan di CI.
- [ ] Tema otomatis sistem, prioritas Beranda `#1 Cadangan Perangkat` & `#2 Web Panduan`, tanpa keterangan berlebih di UI.
- [ ] Sinkronisasi perangkat memakai 1 berkas tunggal di `Documents/<NamaApp>/`: langsung menimpa saat edit/hapus di aplikasi (folder terhapus tidak kembali) dan dilindungi dari timpa file kosong/entri lebih sedikit.
- [ ] Folder `docs/` khusus GitHub Pages (bebas teks/path keluar jalur di layar HP), `panduan/` berisi dokumentasi, berkas `<slug_app>_secrets_download.html` di root sudah dihapus setelah `.hc` diunduh, dan tidak ada rahasia mentah (`.jks`, `.env`, `keystore.properties`) atau `.apk` di Git.
