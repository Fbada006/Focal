package io.disruption.focal.core.model

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A period of time during which a chosen set of apps and websites is blocked.
 *
 * [blocked] is the session's own copy of what it blocks, taken from its Block List at start
 * (ADR 0007). It is not a live view of the Block List.
 */
data class FocusSession(
    val id: FocusSessionId,
    val blockListId: BlockListId,
    /** The Block List's name when the session started, so the Shield can name the session. */
    val name: String,
    val strictness: Strictness,
    val startedAt: Instant,
    val plannedEnd: Instant,
    val blocked: BlockedSet,
    /** The Schedule that started this session, or null when started by hand. */
    val scheduleId: ScheduleId? = null,
    val breaks: List<Break> = emptyList(),
    val endedAt: Instant? = null,
    val outcome: Outcome? = null,
) {
    val isRunning: Boolean get() = outcome == null

    /** How long the session actually lasted, or null while it is running. */
    val focusDuration: Duration? get() = endedAt?.let { it - startedAt }

    fun timeLeft(now: Instant): Duration = (plannedEnd - now).coerceAtLeast(Duration.ZERO)

    /** The Break in progress at [now], if any. */
    fun currentBreak(now: Instant): Break? = breaks.lastOrNull()?.takeIf { now >= it.startedAt && now < it.endsAt }

    val breaksLeft: Int get() = (MAX_BREAKS - breaks.size).coerceAtLeast(0)

    companion object {
        /** Fixed in v1: a Break lasts 5 minutes and a session allows at most three. */
        val BREAK_LENGTH: Duration = Duration.parse("5m")
        const val MAX_BREAKS = 3
    }
}

/** A pause in blocking during a Delayed Focus Session. */
data class Break(
    val startedAt: Instant,
    val endsAt: Instant,
)
