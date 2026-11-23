# 02 — Setup Rilis APK, Anti-429, & CI Dual-Repo (Umum)

Konfigurasi siap pakai untuk Gradle R8 (`< 10 MB`), mirror anti-429, Robolectric Gradle 9+, GitHub Secrets **1-PAT untuk semua repo**, dan rilis **Dual-Repo** (repo kode **Private** → repo publik `<slug>-site`). Ganti `<...>` sesuai aplikasi.

---

## 1. GitHub Secrets & `.gitignore`

Tambahkan di **Settings → Secrets and variables → Actions** repo kode (**Private**):

| Secret | Nilai |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | Base64 1 baris dari `release.jks` (lihat `03_Generator_Kredensial_VeraCrypt_(umum).md`) |
| `STORE_PASSWORD` / `KEY_PASSWORD` / `KEY_ALIAS` | Password keystore, password key, alias (`upload`) |
| `PUBLIC_DIST_TOKEN` | **1 Fine-grained PAT untuk semua repo** (izin tunggal `Contents: Read and write`). Pilih *Only select repositories* (tambah centang tiap `<slug>-site` baru tanpa ubah token) atau *All repositories* (nol-klik). |

`.gitignore` wajib (jangan blokir `<slug_app>_secrets_download.html` agar mudah diunduh lalu dihapus):

```gitignore
.gradle/
/build
local.properties
.env
*.jks
*.keystore
*.b64
*.hc
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

## 2. Mirror Anti-429 & Kecepatan Build

`settings.gradle.kts` — pasang mirror Google Maven Central **sebelum** `mavenCentral()` pada `pluginManagement` dan `dependencyResolutionManagement`:

```kotlin
maven("https://maven-central.storage-download.googleapis.com/maven2")
mavenCentral()
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.parallel=true
android.nonTransitiveRClass=true
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.workers.max=4
kotlin.compiler.execution.strategy=in-process
```

---

## 3. Gradle R8, Robolectric Offline-First (Gradle 9), & ProGuard

`app/build.gradle.kts` (jangan ubah `debug.keystore*` atau blok `signingConfigs` bawaan AI Studio jika sudah ada):

```kotlin
class RobolectricDepsArgProvider(
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    val jars: ConfigurableFileCollection
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> {
        val dir = jars.files.firstOrNull { it.extension == "jar" }?.parentFile ?: return emptyList()
        return listOf("-Drobolectric.offline=true", "-Drobolectric.dependency.dir=${dir.absolutePath}")
    }
}

