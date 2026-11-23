# 03 — Spesifikasi Generator Kredensial & Kontainer VeraCrypt HTML (Umum)

Kirim dokumen ini ke AI lalu cukup ketik **"jalankan"** atau **"jalankan yang sesuai saja"**.

---

## 1. Tiga Berkas Wajib di Root `<slug_app>_secrets.hc`

Tepat **3 berkas** dengan nama persis sama, **tanpa teks panduan/cara**, dan **kotak kode salin (` ```text `) hanya untuk nilai panjang**:

1. **`release.jks`** — Keystore Android RSA 2048-bit, alias `upload`, `-validity 10950` (30 tahun), `STORE_PASSWORD` & `KEY_PASSWORD` acak kuat 24 karakter (atau gunakan keystore existing bila dilampirkan pengguna).
2. **`GITHUB_SECRETS.md`**:
   ~~~markdown
   ### RELEASE_KEYSTORE_BASE64

   ```text
   <base64_1_baris_utuh_tanpa_spasi_atau_newline>
   ```

   - **STORE_PASSWORD:** `<password_keystore>`
   - **KEY_PASSWORD:** `<password_key>`
   - **KEY_ALIAS:** `upload`
   ~~~
3. **`apk Fingerprint.md`**:
   ~~~markdown
   - **Package name:** `<applicationId>`
   - **Alias keystore:** `upload`

   ### SHA-1

   ```text
   <AA:BB:CC:...>
   ```

   ### SHA-256

   ```text
   <AA:BB:CC:...>
   ```
   ~~~

---

## 2. Spesifikasi Kontainer VeraCrypt v5 (Wajib 1–2 MiB)

- **Ukuran wajib:** **1–2 MiB** (standar `1,00 MiB = 1.048.576 byte`).
- **Format asli VeraCrypt v5:** magic `VERA`, versi `0x0005`, min version `0x010b`, cipher **AES-256-XTS** (sektor 512 byte, data area mulai offset `131.072` / `unitNo = 256 + s`), KDF **PBKDF2-HMAC-SHA512** (`500.000` iterasi), primary header (`0..65535`) & backup header (`ukuran - 131.072`) bersalt acak berbeda + CRC-32 valid, filesystem **`ext2`** (`mke2fs -t ext2 -b 1024 -m 0`).
- **Dilarang:** ZIP rename `.hc`, ukuran `< 1 MiB`, atau kata sandi palsu.

---

## 3. Aturan 1 Berkas HTML & Keamanan

- Proses seluruh berkas mentah & `.hc` di **`/tmp`**, lalu hapus bersih.
- Letakkan hanya **1 berkas** di root: **`<slug_app>_secrets_download.html`** (Base64 dibungkus 76 karakter/baris) berisi tombol unduh **Blob**, tautan cadangan **Data URL**, dan verifikasi **SHA-256** browser.
- **Jangan** blokir `*_secrets_download.html` di `.gitignore`; tetap blokir `*.jks`, `*.keystore`, `*.b64`, `*.hc`, `keystore.properties`, `.env`, `google-services.json`.
- **Kata sandi kontainer hanya disebutkan di balasan chat** — dilarang keras menulisnya ke HTML atau berkas repo mana pun.
- Dilarang mengubah `debug.keystore`, `debug.keystore.base64`, atau `signingConfigs` di `app/build.gradle.kts`.

---

## 4. Verifikasi Wajib

1. Dekripsi ulang primary & backup header `.hc` (`VERA`, `0x0005`, kedua CRC-32, master key identik).
2. Dekripsi data area → `e2fsck -fn` → `debugfs` dump & `cmp` byte-per-byte ketiga berkas.
3. `keytool -list -v` memastikan alias, SHA-1, & SHA-256 cocok 100% dengan `apk Fingerprint.md`.
4. Round-trip Base64 dari HTML cocok ukuran (1–2 MiB) & SHA-256, dan bebas kata sandi di HTML.
5. Pastikan tidak ada berkas mentah/sementara tersisa di repo maupun `/tmp`.
