package com.example.data

import com.example.data.db.SyncStateDao
import com.example.data.db.SyncStateEntity
import com.example.sync.PathState
import com.example.sync.SyncStateStore

class RoomSyncStateStore(private val dao: SyncStateDao) : SyncStateStore {

    override suspend fun getAll(sourceKey: String): Map<String, PathState> =
        dao.getAll(sourceKey).associate { it.path to PathState(it.localHash, it.remoteHash, it.remoteRef) }

    override suspend fun put(sourceKey: String, path: String, state: PathState) {
        dao.upsert(SyncStateEntity(sourceKey, path, state.localHash, state.remoteHash, state.remoteRef))
    }

    override suspend fun delete(sourceKey: String, path: String) = dao.delete(sourceKey, path)

    override suspend fun deleteSource(sourceKey: String) = dao.deleteSource(sourceKey)
}
