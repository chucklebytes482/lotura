# Setup release permanen Lotura

Paket ini menambahkan skrip, cadangan terenkripsi, dan contoh workflow **tanpa mengubah workflow aktif**. Keystore mentah dan password tidak boleh masuk Git. Simpan kedua password di password manager; kehilangan key atau password dapat menghalangi pembaruan APK.

## Urutan yang perlu dilakukan

1. Unduh `lotura-release-key.enc`, `backup_keystore.py`, dan `CERT_FINGERPRINT.txt` ke komputer pribadi. Simpan salinan cadangan kedua di tempat terpisah. Password backup dan keystore adalah dua password yang telah Anda masukkan melalui formulir aman, bukan berkas dalam repo.
2. Pulihkan cadangan dan periksa fingerprint seperti bagian di bawah. Jangan membuat key baru untuk setiap release.
3. Signing secrets disiapkan sebelum push paket ini; lihat `VERIFICATION.md` untuk hasil aktual. Nilainya tidak bisa dibaca kembali dari GitHub. Jika perlu memulihkan secrets, gunakan skrip `configure_github_secrets.sh`.
4. Ikuti `SETUP_GOOGLE_CLOUD.md`: aktifkan Drive API, tambah test user, lalu buat OAuth Android release dengan SHA-1 pada `CERT_FINGERPRINT.txt`.
5. Jika ingin keamanan fail-closed, salin `release.yml.example` ke `.github/workflows/release.yml`, review, commit dan push. Contoh ini otomatis pada push `main` serta manual, tetapi **belum aktif** di folder ini. Jangan menambahkan workflow kedua sekaligus karena dapat membuat dua release untuk satu push.
6. Di tab Actions, pastikan tests/build sukses. Unduh APK dan `CERT_FINGERPRINT.txt` dari release terbaru. Cocokkan SHA-256 sertifikat dengan fingerprint key permanen; periksa checksum APK.

## Pulihkan dan verifikasi keystore

Prasyarat: Python 3, JDK 21, Bash (Linux/macOS atau Git Bash/WSL). Android/Termux: gunakan Python dan OpenJDK yang tersedia, atau pindahkan proses ini ke komputer tepercaya. Jangan lakukan pada perangkat publik.

```bash
python -m pip install -r release-setup/requirements.txt
mkdir -p "$HOME/.lotura"
python release-setup/backup_keystore.py decrypt \
  release-setup/lotura-release-key.enc "$HOME/.lotura/release-upload.jks"
bash release-setup/setup_google_cloud.sh "$HOME/.lotura/release-upload.jks"
```

Skrip dekripsi meminta **password backup**, skrip fingerprint meminta **password keystore**. Alias `upload`, tipe JKS; store password dan key password sama. Skrip menolak overwrite. Jangan meletakkan hasil dekripsi dalam folder repo.

Cadangan memakai Scrypt (N=131072, r=8, p=1), salt acak 16 byte, AES-256-GCM dan nonce acak 12 byte; header diautentikasi. Dibutuhkan memori sekitar 128 MiB. Base64 bukan enkripsi. Enkripsi mengurangi risiko, bukan alasan mengabaikan password kuat; backup publik tetap dapat diserang secara offline.

## Pulihkan signing secrets

Pasang GitHub CLI, login dengan `gh auth login` pada akun yang berhak mengatur repository secrets:

```bash
bash release-setup/configure_github_secrets.sh \
  "$HOME/.lotura/release-upload.jks" chucklebytes482/lotura
```

| Secret | Isi |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | Base64 keystore, diunggah lewat stdin oleh skrip |
| `STORE_PASSWORD` | Password keystore |
| `KEY_PASSWORD` | Password yang sama |
| `KEY_ALIAS` | `upload` |

Jangan memakai `bash -x`, mencetak environment, atau menyimpan Base64 di repo. GitHub secrets tidak menyediakan fungsi baca kembali; cadangan pribadi tetap diperlukan. Token GitHub yang digunakan untuk setup tidak ada dalam paket; cabut token sesudah pekerjaan selesai jika tidak dibutuhkan lagi.

## Membuat key untuk proyek baru saja

**Jangan jalankan ini untuk mengganti key permanen yang sudah dibuat dalam pekerjaan ini.**

```bash
bash release-setup/create_keystore.sh "$HOME/.lotura/new-project.jks"
python release-setup/backup_keystore.py encrypt \
  "$HOME/.lotura/new-project.jks" "$HOME/.lotura/new-project.enc"
```

## Catatan workflow dan rilis lama

Audit rilis `v1.0.1` sampai `v1.0.4` menunjukkan SHA-256 sertifikat berbeda untuk setiap rilis. Workflow lama membuat key sementara ketika secrets kosong. Key lama tidak bisa dipulihkan dari fingerprint/APK. Key baru menstabilkan **rilis selanjutnya** tetapi tidak menandatangani pembaruan kompatibel untuk APK lama. Sebelum uninstall APK lama, ekspor/cadangkan data dan pastikan pemulihan tersedia; uninstall dapat menghapus data lokal.

Workflow aktif tetap apa adanya sesuai permintaan. Dengan secrets yang lengkap ia memakai key permanen; jika secrets dihapus, fallback sementara masih berlaku. Contoh pengganti menolak secrets kosong, memverifikasi sertifikat dan membersihkan key runner. Tidak ada build APK dilakukan di sandbox.

Contoh mempertahankan skema versi `1.0.N` dengan melihat tag lama agar pergantian workflow tidak mereset nomor. Jika skema base version di Gradle berubah, sesuaikan perhitungan versi contoh. Repo memakai Gradle 9.3.1 dan SDK 36 minor 1; contoh memasang Gradle eksplisit karena wrapper lengkap tidak tersedia pada audit awal. SDK/package availability tetap harus diuji di Actions.

## Troubleshooting dan rollback

- **Password salah/backup rusak:** dekripsi gagal tanpa menghasilkan key. Unduh ulang backup; jangan menimpa backup yang diketahui baik.
- **Alias/password salah:** cocokkan alias `upload`, fingerprint dan secret; periksa dengan `keytool`, jangan generate key baru.
- **Install update gagal:** sertifikat APK lama berbeda; cadangkan data sebelum install ulang.
- **Actions gagal:** lihat step pertama yang gagal. Jangan mengganti signing key hanya untuk melewati kegagalan build.
- **Rollback workflow:** pulihkan file workflow lama jika perlu, tetapi pertahankan empat secrets dan key permanen. Jangan rollback ke key sementara.
- **Menghapus backup dari repo:** jangan anggap penghapusan membersihkan Git history. Karena backup terenkripsi, lindungi password; penghapusan history tidak menjamin hilangnya salinan pihak lain.

## Referensi

- [Android app signing](https://developer.android.com/studio/publish/app-signing)
- [GitHub Actions secrets](https://docs.github.com/actions/security-guides/using-secrets-in-github-actions)
- [gcloud services enable](https://docs.cloud.google.com/sdk/gcloud/reference/services/enable)
- Bukti proyek: `.github/workflows/release.yml`, `app/build.gradle.kts`, dan aset fingerprint [GitHub Releases](https://github.com/chucklebytes482/lotura/releases), diperiksa 20 November 2026.