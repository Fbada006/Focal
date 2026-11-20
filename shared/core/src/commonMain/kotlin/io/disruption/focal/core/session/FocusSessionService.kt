package io.disruption.focal.core.session

import io.disruption.focal.core.blocking.BlockingEngine
import io.disruption.focal.core.ios.ExtensionEvent
import io.disruption.focal.core.model.BlockList
import io.disruption.focal.core.model.BlockedSet
import io.disruption.focal.core.model.Break
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Outcome
import io.disruption.focal.core.model.Schedule
import io.disruption.focal.core.schedule.ScheduleWindows
import kotlinx.datetime.TimeZone
import io.disruption.focal.core.model.ScheduleId
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.repository.BlockListRepository
import io.disruption.focal.core.repository.FocusSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration
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

    /** Exit Countdowns in progress, by session. Held in memory: leaving the countdown cancels it. */
    private val countdowns = mutableMapOf<FocusSessionId, ExitCountdown>()

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
     * Whether [scheduleId] already has a session overlapping [start, end): running or finished, and
     * whenever it started. A window gets at most one session, even if the Schedule's start time was
     * moved after its session began, or the session was ended early.
     */
    private suspend fun hasSessionInWindow(scheduleId: ScheduleId, start: Instant, end: Instant): Boolean =
        sessions.getStartedBySchedule(scheduleId, since = start - MAX_WINDOW)
            .any { it.startedAt < end && it.plannedEnd > start }

    /**
     * Start a Focus Session for every switched-on Schedule whose window contains now and which
     * hasn't already started one in this window (stories 52, 55–57). Call it when a Schedule's start
     * alarm fires, when Focal starts and after the device boots. On iOS, [ingest] the extensions'
     * events first: they start Schedule sessions while Focal isn't running (ADR 0008).
     *
     * A start that was missed (phone off) starts now and still ends at the window's end; a window
     * that is already over starts nothing. Overlapping Schedules each start their own session. A
     * window gets at most one session, so a session ended early is not started again.
     */
    suspend fun evaluateSchedules(schedules: List<Schedule>, zone: TimeZone): List<FocusSession> = mutex.withLock {
        val now = clock.now()
        val started = mutableListOf<FocusSession>()
        for (schedule in schedules.filter { it.enabled }) {
            val window = ScheduleWindows.current(schedule, now, zone) ?: continue
            if (hasSessionInWindow(schedule.id, window.start, window.end)) continue
            val blockList = blockLists.get(schedule.blockListId)
            if (blockList == null || blockList.blocked.isEmpty) continue

            val session = FocusSession(
                id = FocusSessionId(newId()),
                blockListId = blockList.id,
                name = schedule.name,
                strictness = schedule.strictness,
                startedAt = now,
                plannedEnd = window.end,
                blocked = blockList.blocked,
                scheduleId = schedule.id,
            )
            sessions.save(session)
            engine.apply(session)
            started += session
        }
        started
    }

    /**
     * Start the Exit Countdown for ending a Delayed session early. The user must wait it out and
     * then confirm with [confirmEndEarly]; leaving it cancels it ([cancelExitCountdown]).
     */
    suspend fun beginExitCountdown(id: FocusSessionId): CountdownResult = mutex.withLock {
        val session = sessions.get(id)?.takeIf { it.isRunning } ?: return CountdownResult.NotRunning
        if (session.strictness != Strictness.Delayed) return CountdownResult.NotAllowed(session.strictness)
        val now = clock.now()
        val countdown = ExitCountdown(id, startedAt = now, endsAt = now + EXIT_COUNTDOWN)
        countdowns[id] = countdown
        CountdownResult.Started(countdown)
    }

    /** Leaving the Exit Countdown, or backgrounding Focal, cancels it. */
    suspend fun cancelExitCountdown(id: FocusSessionId) = mutex.withLock {
        countdowns.remove(id)
        Unit
    }

    /**
     * End a Delayed session early once its Exit Countdown has finished, recorded as Ended Early.
     * Without a finished countdown nothing changes and the session keeps running.
     */
    suspend fun confirmEndEarly(id: FocusSessionId): EndResult = mutex.withLock {
        val session = sessions.get(id)?.takeIf { it.isRunning } ?: run {
            countdowns.remove(id)
            return EndResult.NotRunning
        }
        val now = clock.now()
        if (now >= session.plannedEnd) {
            countdowns.remove(id)
            return EndResult.Ended(finish(session, Outcome.Completed, session.plannedEnd))
        }
        // Only Delayed sessions end through a countdown. One raised to Locked mid-countdown can't.
        if (session.strictness != Strictness.Delayed) {
            countdowns.remove(id)
            return EndResult.NotAllowed(session.strictness)
        }
        val countdown = countdowns[id] ?: return EndResult.CountdownRequired
        if (now < countdown.endsAt) return EndResult.CountdownRequired
        countdowns.remove(id)
        EndResult.Ended(finish(session, Outcome.EndedEarly, now))
    }

    /**
     * Take a 5-minute Break from a Delayed session: blocking for this session is lifted until the
     * Break ends, then the engine restores it on its own, even if Focal isn't running. Breaks don't
     * move the planned end or change the outcome. At most three per session; only from Focal,
     * never from the Shield.
     */
    suspend fun takeBreak(id: FocusSessionId): BreakResult = mutex.withLock {
        val session = sessions.get(id)?.takeIf { it.isRunning } ?: return BreakResult.NotRunning
        val now = clock.now()
        if (now >= session.plannedEnd) return BreakResult.NotRunning
        if (session.strictness != Strictness.Delayed) return BreakResult.NotAllowed(session.strictness)
        if (session.currentBreak(now) != null) return BreakResult.AlreadyOnBreak
        if (session.breaksLeft == 0) return BreakResult.NoBreaksLeft

        val endsAt = minOf(now + FocusSession.BREAK_LENGTH, session.plannedEnd)
        val updated = session.copy(breaks = session.breaks + Break(startedAt = now, endsAt = endsAt))
        sessions.save(updated)
        if (!engine.pauseUntil(id, endsAt)) {
            // Nothing would bring blocking back if Focal were killed now: take the Break back.
            sessions.save(session)
            return BreakResult.Unavailable
        }
        BreakResult.Started(updated, endsAt)
    }

    /**
     * Change a running session's end time and/or Strictness. A running session can only get
     * stricter (ADR 0007): a later end and a higher Strictness always apply at once; an earlier end
     * or a lower Strictness is refused for Delayed and Locked sessions. Normal sessions accept any
     * edit, since they could be ended anyway.
     */
    suspend fun edit(
        id: FocusSessionId,
        plannedEnd: Instant? = null,
        strictness: Strictness? = null,
        shiftEndBy: Duration? = null,
    ): EditResult = mutex.withLock {
        val session = sessions.get(id)?.takeIf { it.isRunning } ?: return EditResult.NotRunning
        val now = clock.now()
        if (now >= session.plannedEnd) return EditResult.NotRunning
        val weakeningAllowed = session.strictness == Strictness.Normal
        // A shift is applied to the stored end, so two quick "+15 min" taps add 30 minutes.
        val newEnd = plannedEnd ?: shiftEndBy?.let { session.plannedEnd + it } ?: session.plannedEnd
        val newStrictness = strictness ?: session.strictness

        if (newEnd <= now) return EditResult.Refused(EditRefusal.EndNotInFuture)
        if (newEnd < session.plannedEnd && !weakeningAllowed) return EditResult.Refused(EditRefusal.Shorten)
        if (session.strictness.isStricterThan(newStrictness) && !weakeningAllowed) {
            return EditResult.Refused(EditRefusal.LowerStrictness)
        }

        val updated = session.copy(plannedEnd = newEnd, strictness = newStrictness)
        // An Exit Countdown belongs to a Delayed session; raising the Strictness drops it.
        if (newStrictness != Strictness.Delayed) countdowns.remove(id)
        if (updated != session) {
            sessions.save(updated)
            engine.apply(updated)
            session.currentBreak(now)?.let { continueBreak(updated, minOf(it.endsAt, newEnd), now) }
        }
        EditResult.Applied(updated)
    }

    /**
     * Bring running sessions that use [blockList] in line with an edit to it (ADR 0007). Additions
     * are merged into each session's own copy and applied at once. Removals apply at once to Normal
     * sessions; Delayed and Locked sessions keep blocking them until they end.
     */
    suspend fun blockListChanged(blockList: BlockList): BlockListChangeReport = mutex.withLock {
        val tightened = mutableListOf<FocusSession>()
        val deferred = mutableListOf<String>()
        val now = clock.now()
        for (session in sessions.getRunning().filter { it.blockListId == blockList.id && it.plannedEnd > now }) {
            val newBlocked = if (session.strictness == Strictness.Normal) {
                blockList.blocked
            } else {
                if (!blockList.blocked.containsAll(session.blocked)) deferred += session.name
                session.blocked + blockList.blocked
            }
            if (newBlocked == session.blocked) continue
            val updated = session.copy(blocked = newBlocked)
            sessions.save(updated)
            engine.apply(updated)
            session.currentBreak(now)?.let { continueBreak(updated, it.endsAt, now) }
            if (!session.blocked.containsAll(newBlocked)) tightened += updated
        }
        BlockListChangeReport(tightened, deferred)
    }

    /**
     * The platform detected that blocking was defeated from outside Focal, for example a required
     * permission was revoked (ADR 0002). Running Locked sessions are recorded as Broken and lifted;
     * one that had already run out is Completed instead. Other Strictnesses could have been ended
     * anyway, so they keep running and the platform asks the user to restore the permission.
     */
    suspend fun markBlockingDefeated(): List<FocusSession> = mutex.withLock {
        completeDueLocked()
        val now = clock.now()
        sessions.getRunning()
            .filter { it.strictness == Strictness.Locked }
            .map { finish(it, Outcome.Broken, now) }
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
        val now = clock.now()
        val running = sessions.getRunning()
        running.forEach { session ->
            engine.apply(session)
            session.currentBreak(now)?.let { continueBreak(session, it.endsAt, now) }
        }
        sessions.getRunning()
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
                    // The app may already have started this window's session itself (after a save).
                    if (hasSessionInWindow(ScheduleId(event.scheduleId), event.at, event.plannedEnd)) continue
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

    /**
     * Pause [session] again for the Break in progress, after re-applying it. If the engine can no
     * longer arrange the Break's end, it keeps blocking, so the Break is recorded as over at [now].
     */
    private suspend fun continueBreak(session: FocusSession, until: Instant, now: Instant) {
        if (engine.pauseUntil(session.id, until)) return
        val current = sessions.get(session.id) ?: return
        val ongoing = current.currentBreak(now) ?: return
        sessions.save(current.copy(breaks = current.breaks.map { if (it == ongoing) it.copy(endsAt = now) else it }))
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

/** How long the user must wait before confirming that a Delayed session should end early. */
val EXIT_COUNTDOWN = 30.seconds

/** A Schedule's window never spans more than a day (overnight windows end the next morning). */
private val MAX_WINDOW = 1.days

@OptIn(ExperimentalUuidApi::class)
private fun randomId(): String = Uuid.random().toString()
