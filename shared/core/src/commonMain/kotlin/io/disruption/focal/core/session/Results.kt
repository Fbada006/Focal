package io.disruption.focal.core.session

import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Strictness
import kotlin.time.Instant

sealed interface StartResult {
    data class Started(val session: FocusSession) : StartResult
    data object BlockListNotFound : StartResult
    data object EmptyBlockList : StartResult
    data object EndNotInFuture : StartResult
}

sealed interface EndResult {
    data class Ended(val session: FocusSession) : EndResult
    data object NotRunning : EndResult

    /** The session's Strictness does not allow ending it this way. */
    data class NotAllowed(val strictness: Strictness) : EndResult

    /** A Delayed session needs a finished Exit Countdown before it can end early. */
    data object CountdownRequired : EndResult
}

/** The wait before ending a Delayed session early. */
data class ExitCountdown(
    val sessionId: FocusSessionId,
    val startedAt: Instant,
    val endsAt: Instant,
) {
    fun isFinished(now: Instant): Boolean = now >= endsAt
}

sealed interface CountdownResult {
    data class Started(val countdown: ExitCountdown) : CountdownResult
    data object NotRunning : CountdownResult
    data class NotAllowed(val strictness: Strictness) : CountdownResult
}

data class IngestReport(val sessionsStarted: Int, val sessionsCompleted: Int, val breaksEnded: Int = 0)

sealed interface BreakResult {
    data class Started(val session: FocusSession, val endsAt: Instant) : BreakResult
    data object NotRunning : BreakResult

    /** Breaks are only for Delayed sessions. */
    data class NotAllowed(val strictness: Strictness) : BreakResult
    data object NoBreaksLeft : BreakResult
    data object AlreadyOnBreak : BreakResult
}

sealed interface EditResult {
    data class Applied(val session: FocusSession) : EditResult
    data object NotRunning : EditResult

    /** The edit would weaken a Delayed or Locked session (ADR 0007). Nothing changed. */
    data class Refused(val reason: EditRefusal) : EditResult
}

enum class EditRefusal {
    /** An earlier end time. */
    Shorten,

    /** A lower Strictness. */
    LowerStrictness,

    /** An end time that isn't in the future. */
    EndNotInFuture,
}

/** What happened to running sessions when their Block List changed. */
data class BlockListChangeReport(
    /** Sessions that now block more, applied at once. */
    val tightened: List<FocusSession> = emptyList(),
    /** Names of Delayed or Locked sessions that keep blocking removed items until they end. */
    val removalsDeferredFor: List<String> = emptyList(),
)
