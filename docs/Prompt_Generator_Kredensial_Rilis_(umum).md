# Prompt Generator Kredensial Rilis & Pengunduh HTML VeraCrypt (Umum)

Panduan singkat untuk meminta AI membuat kredensial rilis permanen Android, mengemas **berkas rahasia saja** ke kontainer **VeraCrypt v5** (`<slug_app>_secrets.hc`), lalu menyediakannya dalam **1 berkas HTML di root** (`<slug_app>_secrets_download.html`) agar dapat diunduh lewat browser dan langsung dihapus pengguna setelah selesai.

---

## 1. Perintah Siap Salin untuk AI

```text
Buatkan kredensial rilis permanen untuk aplikasi Android yang sedang kita buat, kemas HANYA berkas rahasianya ke kontainer standar VeraCrypt v5 (`<slug_app>_secrets.hc`, ukuran 1,00 MiB) di `/tmp`, lalu bungkus ke dalam 1 berkas HTML mandiri di root repositori (`<slug_app>_secrets_download.html`) agar bisa diunduh lewat browser.

Berkas rahasia yang wajib dimasukkan ke folder `release/` di dalam kontainer `.hc`:
1. `<slug_app>-release.jks`: keystore permanen RSA 2048-bit, berlaku 30 tahun (`-validity 10950`), alias `upload`, dengan `STORE_PASSWORD` dan `KEY_PASSWORD` acak 24 karakter alfanumerik.
2. `keystore.properties`: berisi `storeFile`, `storePassword`, `keyAlias`, dan `keyPassword`.
3. `GITHUB_SECRETS.md`: hanya berisi kotak kode terpisah 1-ketuk untuk `RELEASE_KEYSTORE_BASE64` (1 baris tanpa spasi/baris baru), `STORE_PASSWORD`, `KEY_PASSWORD`, dan `KEY_ALIAS` (`upload`).
4. *(Opsional)* `.env` hanya jika aplikasi memakai API key eksternal (aplikasi 100% luring cukup 3 berkas di atas).

Aturan Enkripsi, HTML Pengunduh & Keamanan:
- Format kontainer: VeraCrypt v5, AES-256-XTS, PBKDF2-HMAC-SHA512 (500.000 iterasi / PIM default), header utama + cadangan valid, filesystem internal `ext2`.
- Bungkus biner `.hc` (Base64 dibungkus 76 karakter/baris) ke 1 berkas HTML mandiri di root: `<slug_app>_secrets_download.html` dengan tombol unduh utama (Blob), tautan cadangan (Data URL), serta verifikasi SHA-256 otomatis di browser. Jangan simpan file biner `.hc` terpisah atau folder `secret/`.
- Jangan ubah `debug.keystore`, `debug.keystore.base64`, atau `signingConfigs` / `signingConfig` di `app/build.gradle.kts`.
- Proses seluruh berkas mentah dan `.hc` di `/tmp` lalu hapus bersih setelah masuk ke `<slug_app>_secrets_download.html`.
- JANGAN menyembunyikan `<slug_app>_secrets_download.html` di `.gitignore` agar terlihat di root dan dapat langsung dihapus pengguna setelah `.hc` diunduh; tetap blokir rahasia mentah (`*.jks`, `*.keystore`, `*.b64`, `keystore.properties`, `google-services.json`, `.env`) di `.gitignore`.
- Verifikasi dekripsi kontainer (header utama & cadangan, CRC-32, `e2fsck`, `keytool` SHA-1/SHA-256) serta verifikasi round-trip Base64 HTML cocok 100% SHA-256, lalu tampilkan password kontainer di respons chat saja (jangan pernah tulis password ke HTML atau berkas repositori mana pun).
```

---

## 2. Daftar Isi Standar Kontainer `<slug_app>_secrets.hc` (di dalam `<slug_app>_secrets_download.html`)

Hanya berisi berkas rahasia (tanpa panduan umum, tanpa setup Google Cloud, dan tanpa file publik):

| Lokasi di Dalam `.hc` | Isi |
|---|---|
| `release/<slug_app>-release.jks` | Keystore rilis permanen (RSA 2048-bit, alias `upload`, berlaku 30 tahun) |
| `release/keystore.properties` | Konfigurasi signing lokal (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) |
| `release/GITHUB_SECRETS.md` | Nilai 1-ketuk salin: `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS` |
| *(Opsional)* `release/.env` | Hanya bila aplikasi memakai API key eksternal (tidak diperlukan untuk aplikasi 100% luring) |
| *(Opsional)* `katasandi/` | Daftar kata sandi tambahan bila diminta |
