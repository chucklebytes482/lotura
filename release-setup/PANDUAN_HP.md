# Buka keystore Lotura lewat HP

Gunakan **`lotura-release-key.7z`** untuk membuka cadangan lewat aplikasi Android yang mendukung 7z AES-256, misalnya [VaultExplorer](https://github.com/R0b0To/VaultExplorer). File ini berisi **keystore permanen yang sama**, bukan signing key baru. Nama file di dalam arsip juga dienkripsi. Cadangan `.enc` lama tetap tersedia sebagai alternatif berbasis Python.

Folder ini dibuat ulang khusus untuk cadangan: hanya arsip 7z, cadangan `.enc`, panduan, skrip pembuat 7z, dependensi dan fingerprint. Kode aplikasi, workflow aktif, signing secrets, serta perubahan dari commit lain tidak diubah. Untuk memulihkan `.enc`, skrip lama tersedia di [commit setup awal](https://github.com/chucklebytes482/lotura/blob/7d56784df372a571474b14e8abe504b287a6e247/release-setup/backup_keystore.py); skrip tersebut memerlukan pustaka Python `cryptography`. Untuk HP, gunakan 7z saja.

## Langkah di VaultExplorer

1. Unduh `lotura-release-key.7z` dari folder `release-setup/` di GitHub; pada halaman file gunakan **Raw/Download raw file**, bukan menyimpan halaman HTML. Atau unduh berkas 7z yang diserahkan pada chat.
2. Buka VaultExplorer dan pilih lokasi penyimpanan yang memuat file tersebut. Berikan izin Android untuk folder itu jika diminta.
3. Buka `lotura-release-key.7z` melalui penjelajah/Archive browser, lalu masukkan **password backup** yang sebelumnya Anda simpan. Ini bukan password keystore dan bukan token GitHub. Nama menu dapat berbeda antarversi; README proyek menyatakan arsip 7z berpassword dapat dibuka pada penyimpanan biasa maupun di dalam vault.
4. Setelah arsip terbuka, file `lotura-release-upload.jks` dapat diekstrak bila diperlukan. Pilih tempat privat/terlindungi; jangan mengekstraknya ke folder yang otomatis disinkronkan, dibagikan, atau ke repo Git.
5. Jika menggunakan keystore untuk signing, password `.jks` adalah **password keystore** yang berbeda dari password backup; aliasnya **`upload`**. JKS adalah file biner: tidak perlu dibuka dengan editor teks.
6. Setelah selesai, simpan cadangan terenkripsi dan jangan bagikan hasil ekstraksi. Jangan menganggap penghapusan biasa menjamin penghapusan fisik pada flash storage.

VaultExplorer mendokumentasikan dukungan **7z AES-256 dengan nama file terenkripsi** pada [README](https://github.com/R0b0To/VaultExplorer#features), diperiksa 20 November 2026. Kompatibilitas format diverifikasi di sandbox, **bukan pengujian UI pada HP atau versi aplikasi yang Anda pasang**. README menyebut pemasangan dari GitHub Releases; ketersediaan/versi F-Droid tidak diverifikasi dalam pekerjaan ini. Unduh aplikasi dari sumber yang Anda percaya.

## Verifikasi file

Jika tersedia, gunakan Hash Verifier SHA-256 di aplikasi:

| File | SHA-256 isi file |
|---|---|
| `lotura-release-key.7z` | `2c5cf4a44e9eb2b648842f8cd3025748cdf7c62afe2bdb445bdb54eafa62e67e` |
| `lotura-release-upload.jks` setelah ekstraksi | `ae97b0a06d558e91692d0613d5d3e317b6dcef6892c1af1834a185af830bc6c5` |

Hash file `.jks` di atas **berbeda konsep** dari SHA-1/SHA-256 sertifikat OAuth di `CERT_FINGERPRINT.txt`. Untuk OAuth, tetap gunakan fingerprint sertifikat, bukan hash file.

## Jika tidak bisa dibuka

- Pastikan file berukuran masuk akal dan hash arsip cocok; unduh ulang jika berbeda.
- Pakai password backup yang benar, termasuk huruf besar/kecil dan simbol.
- Gunakan versi aplikasi yang mendukung arsip 7z terenkripsi dan encrypted headers. `.enc` format lama tidak dapat dibuka sebagai 7z; mengubah ekstensi tidak mengubah format.
- Pada komputer, arsip ini juga dapat dibuka dengan 7-Zip yang mendukung AES-256.

## Membuat cadangan 7z lagi dari key yang sama

Tidak perlu membuat signing key baru. Pada komputer tepercaya:

```bash
python -m pip install -r release-setup/requirements.txt
python release-setup/create_phone_backup.py \
  "$HOME/.lotura/release-upload.jks" "$HOME/.lotura/lotura-backup.7z"
```

Password diminta secara tersembunyi; skrip menolak menimpa arsip yang sudah ada. Jangan memasukkan password pada command line atau commit ke Git. Arsip publik tetap bisa menjadi sasaran tebakan password offline; gunakan password acak kuat dan simpan salinan cadangan kedua secara pribadi.