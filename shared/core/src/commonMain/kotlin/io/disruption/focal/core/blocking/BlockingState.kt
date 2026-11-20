package io.disruption.focal.core.blocking

import io.disruption.focal.core.model.FocusSession
import kotlin.time.Instant

/**
 * What the Shield shows for one blocked app or website: the running session that holds the block,
 * how many other running sessions also block it, and when that session ends.
 */
data class ShieldContent(
    val sessionId: String,
    val sessionName: String,
    /** Running sessions beyond the holding one that also block this app or website. */
    val otherSessions: Int,
    val plannedEnd: Instant,
    val motivationalLine: String,
)

/**
 * The union of everything running sessions block right now (ADR 0006). A session on a Break
 * blocks nothing until the Break ends. Pure: the Android engine builds it from its applied
 * sessions on every foreground event.
 */
class BlockingState(private val sessions: List<FocusSession>) {
    /** The Shield for [packageName], or null when no running session blocks it. */
    fun forApp(packageName: String, category: String?, now: Instant): ShieldContent? =
        shieldFor(now) { it.blocked.blocksApp(packageName, category) }

    /** The Shield for a browser showing [host], or null when no running session blocks it. */
    fun forWebsite(host: String, now: Instant): ShieldContent? =
        shieldFor(now) { it.blocked.blocksWebsite(host) }

    /**
     * The next moment what is blocked changes on its own: the soonest planned end or Break end
     * after [now], or null when nothing is running. Platforms wake up then to refresh the Shield
     * and the running-session notification (T19).
     */
    fun nextChange(now: Instant): Instant? = sessions
        .filter { it.isRunning && it.plannedEnd > now }
        .flatMap { listOfNotNull(it.plannedEnd, it.currentBreak(now)?.endsAt) }
        .minOrNull()

    private fun shieldFor(now: Instant, matches: (FocusSession) -> Boolean): ShieldContent? {
        val blocking = sessions.filter { session ->
            session.isRunning &&
                session.plannedEnd > now &&
                session.currentBreak(now) == null &&
                matches(session)
        }
        // The soonest end is the first moment this app could come back, so that is what to show.
        val holding = blocking.minByOrNull { it.plannedEnd } ?: return null
        return ShieldContent(
            sessionId = holding.id.value,
            sessionName = holding.name,
            otherSessions = blocking.size - 1,
            plannedEnd = holding.plannedEnd,
            motivationalLine = MOTIVATIONAL_LINES[holding.id.value.hashCode().mod(MOTIVATIONAL_LINES.size)],
        )
    }

    companion object {
        /** Short lines the Shield shows under the timer. Picked by session id, so it is stable. */
        val MOTIVATIONAL_LINES = listOf(
            "This is the work that counts.",
            "You chose this. Stay with it.",
            "Future you says thanks.",
            "One thing at a time.",
            "The pull is the point.",
            "Keep going, you're doing it.",
        )
    }
}
