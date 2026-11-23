# 01 — Panduan Lotura (Umum)

Panduan pemakaian Lotura: sinkronisasi manual antara perangkat Android, Google Drive, dan GitHub.

---

## 1. Persiapan

- Beri izin **Akses Semua File** saat diminta di Beranda. Tanpa izin ini Lotura tidak bisa membaca atau menulis folder lokal.
- Pastikan ponsel terhubung internet saat memuat pohon, mengunduh, atau mengunggah.
- Lokasi folder di perangkat:
  - Google Drive: `/storage/emulated/0/google drive <email>/`
  - GitHub: `/storage/emulated/0/github <username>/<repo>/`
- Folder lama di `Documents` dari versi sebelumnya tidak dihapus oleh aplikasi. Pindahkan isinya manual ke lokasi di atas.

## 2. Hubungkan akun

- **Google Drive:** ketuk *Hubungkan akun Google Drive* di Beranda, pilih akun, lalu beri izin. Bisa menambah banyak akun.
- **GitHub:** ketuk *Tambah akun GitHub*, tempel fine-grained PAT dengan izin `Contents: Read and write`, lalu ketuk *Simpan*. Semua repo di akun itu terhubung, masing-masing sebagai folder sendiri. Repo yang diarsipkan diberi label *Archived*.
- Setelah pembaruan aplikasi, akun Drive harus disambungkan ulang dan akun GitHub harus ditambahkan ulang.

## 3. Pilih file

- Buka pohon akun atau repo dengan ketuk panah di sebelahnya.
- Centang file atau folder. Mencentang folder ikut mencentang seluruh isinya.
- Dokumen Google (Docs, Sheets, Slides) selalu ditransfer karena ukurannya tidak diketahui.
- Muat ulang pohon dengan ikon *muat ulang* untuk daftar terbaru.

## 4. Download dan Upload

- **Download:** menyalin item yang dicentang dari Drive atau GitHub ke folder lokal.
- **Upload:** mengirim item yang dicentang dari folder lokal ke Drive atau GitHub.
- Perbandingan hanya memakai ukuran. Ukuran sama berarti dilewati. Ukuran beda berarti ditransfer.
- Hasil setiap operasi tercatat di tab *Log*.
- Batasan: upload hanya mengirim file yang sudah ada di pohon. File baru harus berada di folder yang sudah ada di pohon. Muat ulang pohon setelah menambah folder.

## 5. Hapus akun

- Ketuk ikon hapus di header akun untuk memutus akun dari Lotura.
- Menghapus akun hanya memutus koneksi. Berkas di perangkat, Drive, dan GitHub tidak ikut terhapus.

## 6. Rilis

- APK dirilis sebagai GitHub Release di repo `chucklebytes482/lotura`.
- Unduhan terbaru: `https://github.com/chucklebytes482/lotura/releases/latest/download/lotura-latest-arm64-v8a.apk`
- Langkah CI dan keystore ada di `02_Setup_Rilis_CI_(umum).md`.
