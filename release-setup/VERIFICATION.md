# Hasil verifikasi setup Lotura

Tanggal: 20 November 2026. Baseline repo: `11c9b20d185657742646672dde96fd6c2be1d20a`.

## Berhasil diperiksa sebelum commit

- GitHub API mengautentikasi akun `chucklebytes482`; akses push repository terkonfirmasi. Daftar repository secrets awal kosong.
- Aset fingerprint empat rilis `v1.0.1`–`v1.0.4` menunjukkan empat SHA-256 berbeda. Tidak ditemukan signing key permanen melalui secrets yang tersedia; fingerprint saja tidak memulihkan private key.
- Keystore permanen dibuat sekali: tipe JKS, alias `upload`, RSA 2048, validitas 10000 hari. Password minimal 16 karakter dan berbeda dari password backup; nilai rahasia tidak dicatat.
- Cadangan `lotura-release-key.enc` diuji dekripsi: hasil identik byte-for-byte dengan key sumber. Password salah gagal tanpa menulis output. Pembuatan key pada path yang sudah ada ditolak.
- `bash -n` dan opsi `--help` lulus untuk tiga skrip shell.
- YAML contoh workflow berhasil diparse. Ini pemeriksaan struktur, bukan bukti workflow berhasil dijalankan.
- Empat secrets berhasil diunggah melalui GitHub CLI: `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`. Nama secrets terlihat kembali; nilai secret tidak bisa dibaca kembali.
- Keystore mentah dan formulir rahasia berada di luar repository, tidak disertakan dalam folder ini.

## Belum dilakukan / batas verifikasi

- Google Cloud Console, Drive API, test users dan OAuth client release **belum** diperiksa atau dikonfigurasi dengan akun pemilik.
- Tidak melakukan build APK atau menjalankan unit/Robolectric tests di sandbox. Runtime aplikasi/OAuth belum diuji.
- Tidak mengaktifkan contoh workflow. Workflow aktif `.github/workflows/release.yml` tidak diubah sesuai permintaan; masih mempunyai fallback key sementara jika secrets dihapus.
- Push ke `main` akan memicu workflow lama yang otomatis. Status build dan sertifikat APK hasilnya perlu diverifikasi melalui Actions/Release. Keberhasilan push bukan keberhasilan build.

## Checklist pemilik

- [ ] Unduh cadangan terenkripsi dan simpan salinan kedua di luar GitHub.
- [ ] Simpan kedua password di password manager; uji pemulihan di komputer pribadi.
- [ ] Daftarkan release SHA-1 dari `CERT_FINGERPRINT.txt` pada Google Cloud.
- [ ] Tambahkan Gmail test user dan aktifkan Drive API bila perlu.
- [ ] Periksa hasil Actions untuk commit setup dan sertifikat APK terbaru.
- [ ] Aktifkan contoh fail-closed ketika siap; ganti workflow lama, jangan duplikasi.
- [ ] Cabut fine-grained token setup setelah tidak dibutuhkan lagi.

Commit final dan URL folder dilaporkan pada chat setelah remote diverifikasi; tidak dicantumkan sebagai klaim sebelum push terjadi.