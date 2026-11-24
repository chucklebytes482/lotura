# 06 — Standar Sinkronisasi Online dan Offline

Standar lintas aplikasi untuk dua mode sinkronisasi. Dipakai sebagai acuan saat membangun aplikasi baru atau memeriksa aplikasi yang sudah ada.

---

## 1. Istilah dan arah

| Istilah | Arti | Contoh di Lotura |
|---|---|---|
| **Timpa ke atas** | Data dari sisi perangkat diterapkan ke sisi tujuan yang lebih tinggi | Online: unggah ke Drive atau GitHub. Offline: folder HP ke data internal aplikasi |
| **Timpa ke bawah** | Data dari sisi sumber ditulis ke sisi perangkat | Online: download dari Drive atau GitHub ke HP. Offline: data internal aplikasi ke folder HP |

Istilah ini dipakai konsisten di seluruh dokumen dan antarmuka. Jangan memakai "upload", "download", atau "push" sebagai pengganti di dokumen standar. Istilah teknis itu boleh muncul di keterangan tombol atau kode.

---

## 2. Mode online

Sumber adalah layanan jarak jauh (misalnya Google Drive atau GitHub). Perangkat adalah folder lokal.

- **Timpa ke atas:** perangkat ke sumber.
- **Timpa ke bawah:** sumber ke perangkat.

### Aturan per file

Dibandingkan nama dan ukuran file di tujuan:

1. Nama belum ada di tujuan: file **disalin**.
2. Nama sama, ukuran berbeda: file **ditimpa**.
3. Nama sama, ukuran sama: file **dilewati**.

Hanya file yang nama dan ukurannya cocok yang dilewati. Perubahan isi dengan ukuran yang sama tidak terdeteksi. Ini batasan yang diterima dan dicatat (lihat bagian 6).

### File yang hanya ada di tujuan

File di tujuan yang tidak ada di sumber **dibiarkan**. Tidak ada penghapusan otomatis dalam mode online.

### Dokumen khusus

Dokumen layanan yang ukurannya tidak bisa dicek (misalnya Docs, Sheets, Slides) **selalu disalin ulang**.

### Item tersembunyi

File dan folder yang namanya diawali titik tidak ikut sinkron, dan jumlahnya dilaporkan sebagai item yang dilewati.

---

## 3. Mode offline

Sumber tidak ada. Dua sisi adalah data lokal:

- **Aplikasi:** data internal aplikasi (misalnya database atau penyimpanan privat).
- **Perangkat:** file di folder HP yang bisa dibaca pengguna.

- **Timpa ke atas:** perangkat ke aplikasi. Perubahan di file folder HP diterapkan ke data internal aplikasi.
- **Timpa ke bawah:** aplikasi ke perangkat. Data internal aplikasi ditulis ke file di folder HP.

### Aturan per file

Sama dengan mode online (bagian 2): nama belum ada disalin, nama sama dan ukuran beda ditimpa, nama dan ukuran sama dilewati.

### File yang hanya ada di tujuan

Dibiarkan. Tidak ada penghapusan otomatis.

---

## 4. Perbandingan ringkas

| Aspek | Online | Offline |
|---|---|---|
| Sumber | Layanan jarak jauh | Data internal aplikasi atau folder HP |
| Timpa ke atas | Perangkat ke layanan | Perangkat ke aplikasi |
| Timpa ke bawah | Layanan ke perangkat | Aplikasi ke perangkat |
| Aturan file sama | Nama dan ukuran | Nama dan ukuran |
| File yatim di tujuan | Dibiarkan | Dibiarkan |
| Dokumen khusus | Disalin ulang | Tidak berlaku |

---

## 5. Contoh alur

**Timpa ke atas (online):** folder HP berisi `catatan.md` 1.200 byte. Di GitHub ada `catatan.md` 900 byte. Hasilnya: file di GitHub ditimpa dengan versi 1.200 byte. Jika GitHub juga punya `lampiran.pdf` yang tidak ada di HP, file itu dibiarkan.

**Timpa ke bawah (offline):** data aplikasi punya `profil.json` 500 byte dan folder HP sudah punya `profil.json` 500 byte. Hasilnya: dilewati, karena nama dan ukuran sama.

---

## 6. Batasan dan keputusan terbuka

- **Ukuran sama, isi beda:** tidak terdeteksi. Untuk file penting, pilih pembanding yang lebih kuat (hash atau waktu ubah) dan catat di dokumen aplikasi.
- **Auto-sync yang menghapus:** implementasi yang menghapus item di tujuan saat sumber tidak punya item itu **melanggar** standar ini. Perbaiki implementasi, jangan ubah standarnya.
- **Mode offline belum diuji pada aplikasi nyata.** Aturan offline adalah standar, bukan hasil pengujian.
- **Konflik** (file yang berubah di dua sisi sekaligus) tidak diputuskan otomatis oleh standar ini. Laporkan dan biarkan pengguna memilih.

---

## 7. Checklist untuk aplikasi baru

- [ ] Istilah "timpa ke atas" dan "timpa ke bawah" tampil konsisten di antarmuka dan dokumen.
- [ ] Aturan per file mengikuti bagian 2 atau 3.
- [ ] Tidak ada penghapusan otomatis di sisi tujuan.
- [ ] Dokumen khusus dan item tersembunyi ditangani dan dilaporkan.
- [ ] Batasan bagian 6 dicatat di panduan aplikasi.
