package io.disruption.focal.core.session

import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.Strictness

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
}

data class IngestReport(val sessionsStarted: Int, val sessionsCompleted: Int, val breaksEnded: Int = 0)
