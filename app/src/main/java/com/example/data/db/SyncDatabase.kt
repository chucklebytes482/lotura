package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DriveAccountEntity::class,
        GithubAccountEntity::class,
        GithubRepoEntity::class,
        SyncLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun driveAccountDao(): DriveAccountDao
    abstract fun githubAccountDao(): GithubAccountDao
    abstract fun githubRepoDao(): GithubRepoDao
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        @Volatile
        private var instance: SyncDatabase? = null

        fun getInstance(context: Context): SyncDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SyncDatabase::class.java,
                    "sync_bebas.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
