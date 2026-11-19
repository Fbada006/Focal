package io.disruption.focal.core.session

import io.disruption.focal.core.blocking.BlockingEngine
import io.disruption.focal.core.ios.ExtensionEvent
import io.disruption.focal.core.model.BlockedSet
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Outcome
import io.disruption.focal.core.model.ScheduleId
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.repository.BlockListRepository
import io.disruption.focal.core.repository.FocusSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Owns the Focus Session lifecycle: start, end and outcome assignment. It is the only place
 * domain rules about sessions live; platforms and view models call into it.
 *
 * Any number of sessions may run at once (ADR 0006). Each is applied to and lifted from the
 * [BlockingEngine] on its own. Nothing here runs timers: platforms call [completeDueSessions] when a
 * session's planned end passes, and [restore] when Focal starts.
 */
class FocusSessionService(
    private val blockLists: BlockListRepository,
    private val sessions: FocusSessionRepository,
    private val engine: BlockingEngine,
    private val clock: Clock,
    private val newId: () -> String = ::randomId,
    lock: SessionLock = SessionLock(),
) {
    private val mutex = lock.mutex

    /** Running sessions, ordered by planned end. */
    val running: Flow<List<FocusSession>> = sessions.observeRunning()

    /**
     * Start a Focus Session now. It takes its own copy of the Block List's blocked set (ADR 0007),
     * so later edits to the list don't weaken it.
     */
    suspend fun start(
        blockListId: BlockListId,
        length: SessionLength,
        strictness: Strictness,
        scheduleId: ScheduleId? = null,
    ): StartResult = mutex.withLock {
        val now = clock.now()
        val blockList = blockLists.get(blockListId) ?: return StartResult.BlockListNotFound
        if (blockList.blocked.isEmpty) return StartResult.EmptyBlockList
        val plannedEnd = length.endFrom(now)
        if (plannedEnd <= now) return StartResult.EndNotInFuture

        val session = FocusSession(
            id = FocusSessionId(newId()),
            blockListId = blockList.id,
            name = blockList.name,
            strictness = strictness,
            startedAt = now,
            plannedEnd = plannedEnd,
            blocked = blockList.blocked,
            scheduleId = scheduleId,
        )
        sessions.save(session)
        engine.apply(session)
        StartResult.Started(session)
    }

    /**
     * End a running session before its planned end, recorded as Ended Early. Only Normal sessions
     * can be ended directly; Delayed sessions go through the Exit Countdown and Locked sessions
     * can't be ended.
     */
    suspend fun endEarly(id: FocusSessionId): EndResult = mutex.withLock {
        val session = sessions.get(id)?.takeIf { it.isRunning } ?: return EndResult.NotRunning
        val now = clock.now()
        if (now >= session.plannedEnd) {
            return EndResult.Ended(finish(session, Outcome.Completed, session.plannedEnd))
        }
        if (session.strictness != Strictness.Normal) return EndResult.NotAllowed(session.strictness)
        EndResult.Ended(finish(session, Outcome.EndedEarly, now))
    }

    /**
     * Record every running session whose planned end has passed as Completed and lift its
     * blocking. Returns the sessions it completed.
     */
    suspend fun completeDueSessions(): List<FocusSession> = mutex.withLock { completeDueLocked() }

    /**
     * Bring blocking back in line with the stored sessions, for example after Focal's process was
     * killed or the phone restarted: complete sessions that ran out meanwhile and re-apply the
     * rest.
     */
    suspend fun restore(): List<FocusSession> = mutex.withLock {
        completeDueLocked()
        val running = sessions.getRunning()
        running.forEach { engine.apply(it) }
        running
    }

    /**
     * Apply what the iOS extensions recorded while Focal wasn't running (ADR 0008): sessions they
     * started for Schedules and sessions whose window they ended. Ingesting the same events again
     * changes nothing. Call [restore] afterwards to bring blocking and outcomes up to date.
     */
    suspend fun ingest(events: List<ExtensionEvent>): IngestReport = mutex.withLock {
        var started = 0
        var ended = 0
        var breaksEnded = 0
        for (event in events.sortedBy { it.at }) {
            when (event) {
                is ExtensionEvent.ScheduleSessionStarted -> {
                    val id = FocusSessionId(event.sessionId)
                    if (sessions.get(id) != null) continue
                    sessions.save(
                        FocusSession(
                            id = id,
                            blockListId = BlockListId(event.blockListId),
                            name = event.name,
                            // An unreadable Strictness must never make a session weaker than it was.
                            strictness = Strictness.entries.firstOrNull { it.name == event.strictness } ?: Strictness.Locked,
                            startedAt = event.at,
                            plannedEnd = event.plannedEnd,
                            // The extension's copy, not the list as it is now (ADR 0007).
                            blocked = BlockedSet(
                                apps = event.apps.toSet(),
                                appCategories = event.appCategories.toSet(),
                                domains = event.domains.toSet(),
                            ),
                            scheduleId = ScheduleId(event.scheduleId),
                        ),
                    )
                    started++
                }
                is ExtensionEvent.ScheduleSessionEnded -> {
                    val session = sessions.get(FocusSessionId(event.sessionId))?.takeIf { it.isRunning } ?: continue
                    if (event.at < session.plannedEnd) continue // only the window's end completes a session
                    finish(session, Outcome.Completed, session.plannedEnd)
                    ended++
                }
                // Breaks never change the outcome or the end time. Blocking came back at event.at, so
                // a Break recorded as running past that is cut short to match.
                is ExtensionEvent.BreakEnded -> {
                    val session = sessions.get(FocusSessionId(event.sessionId))?.takeIf { it.isRunning } ?: continue
                    val breaks = session.breaks.map { b ->
                        if (event.at > b.startedAt && event.at < b.endsAt) b.copy(endsAt = event.at) else b
                    }
                    if (breaks != session.breaks) {
                        sessions.save(session.copy(breaks = breaks))
                        breaksEnded++
                    }
                }
            }
        }
        IngestReport(sessionsStarted = started, sessionsCompleted = ended, breaksEnded = breaksEnded)
    }

    private suspend fun completeDueLocked(): List<FocusSession> {
        val now = clock.now()
        return sessions.getRunning()
            .filter { it.plannedEnd <= now }
            .map { finish(it, Outcome.Completed, it.plannedEnd) }
    }

    private suspend fun finish(session: FocusSession, outcome: Outcome, endedAt: Instant): FocusSession {
        val finished = session.copy(outcome = outcome, endedAt = endedAt)
        sessions.save(finished)
        engine.lift(session.id)
        return finished
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun randomId(): String = Uuid.random().toString()
