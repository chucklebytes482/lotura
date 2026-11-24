# 05 — Preferensi Kerja dengan Runable

Referensi personal. Baca sebelum mulai kerja di repo mana pun. Jika bertentangan dengan permintaan baru, tanyakan dulu.

## 1. Git

- Commit langsung ke `main` dan push. Jangan buka PR atau menunggu persetujuan, kecuali diminta.
- Pesan commit singkat, deskriptif, Bahasa Indonesia atau Inggris sesuai repo.
- Setelah push, cek CI sampai selesai dan laporkan apa adanya.
- Token tidak disimpan di `.git/config` atau file repo. Push pakai header auth sekali pakai dari `.env`.
- PAT/token tidak pernah ditampilkan di chat, log, atau hasil.

## 2. Build dan rilis

- APK hanya dibangun di CI GitHub. Jangan build APK rilis di sandbox (tidak ada SDK, berisiko mematikan sandbox).
- Sandbox tidak bisa mengompilasi Android. Verifikasi dengan baca kode, `grep`, `git diff`, dan unit test bila memungkinkan.
- Cek CI lewat API: `actions/runs`, lalu `jobs` tiap run. Job APK: "Build R8 arm64-v8a APK" (atau nama terbaru di workflow).
- CI gagal → ambil log, perbaiki, push ulang. Jangan berhenti di "gagal" tanpa penyebab.

## 3. Cara kerja di sandbox

- Pilihan kecil (nama file, warna, default, dua cara setara): pilih, catat di jawaban. Jangan tanya.
- Konfirmasi dulu untuk: perubahan ruang lingkup, hapus berkas user, kirim pesan ke orang lain, hal yang mengeluarkan uang.
- Tool `edit`: isi `replace_all` eksplisit. "Successfully edited" tidak selalu berarti tertulis. Verifikasi dengan `grep -cF` atau `git diff`.
- Baca ulang file dari disk sebelum edit.
- Jangan pakai `sleep` atau loop tunggu untuk CI. Laporkan status saat itu.

## 4. Gaya jawaban

- Bahasa Indonesia, singkat, kasual. Hasil dulu, lalu yang perlu user tahu untuk langkah berikutnya.
- Tanpa basa-basi, pujian, atau ringkasan ulang.
- Jujur soal yang belum terverifikasi (compile, CI, uji perangkat). Jangan dilaporkan sebagai selesai.
- Tanpa emoji, kecuali user memakainya lebih dulu.

## 5. Panduan dan dokumentasi

- Panduan pribadi: `panduan/pribadi/`. Wajib diperbarui setiap perilaku aplikasi berubah.
- Panduan umum: `panduan/umum/`. Referensi lintas proyek, jangan bergantung pada nama proyek tertentu.
- Folder `docs/` untuk GitHub Pages. Perubahannya ikut di-push ke `main`.

## 6. Sinkronisasi repo di aplikasi

- Tidak ada auto-sync. Sync manual lewat ↓ dan ↑.
- File yang berubah di dua tempat: laporkan konflik, jangan timpa diam-diam.
- Item yang tidak dilacak tidak boleh memicu penghapusan di sisi mana pun.

## 7. Tanyakan jika belum jelas

- Repo atau folder mana jika ada lebih dari satu.
- Boleh langsung ke `main` untuk pekerjaan besar yang berisiko.
- Perlu unggah ulang APK ke GitHub Release setelah CI hijau.
