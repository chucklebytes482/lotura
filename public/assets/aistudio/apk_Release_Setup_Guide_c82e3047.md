# Panduan Setup & Rilis APK Android (Umum)

Panduan umum ini menjelaskan cara menyiapkan **keystore release**, mengonfigurasi **GitHub Secrets**, menghubungkan **Firebase / Google Cloud Console** (jika aplikasi memakai Google Sign-In atau API Google), serta menyertakan **source code siap salin** (`.gitignore`, `app/build.gradle.kts`, `app/proguard-rules.pro`, dan `.github/workflows/release.yml`) untuk menghasilkan **APK release bertanda tangan (< 10 MB)** secara otomatis lewat **GitHub Actions** — tanpa menyimpan keystore maupun file APK di dalam repositori Git.

> **Penting:** Panduan ini bersifat umum untuk aplikasi Android berbasis Gradle (Kotlin DSL `.gradle.kts`). Ganti placeholder bertanda `<...>` sesuai identitas proyek Anda.

---

## 1. Membuat Keystore Release (Identitas Permanen Aplikasi)

Keystore release adalah tanda tangan kriptografi resmi aplikasi Anda. Jika file ini hilang atau berganti, pembaruan APK tidak dapat dipasang menimpa versi sebelumnya. **Simpan file `.jks` dan password-nya di pengelola sandi aman, JANGAN pernah mengunggahnya ke Git.**

Jalankan perintah berikut di terminal (di luar folder repositori Git):

```bash
keytool -genkeypair -v \
  -keystore release-upload.jks \
  -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype PKCS12 \
  -dname "CN=<NamaAplikasi>, OU=Mobile, O=<Organisasi>, L=<Kota>, ST=<Provinsi>, C=ID"
```

Atau jalankan tanpa `-dname` agar `keytool` menanyakan isian dan password secara interaktif.

> **Tips:** Jika memakai `storetype PKCS12`, samakan `KEY_PASSWORD` dengan `STORE_PASSWORD` agar konfigurasi CI lebih sederhana dan bebas error alias.

---

## 2. Mengubah Keystore & `google-services.json` Menjadi Base64

GitHub Secrets hanya menerima teks satu baris, sehingga file biner keystore (`.jks`) dan file konfigurasi Firebase (`google-services.json`, jika digunakan) perlu dikonversi menjadi teks Base64 tanpa baris baru.

### Linux
```bash
base64 -w 0 release-upload.jks > keystore.b64
base64 -w 0 app/google-services.json > google-services.b64
```

### macOS
```bash
base64 -i release-upload.jks | tr -d '\n' > keystore.b64
base64 -i app/google-services.json | tr -d '\n' > google-services.b64
```

### Windows PowerShell
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release-upload.jks")) | Set-Clipboard
```
*(Ulangi untuk `google-services.json` bila menggunakan Firebase/Google Auth, lalu hapus file `.b64` setelah disalin.)*

---

## 3. Menambahkan GitHub Secrets & File `.gitignore`

Buka repositori GitHub Anda → **Settings** → **Secrets and variables** → **Actions** → klik **New repository secret**.

Tambahkan secret berikut sesuai nama persisnya:

| Nama Secret | Isi Nilai | Wajib? |
|---|---|---|
| `RELEASE_KEYSTORE_BASE64` | Seluruh teks Base64 satu baris dari file `.jks` | Ya (untuk signed release permanen) |
| `STORE_PASSWORD` | Password keystore yang dibuat pada Langkah 1 | Ya |
| `KEY_PASSWORD` | Password key/alias (sama dengan `STORE_PASSWORD` pada PKCS12) | Ya |
| `KEY_ALIAS` | Nama alias di dalam keystore (mis. `upload`) | Disarankan |
| `GOOGLE_SERVICES_JSON_BASE64` | Seluruh teks Base64 dari `app/google-services.json` | Wajib jika memakai Firebase / Google Auth |

### Source Code Siap Salin: `.gitignore`
Tambahkan baris berikut ke `.gitignore` di root proyek agar file sensitif dan hasil build lokal tidak pernah ter-commit:

```gitignore
# Build & cache lokal
*.iml
.gradle/
.kotlin/
/build
/captures
.DS_Store
local.properties
.env

