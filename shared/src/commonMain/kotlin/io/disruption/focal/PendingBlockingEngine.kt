package io.disruption.focal

import io.disruption.focal.core.blocking.BlockingEngine
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import kotlin.time.Instant

/**
 * Stand-in until the platform blocking engines land (T13 on Android, T16 on iOS). It blocks
 * nothing; it only lets the shared screens and session service run end to end.
 */
class PendingBlockingEngine : BlockingEngine {
    override suspend fun apply(session: FocusSession) = Unit
    override suspend fun lift(sessionId: FocusSessionId) = Unit
    override suspend fun pauseUntil(sessionId: FocusSessionId, until: Instant) = Unit
}
