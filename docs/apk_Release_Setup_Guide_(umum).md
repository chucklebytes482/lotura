# Panduan Setup & Rilis APK Android (Umum)

Referensi teknis siap salin untuk keystore rilis, GitHub Secrets, `.gitignore`, Gradle R8 (`< 10 MB`), ProGuard, dan workflow GitHub Actions. Ganti placeholder `<...>` sesuai identitas aplikasi.

---

## 1. Keystore Rilis & Encode Base64

Buat keystore di luar repositori Git (atau gunakan `Prompt_Generator_Kredensial_Rilis_(umum).md` untuk generate otomatis ke 1 file HTML pengunduh `.hc` di root):

```bash
keytool -genkeypair -v \
  -keystore release-upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10950 \
  -dname "CN=<NamaApp>, OU=Mobile, O=<Organisasi>, C=ID"

# Encode 1 baris tanpa spasi/baris baru (Linux)
base64 -w 0 release-upload.jks > keystore.b64
```

---

## 2. GitHub Secrets & `.gitignore`

Tambahkan 4 secret keystore berikut di **Settings → Secrets and variables → Actions** (tidak perlu `GOOGLE_SERVICES_JSON_BASE64` untuk aplikasi 100% luring):

| Nama Secret | Isi |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | Teks Base64 1 baris dari file `.jks` |
| `STORE_PASSWORD` | Password keystore |
| `KEY_PASSWORD` | Password key/alias |
| `KEY_ALIAS` | `upload` |

Tambahkan ke `.gitignore` (jangan blokir `<slug_app>_secrets_download.html` di root agar bisa langsung diunduh lewat browser lalu dihapus pengguna):

```gitignore
.gradle/
/build
local.properties
.env
*.jks
*.keystore
*.b64
keystore.properties
debug.keystore
google-services.json
app/google-services.json
dist/
releases/
*.apk
*.aab
```

---

## 3. Gradle R8 & ProGuard (`app/build.gradle.kts` & `app/proguard-rules.pro`)

```kotlin
android {
    namespace = "<com.contoh.aplikasi>"
    compileSdk = 36

    defaultConfig {
        applicationId = "<com.aistudio.kata>"
        minSdk = 24
        targetSdk = 36

        val baseVersionCode = 1
        val baseVersionName = "1.0"
        versionCode = System.getenv("CI_VERSION_CODE")?.toIntOrNull() ?: baseVersionCode
        versionName = System.getenv("CI_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: baseVersionName
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "${rootDir}/release-upload.jks")
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
            ndk { abiFilters += "arm64-v8a" }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            val robolectricJar = listOfNotNull(
                "${System.getProperty("user.home")}/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7/android-all-instrumented-16-robolectric-13921718-i7.jar",
                "/home/appuser/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7/android-all-instrumented-16-robolectric-13921718-i7.jar",
                System.getenv("HOME")?.let { "$it/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7/android-all-instrumented-16-robolectric-13921718-i7.jar" }
            ).map { File(it) }.firstOrNull { it.exists() }

            if (robolectricJar != null) {
                systemProperty("robolectric.offline", "true")
                systemProperty("robolectric.dependency.dir", robolectricJar.parentFile.absolutePath)
            } else {
                // Fallback CI online (aman untuk Gradle Configuration Cache via FileCollection)
                jvmArgumentProviders.add(RobolectricDepsArgProvider(objects.fileCollection().from(robolectricSdk)))
            }
        }
    }
}
```

`app/proguard-rules.pro`:

```pro
-keep class <com.contoh.aplikasi>.model.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
```

---

## 4. Workflow GitHub Actions (`.github/workflows/release.yml`)