# File sensitif, keystore & konfigurasi lokal
*.jks
*.keystore
*.b64
keystore.properties
debug.keystore
google-services.json
app/google-services.json

# Direktori output APK sementara
dist/
releases/
*.apk
*.aab
```

---

## 4. Konfigurasi Signing, Kompresi & ProGuard (`app/build.gradle.kts` & `proguard-rules.pro`)

Konfigurasi standar agar build release tetap ringan (**< 10 MB**), mengambil versi otomatis dari CI (`CI_VERSION_CODE` & `CI_VERSION_NAME`), dan aman saat dijalankan di lingkungan lokal.

### Source Code Siap Salin: `app/build.gradle.kts` (Bagian `android { ... }`)

```kotlin
android {
    namespace = "<com.contoh.aplikasi>"
    compileSdk = 36

    defaultConfig {
        applicationId = "<com.contoh.aplikasi>"
        minSdk = 24
        targetSdk = 36

        val baseVersionCode = 1
        val baseVersionName = "1.0"
        val ciVersionCode = System.getenv("CI_VERSION_CODE")?.toIntOrNull()
        val ciVersionName = System.getenv("CI_VERSION_NAME")?.takeIf { it.isNotBlank() }

        versionCode = ciVersionCode ?: baseVersionCode
        versionName = ciVersionName ?: baseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/release-upload.jks"
            storeFile = file(keystorePath)
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isCrunchPngs = false
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                abiFilters += "arm64-v8a"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Biarkan debug tanpa shrink/arm64-only agar nyaman di emulator/perangkat uji
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }
}
```

### Source Code Siap Salin: `app/proguard-rules.pro`

```pro
# Pertahankan model data agar serialisasi JSON / Gson / Moshi / Firestore tidak terhapus R8
-keep class <com.contoh.aplikasi>.model.** { *; }

# Jika memakai kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }

