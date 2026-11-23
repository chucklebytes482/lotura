# 02 — Setup Rilis APK, Anti-429, & CI (Umum)

Konfigurasi siap pakai untuk Gradle R8 (`< 10 MB`), mirror anti-429, Robolectric Gradle 9+, GitHub Secrets, dan rilis APK lewat **GitHub Release** di satu repo. Ganti `<...>` sesuai aplikasi.

---

## 1. GitHub Secrets & `.gitignore`

Tambahkan di **Settings → Secrets and variables → Actions** repo:

| Secret | Nilai |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | Base64 1 baris dari `release.jks` (lihat `03_Generator_Kredensial_VeraCrypt_(umum).md`) |
| `STORE_PASSWORD` / `KEY_PASSWORD` / `KEY_ALIAS` | Password keystore, password key, alias (`upload`) |

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

## 4. Workflow Rilis (`.github/workflows/release.yml`)

Urutan langkah di workflow (satu repo, tanpa repo tambahan):

1. Hitung versi dari `baseVersionCode` / `baseVersionName` di `app/build.gradle.kts` dan nomor run CI.
2. Pasang JDK 21 dan Android SDK (`platforms;android-36`, `build-tools;36.0.0`).
3. Siapkan keystore dari secret `RELEASE_KEYSTORE_BASE64`. Jika kosong, buat keystore sementara.
4. Unduh JAR Robolectric lebih dulu (cache `~/.m2`) untuk menghindari HTTP 429.
5. Jalankan `:app:testDebugUnitTest`, lalu `:app:assembleRelease`.
6. Kemas APK ke `dist/`, verifikasi tanda tangan dengan `apksigner verify`, dan buat `CHECKSUMS.txt`.
7. Unggah `dist/*.apk`, `CHECKSUMS.txt`, dan `CERT_FINGERPRINT.txt` sebagai GitHub Release `vX.Y.Z` di repo ini.

Hasil rilis: halaman **Releases** di repo. Unduhan terbaru selalu tersedia di
`https://github.com/<user>/<slug>/releases/latest/download/<slug>-latest-arm64-v8a.apk`.

---
