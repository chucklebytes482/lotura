
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

- **Google Drive:** ketuk *Hubungkan akun Google Drive* di tab Google Drive, pilih akun, lalu beri izin. Bisa menambah banyak akun.
- **GitHub:** di tab GitHub, ketuk *Tambah akun GitHub*, tempel fine-grained PAT dengan izin `Contents: Read and write`, lalu ketuk *Simpan*. Semua repo di akun itu tampil sebagai kartu sendiri. Repo yang diarsipkan diberi label *Archived*.
- Setelah pembaruan aplikasi, akun Drive harus disambungkan ulang dan akun GitHub harus ditambahkan ulang.

## 3. Beranda

Beranda punya tiga tab:

- **Google Drive:** folder root tiap akun Drive, masing-masing sebagai baris dengan ikon **↓** dan **↑**.
- **GitHub:** repo tiap akun GitHub, masing-masing sebagai kartu dengan ikon **↓** dan **↑**. Baris akun punya dua ikon: *muat ulang* (mengambil daftar repo terbaru) dan *hapus* (memutus akun).
- **Log standar:** riwayat semua sync dari Drive dan GitHub, terbaru di atas. Tiap entri menampilkan arah (↓ download atau ↑ unggah), sumber, nama folder atau repo, waktu, status (OK, Sebagian, Gagal), dan jumlah ditransfer, dilewati, serta gagal. Log menyimpan 200 entri terakhir.

Ketuk kartu atau baris tidak melakukan apa pun. Sync hanya jalan lewat ikon ↓ dan ↑.

## 4. Download dan Unggah

- **↓ Download:** menyalin isi folder atau repo dari Drive atau GitHub ke folder lokal.
- **↑ Unggah:** mengirim isi folder atau repo dari folder lokal ke Drive atau GitHub. File baru di dalam folder itu ikut terkirim.
- Perbandingan hanya memakai ukuran. Ukuran sama berarti dilewati. Ukuran beda berarti ditransfer.
- Dokumen Google (Docs, Sheets, Slides) selalu ditransfer karena ukurannya tidak diketahui.
- File dan folder tersembunyi (nama diawali titik) tidak ikut disinkronkan.
- Saat transfer berjalan, bar progres tampil di atas dan tombol tidak bisa ditekan sampai transfer selesai.
- Untuk repo GitHub, download dan unggah hanya memproses file yang dilacak (lihat bagian 5).

## 5. Berkas yang disinkronkan dari GitHub

- Repo GitHub hanya menyinkronkan berkas yang sering Anda edit: semua berkas `.md` (termasuk `README.md`), berkas `README*`, dan seluruh isi folder `panduan`, `doc`, dan `docs` di root repo. File lain di repo tidak ikut download atau unggah.
- Berkas dan folder berawalan titik tidak ikut sinkron.

## 6. Aturan sync manual

- Tidak ada auto-sync. Perubahan di HP tidak dikirim ke GitHub, dan perubahan di GitHub tidak ditarik, sampai Anda menekan ↓ atau ↑.
- Lotura tidak menghapus apa pun di sisi tujuan saat sync. Berkas yang hanya ada di satu tempat dibiarkan.
- Jika file yang sama berubah di HP dan di GitHub, Lotura tidak menimpa salah satunya. Pilih arah ↓ atau ↑ sesuai versi yang ingin dipertahankan.
- File di atas 45 MB dilewati dan dilaporkan.
- Repo yang diarsipkan dilewati.

## 7. Hapus akun

- Ketuk ikon hapus di baris akun GitHub, atau ikon hapus di akun Drive, untuk memutus akun dari Lotura.
- Menghapus akun hanya memutus koneksi. Berkas di perangkat, Drive, dan GitHub tidak ikut terhapus.

## 8. Rilis

- APK terbaru tersedia di halaman unduhan Lotura.
- Langkah CI dan keystore ada di `../umum/02_Setup_Rilis_CI.md`.
