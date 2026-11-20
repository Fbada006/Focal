package io.disruption.focal.core.blocking

import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import kotlin.time.Instant

/**
 * The platform blocking contract (ADR 0006). It is per Focus Session, not a single global block
 * set: an app or website stays blocked while any applied session includes it.
 *
 * Android implements it with the accessibility service and a foreground service; iOS with one
 * named `ManagedSettingsStore` per session. Implementations make no domain decisions.
 */
interface BlockingEngine {
    /**
     * Block [FocusSession.blocked] for [session] until it is lifted. Calling it again for a running
     * session replaces what that session blocks (for example after additions while it runs).
     * The session's name and planned end are what the Shield shows.
     */
    suspend fun apply(session: FocusSession)

    /** Stop blocking for the session. Other sessions keep their blocks. */
    suspend fun lift(sessionId: FocusSessionId)

    /**
     * Lift blocking for the session during a Break and restore it automatically at [until], even if
     * Focal isn't running then. Returns false, with the session still blocking, when the platform
     * can't arrange that restore (on iOS, registering the Break's DeviceActivity interval failed):
     * a Break that might never end must not start.
     */
    suspend fun pauseUntil(sessionId: FocusSessionId, until: Instant): Boolean
}
