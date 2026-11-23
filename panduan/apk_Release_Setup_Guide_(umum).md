# Panduan Setup & Rilis APK Android (Umum)

Referensi teknis siap salin untuk keystore rilis, GitHub Secrets, `.gitignore`, mirror Maven anti-429, optimasi `gradle.properties`, Gradle R8 (`< 10 MB`), ProGuard, serta pola **Dual-Repository** (repo kode **Private** + repo situs & unduhan **Public `<slug>-site`**). Ganti placeholder `<...>` sesuai identitas aplikasi.

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

Tambahkan secret berikut di **Settings → Secrets and variables → Actions** pada repositori kode utama (**Private**):

| Nama Secret | Isi |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | Teks Base64 1 baris dari file `.jks` |
| `STORE_PASSWORD` | Password keystore |
| `KEY_PASSWORD` | Password key/alias |
| `KEY_ALIAS` | `upload` |
| `PUBLIC_DIST_TOKEN` | *(Dual-Repo)* Fine-grained PAT dengan akses **hanya 1 repo publik `<user>/<slug>-site`** dan izin **Contents: Read and write** |

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

## 3. Mirror Maven Anti-429 & Optimasi Kecepatan Build

### `settings.gradle.kts` (Wajib untuk mencegah HTTP 429 di GitHub Actions)

Tambahkan mirror resmi Google Maven Central **sebelum** `mavenCentral()` agar runner GitHub Actions tidak terkena limitasi HTTP 429 dari `repo.maven.apache.org`:

```kotlin
pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    maven("https://maven-central.storage-download.googleapis.com/maven2")
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    maven("https://maven-central.storage-download.googleapis.com/maven2")
    mavenCentral()
  }
}
```

### `gradle.properties` (Cache & Kecepatan Build Inkremental)

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.parallel=true
android.nonTransitiveRClass=true
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.workers.max=4
kotlin.compiler.execution.strategy=in-process
```

> **Catatan Cache:** Jangan menyimpan biner cache di dalam folder proyek Git. Cache Gradle, Android SDK, dan Robolectric sudah otomatis disimpan di luar folder proyek oleh sistem container AI Studio maupun server cache GitHub Actions.

---

## 4. Gradle R8, Robolectric Offline-First, & ProGuard (`app/build.gradle.kts` & `app/proguard-rules.pro`)

Letakkan class `RobolectricDepsArgProvider` tepat di bawah blok `plugins { ... }`, dan gunakan `configurations.create("robolectricSdk")` agar bebas peringatan *deprecation* pada Gradle 9+:

```kotlin
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

class RobolectricDepsArgProvider(
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val jars: ConfigurableFileCollection
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> {
        val depDir = jars.files.firstOrNull { it.extension == "jar" }?.parentFile ?: return emptyList()
        return listOf(
            "-Drobolectric.offline=true",
            "-Drobolectric.dependency.dir=${depDir.absolutePath}"
        )
    }
}

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
        unitTests.all { test ->
            val robolectricJar = listOfNotNull(
                "${System.getProperty("user.home")}/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7/android-all-instrumented-16-robolectric-13921718-i7.jar",
                "/home/appuser/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7/android-all-instrumented-16-robolectric-13921718-i7.jar",
                System.getenv("HOME")?.let {
                    "$it/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7/android-all-instrumented-16-robolectric-13921718-i7.jar"
                }
            ).map { File(it) }.firstOrNull { it.exists() && it.length() > 0L }

            if (robolectricJar != null) {
                test.systemProperty("robolectric.offline", "true")
                test.systemProperty("robolectric.dependency.dir", robolectricJar.parentFile.absolutePath)
            } else {
                // Fallback CI online (aman untuk Gradle Configuration Cache via FileCollection)
                test.jvmArgumentProviders.add(
                    RobolectricDepsArgProvider(objects.fileCollection().from(configurations.getByName("robolectricSdk")))
                )
            }
        }
    }
}

