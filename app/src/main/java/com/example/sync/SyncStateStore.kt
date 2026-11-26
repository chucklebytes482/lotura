package com.example.sync

/** Penyimpanan base sync dua arah: keadaan tiap path per sumber. */
interface SyncStateStore {
    suspend fun getAll(sourceKey: String): Map<String, PathState>
    suspend fun put(sourceKey: String, path: String, state: PathState)
    suspend fun delete(sourceKey: String, path: String)
    suspend fun deleteSource(sourceKey: String)
}
