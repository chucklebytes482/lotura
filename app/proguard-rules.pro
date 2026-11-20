# Application ID: com.aistudio.lotura | Namespace: com.example
# Pertahankan model data, entitas Room, dan DTO agar R8 tidak menghapus field penting
-keep class com.example.data.db.** { *; }
-keep class com.example.drive.** { *; }
-keep class com.example.github.** { *; }
-keep class com.example.sync.** { *; }

# Room & WorkManager
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# OkHttp & Kotlin Coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.codehaus.mojo.**
-dontwarn javax.annotation.**
