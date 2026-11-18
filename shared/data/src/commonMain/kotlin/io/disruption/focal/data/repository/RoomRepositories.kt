package io.disruption.focal.data.repository

import io.disruption.focal.core.model.BlockList
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.repository.BlockListRepository
import io.disruption.focal.core.repository.FocusSessionRepository
import io.disruption.focal.data.db.BlockListDao
import io.disruption.focal.data.db.FocusSessionDao
import io.disruption.focal.data.db.toEntity
import io.disruption.focal.data.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomBlockListRepository(private val dao: BlockListDao) : BlockListRepository {
    override fun observeAll(): Flow<List<BlockList>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }
    override suspend fun get(id: BlockListId): BlockList? = dao.get(id.value)?.toModel()
    override suspend fun save(blockList: BlockList) = dao.upsert(blockList.toEntity())
    override suspend fun delete(id: BlockListId) = dao.delete(id.value)
}

class RoomFocusSessionRepository(private val dao: FocusSessionDao) : FocusSessionRepository {
    override fun observeRunning(): Flow<List<FocusSession>> = dao.observeRunning().map { rows -> rows.map { it.toModel() } }
    override fun observeAll(): Flow<List<FocusSession>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }
    override suspend fun getRunning(): List<FocusSession> = dao.getRunning().map { it.toModel() }
    override suspend fun get(id: FocusSessionId): FocusSession? = dao.get(id.value)?.toModel()
    override suspend fun save(session: FocusSession) = dao.upsert(session.toEntity())
}
