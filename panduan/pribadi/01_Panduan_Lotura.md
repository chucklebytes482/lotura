# 01 — Panduan Lotura

Panduan pemakaian Lotura: sinkronisasi manual antara perangkat Android, Google Drive, dan GitHub.

---

## 1. Persiapan

- Beri izin **Akses Semua File** saat diminta di Beranda. Tanpa izin ini Lotura tidak bisa membaca atau menulis folder lokal.
- Pastikan ponsel terhubung internet saat memuat daftar, mengunduh, atau mengunggah.
- Lokasi folder di perangkat:
  - Google Drive: `/storage/emulated/0/google drive <email>/`
  - GitHub: `/storage/emulated/0/github <username>/<repo>/`
- Folder lama di `Documents` dari versi sebelumnya tidak dihapus oleh aplikasi. Pindahkan isinya manual ke lokasi di atas.

## 2. Hubungkan akun

- **Google Drive:** ketuk *Hubungkan akun Google Drive* di Beranda, pilih akun, lalu beri izin. Bisa menambah banyak akun.
- **GitHub:** ketuk *Tambah akun GitHub*, tempel fine-grained PAT dengan izin `Contents: Read and write`, lalu ketuk *Simpan*. Semua repo di akun itu terhubung, masing-masing sebagai kartu sendiri. Repo yang diarsipkan diberi label *Archived*.
- Setelah pembaruan aplikasi, akun Drive harus disambungkan ulang dan akun GitHub harus ditambahkan ulang.

## 3. Beranda

- Beranda hanya menampilkan folder root Drive dan daftar repo GitHub, masing-masing sebagai kartu.
- Baris akun GitHub punya dua ikon saja: *muat ulang* (mengambil daftar repo terbaru) dan *hapus* (memutus akun).
- Isi repo GitHub tidak tampil di aplikasi.
- Ikon status di kartu menunjukkan hasil sync terakhir: centang (OK), peringatan (sebagian berhasil), silang (gagal). Teks lengkapnya ada di layar detail.

## 4. Download dan Upload

- Ikon **download** dan **unggah** tampil langsung di setiap kartu folder root Drive dan repo GitHub.
- **Download:** menyalin isi folder atau repo dari Drive atau GitHub ke folder lokal.
- **Upload:** mengirim isi folder atau repo dari folder lokal ke Drive atau GitHub. File baru di dalam folder itu ikut terkirim.
- Perbandingan hanya memakai ukuran. Ukuran sama berarti dilewati. Ukuran beda berarti ditransfer.
- Dokumen Google (Docs, Sheets, Slides) selalu ditransfer karena ukurannya tidak diketahui.
- File dan folder tersembunyi (nama diawali titik) tidak ikut disinkronkan.
- Saat transfer berjalan, ikon di kartu tidak bisa ditekan dan bar progres tampil di atas daftar.

## 5. Detail folder

- Ketuk kartu folder root Drive atau repo GitHub untuk membuka layar detail.
- Layar detail menampilkan:
  - nama, path relatif, dan path lokal,
  - jumlah file dan total ukuran (dihitung di latar belakang),
  - ringkasan sync terakhir: waktu, status (OK, Sebagian, Gagal), jumlah berhasil atau sudah sama, jumlah gagal, dan jumlah item tersembunyi yang dilewati.
- Ikon download dan unggah juga ada di layar detail.
- Hitungan Drive dibatasi jumlah panggilan API. Jika hitungan berhenti di batas itu, layar menandai angkanya belum lengkap.
- Dokumen Google tidak punya ukuran. Jumlahnya dihitung sebagai file, tetapi tidak ikut total ukuran.
- Tombol kembali di top bar kembali ke Beranda.

## 6. Hapus akun

- Ketuk ikon hapus di header akun untuk memutus akun dari Lotura.
- Menghapus akun hanya memutus koneksi. Berkas di perangkat, Drive, dan GitHub tidak ikut terhapus.

## 7. Rilis

- APK dirilis sebagai GitHub Release di repo `chucklebytes482/lotura`.
- Unduhan terbaru: `https://github.com/chucklebytes482/lotura/releases/latest/download/lotura-latest-arm64-v8a.apk`
- Langkah CI dan keystore ada di `../umum/02_Setup_Rilis_CI.md`.