val robolectricSdk = configurations.create("robolectricSdk") {
    isTransitive = false
}

dependencies {
    "robolectricSdk"("org.robolectric:android-all-instrumented:16-robolectric-13921718-i7")
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

## 5. Workflow Rilis Dual-Repo & Anti-429 (`.github/workflows/release.yml`)

Workflow ini berjalan di repo kode **Private**, mem-prefetch & menyimpan cache JAR Robolectric dari mirror Google, membangun APK R8 `arm64-v8a`, menyimpan cadangan rilis privat (`retention-days: 7` untuk hemat kuota 500 MB), lalu otomatis menerbitkan `docs/` dan berkas `<slug>-latest-arm64-v8a.apk` ke repo publik **`<user>/<slug>-site`**:

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
  APP_SLUG: "<slug-aplikasi>"
  APP_DISPLAY_NAME: "<Nama Aplikasi>"
  PUBLIC_SITE_REPO: "<username>/<slug-aplikasi>-site"

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

      - name: Cache Robolectric Android SDK JAR
        uses: actions/cache@v4
        with:
          path: ~/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7
          key: robolectric-sdk-16-13921718-i7

      - name: Pre-fetch Robolectric Android SDK JAR
        shell: bash
        run: |
          set -euo pipefail
          SDK_DIR="$HOME/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7"
          SDK_JAR="$SDK_DIR/android-all-instrumented-16-robolectric-13921718-i7.jar"
          if [ -s "$SDK_JAR" ]; then exit 0; fi
          mkdir -p "$SDK_DIR"
          TMP_JAR="${SDK_JAR}.tmp"
          PRIMARY_URL="https://maven-central.storage-download.googleapis.com/maven2/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7/android-all-instrumented-16-robolectric-13921718-i7.jar"
          FALLBACK_URL="https://repo.maven.apache.org/maven2/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7/android-all-instrumented-16-robolectric-13921718-i7.jar"
          if curl --fail --location --silent --show-error --retry 5 --retry-delay 5 --retry-all-errors -o "$TMP_JAR" "$PRIMARY_URL"; then
            mv "$TMP_JAR" "$SDK_JAR"
          elif curl --fail --location --silent --show-error --retry 5 --retry-delay 5 --retry-all-errors -o "$TMP_JAR" "$FALLBACK_URL"; then
            mv "$TMP_JAR" "$SDK_JAR"
          else
            rm -f "$TMP_JAR"
            exit 1
          fi

      - name: Run Unit & Robolectric Tests
        run: gradle :app:testDebugUnitTest --no-daemon --no-build-cache --stacktrace

      - name: Build R8 Minified arm64-v8a APK
        run: gradle :app:assembleRelease --no-daemon --no-build-cache --stacktrace

      - name: Verify APK & Package Artifacts
        shell: bash
        run: |
          set -euo pipefail
          mkdir -p dist
          APK_VERSIONED="${APP_SLUG}-${VERSION_TAG}-arm64-v8a-release.apk"
          APK_LATEST="${APP_SLUG}-latest-arm64-v8a.apk"
          cp app/build/outputs/apk/release/app-release.apk "dist/$APK_VERSIONED"
          cp app/build/outputs/apk/release/app-release.apk "dist/$APK_LATEST"
          APKSIGNER=$(find "${ANDROID_HOME}/build-tools" -name apksigner | sort -V | tail -n 1)
          "$APKSIGNER" verify --print-certs "dist/$APK_VERSIONED" | tee dist/CERT_FINGERPRINT.txt
          (cd dist && sha256sum "$APK_VERSIONED" "$APK_LATEST") | tee dist/CHECKSUMS.txt

      - uses: actions/upload-artifact@v4
        with:
          name: ${{ env.APP_SLUG }}-release-apk
          path: dist/*
          retention-days: 7

      - name: Create Private Backup Release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: ${{ env.VERSION_TAG }}
          name: ${{ env.APP_DISPLAY_NAME }} ${{ env.VERSION_TAG }}
          files: |
            dist/*.apk
            dist/CHECKSUMS.txt
            dist/CERT_FINGERPRINT.txt
          generate_release_notes: true

      - name: Publish Web Panduan to Public Site
        id: public_site
        shell: bash
        env:
          PUBLIC_DIST_TOKEN: ${{ secrets.PUBLIC_DIST_TOKEN }}
        run: |
          set -euo pipefail
          if [ -z "$PUBLIC_DIST_TOKEN" ]; then
            echo "::warning::Secret PUBLIC_DIST_TOKEN belum diatur; publikasi situs dilewati."
            echo "published=false" >> "$GITHUB_OUTPUT"
            exit 0
          fi
          WORK_DIR="$RUNNER_TEMP/public-site-sync"
          rm -rf "$WORK_DIR"
          AUTH_HEADER="AUTHORIZATION: basic $(printf 'x-access-token:%s' "$PUBLIC_DIST_TOKEN" | base64 | tr -d '\n')"
          REMOTE_URL="https://github.com/${PUBLIC_SITE_REPO}.git"
          if git -c http.extraheader="$AUTH_HEADER" ls-remote --exit-code --heads "$REMOTE_URL" main >/dev/null 2>&1; then
            git -c http.extraheader="$AUTH_HEADER" clone --depth 1 --branch main "$REMOTE_URL" "$WORK_DIR"
          else
            mkdir -p "$WORK_DIR"
            git init -b main "$WORK_DIR"
            git -C "$WORK_DIR" remote add origin "$REMOTE_URL"
          fi
          find "$WORK_DIR" -mindepth 1 -maxdepth 1 ! -name '.git' -exec rm -rf {} +
          cp -R docs/. "$WORK_DIR/"
          touch "$WORK_DIR/.nojekyll"
          git -C "$WORK_DIR" config user.name "github-actions[bot]"
          git -C "$WORK_DIR" config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git -C "$WORK_DIR" add -A
          if ! git -C "$WORK_DIR" diff --cached --quiet; then
            git -C "$WORK_DIR" commit -m "chore: perbarui situs (${VERSION_TAG})"
            git -C "$WORK_DIR" -c http.extraheader="$AUTH_HEADER" push -u origin main
          fi
          rm -rf "$WORK_DIR"
          echo "published=true" >> "$GITHUB_OUTPUT"

      - name: Publish Public APK Release
        if: steps.public_site.outputs.published == 'true'
        uses: softprops/action-gh-release@v2
        with:
          repository: ${{ env.PUBLIC_SITE_REPO }}
          token: ${{ secrets.PUBLIC_DIST_TOKEN }}
          tag_name: ${{ env.VERSION_TAG }}
          target_commitish: main
          name: ${{ env.APP_DISPLAY_NAME }} ${{ env.VERSION_TAG }}
          body: |
            Rilis terbaru **${{ env.APP_DISPLAY_NAME }} ${{ env.VERSION_TAG }}**.
          files: |
            dist/${{ env.APP_SLUG }}-latest-arm64-v8a.apk
          generate_release_notes: false

      - name: Clean Private Key & Temp Sync
        if: always()
        shell: bash
        run: rm -rf "$RUNNER_TEMP/upload.jks" "$RUNNER_TEMP/public-site-sync"
```

---

## 6. Workflow Ringan Khusus Situs (`.github/workflows/publish-site.yml`)

Tambahkan workflow terpisah agar suntingan `docs/**` saja langsung memperbarui `<user>/<slug>-site` dalam ~15 detik tanpa menghabiskan menit build Android di repo Private:

```yaml
name: Publish Public Site

on:
  push:
    branches: [main]
    paths:
      - 'docs/**'
      - '.github/workflows/publish-site.yml'
  workflow_dispatch:

env:
  PUBLIC_SITE_REPO: "<username>/<slug-aplikasi>-site"

jobs:
  publish-site:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Sync docs/ to Public Site
        shell: bash
        env:
          PUBLIC_DIST_TOKEN: ${{ secrets.PUBLIC_DIST_TOKEN }}
        run: |
          set -euo pipefail
          [ -n "$PUBLIC_DIST_TOKEN" ] || { echo "::error::Secret PUBLIC_DIST_TOKEN belum diatur."; exit 1; }
          WORK_DIR="$RUNNER_TEMP/public-site-sync"
          rm -rf "$WORK_DIR"
          AUTH_HEADER="AUTHORIZATION: basic $(printf 'x-access-token:%s' "$PUBLIC_DIST_TOKEN" | base64 | tr -d '\n')"
          REMOTE_URL="https://github.com/${PUBLIC_SITE_REPO}.git"
          if git -c http.extraheader="$AUTH_HEADER" ls-remote --exit-code --heads "$REMOTE_URL" main >/dev/null 2>&1; then
            git -c http.extraheader="$AUTH_HEADER" clone --depth 1 --branch main "$REMOTE_URL" "$WORK_DIR"
          else
            mkdir -p "$WORK_DIR"
            git init -b main "$WORK_DIR"
            git -C "$WORK_DIR" remote add origin "$REMOTE_URL"
          fi
          find "$WORK_DIR" -mindepth 1 -maxdepth 1 ! -name '.git' -exec rm -rf {} +
          cp -R docs/. "$WORK_DIR/"
          touch "$WORK_DIR/.nojekyll"
          git -C "$WORK_DIR" config user.name "github-actions[bot]"
          git -C "$WORK_DIR" config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git -C "$WORK_DIR" add -A
          if ! git -C "$WORK_DIR" diff --cached --quiet; then
            git -C "$WORK_DIR" commit -m "chore: perbarui halaman panduan"
            git -C "$WORK_DIR" -c http.extraheader="$AUTH_HEADER" push -u origin main
          fi
          rm -rf "$WORK_DIR"
```

> **Aktivasi GitHub Pages (Satu Kali):** Di repo publik `<username>/<slug>-site` → **Settings → Pages** → pilih **Deploy from a branch**, branch **`main`**, folder **`/ (root)`**. Tombol unduh langsung di `docs/index.html` cukup mengarah ke:
> `https://github.com/<username>/<slug>-site/releases/latest/download/<slug>-latest-arm64-v8a.apk`

---

## 7. Troubleshooting Singkat

| Error | Solusi |
|---|---|
| `Keystore was tampered with` / gagal buka keystore | Pastikan `RELEASE_KEYSTORE_BASE64` 1 baris tanpa spasi dan `STORE_PASSWORD` / `KEY_PASSWORD` cocok. |
| `No key with alias '...' found` | Samakan `KEY_ALIAS` dengan alias di keystore (`upload`). |
| Ukuran APK > 10 MB | Aktifkan `isMinifyEnabled`, `isShrinkResources`, `abiFilters += "arm64-v8a"`, dan komentari/hapus library tak terpakai. |
| Robolectric gagal **HTTP 429 Too Many Requests** / Configuration Cache | Pasang mirror Google Maven Central sebelum `mavenCentral()` di `settings.gradle.kts`, aktifkan langkah `actions/cache` + pre-fetch atomik `curl --retry` di `release.yml`, dan verifikasi `it.exists() && it.length() > 0L`. |
| Peringatan *deprecated* Gradle 9 (`by configurations.creating`) | Ganti dengan `val robolectricSdk = configurations.create("robolectricSdk") { isTransitive = false }`. |
| Kuota penyimpanan artefak repo Private penuh (500 MB) | Pastikan `retention-days: 7` terpasang pada `actions/upload-artifact@v4`. |
| GitHub Pages mati saat repo diubah ke Private (paket Free) | Gunakan pola **Dual-Repo**: jaga repo kode tetap **Private** dan publikasikan `docs/` + APK terbaru otomatis ke repo publik `<slug>-site` via `PUBLIC_DIST_TOKEN`. |
