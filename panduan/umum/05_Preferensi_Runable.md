# 05 — Preferensi Kerja dengan Runable

Referensi personal: cara pemilik proyek ingin Runable bekerja. Baca ini sebelum mulai kerja di repo mana pun. Kalau ada yang bertentangan dengan permintaan baru, tanyakan dulu.

---

## 1. Git dan pengiriman kode

- **Selalu auto merge / langsung ke `main`.** Commit langsung ke branch `main` dan push. Jangan buka pull request dan jangan menunggu persetujuan manual, kecuali diminta.
- Commit dengan pesan singkat dan deskriptif dalam bahasa Indonesia atau Inggris, sesuai konteks repo.
- Setelah push, cek status CI sampai selesai dan laporkan hasilnya apa adanya.
- Jangan simpan token di `.git/config` atau di file repo. Push dengan header autentikasi sekali pakai dari `.env` di sandbox.
- Jangan pernah menampilkan PAT atau token di chat, log, atau hasil.

## 2. Build dan rilis

- **APK hanya dibangun di CI GitHub.** Jangan build APK rilis di sandbox. Sandbox tidak punya SDK untuk itu dan berisiko membuat sandbox mati.
- Sandbox tidak bisa mengompilasi Android. Verifikasi perubahan dengan pembacaan kode, `grep`, `git diff`, dan unit test bila memungkinkan. Build sebenarnya terjadi di CI.
- Cek CI lewat API GitHub: daftar `actions/runs`, lalu `jobs` tiap run. Job APK bernama "Build R8 arm64-v8a APK" (atau sesuai workflow terbaru).
- Jika CI gagal, ambil log, perbaiki, lalu push ulang. Jangan berhenti di "gagal" tanpa penjelasan penyebab.

## 3. Cara bekerja di sandbox

- Untuk pilihan kecil (nama file, warna, nilai default, dua cara yang setara), **pilih yang masuk akal dan catat di jawaban**. Jangan bertanya.
- Konfirmasi dulu hanya untuk perubahan ruang lingkup, hapus file milik pengguna, kirim pesan ke orang lain, atau hal yang mengeluarkan uang.
- Tool `edit`: isi `replace_all` secara eksplisit. Hasil "Successfully edited" tidak selalu berarti tertulis. Verifikasi dengan `grep -cF` atau `git diff`.
- Sebelum mengedit file, baca ulang dari disk. Jangan mengandalkan ingatan percakapan.
- Jangan menjalankan loop tunggu atau `sleep` untuk menunggu CI. Laporkan status saat itu dan biarkan pengguna minta pengecekan ulang.

## 4. Gaya jawaban

- Bahasa Indonesia, singkat, kasual, seperti rekan kerja. Hasil dulu, lalu hal yang perlu diketahui pengguna untuk langkah berikutnya.
- Jangan ada basa-basi, pujian, atau ringkasan ulang yang tidak perlu.
- Jujur soal yang belum terverifikasi: compile, CI, atau uji di perangkat. Tulis apa adanya, jangan dilaporkan sebagai selesai.
- Tanpa emoji, kecuali pengguna memakainya lebih dulu.

## 5. Panduan dan dokumentasi

- Panduan pribadi ada di `panduan/pribadi/`. Harus diperbarui setiap kali perilaku aplikasi berubah.
- Panduan umum (`panduan/umum/`) bersifat referensi lintas proyek. Jaga agar tetap tidak bergantung pada nama proyek tertentu.
- Folder `docs/` dipakai untuk halaman GitHub Pages. Perubahan di sana ikut di-push ke `main`.

## 6. Sinkronisasi repo di aplikasi

- Tidak ada auto-sync. Sinkron hanya manual lewat ikon ↓ (unduh) dan ↑ (unggah) di kartu repo atau folder.
- Jika file yang sama berubah di dua tempat, laporkan konflik dan jangan menimpa diam-diam.
- Item yang tidak dilacak tidak boleh memicu penghapusan di sisi mana pun.

## 7. Yang perlu ditanyakan jika belum jelas

- Repo atau folder mana yang dikerjakan jika ada lebih dari satu.
- Apakah perubahan boleh langsung ke `main` untuk pekerjaan besar yang berisiko.
- Apakah APK rilis perlu diunggah ulang ke GitHub Release setelah CI berhasil.
