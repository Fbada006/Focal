package io.disruption.focal.data.repository

import io.disruption.focal.core.model.BlockList
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Schedule
import io.disruption.focal.core.model.ScheduleId
import io.disruption.focal.core.repository.BlockListRepository
import io.disruption.focal.core.repository.FocusSessionRepository
import io.disruption.focal.core.repository.ScheduleRepository
import io.disruption.focal.data.db.BlockListDao
import io.disruption.focal.data.db.FocusSessionDao
import io.disruption.focal.data.db.ScheduleDao
import io.disruption.focal.data.db.toEntity
import io.disruption.focal.data.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

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
    override suspend fun getStartedBySchedule(scheduleId: ScheduleId, since: Instant): List<FocusSession> =
        dao.getStartedBySchedule(scheduleId.value, since.toEpochMilliseconds()).map { it.toModel() }
    override suspend fun get(id: FocusSessionId): FocusSession? = dao.get(id.value)?.toModel()
    override suspend fun save(session: FocusSession) = dao.upsert(session.toEntity())
}

class RoomScheduleRepository(private val dao: ScheduleDao) : ScheduleRepository {
    override fun observeAll(): Flow<List<Schedule>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }
    override suspend fun getAll(): List<Schedule> = dao.getAll().map { it.toModel() }
    override suspend fun get(id: ScheduleId): Schedule? = dao.get(id.value)?.toModel()
    override suspend fun save(schedule: Schedule) = dao.upsert(schedule.toEntity())
    override suspend fun delete(id: ScheduleId) = dao.delete(id.value)
}
