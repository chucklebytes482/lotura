# Panduan Lotura

Sync manual antara Android, Google Drive, dan GitHub.

## 1. Persiapan

- Beri izin **Akses Semua File** di Beranda. Tanpa ini folder lokal tidak bisa dibaca/ditulis.
- Internet dibutuhkan untuk muat daftar, download, dan unggah.
- Lokasi folder:
  - Drive: `/storage/emulated/0/google drive <email>/`
  - GitHub: `/storage/emulated/0/github <username>/<repo>/`
- Folder lama di `Documents` tidak dihapus aplikasi. Pindahkan isinya manual.

## 2. Hubungkan akun

- **Drive:** tab Google Drive → *Hubungkan akun Google Drive* → pilih akun → beri izin. Bisa banyak akun.
- **GitHub:** tab GitHub → *Tambah akun GitHub* → tempel fine-grained PAT dengan `Contents: Read and write` → *Simpan*. Tiap repo jadi kartu. Repo arsip diberi label *Archived*.
- Setelah update aplikasi, akun Drive harus disambung ulang dan akun GitHub ditambah ulang.

## 3. Beranda

Tiga tab: **Google Drive**, **GitHub**, **Panduan**. Geser kiri/kanan atau ketuk nama tab.

- **Drive:** tiap akun root Drive jadi baris dengan ikon **↓** dan **↑**.
- **GitHub:** tiap repo jadi kartu dengan ikon **↓** dan **↑**. Baris akun punya *muat ulang* (ambil daftar repo) dan *hapus* (putus akun).
- **Panduan:** isi panduan ini, tombol unduh aplikasi, referensi tataletak. Offline kecuali saat unduh APK.

**Log.** Ketuk judul akun Drive atau nama repo → lembar bawah berisi log sumber itu (akun Drive: semua folder di akun itu). Tiap entri: arah (↓/↑), nama folder/repo, waktu, status (OK, Sebagian, Gagal, Dibatalkan), jumlah ditransfer/dilewati/gagal. Ketuk entri yang punya *dilewati* untuk melihat file dan alasannya: *ukuran sama*, *tidak ada di perangkat*, *lebih dari 45 MB*, *tersembunyi*. Simpan 200 entri terakhir per aplikasi. Ikon di kartu (↓, ↑, muat ulang, hapus, batal) tidak membuka log.

**Antrean.** Sync hanya lewat ↓ dan ↑. Satu sumber maksimal satu sync aktif atau antre. Sync berikutnya antre dan jalan berurutan. Saat berjalan/antre, baris menampilkan spinner dan *Batalkan sync*. Sync yang dibatalkan tercatat di log.

## 4. Download dan unggah

- **↓ Download:** salin isi folder/repo dari Drive atau GitHub ke lokal.
- **↑ Unggah:** kirim isi folder lokal ke Drive atau GitHub. File baru ikut terkirim.
- Pembanding hanya ukuran. Sama → dilewati. Beda → ditransfer.
- Dokumen Google (Docs, Sheets, Slides) selalu ditransfer karena ukurannya tidak diketahui.
- File/folder berawalan titik tidak ikut sync.
- Saat transfer, bar progres tampil dan tombol nonaktif sampai selesai.
- Repo GitHub hanya memproses berkas `.md` (lihat bagian 5).

## 5. Berkas yang disinkronkan dari GitHub

- Hanya berkas **`.md`** di mana pun di repo (termasuk `README.md`). Tanpa filter folder khusus.
- File lain diabaikan saat tampil, download, unggah, dan auto-sync.
- Path berawalan titik (mis. `.github/`) tetap diabaikan.

## 6. Aturan sync

- Tidak ada auto-sync. Perubahan tidak terkirim atau tertarik sampai Anda menekan ↓ atau ↑.
- Sync tidak menghapus apa pun di sisi tujuan. Berkas yang hanya ada di satu sisi dibiarkan.
- Jika file sama berubah di HP dan GitHub, Lotura tidak menimpa diam-diam. Pilih ↓ atau ↑ sesuai versi yang dipertahankan.
- File di atas 45 MB dilewati dan dilaporkan.
- Repo arsip dilewati.

## 7. Hapus akun

- Ikon hapus di baris akun Drive atau kartu/akun GitHub memutus akun dari Lotura.
- Berkas di HP, Drive, dan GitHub tidak terhapus.

## 8. Rilis

- Tab *Panduan* → **Unduh dan pasang**: ambil APK terbaru dari GitHub Releases lalu buka installer Android. Butuh internet.
- Jika Android minta izin *pasang aplikasi tidak dikenal*, aktifkan untuk Lotura, kembali, lalu ketuk *Sudah diizinkan*.
- Android menolak APK dengan versi lebih rendah dari yang terpasang. Pesannya ditampilkan apa adanya.
- Langkah CI dan keystore: `../umum/02_Setup_Rilis_CI.md`.
