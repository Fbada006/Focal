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
 * What the Shield covers on Android, which decides where its Close button goes: a blocked app
 * closes to the home screen; a blocked website closes to a new tab in the same [browser], so the
 * browser stays usable for sites that aren't blocked.
 */
sealed interface Shielded {
    val content: ShieldContent

    data class App(override val content: ShieldContent) : Shielded

    data class Website(override val content: ShieldContent, val browser: String) : Shielded
}

/** One running session the running-session notification names, and whether it is on a Break. */
data class RunningSummary(
    val session: FocusSession,
    /** When the session's Break ends, or null when it is blocking. */
    val breakEndsAt: Instant?,
    /** Running sessions beyond [session]. */
    val otherSessions: Int,
) {
    /** Whether blocking comes back after the Break; false when the Break runs to the session's end. */
    val resumesAfterBreak: Boolean get() = breakEndsAt != null && breakEndsAt < session.plannedEnd
}

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
     * The Shield for the foreground app [packageName] (in App Category [category]) and, in a
     * supported browser, the committed site [host] it shows. The app is checked first: a browser
     * that is itself blocked is Shielded as an app, whatever site it shows.
     */
    fun forForeground(packageName: String, category: String?, host: String?, now: Instant): Shielded? =
        forApp(packageName, category, now)?.let { Shielded.App(it) }
            ?: host?.let { forWebsite(it, now) }?.let { Shielded.Website(it, browser = packageName) }

    /**
     * The next moment what is blocked changes on its own: the soonest planned end or Break end, or
     * null when nothing is running. A running session whose planned end has already passed is due
     * now, so its planned end (at or before [now]) comes first and gets completed straight away.
     * Platforms wake up then to refresh the Shield and the running-session notification (T19).
     */
    fun nextChange(now: Instant): Instant? = sessions
        .filter { it.isRunning }
        .flatMap { listOfNotNull(it.plannedEnd, it.currentBreak(now)?.endsAt) }
        .minOrNull()

    /**
     * What the running-session notification describes (T19): a session that is blocking if any is
     * (the soonest to end), otherwise the session whose Break ends first. Null when nothing runs.
     */
    fun summary(now: Instant): RunningSummary? {
        val running = sessions.filter { it.isRunning && it.plannedEnd > now }
        val blocking = running.filter { it.currentBreak(now) == null }
        val others = running.size - 1
        blocking.minByOrNull { it.plannedEnd }?.let { return RunningSummary(it, breakEndsAt = null, others) }
        val onBreak = running.minByOrNull { it.currentBreak(now)!!.endsAt } ?: return null
        return RunningSummary(onBreak, onBreak.currentBreak(now)!!.endsAt, others)
    }

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