```yaml
name: Build & Publish Android Release

on:
  push:
    branches: [main, master]
    paths:
      - 'app/**'
      - 'gradle/**'
      - 'build.gradle.kts'
      - 'settings.gradle.kts'
      - 'gradle.properties'
      - '.github/workflows/release.yml'
  workflow_dispatch:

permissions:
  contents: write

concurrency:
  group: android-release-${{ github.ref }}
  cancel-in-progress: false

env:
  APP_SLUG: "nama-aplikasi"
  APP_DISPLAY_NAME: "Nama Aplikasi"

jobs:
  build-release:
    name: Build R8 arm64-v8a APK
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Compute Automatic Version
        shell: bash
        run: |
          set -euo pipefail
          BASE_CODE=$(grep -E 'baseVersionCode\s*=' app/build.gradle.kts | head -n1 | sed -E 's/[^0-9]*([0-9]+).*/\1/')
          BASE_NAME=$(grep -E 'baseVersionName\s*=' app/build.gradle.kts | head -n1 | sed -E 's/.*"([^"]+)".*/\1/')
          BASE_CODE="${BASE_CODE:-1}"
          BASE_NAME="${BASE_NAME:-1.0}"
          RUN_NUM="${GITHUB_RUN_NUMBER:-1}"
          ESCAPED_BASE=$(printf '%s' "$BASE_NAME" | sed 's/\./\\./g')
          MAX_TAG=$(git ls-remote --tags origin "refs/tags/v${BASE_NAME}.*" 2>/dev/null \
            | sed -nE "s|.*refs/tags/v${ESCAPED_BASE}\.([0-9]+)$|\1|p" | sort -n | tail -n 1 || true)
          NEXT_NUM=$(( RUN_NUM > ${MAX_TAG:-0} ? RUN_NUM : ${MAX_TAG:-0} + 1 ))
          echo "CI_VERSION_CODE=$((BASE_CODE + NEXT_NUM))" >> "$GITHUB_ENV"
          echo "CI_VERSION_NAME=${BASE_NAME}.${NEXT_NUM}" >> "$GITHUB_ENV"
          echo "VERSION_TAG=v${BASE_NAME}.${NEXT_NUM}" >> "$GITHUB_ENV"

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"

      - name: Set up Android SDK
        shell: bash
        run: |
          SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
          SDKMANAGER=$(find "$SDK_DIR/cmdline-tools" -name sdkmanager -type f | sort -V | tail -n 1)
          (yes || true) | "$SDKMANAGER" --licenses >/dev/null
          "$SDKMANAGER" "platform-tools" "platforms;android-36" "platforms;android-36.1" "build-tools;36.0.0"
          echo "ANDROID_HOME=$SDK_DIR" >> "$GITHUB_ENV"
          echo "ANDROID_SDK_ROOT=$SDK_DIR" >> "$GITHUB_ENV"

      - uses: gradle/actions/setup-gradle@v4

      - name: Prepare Release Keystore
        shell: bash
        env:
          RELEASE_KEYSTORE_BASE64: ${{ secrets.RELEASE_KEYSTORE_BASE64 }}
          STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
        run: |
          set -euo pipefail
          umask 077
          if [ ! -f debug.keystore ] && [ -f debug.keystore.base64 ]; then
            tr -d '[:space:]' < debug.keystore.base64 | base64 -d > debug.keystore
          fi
          if [ -n "$RELEASE_KEYSTORE_BASE64" ]; then
            printf '%s' "$RELEASE_KEYSTORE_BASE64" | tr -d '[:space:]' | base64 -d > "$RUNNER_TEMP/upload.jks"
            CLEAN_STORE=$(printf '%s' "$STORE_PASSWORD" | tr -d '\r\n' | xargs)
            CLEAN_KEY=$(printf '%s' "${KEY_PASSWORD:-$CLEAN_STORE}" | tr -d '\r\n' | xargs)
            keytool -list -keystore "$RUNNER_TEMP/upload.jks" -storepass "$CLEAN_STORE" >/dev/null
            echo "KEYSTORE_PATH=$RUNNER_TEMP/upload.jks" >> "$GITHUB_ENV"
            echo "STORE_PASSWORD=$CLEAN_STORE" >> "$GITHUB_ENV"
            echo "KEY_PASSWORD=$CLEAN_KEY" >> "$GITHUB_ENV"
          else
            TEMP_PASS="ci-temp-keystore-pass"
            keytool -genkeypair -keystore "$RUNNER_TEMP/upload.jks" -alias upload \
              -keyalg RSA -keysize 2048 -validity 10000 \
              -storepass "$TEMP_PASS" -keypass "$TEMP_PASS" \
              -dname "CN=${APP_DISPLAY_NAME}, O=Developer, C=ID" >/dev/null 2>&1
            echo "KEYSTORE_PATH=$RUNNER_TEMP/upload.jks" >> "$GITHUB_ENV"
            echo "STORE_PASSWORD=$TEMP_PASS" >> "$GITHUB_ENV"
            echo "KEY_PASSWORD=$TEMP_PASS" >> "$GITHUB_ENV"
          fi

      - name: Run Unit & Robolectric Tests
        run: gradle :app:testDebugUnitTest --no-daemon --no-build-cache

      - name: Build R8 Minified arm64-v8a APK
        run: gradle :app:assembleRelease --no-daemon --no-build-cache

      - name: Verify APK & Package Artifacts
        shell: bash
        run: |
          mkdir -p dist
          APK_OUT="dist/${APP_SLUG}-${VERSION_TAG}-arm64-v8a-release.apk"
          cp app/build/outputs/apk/release/app-release.apk "$APK_OUT"
          APKSIGNER=$(find "${ANDROID_HOME}/build-tools" -name apksigner | sort -V | tail -n 1)
          "$APKSIGNER" verify --print-certs "$APK_OUT" | tee dist/CERT_FINGERPRINT.txt
          sha256sum "$APK_OUT" | tee dist/CHECKSUMS.txt

      - uses: actions/upload-artifact@v4
        with:
          name: ${{ env.APP_SLUG }}-release-apk
          path: dist/*

      - uses: softprops/action-gh-release@v2
        with:
          tag_name: ${{ env.VERSION_TAG }}
          name: ${{ env.APP_DISPLAY_NAME }} ${{ env.VERSION_TAG }}
          files: |
            dist/*.apk
            dist/CHECKSUMS.txt
            dist/CERT_FINGERPRINT.txt
          generate_release_notes: true

      - name: Clean Private Key
        if: always()
        shell: bash
        run: rm -f "$RUNNER_TEMP/upload.jks"
```

---

## 5. Troubleshooting Singkat

| Error | Solusi |
|---|---|
| `Keystore was tampered with` / gagal buka keystore | Pastikan `RELEASE_KEYSTORE_BASE64` 1 baris tanpa spasi dan `STORE_PASSWORD` / `KEY_PASSWORD` cocok. |
| `No key with alias '...' found` | Samakan `KEY_ALIAS` dengan alias di keystore (`upload`). |
| Ukuran APK > 10 MB | Aktifkan `isMinifyEnabled`, `isShrinkResources`, `abiFilters += "arm64-v8a"`, dan hapus library tak terpakai (Firebase/Auth/OkHttp). |
| Robolectric gagal HTTP 429 / Configuration Cache | Gunakan resolusi cache lokal `android-all-instrumented` (`user.home/.m2` & `/home/appuser/.m2`) + `objects.fileCollection().from(robolectricSdk)`. |
