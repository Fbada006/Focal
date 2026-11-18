package io.disruption.focal.core.session

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
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

    companion object {
        /**
         * Until the next time the wall clock shows [time]: later today, or tomorrow if that time
         * has already passed (or is now).
         */
        fun untilNext(time: LocalTime, now: Instant, zone: TimeZone): Until {
            val today = now.toLocalDateTime(zone).date
            var end = LocalDateTime(today, time).toInstant(zone)
            if (end <= now) end = today.plus(1, DateTimeUnit.DAY).atTime(time).toInstant(zone)
            return Until(end)
        }
    }
}
