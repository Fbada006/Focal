package io.disruption.focal.core.schedule

import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.Schedule
import io.disruption.focal.core.model.ScheduleId
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.permissions.Permission
import io.disruption.focal.core.repository.BlockListRepository
import io.disruption.focal.core.repository.ScheduleRepository
import io.disruption.focal.core.session.FocusSessionService
import io.disruption.focal.core.time.TimeZoneProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** What the user fills in for a Schedule. */
data class ScheduleDraft(
    val name: String,
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val end: LocalTime,
    val blockListId: BlockListId?,
    val strictness: Strictness,
    val enabled: Boolean = true,
)

/** What [ScheduleService.catchUp] did: the Schedule sessions it [started] and the Locked sessions it found [broken]. */
data class CatchUpReport(val started: List<FocusSession>, val broken: List<FocusSession>)

sealed interface ScheduleSaveResult {
    data class Saved(val schedule: Schedule) : ScheduleSaveResult
    data class Invalid(val problems: Set<ScheduleProblem>) : ScheduleSaveResult
    data object NotFound : ScheduleSaveResult
}

enum class ScheduleProblem {
    NoName,
    NoDays,
    SameStartAndEnd,
    NoBlockList,

    /** A Schedule on an empty Block List would never block anything, so it would never start. */
    EmptyBlockList,
}

/**
 * Create, edit, switch and delete Schedules (stories 51, 53, 54). A running session is never
 * weakened by this: it holds its own copy (ADR 0007), so edits, switching off and deleting only
 * affect sessions the Schedule starts in the future. After a save, a window that is open right now
 * starts its session at once, unless one already ran in it.
 */
class ScheduleService(
    private val schedules: ScheduleRepository,
    private val blockLists: BlockListRepository,
    private val sessions: FocusSessionService,
    private val timeZone: TimeZoneProvider,
    private val newId: () -> String = { Uuid.random().toString() },
) {
    fun observeAll(): Flow<List<Schedule>> = schedules.observeAll()

    suspend fun get(id: ScheduleId): Schedule? = schedules.get(id)

    suspend fun create(draft: ScheduleDraft): ScheduleSaveResult = save(ScheduleId(newId()), draft)

    suspend fun update(id: ScheduleId, draft: ScheduleDraft): ScheduleSaveResult {
        schedules.get(id) ?: return ScheduleSaveResult.NotFound
        return save(id, draft)
    }

    suspend fun setEnabled(id: ScheduleId, enabled: Boolean): ScheduleSaveResult {
        val schedule = schedules.get(id) ?: return ScheduleSaveResult.NotFound
        val updated = schedule.copy(enabled = enabled)
        schedules.save(updated)
        if (enabled) evaluate()
        return ScheduleSaveResult.Saved(updated)
    }

    /** Deleting a Schedule leaves a session it already started running until that session ends. */
    suspend fun delete(id: ScheduleId) = schedules.delete(id)

    /** Start the sessions due now for every Schedule. */
    suspend fun evaluate() = sessions.evaluateSchedules(schedules.getAll(), timeZone.current())

    /**
     * Complete sessions that ran out and start the ones Schedules have due now. Platforms call it
     * when a Schedule alarm fires, after a reboot and when the clock or time zone changes; a start
     * that was missed meanwhile still starts, for what is left of its window (T28, T30).
     *
     * [missing] is what the permission gateway reports missing now. Without a permission blocking
     * depends on, running Locked sessions are Broken ([FocusSessionService.checkPermissions]) and no
     * Schedule session starts: it would block nothing, yet could end Completed and count for the
     * Streak. Its window starts once the permission is back and Focal catches up again.
     */
    suspend fun catchUp(missing: Collection<Permission>): CatchUpReport {
        sessions.completeDueSessions()
        val broken = sessions.checkPermissions(missing)
        val started = if (canStartSessions(missing)) evaluate() else emptyList()
        return CatchUpReport(started = started, broken = broken)
    }

    /**
     * When to wake Focal next for a Schedule start or a session end; see [FocusSessionService.nextWakeUp].
     * While a permission blocking depends on is [missing], a window already open isn't due now:
     * catching up wouldn't start it, so waking for it would only repeat.
     */
    suspend fun nextWakeUp(missing: Collection<Permission>): Instant? =
        sessions.nextWakeUp(schedules.getAll(), timeZone.current(), schedulesCanStart = canStartSessions(missing))

    private fun canStartSessions(missing: Collection<Permission>) = missing.none { it.blockingDependsOn }

    /**
     * Emits whenever [nextWakeUp] may have moved, so the platform re-arms its alarm: a Schedule, a
     * Block List or the running sessions changed. Block Lists count because a Schedule window on an
     * empty Block List is skipped; adding an app or domain during that window makes it due now.
     */
    fun wakeUpChanges(): Flow<Unit> =
        combine(schedules.observeAll(), blockLists.observeAll(), sessions.running) { _, _, _ -> }

    private suspend fun save(id: ScheduleId, draft: ScheduleDraft): ScheduleSaveResult {
        val name = draft.name.trim()
        val problems = buildSet {
            if (name.isEmpty()) add(ScheduleProblem.NoName)
            if (draft.days.isEmpty()) add(ScheduleProblem.NoDays)
            if (draft.start == draft.end) add(ScheduleProblem.SameStartAndEnd)
            val blockList = draft.blockListId?.let { blockLists.get(it) }
            if (blockList == null) add(ScheduleProblem.NoBlockList)
            else if (blockList.blocked.isEmpty) add(ScheduleProblem.EmptyBlockList)
        }
        if (problems.isNotEmpty()) return ScheduleSaveResult.Invalid(problems)

        val schedule = Schedule(
            id = id,
            name = name,
            days = draft.days,
            start = draft.start,
            end = draft.end,
            blockListId = draft.blockListId!!,
            strictness = draft.strictness,
            enabled = draft.enabled,
        )
        schedules.save(schedule)
        evaluate()
        return ScheduleSaveResult.Saved(schedule)
    }
}
