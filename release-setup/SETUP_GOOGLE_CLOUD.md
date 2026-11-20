# Google Cloud & OAuth release Lotura

Signing key permanen saja tidak mengaktifkan OAuth. Konfigurasi berikut harus diperiksa dengan akun pemilik Google Cloud; pekerjaan ini tidak memiliki akses GCP dan belum memverifikasi keadaan console.

## Identitas proyek

| Komponen | Nilai / status bukti |
|---|---|
| Project ID | `gen-lang-client-0224561583` (dari lampiran) |
| Project number | `1000578483438` (dari lampiran) |
| Application ID | `com.aistudio.lotura` (dikonfirmasi dari Gradle) |
| Brand / scope | `Lotura` / `https://www.googleapis.com/auth/drive` (laporan lampiran, belum diverifikasi di console) |
| Debug client | `1000578483438-kr5lrgrhndrd4i838bqt7kmtnlsffcdj.apps.googleusercontent.com` (laporan lampiran) |
| Debug SHA-1 | `06:E9:FF:98:CE:CF:2D:FC:62:4C:79:50:34:9B:2B:1A:6F:72:46:EC` (laporan lampiran) |
| Release SHA-1/SHA-256 | Lihat `CERT_FINGERPRINT.txt` pada folder ini; dihasilkan langsung dari keystore permanen |

## 1. Aktifkan Google Drive API

[Buka Drive API](https://console.cloud.google.com/apis/library/drive.googleapis.com?project=gen-lang-client-0224561583) → Enable jika belum aktif. Atau dengan gcloud pada komputer tepercaya:

```bash
gcloud auth login
bash release-setup/setup_google_cloud.sh --enable-api "$HOME/.lotura/release-upload.jks"
```

Skrip menggunakan `--project` dan tidak mengubah global project gcloud. Pengaktifan memerlukan izin GCP yang sesuai; keberadaan CLI tidak berarti sudah login.

## 2. Tambahkan test user

[Audience](https://console.cloud.google.com/auth/audience?project=gen-lang-client-0224561583) → bila publishing status **Testing**, tambahkan Gmail yang benar-benar dipakai login aplikasi pada Test users. Alamat Gmail belum diberikan dalam sesi ini. Mode Testing bukan pengganti verifikasi aplikasi untuk distribusi publik; review persyaratan scope Drive sebelum produksi.

## 3. Daftarkan client Android release

[Credentials → Create OAuth client](https://console.cloud.google.com/apis/credentials/oauthclient?project=gen-lang-client-0224561583):

- Application type: **Android**.
- Name: **Lotura Android Release**.
- Package name: **`com.aistudio.lotura`** (bukan namespace `com.example`).
- SHA-1: salin baris `SHA-1:` dari `release-setup/CERT_FINGERPRINT.txt`.

Jangan tempel SHA-256 pada kolom SHA-1. Client debug dan release dapat terpisah. Jika nanti menggunakan Play App Signing, APK yang didistribusikan Play ditandatangani app-signing key milik Play, bukan selalu upload key ini; daftarkan SHA-1 app-signing certificate dari Play Console juga.

## 4. Uji pada APK terbaru

Pastikan sertifikat APK release terbaru sama dengan fingerprint folder ini. Hubungkan Google, berikan izin Drive, lalu uji akses sinkronisasi pada perangkat. Keberhasilan pembuatan keystore atau push bukan bukti OAuth bekerja.

| Gejala | Pemeriksaan |
|---|---|
| `ApiException: 10` | Package name dan SHA-1 APK terpasang harus cocok dengan Android OAuth client |
| Access blocked / verification | Periksa publishing status, test user dan persyaratan verifikasi scope |
| `403 accessNotConfigured` | Drive API di proyek OAuth belum aktif |
| `403 insufficientPermissions` | Periksa scope dan izin yang benar-benar diberikan; hubungkan ulang akun jika diperlukan |

## Referensi resmi

- [Google authorization Android](https://developers.google.com/identity/authorization/android)
- [Google OAuth consent setup](https://developers.google.com/workspace/guides/configure-oauth-consent)
- [Drive API authorization scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
- [Android app signing](https://developer.android.com/studio/publish/app-signing)

Dokumen awal pengguna menyebut konfigurasi debug sudah otomatis selesai; di sini klaim itu tetap ditandai sebagai laporan, bukan pemeriksaan langsung atas akun Google Cloud.