# Lotura

Aplikasi Android untuk menjelajahi dan mengelola berkas di Google Drive dan repositori GitHub.

## Keputusan

- Pilih banyak dengan tekan lama hanya di GitHub. Google Drive tidak berubah.
- Unduh GitHub masuk satu folder datar `/storage/emulated/0/github/` tanpa owner dan repo. Nama diberi awalan repo: `<repo>_<nama>`, folder menjadi `<repo>_<nama>.zip`. Contoh: `repo-x_docs.zip`.
- Item GitHub yang isinya sama dengan yang sudah ada di perangkat dilewati tanpa unduh ulang. Pengecekan lewat SHA: blob GitHub untuk berkas, dan komentar zip (SHA tree) untuk folder. Item yang beda menimpa berkas lokal.
- Aturan lewati hanya berlaku untuk GitHub. Unduh folder Drive tetap berupa direktori di `/storage/emulated/0/google drive/<email>/`.
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
