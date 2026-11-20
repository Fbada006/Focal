package io.disruption.focal.testing

import io.disruption.focal.core.blocking.BlockingEngine
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import kotlin.time.Instant

/**
 * A [BlockingEngine] that records what it was told and keeps the resulting state, so tests can
 * assert on what is blocked rather than on call order.
 */
class RecordingBlockingEngine : BlockingEngine {
    sealed interface Call {
        data class Apply(val session: FocusSession) : Call
        data class Lift(val sessionId: FocusSessionId) : Call
        data class Pause(val sessionId: FocusSessionId, val until: Instant) : Call
    }

    val calls = mutableListOf<Call>()

    /** Sessions currently applied, by id. */
    val applied = linkedMapOf<FocusSessionId, FocusSession>()

    /** Sessions currently paused for a Break, and when blocking resumes. */
    val pausedUntil = linkedMapOf<FocusSessionId, Instant>()

    override suspend fun apply(session: FocusSession) {
        calls += Call.Apply(session)
        applied[session.id] = session
        pausedUntil.remove(session.id)
    }

    override suspend fun lift(sessionId: FocusSessionId) {
        calls += Call.Lift(sessionId)
        applied.remove(sessionId)
        pausedUntil.remove(sessionId)
    }

    /** False makes [pauseUntil] fail, as iOS does when it can't register a Break's end. */
    var canScheduleBreakEnds = true

    override suspend fun pauseUntil(sessionId: FocusSessionId, until: Instant): Boolean {
        calls += Call.Pause(sessionId, until)
        // A failed pause leaves the session blocking, as the contract says.
        if (!canScheduleBreakEnds) return false
        pausedUntil[sessionId] = until
        return true
    }

    /** Whether any applied, unpaused session blocks [app], the union rule of ADR 0006. */
    fun isAppBlocked(app: String): Boolean =
        applied.values.any { it.id !in pausedUntil && app in it.blocked.apps }
}
