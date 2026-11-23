package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DriveAccountEntity::class,
        GithubAccountEntity::class,
        GithubRepoEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun driveAccountDao(): DriveAccountDao
    abstract fun githubAccountDao(): GithubAccountDao
    abstract fun githubRepoDao(): GithubRepoDao

    companion object {
        /** v2 -> v3: tabel log dihapus. Akun Drive dan GitHub tidak disentuh. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS sync_logs")
            }
        }

        @Volatile
        private var instance: SyncDatabase? = null

        fun getInstance(context: Context): SyncDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SyncDatabase::class.java,
                    "sync_bebas.db"
                ).addMigrations(MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
