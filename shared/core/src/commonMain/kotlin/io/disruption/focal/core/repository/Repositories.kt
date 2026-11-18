package io.disruption.focal.core.repository

import io.disruption.focal.core.model.BlockList
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Schedule
import io.disruption.focal.core.model.ScheduleId
import kotlinx.coroutines.flow.Flow

/** Block Lists, persisted on the device (ADR 0003, ADR 0009). */
interface BlockListRepository {
    fun observeAll(): Flow<List<BlockList>>
    suspend fun get(id: BlockListId): BlockList?
    suspend fun save(blockList: BlockList)
    suspend fun delete(id: BlockListId)
}

/** Focus Sessions, running and finished, persisted on the device (ADR 0003, ADR 0009). */
interface FocusSessionRepository {
    /** Running sessions, ordered by planned end. */
    fun observeRunning(): Flow<List<FocusSession>>

    /** Every session, newest first. */
    fun observeAll(): Flow<List<FocusSession>>
    suspend fun getRunning(): List<FocusSession>
    suspend fun get(id: FocusSessionId): FocusSession?
    suspend fun save(session: FocusSession)
}

/** Schedules, persisted on the device. */
interface ScheduleRepository {
    fun observeAll(): Flow<List<Schedule>>
    suspend fun getAll(): List<Schedule>
    suspend fun get(id: ScheduleId): Schedule?
    suspend fun save(schedule: Schedule)
    suspend fun delete(id: ScheduleId)
}
