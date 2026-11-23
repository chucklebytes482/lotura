# 01 — Panduan Lotura

Panduan pemakaian Lotura: sinkronisasi manual antara perangkat Android, Google Drive, dan GitHub, serta edit file repo GitHub langsung di HP.

---

## 1. Persiapan

- Beri izin **Akses Semua File** saat diminta di Beranda. Tanpa izin ini Lotura tidak bisa membaca atau menulis folder lokal.
- Pastikan ponsel terhubung internet saat memuat daftar, mengunduh, mengunggah, atau menyimpan perubahan file GitHub.
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
- Ikon status di kartu menunjukkan hasil sync terakhir: centang (OK), peringatan (sebagian berhasil), silang (gagal). Teks lengkapnya ada di layar detail.

## 4. Download dan Upload

- Ikon **download** dan **unggah** tampil langsung di setiap kartu folder root Drive dan repo GitHub.
- **Download:** menyalin isi folder atau repo dari Drive atau GitHub ke folder lokal.
- **Unggah:** mengirim isi folder atau repo dari folder lokal ke Drive atau GitHub. File baru di dalam folder itu ikut terkirim.
- Perbandingan hanya memakai ukuran. Ukuran sama berarti dilewati. Ukuran beda berarti ditransfer.
- Dokumen Google (Docs, Sheets, Slides) selalu ditransfer karena ukurannya tidak diketahui.
- File dan folder tersembunyi (nama diawali titik) tidak ikut disinkronkan.
- Saat transfer berjalan, ikon di kartu tidak bisa ditekan dan bar progres tampil di atas daftar.

## 5. Detail kartu

- Ketuk kartu folder root Drive atau repo GitHub untuk membuka layar detail.
- Layar detail hanya menampilkan:
  - tombol **Jelajahi file** (khusus repo GitHub),
  - status sync terakhir: waktu, status (OK, Sebagian, Gagal), jumlah berhasil atau sudah sama, jumlah gagal, dan jumlah item tersembunyi yang dilewati.
- Layar detail tidak punya ikon download dan unggah. Pakai ikon di kartu Beranda untuk itu.
- Tombol kembali di top bar kembali ke Beranda.

## 6. Jelajahi dan edit file GitHub

- Di layar detail repo GitHub, ketuk **Jelajahi file**. Isi repo lokal tampil sebagai daftar: folder dulu, lalu file.
- Ketuk folder untuk masuk. Tombol kembali naik satu folder, dan kembali ke detail saat sudah di folder paling atas.
- Ketuk file teks untuk membuka editor. Ubah isinya, lalu tunggu sebentar. Perubahan disimpan otomatis ke HP setelah berhenti mengetik.
- Dari daftar file, Anda bisa:
  - membuat file atau folder baru,
  - mengganti nama,
  - menghapus (folder ikut terhapus beserta isinya).
- Berkas berukuran di atas 2 MB atau berkas biner (gambar, APK, dan sejenisnya) tidak bisa dibuka di editor.
- Berkas dan folder berawalan titik ditandai *tidak ikut sinkron*.

### Auto-sync ke GitHub

- Setiap perubahan di repo itu di-push otomatis ke branch `main` di GitHub. Tidak ada tombol simpan atau unggah.
- Perubahan dikumpulkan dulu selama sekitar 3 detik, lalu dikirim sebagai satu commit.
- Lotura juga memeriksa GitHub tiap 60 detik. Jika ada perubahan di GitHub yang belum ada di HP, perubahan itu tidak ditarik otomatis. Gunakan ikon download di kartu Beranda untuk menarik.
- Jika file yang sama berubah di HP dan di GitHub, Lotura melaporkan konflik dan tidak menimpa salah satunya. Selesaikan dengan download atau unggah manual.
- File di atas 45 MB dilewati dan dilaporkan.
- Auto-sync hanya berjalan selama aplikasi terbuka atau masih aktif di latar belakang. Perubahan yang dibuat saat aplikasi sudah ditutup baru terdeteksi lewat sync manual.
- Auto-sync hanya untuk GitHub. Folder Google Drive tidak ikut.
- Repo yang diarsipkan dilewati.

## 7. Hapus akun

- Ketuk ikon hapus di header akun untuk memutus akun dari Lotura.
- Menghapus akun hanya memutus koneksi. Berkas di perangkat, Drive, dan GitHub tidak ikut terhapus.

## 8. Rilis

- APK terbaru tersedia di halaman unduhan Lotura.
- Langkah CI dan keystore ada di `../umum/02_Setup_Rilis_CI.md`.
