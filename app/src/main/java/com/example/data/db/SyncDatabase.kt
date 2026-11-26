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
        GithubRepoEntity::class,
        SyncStateEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun driveAccountDao(): DriveAccountDao
    abstract fun githubAccountDao(): GithubAccountDao
    abstract fun githubRepoDao(): GithubRepoDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        /** v2 -> v3: tabel log dihapus. Akun Drive dan GitHub tidak disentuh. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS sync_logs")
            }
        }

        /** v3 -> v4: tabel keadaan sync dua arah. Akun dan repo tidak disentuh. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_state` (" +
                        "`sourceKey` TEXT NOT NULL, `path` TEXT NOT NULL, " +
                        "`localHash` TEXT, `remoteHash` TEXT, `remoteRef` TEXT, " +
                        "PRIMARY KEY(`sourceKey`, `path`))"
                )
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
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
