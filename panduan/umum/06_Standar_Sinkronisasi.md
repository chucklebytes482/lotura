# 06 — Standar Sinkronisasi Online dan Offline

Standar lintas aplikasi untuk dua mode sync. Acuan saat membangun atau memeriksa aplikasi.

## 1. Istilah dan arah

| Istilah | Arti | Contoh di Lotura |
|---|---|---|
| **Timpa ke atas** | Data sisi perangkat diterapkan ke sisi tujuan | Online: unggah ke Drive/GitHub. Offline: folder HP ke data internal |
| **Timpa ke bawah** | Data sisi sumber ditulis ke perangkat | Online: download ke HP. Offline: data internal ke folder HP |

Istilah ini konsisten di dokumen dan antarmuka. Jangan pakai "upload", "download", atau "push" sebagai pengganti di dokumen standar. Istilah teknis boleh di keterangan tombol atau kode.

## 2. Mode online

Sumber: layanan jarak jauh (Google Drive, GitHub). Perangkat: folder lokal.

- **Timpa ke atas:** perangkat ke sumber.
- **Timpa ke bawah:** sumber ke perangkat.

**Aturan per file** (nama dan ukuran di tujuan):

1. Nama belum ada → **disalin**.
2. Nama sama, ukuran beda → **ditimpa**.
3. Nama dan ukuran sama → **dilewati**.

**File yang hanya ada di tujuan:** dibiarkan. Tidak ada penghapusan otomatis.

**Dokumen khusus** (Docs, Sheets, Slides, ukuran tidak bisa dicek): selalu disalin ulang.

**Item tersembunyi** (nama berawalan titik): tidak ikut sync, dilaporkan sebagai dilewati.

## 3. Mode offline

Sumber tidak ada. Dua sisi adalah data lokal:

- **Aplikasi:** data internal (database atau penyimpanan privat).
- **Perangkat:** file di folder yang bisa dibaca pengguna.

- **Timpa ke atas:** perangkat ke aplikasi.
- **Timpa ke bawah:** aplikasi ke perangkat.

Aturan per file sama dengan bagian 2. File yang hanya ada di tujuan dibiarkan.

## 4. Perbandingan ringkas

| Aspek | Online | Offline |
|---|---|---|
| Sumber | Layanan jarak jauh | Data internal atau folder HP |
| Timpa ke atas | Perangkat ke layanan | Perangkat ke aplikasi |
| Timpa ke bawah | Layanan ke perangkat | Aplikasi ke perangkat |
| Aturan file sama | Nama dan ukuran | Nama dan ukuran |
| File yatim di tujuan | Dibiarkan | Dibiarkan |
| Dokumen khusus | Disalin ulang | Tidak berlaku |

## 5. Contoh

- **Timpa ke atas (online):** HP punya `catatan.md` 1.200 byte, GitHub punya 900 byte → GitHub ditimpa 1.200 byte. `lampiran.pdf` yang hanya di GitHub dibiarkan.
- **Timpa ke bawah (offline):** data aplikasi dan folder HP sama-sama `profil.json` 500 byte → dilewati.

## 6. Batasan

- **Ukuran sama, isi beda:** tidak terdeteksi. Untuk file penting, pakai pembanding lebih kuat (hash atau waktu ubah) dan catat di panduan aplikasi.
- **Auto-sync yang menghapus** di sisi tujuan melanggar standar. Perbaiki implementasi, jangan ubah standar.
- **Mode offline** belum diuji di aplikasi nyata. Aturannya standar, bukan hasil uji.
- **Konflik** (berubah di dua sisi) tidak diputuskan otomatis. Laporkan dan biarkan user memilih.

## 7. Checklist aplikasi baru

- [ ] "Timpa ke atas" dan "timpa ke bawah" konsisten di antarmuka dan dokumen.
- [ ] Aturan per file mengikuti bagian 2 atau 3.
- [ ] Tidak ada penghapusan otomatis di sisi tujuan.
- [ ] Dokumen khusus dan item tersembunyi ditangani dan dilaporkan.
- [ ] Batasan bagian 6 dicatat di panduan aplikasi.