# Jika memakai Moshi
-keepclassmembers class * extends com.squareup.moshi.JsonAdapter {
    public <init>(...);
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

-dontwarn org.codehaus.mojo.**
-dontwarn javax.annotation.**
```

---

## 5. Pendaftaran Fingerprint SHA-1 & SHA-256 (Jika Memakai Google Login / Firebase / Drive)

Build `debug` dan build `release` menggunakan sertifikat yang berbeda. **Jika SHA-1 & SHA-256 release belum didaftarkan, fitur Google Sign-In / Drive akan gagal di APK rilis meskipun berjalan normal di mode debug.**

Cetak sidik jari sertifikat dari keystore release Anda:

```bash
keytool -list -v -keystore release-upload.jks -alias upload
```

Salin baris **SHA1** dan **SHA256**, lalu daftarkan ke:
1. **Firebase Console:** **Project Settings** → **Your apps** (aplikasi Android dengan `applicationId` yang sama) → **Add fingerprint** (masukkan SHA-1 dan SHA-256). Jika berubah, unduh ulang `google-services.json` terbaru dan perbarui secret `GOOGLE_SERVICES_JSON_BASE64`.
2. **Google Cloud Console:** **APIs & Services** → **Credentials** → pilih **OAuth 2.0 Client ID (Android)** yang sesuai → verifikasi `Package name` dan `SHA-1`.
3. **OAuth Consent Screen & API:**
   - Aktifkan API yang dibutuhkan (misal: **Google Drive API**).
   - Tambahkan scope minimal (misal: `https://www.googleapis.com/auth/drive.file`).
   - Jika status aplikasi masih *Testing*, daftarkan akun penguji pada bagian **Test users**.

---

## 6. Workflow GitHub Actions Siap Salin (`.github/workflows/release.yml`)

Simpan file berikut sebagai `.github/workflows/release.yml`. Workflow ini:
1. Menghitung `versionCode` dan `versionName` otomatis dari `baseVersionCode` / `baseVersionName` + `GITHUB_RUN_NUMBER`.
2. Mendekode `RELEASE_KEYSTORE_BASE64` dan `GOOGLE_SERVICES_JSON_BASE64` (opsional bila tidak memakai Firebase) di runner sementara.
3. Menjalankan Unit Test & Robolectric (`gradle :app:testDebugUnitTest`).
4. Merakit APK R8 `arm64-v8a` (`gradle :app:assembleRelease`).
5. Memverifikasi tanda tangan dengan `apksigner` serta membuat `CERT_FINGERPRINT.txt` & `CHECKSUMS.txt`.
6. Mengunggah hasilnya ke **Workflow Artifacts** dan **GitHub Releases** tanpa menyimpan biner APK di repositori.

> **Catatan:** Ganti nilai `APP_SLUG` dan `APP_DISPLAY_NAME` pada blok `env:` di baris atas sesuai nama aplikasi Anda.

```yaml
name: Build & Publish Android Release

on:
  push:
    branches:
      - main
      - master
  workflow_dispatch:

permissions:
  contents: write

concurrency:
  group: android-release-${{ github.ref }}
  cancel-in-progress: false

env:
  APP_SLUG: "nama-aplikasi"
  APP_DISPLAY_NAME: "Nama Aplikasi"
  REQUIRE_GOOGLE_SERVICES: "false" # Ubah ke "true" jika memakai Firebase / Google Sign-In

jobs:
  build-release:
    name: Build R8 arm64-v8a APK
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Compute Automatic Version
        shell: bash
        run: |
          BASE_CODE=$(grep -E 'baseVersionCode\s*=' app/build.gradle.kts | head -n1 | sed -E 's/[^0-9]*([0-9]+).*/\1/')
          BASE_NAME=$(grep -E 'baseVersionName\s*=' app/build.gradle.kts | head -n1 | sed -E 's/.*"([^"]+)".*/\1/')
          BASE_CODE="${BASE_CODE:-1}"
          BASE_NAME="${BASE_NAME:-1.0}"
          RUN_NUM="${GITHUB_RUN_NUMBER:-1}"

          CI_VERSION_CODE=$((BASE_CODE + RUN_NUM))
          CI_VERSION_NAME="${BASE_NAME}.${RUN_NUM}"
          VERSION_TAG="v${CI_VERSION_NAME}"

          echo "CI_VERSION_CODE=${CI_VERSION_CODE}" >> "$GITHUB_ENV"
          echo "CI_VERSION_NAME=${CI_VERSION_NAME}" >> "$GITHUB_ENV"
          echo "VERSION_TAG=${VERSION_TAG}" >> "$GITHUB_ENV"
          echo "Release version: ${VERSION_TAG} (versionCode=${CI_VERSION_CODE})"

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"

      - name: Set up Android SDK
        shell: bash
        run: |
          SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
          SDKMANAGER=$(find "$SDK_DIR/cmdline-tools" -name sdkmanager -type f 2>/dev/null | sort -V | tail -n 1)
          if [ -z "$SDKMANAGER" ]; then
            echo "sdkmanager not found under $SDK_DIR/cmdline-tools" >&2
            exit 1
          fi
          (yes || true) | "$SDKMANAGER" --licenses >/dev/null
          "$SDKMANAGER" "platform-tools" "platforms;android-36" "build-tools;36.0.0"
          echo "ANDROID_HOME=$SDK_DIR" >> "$GITHUB_ENV"
          echo "ANDROID_SDK_ROOT=$SDK_DIR" >> "$GITHUB_ENV"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Prepare Release Keystore
        shell: bash
        env:
          RELEASE_KEYSTORE_BASE64: ${{ secrets.RELEASE_KEYSTORE_BASE64 }}
          STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
        run: |
          if [ ! -f debug.keystore ] && [ -f debug.keystore.base64 ]; then
            tr -d '[:space:]' < debug.keystore.base64 | base64 -d > debug.keystore
          fi

          if [ -n "$RELEASE_KEYSTORE_BASE64" ]; then
            if ! printf '%s' "$RELEASE_KEYSTORE_BASE64" | tr -d '[:space:]' | base64 -d > "$RUNNER_TEMP/upload.jks"; then
              echo "::error::RELEASE_KEYSTORE_BASE64 tidak valid. Encode ulang file .jks tanpa spasi/baris baru."
              exit 1
            fi

            CLEAN_STORE_PASSWORD=$(printf '%s' "$STORE_PASSWORD" | tr -d '\r\n' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
            CLEAN_KEY_PASSWORD=$(printf '%s' "${KEY_PASSWORD:-$CLEAN_STORE_PASSWORD}" | tr -d '\r\n' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
            SRC_ALIAS=$(printf '%s' "${KEY_ALIAS:-}" | tr -d '\r\n' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')

            if ! keytool -list -keystore "$RUNNER_TEMP/upload.jks" -storepass "$CLEAN_STORE_PASSWORD" >/dev/null 2>&1; then
              echo "::error::Gagal membuka keystore. Periksa STORE_PASSWORD dan RELEASE_KEYSTORE_BASE64."
              exit 1
            fi

            if [ -z "$SRC_ALIAS" ]; then
              SRC_ALIAS=$(keytool -list -keystore "$RUNNER_TEMP/upload.jks" -storepass "$CLEAN_STORE_PASSWORD" 2>/dev/null | awk -F',' '/PrivateKeyEntry/{print $1; exit}' | xargs)
            fi
            if [ -n "$SRC_ALIAS" ] && [ "$SRC_ALIAS" != "upload" ]; then
              keytool -changealias \
                -keystore "$RUNNER_TEMP/upload.jks" \
                -alias "$SRC_ALIAS" \
                -destalias upload \
                -storepass "$CLEAN_STORE_PASSWORD" \
                -keypass "$CLEAN_KEY_PASSWORD"
            fi

            echo "KEYSTORE_PATH=$RUNNER_TEMP/upload.jks" >> "$GITHUB_ENV"
            echo "STORE_PASSWORD=$CLEAN_STORE_PASSWORD" >> "$GITHUB_ENV"
            echo "KEY_PASSWORD=$CLEAN_KEY_PASSWORD" >> "$GITHUB_ENV"
          else
            # Fallback sementara jika secret keystore belum ditambahkan (jangan dipakai untuk produksi)
            TEMP_PASS="ci-temp-keystore-pass"
            keytool -genkeypair -v \
              -keystore "$RUNNER_TEMP/upload.jks" \
              -alias upload \
              -keyalg RSA -keysize 2048 -validity 10000 \
              -storepass "$TEMP_PASS" -keypass "$TEMP_PASS" \
              -dname "CN=${APP_DISPLAY_NAME}, OU=Mobile, O=Developer, L=Jakarta, ST=Jakarta, C=ID"
            echo "KEYSTORE_PATH=$RUNNER_TEMP/upload.jks" >> "$GITHUB_ENV"
            echo "STORE_PASSWORD=$TEMP_PASS" >> "$GITHUB_ENV"
            echo "KEY_PASSWORD=$TEMP_PASS" >> "$GITHUB_ENV"
          fi

      - name: Prepare google-services.json (Opsional Firebase/Google Auth)
        shell: bash
        env:
          GOOGLE_SERVICES_JSON_BASE64: ${{ secrets.GOOGLE_SERVICES_JSON_BASE64 }}
        run: |
          if [ -n "$GOOGLE_SERVICES_JSON_BASE64" ]; then
            printf '%s' "$GOOGLE_SERVICES_JSON_BASE64" | tr -d '[:space:]' | base64 -d > app/google-services.json
          fi
          if [ "$REQUIRE_GOOGLE_SERVICES" = "true" ]; then
            if [ ! -f app/google-services.json ]; then
              echo "::error::app/google-services.json tidak ditemukan. Tambahkan secret GOOGLE_SERVICES_JSON_BASE64."
              exit 1
            fi
            if ! grep -Eq '"client_type"[[:space:]]*:[[:space:]]*3' app/google-services.json; then
              echo "::error::app/google-services.json tidak memiliki Web OAuth Client (client_type: 3)."
              exit 1
            fi
          fi

      - name: Run Unit & Robolectric Tests
        run: gradle :app:testDebugUnitTest --no-daemon --no-build-cache --stacktrace

      - name: Build R8 Minified arm64-v8a APK
        run: gradle :app:assembleRelease --no-daemon --no-build-cache --stacktrace

      - name: Verify APK Signature & Package Artifacts
        shell: bash
        run: |
          mkdir -p dist
          APK_SRC="app/build/outputs/apk/release/app-release.apk"
          APK_OUT="dist/${APP_SLUG}-${VERSION_TAG}-arm64-v8a-release.apk"
          cp "$APK_SRC" "$APK_OUT"

          SDK_DIR="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
          APKSIGNER=$(find "$SDK_DIR/build-tools" -name apksigner 2>/dev/null | sort -V | tail -n 1)
          if [ -z "$APKSIGNER" ]; then
            echo "apksigner tidak ditemukan di $SDK_DIR/build-tools" >&2
            exit 1
          fi
          "$APKSIGNER" verify --print-certs "$APK_OUT" | tee dist/CERT_FINGERPRINT.txt
          sha256sum "$APK_OUT" | tee dist/CHECKSUMS.txt

      - name: Upload Build Artifacts
        uses: actions/upload-artifact@v4
        with:
          name: ${{ env.APP_SLUG }}-release-apk
          path: dist/*

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: ${{ env.VERSION_TAG }}
          name: ${{ env.APP_DISPLAY_NAME }} ${{ env.VERSION_TAG }}
          files: |
            dist/*.apk
            dist/CHECKSUMS.txt
            dist/CERT_FINGERPRINT.txt
          generate_release_notes: true
```

---

## 7. Troubleshooting Umum

| Gejala Error | Penyebab | Solusi |
|---|---|---|
| `Keystore was tampered with, or password was incorrect` | `STORE_PASSWORD` salah atau Base64 terpotong/berisi spasi | Encode ulang tanpa baris baru (`base64 -w 0`) dan salin ulang ke secret `RELEASE_KEYSTORE_BASE64`. |
| `No key with alias '...' found` | Nama alias di keystore berbeda dengan `keyAlias` di Gradle/CI | Cek alias asli dengan `keytool -list -keystore file.jks` dan samakan `KEY_ALIAS`. |
| Google Sign-In gagal / Error `ApiException: 10` di APK rilis | SHA-1 sertifikat rilis belum didaftarkan di Firebase/Google Cloud | Daftarkan SHA-1 & SHA-256 keystore rilis lalu pastikan `applicationId` cocok persis. |
| `google-services.json` missing `client_type: 3` | Konfigurasi Firebase belum punya Web OAuth Client | Aktifkan Google Sign-In di Firebase Authentication lalu unduh ulang `google-services.json`. |
| Ukuran APK rilis > 10 MB | R8/ShrinkResources nonaktif, multi-ABI ikut terkemas, atau aset/library berat | Pastikan `isMinifyEnabled = true`, `isShrinkResources = true`, `abiFilters += "arm64-v8a"`, dan hapus library tak terpakai. |
