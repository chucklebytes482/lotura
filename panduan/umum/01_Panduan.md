# 01 — Panduan Umum Aplikasi

Referensi umum untuk membangun aplikasi Android yang menyalin dan menyinkronkan file antara perangkat, penyimpanan cloud, dan repo GitHub. Gunakan sebagai titik awal. Ganti bagian dalam `<kurung>` sesuai aplikasi baru.

---

## 1. Persiapan

- Minta izin akses file yang dibutuhkan (misalnya **Akses Semua File**) di layar awal. Jelaskan alasannya kepada pengguna. Tanpa izin ini aplikasi tidak bisa membaca atau menulis folder lokal.
- Pastikan perangkat terhubung internet saat memuat daftar, mengunduh, mengunggah, atau menyimpan perubahan.
- Tentukan lokasi folder lokal di perangkat dengan pola yang konsisten, misalnya `/storage/emulated/0/<nama-layanan> <identitas>/`.
- Jangan menghapus data lama dari versi sebelumnya secara otomatis. Beri tahu pengguna cara memindahkannya secara manual.

## 2. Hubungkan akun

- **Penyimpanan cloud:** pengguna mengetuk tombol hubungkan, memilih akun, lalu memberi izin. Dukung beberapa akun bila diperlukan.
- **GitHub:** pengguna menempel fine-grained PAT dengan izin minimal `Contents: Read and write`, lalu menyimpannya. Simpan token di penyimpanan aman perangkat, jangan di `.git/config` atau log.
- Repo yang diarsipkan diberi label *Archived* dan dilewati saat sinkron.
- Setelah pembaruan aplikasi yang mengubah skema penyimpanan, minta pengguna menyambungkan ulang akun.

## 3. Beranda

- Beranda hanya menampilkan daftar sumber (folder root cloud dan daftar repo) sebagai kartu.
- Setiap baris akun punya aksi sesedikit mungkin: muat ulang dan hapus (putus akun).
- Ikon status di kartu menunjukkan hasil sinkron terakhir: centang (OK), peringatan (sebagian), silang (gagal). Teks lengkapnya ada di layar detail.

## 4. Download dan Unggah

Aksi download dan unggah ada di layar detail, bukan di Beranda.

- **Download:** menyalin isi folder atau repo dari sumber jarak jauh ke folder lokal.
- **Unggah:** mengirim isi folder lokal ke sumber jarak jauh. File baru di dalam folder itu ikut terkirim.
- Perbandingan memakai ukuran. Ukuran sama berarti dilewati, ukuran beda berarti ditransfer. Jika memungkinkan, gunakan hash atau waktu ubah sebagai pembanding yang lebih akurat.
- Berkas yang ukurannya tidak diketahui (misalnya dokumen khusus layanan) selalu ditransfer.
- Berkas dan folder tersembunyi (nama diawali titik) tidak ikut sinkron.
- Selama transfer berjalan, tombol download dan unggah dinonaktifkan dan progres ditampilkan.

## 5. Layar detail

- Ketuk kartu untuk membuka layar detail.
- Layar detail menampilkan:
  - tombol **Download** dan **Unggah** untuk seluruh isi folder atau repo,
  - tombol **Jelajahi file** untuk menelusuri isi lokal,
  - bagian **Sync terakhir**: waktu, status (OK, Sebagian, Gagal), jumlah berhasil atau sudah sama, jumlah gagal, dan jumlah item tersembunyi yang dilewati.
- Tombol kembali di top bar kembali ke Beranda.

## 6. Jelajahi dan edit file

- Batasi file yang tampil dan ikut sinkron sesuai kebutuhan. Contoh: hanya `*.md`, `README*`, dan folder dokumentasi tertentu. Berkas lain tidak tampil dan tidak ikut download, unggah, atau auto-sync.
- Tampilkan isi folder sebagai daftar: folder dulu, lalu file. Tombol kembali naik satu folder, dan kembali ke detail saat sudah di folder paling atas.
- Berkas teks dibuka di editor. Perubahan disimpan otomatis ke perangkat setelah pengguna berhenti mengetik.
- Dari daftar file, pengguna bisa membuat, mengganti nama, dan menghapus file atau folder. Menghapus folder ikut menghapus isinya.
- **Riwayat** (jika ada): tampilkan commit yang pernah mengubah berkas. Pemulihan versi lama dikirim sebagai perubahan baru, bukan menulis ulang sejarah.
- Berkas di atas batas ukuran (misalnya 2 MB) atau berkas biner tidak dibuka di editor.

### Auto-sync

- Perubahan lokal di-push otomatis dalam satu commit setelah jeda singkat (sekitar 3 detik).
- Aplikasi memeriksa sumber jarak jauh berkala (misalnya tiap 60 detik). Perubahan dari sana ditarik ke perangkat. Berkas yang sedang dibuka ditarik setelah editor ditutup.
- Jika berkas yang sama berubah di perangkat dan di sumber jarak jauh, laporkan konflik dan jangan menimpa salah satunya diam-diam. Biarkan pengguna memilih lewat download atau unggah manual.
- Berkas di atas batas ukuran (misalnya 45 MB) dilewati dan dilaporkan.
- Auto-sync hanya berjalan selama aplikasi terbuka atau aktif di latar belakang. Perubahan saat aplikasi tertutup terdeteksi lewat sinkron manual.
- Aturan penghapusan: item yang belum dilacak tidak boleh memicu penghapusan di kedua sisi.

## 7. Putus akun

- Ketuk ikon hapus di header akun untuk memutus akun dari aplikasi.
- Memutus akun hanya memutus koneksi. Berkas di perangkat dan di layanan jarak jauh tidak ikut terhapus.

## 8. Rilis

- Bangun APK hanya di CI, jangan di sandbox pengembangan.
- Simpan keystore dan kredensial di secret CI, jangan di repo. Lihat `02_Setup_Rilis_CI.md` dan `03_Generator_Kredensial_VeraCrypt.md`.
- Unggah APK, checksum, dan sidik jari sertifikat sebagai GitHub Release bertanda versi, lalu tautkan ke tautan `releases/latest` untuk unduhan tetap.
- Tampilkan tanggal dan jam rilis di halaman unduhan, dalam zona waktu pengguna.
- Verifikasi hasil CI dan perangkat sebelum menyatakan rilis selesai.
