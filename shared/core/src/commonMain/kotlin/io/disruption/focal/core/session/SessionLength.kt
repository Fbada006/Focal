package io.disruption.focal.core.session

import kotlin.time.Duration
import kotlin.time.Instant

/** How long a Focus Session started by hand should run: for a duration, or until an end time. */
sealed interface SessionLength {
    data class For(val duration: Duration) : SessionLength
    data class Until(val end: Instant) : SessionLength

    fun endFrom(start: Instant): Instant = when (this) {
        is For -> start + duration
        is Until -> end
    }
}
