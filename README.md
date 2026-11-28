# Lotura

Aplikasi Android untuk menjelajahi dan mengelola berkas di Google Drive dan repositori GitHub.

## Keputusan

- Pilih banyak dengan tekan lama hanya di GitHub. Google Drive tidak berubah.
- Unduh folder GitHub menjadi `<folder>.zip` di `/storage/emulated/0/github/<owner>/<repo>/`. Unduh folder Drive tetap berupa direktori.
- Pindah, ganti nama, dan hapus di GitHub: satu commit per aksi bulk.
- Format pesan commit:
  - `Lotura: Pindah N item`
  - `Lotura: Ganti nama X ke Y`
  - `Lotura: Ubah <file>`
  - `Lotura: Hapus N item`
  Untuk satu item, hapus dan pindah memakai pola yang sama.
- Editor `.md` di GitHub: perubahan dikumpulkan di draft. Commit hanya dibuat saat "Selesai". "Batal" atau kembali dengan perubahan membuang draft setelah konfirmasi.
- Tujuan pindah dipilih lewat dialog pemilih folder di repositori yang sama. Root repositori bisa dipilih.

## Build dan tes

Build dan tes hanya dijalankan di CI (`.github/workflows/release.yml`). Jangan jalankan Gradle lokal.
