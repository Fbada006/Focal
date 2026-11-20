package io.disruption.focal.testing

import io.disruption.focal.core.model.BlockList
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Schedule
import io.disruption.focal.core.model.ScheduleId
import io.disruption.focal.core.repository.AppPreferences
import io.disruption.focal.core.repository.BlockListRepository
import io.disruption.focal.core.repository.FocusSessionRepository
import io.disruption.focal.core.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

class InMemoryBlockListRepository(vararg initial: BlockList) : BlockListRepository {
    private val lists = MutableStateFlow(initial.associateBy { it.id })

    override fun observeAll(): Flow<List<BlockList>> = lists.map { it.values.sortedBy { list -> list.name } }
    override suspend fun get(id: BlockListId): BlockList? = lists.value[id]
    override suspend fun save(blockList: BlockList) = lists.update { it + (blockList.id to blockList) }
    override suspend fun delete(id: BlockListId) = lists.update { it - id }
}

class InMemoryFocusSessionRepository(vararg initial: FocusSession) : FocusSessionRepository {
    private val sessions = MutableStateFlow(initial.associateBy { it.id })

    val all: List<FocusSession> get() = sessions.value.values.toList()

    override fun observeRunning(): Flow<List<FocusSession>> = sessions.map { running(it.values) }
    override fun observeAll(): Flow<List<FocusSession>> =
        sessions.map { it.values.sortedByDescending { session -> session.startedAt } }
    override suspend fun getRunning(): List<FocusSession> = running(sessions.value.values)
    override suspend fun getStartedBySchedule(scheduleId: ScheduleId, since: Instant): List<FocusSession> =
        sessions.value.values.filter { it.scheduleId == scheduleId && it.startedAt >= since }
    override suspend fun get(id: FocusSessionId): FocusSession? = sessions.value[id]
    override suspend fun save(session: FocusSession) = sessions.update { it + (session.id to session) }

    private fun running(all: Collection<FocusSession>) = all.filter { it.isRunning }.sortedBy { it.plannedEnd }
}

class InMemoryScheduleRepository(vararg initial: Schedule) : ScheduleRepository {
    private val schedules = MutableStateFlow(initial.associateBy { it.id })

    override fun observeAll(): Flow<List<Schedule>> = schedules.map { it.values.sortedBy { s -> s.name } }
    override suspend fun getAll(): List<Schedule> = schedules.value.values.sortedBy { it.name }
    override suspend fun get(id: ScheduleId): Schedule? = schedules.value[id]
    override suspend fun save(schedule: Schedule) = schedules.update { it + (schedule.id to schedule) }
    override suspend fun delete(id: ScheduleId) = schedules.update { it - id }
}

class InMemoryAppPreferences(onboardingCompleted: Boolean = false) : AppPreferences {
    private val completed = MutableStateFlow(onboardingCompleted)
    override val onboardingCompleted: Flow<Boolean> = completed
    override suspend fun setOnboardingCompleted(completed: Boolean) {
        this.completed.value = completed
    }

    private val brokenSeenUntil = MutableStateFlow<Instant?>(null)
    override val brokenWarningsSeenUntil: Flow<Instant?> = brokenSeenUntil
    override suspend fun setBrokenWarningsSeenUntil(seenUntil: Instant) {
        brokenSeenUntil.value = seenUntil
    }
}