android {
    defaultConfig {
        val baseVersionCode = 1; val baseVersionName = "1.0"
        versionCode = System.getenv("CI_VERSION_CODE")?.toIntOrNull() ?: baseVersionCode
        versionName = System.getenv("CI_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: baseVersionName
    }
    buildTypes {
        release {
            isCrunchPngs = false; isMinifyEnabled = true; isShrinkResources = true
            ndk { abiFilters += "arm64-v8a" }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    dependenciesInfo { includeInApk = false; includeInBundle = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            val rel = ".m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7/android-all-instrumented-16-robolectric-13921718-i7.jar"
            val jar = listOfNotNull("${System.getProperty("user.home")}/$rel", "/home/appuser/$rel", System.getenv("HOME")?.let { "$it/$rel" })
                .map(::File).firstOrNull { it.exists() && it.length() > 0L }
            if (jar != null) {
                test.systemProperty("robolectric.offline", "true")
                test.systemProperty("robolectric.dependency.dir", jar.parentFile.absolutePath)
            } else {
                test.jvmArgumentProviders.add(RobolectricDepsArgProvider(objects.fileCollection().from(configurations.getByName("robolectricSdk"))))
            }
        }
    }
}

val robolectricSdk = configurations.create("robolectricSdk") { isTransitive = false }
dependencies { "robolectricSdk"("org.robolectric:android-all-instrumented:16-robolectric-13921718-i7") }
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

## 4. Workflow Rilis Dual-Repo (`.github/workflows/release.yml`)

```yaml
name: Build & Publish Android Release
on:
  push:
    branches: [main, master]
    paths: ['app/**', 'gradle/**', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', '.github/workflows/release.yml']
  workflow_dispatch:
permissions: { contents: write }
concurrency: { group: android-release-${{ github.ref }}, cancel-in-progress: false }
env:
  APP_SLUG: "<slug>"
  APP_DISPLAY_NAME: "<NamaApp>"
  PUBLIC_SITE_REPO: "<user>/<slug>-site"

jobs:
  build-release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Compute Version
        run: |
          set -euo pipefail
          BC=$(grep -E 'baseVersionCode\s*=' app/build.gradle.kts | head -n1 | sed -E 's/[^0-9]*([0-9]+).*/\1/'); BC="${BC:-1}"
          BN=$(grep -E 'baseVersionName\s*=' app/build.gradle.kts | head -n1 | sed -E 's/.*"([^"]+)".*/\1/'); BN="${BN:-1.0}"
          ESC=$(printf '%s' "$BN" | sed 's/\./\\./g')
          MAX=$(git ls-remote --tags origin "refs/tags/v${BN}.*" 2>/dev/null | sed -nE "s|.*refs/tags/v${ESC}\.([0-9]+)$|\1|p" | sort -n | tail -n1 || true)
          N=$(( ${GITHUB_RUN_NUMBER:-1} > ${MAX:-0} ? ${GITHUB_RUN_NUMBER:-1} : ${MAX:-0} + 1 ))
          { echo "CI_VERSION_CODE=$((BC+N))"; echo "CI_VERSION_NAME=${BN}.${N}"; echo "VERSION_TAG=v${BN}.${N}"; } >> "$GITHUB_ENV"
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "21" }
      - name: Set up Android SDK
        run: |
          SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
          SM=$(find "$SDK/cmdline-tools" -name sdkmanager -type f | sort -V | tail -n1)
          (yes || true) | "$SM" --licenses >/dev/null
          "$SM" "platform-tools" "platforms;android-36" "platforms;android-36.1" "build-tools;36.0.0"
          { echo "ANDROID_HOME=$SDK"; echo "ANDROID_SDK_ROOT=$SDK"; } >> "$GITHUB_ENV"
      - uses: gradle/actions/setup-gradle@v4
      - name: Prepare Keystore
        env:
          RELEASE_KEYSTORE_BASE64: ${{ secrets.RELEASE_KEYSTORE_BASE64 }}
          STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: |
          set -euo pipefail; umask 077
          [ ! -f debug.keystore ] && [ -f debug.keystore.base64 ] && tr -d '[:space:]' < debug.keystore.base64 | base64 -d > debug.keystore
          if [ -n "$RELEASE_KEYSTORE_BASE64" ]; then
            printf '%s' "$RELEASE_KEYSTORE_BASE64" | tr -d '[:space:]' | base64 -d > "$RUNNER_TEMP/upload.jks"
            SP=$(printf '%s' "$STORE_PASSWORD" | tr -d '\r\n' | xargs)
            KP=$(printf '%s' "${KEY_PASSWORD:-$SP}" | tr -d '\r\n' | xargs)
            keytool -list -keystore "$RUNNER_TEMP/upload.jks" -storepass "$SP" >/dev/null
          else
            SP="ci-temp-keystore-pass"; KP="$SP"
            keytool -genkeypair -keystore "$RUNNER_TEMP/upload.jks" -alias upload -keyalg RSA -keysize 2048 -validity 10000 -storepass "$SP" -keypass "$KP" -dname "CN=${APP_DISPLAY_NAME}, O=Dev, C=ID" >/dev/null 2>&1
          fi
          { echo "KEYSTORE_PATH=$RUNNER_TEMP/upload.jks"; echo "STORE_PASSWORD=$SP"; echo "KEY_PASSWORD=$KP"; } >> "$GITHUB_ENV"
      - uses: actions/cache@v4
        with:
          path: ~/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7
          key: robolectric-sdk-16-13921718-i7
      - name: Pre-fetch Robolectric JAR
        run: |
          set -euo pipefail
          D="$HOME/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7"; J="$D/android-all-instrumented-16-robolectric-13921718-i7.jar"
          [ -s "$J" ] && exit 0; mkdir -p "$D"
          P="org/robolectric/android-all-instrumented/16-robolectric-13921718-i7/android-all-instrumented-16-robolectric-13921718-i7.jar"
          curl -fLsS --retry 5 --retry-delay 5 --retry-all-errors -o "$J.tmp" "https://maven-central.storage-download.googleapis.com/maven2/$P" \
            || curl -fLsS --retry 5 --retry-delay 5 --retry-all-errors -o "$J.tmp" "https://repo.maven.apache.org/maven2/$P"
          mv "$J.tmp" "$J"
      - run: gradle :app:testDebugUnitTest --no-daemon --no-build-cache --stacktrace
      - run: gradle :app:assembleRelease --no-daemon --no-build-cache --stacktrace
      - name: Package & Verify APK
        run: |
          set -euo pipefail; mkdir -p dist
          V="${APP_SLUG}-${VERSION_TAG}-arm64-v8a-release.apk"; L="${APP_SLUG}-latest-arm64-v8a.apk"
          cp app/build/outputs/apk/release/app-release.apk "dist/$V"; cp "dist/$V" "dist/$L"
          $(find "$ANDROID_HOME/build-tools" -name apksigner | sort -V | tail -n1) verify --print-certs "dist/$V" > dist/CERT_FINGERPRINT.txt
          (cd dist && sha256sum "$V" "$L") > dist/CHECKSUMS.txt
      - uses: actions/upload-artifact@v4
        with: { name: "${{ env.APP_SLUG }}-release-apk", path: "dist/*", retention-days: 7 }
      - uses: softprops/action-gh-release@v2
        with: { tag_name: "${{ env.VERSION_TAG }}", name: "${{ env.APP_DISPLAY_NAME }} ${{ env.VERSION_TAG }}", files: "dist/*.apk\ndist/CHECKSUMS.txt\ndist/CERT_FINGERPRINT.txt", generate_release_notes: true }
      - name: Publish Public Site
        id: site
        env: { PUBLIC_DIST_TOKEN: "${{ secrets.PUBLIC_DIST_TOKEN }}" }
        run: |
          set -euo pipefail
          [ -n "$PUBLIC_DIST_TOKEN" ] || { echo "published=false" >> "$GITHUB_OUTPUT"; exit 0; }
          W="$RUNNER_TEMP/site"; rm -rf "$W"
          H="AUTHORIZATION: basic $(printf 'x-access-token:%s' "$PUBLIC_DIST_TOKEN" | base64 | tr -d '\n')"
          U="https://github.com/${PUBLIC_SITE_REPO}.git"
          if git -c http.extraheader="$H" ls-remote --exit-code --heads "$U" main >/dev/null 2>&1; then
            git -c http.extraheader="$H" clone --depth 1 --branch main "$U" "$W"
          else
            mkdir -p "$W" && git init -b main "$W" && git -C "$W" remote add origin "$U"
          fi
          find "$W" -mindepth 1 -maxdepth 1 ! -name '.git' -exec rm -rf {} +; cp -R docs/. "$W/"
          STAMP=$(TZ='Asia/Jakarta' date '+%d %b %Y, %H:%M WIB')
          [ -f "$W/index.html" ] && sed -i -E "s|<!-- RELEASE_UPDATED_META -->.*<!-- /RELEASE_UPDATED_META -->|<!-- RELEASE_UPDATED_META -->Diperbarui: ${VERSION_TAG} • ${STAMP}<!-- /RELEASE_UPDATED_META -->|g" "$W/index.html"
          touch "$W/.nojekyll"
          git -C "$W" config user.name "github-actions[bot]"; git -C "$W" config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git -C "$W" add -A
          git -C "$W" diff --cached --quiet || { git -C "$W" commit -m "chore: update site (${VERSION_TAG})"; git -C "$W" -c http.extraheader="$H" push -u origin main; }
          echo "published=true" >> "$GITHUB_OUTPUT"
      - if: steps.site.outputs.published == 'true'
        uses: softprops/action-gh-release@v2
        with:
          repository: ${{ env.PUBLIC_SITE_REPO }}
          token: ${{ secrets.PUBLIC_DIST_TOKEN }}
          tag_name: ${{ env.VERSION_TAG }}
          target_commitish: main
          name: "${{ env.APP_DISPLAY_NAME }} ${{ env.VERSION_TAG }}"
          files: "dist/${{ env.APP_SLUG }}-latest-arm64-v8a.apk"
          generate_release_notes: false
      - if: always()
        run: rm -rf "$RUNNER_TEMP/upload.jks" "$RUNNER_TEMP/site"
```

---

## 5. Workflow Cepat Situs (`.github/workflows/publish-site.yml`) & `docs/index.html`

Gunakan workflow terpisah untuk `paths: ['docs/**', '.github/workflows/publish-site.yml']`: salin `docs/.` ke `<user>/<slug>-site` branch `main` via `PUBLIC_DIST_TOKEN` sambil **mempertahankan** blok `<!-- RELEASE_UPDATED_META -->...<!-- /RELEASE_UPDATED_META -->` yang sudah ada di `index.html` target.

Pasang di `docs/index.html` tepat di bawah tombol unduh (`https://github.com/<user>/<slug>-site/releases/latest/download/<slug>-latest-arm64-v8a.apk`):

```html
<p class="download-meta" id="release-updated">
  <!-- RELEASE_UPDATED_META -->Versi terbaru siap diunduh<!-- /RELEASE_UPDATED_META -->
</p>
<script>
fetch("https://api.github.com/repos/<user>/<slug>-site/releases/latest")
  .then(r => r.ok ? r.json() : null).then(d => {
    if (!d || !d.published_at) return;
    const dt = new Date(d.published_at);
    const tgl = dt.toLocaleDateString("id-ID", { day: "2-digit", month: "short", year: "numeric" });
    const jam = dt.toLocaleTimeString("id-ID", { hour: "2-digit", minute: "2-digit", hour12: false });
    document.getElementById("release-updated").textContent = "Diperbarui: " + (d.tag_name ? d.tag_name + " • " : "") + tgl + ", " + jam;
  }).catch(() => {});
</script>
```

> **GitHub Pages (1×):** di repo `<user>/<slug>-site` → **Settings → Pages** → *Deploy from a branch* → `main` / `/ (root)`.
