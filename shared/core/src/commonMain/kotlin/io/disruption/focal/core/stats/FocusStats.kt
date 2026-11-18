package io.disruption.focal.core.stats

import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.Outcome
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Everything the Stats screen shows, computed on the device (stories 66–73). */
data class FocusStats(
    val focusToday: Duration,
    val focusThisWeek: Duration,
    val completed: Int,
    val endedEarly: Int,
    val broken: Int,
    val currentStreak: Int,
    val longestStreak: Int,
)

object StatsCalculator {
    /** A day counts toward the Streak only with a Completed session at least this long. */
    val STREAK_MINIMUM: Duration = 15.minutes

    /**
     * Focus time is time spent blocking: from start to end (or now, while running), less Breaks,
     * split across the local days it covers. Weeks start on [weekStart].
     *
     * The Streak is the number of consecutive local days each with a Completed session of at
     * least [STREAK_MINIMUM]. A session counts for the day it ends. Ended Early sessions neither
     * count nor reset it; a Broken session resets it to zero at once, so only days completed after
     * it count. Schedule-started sessions count like manual ones.
     */
    fun calculate(
        sessions: List<FocusSession>,
        now: Instant,
        zone: TimeZone,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ): FocusStats {
        val today = now.toLocalDateTime(zone).date
        val daysSinceWeekStart = (today.dayOfWeek.ordinal - weekStart.ordinal + 7) % 7
        val weekFirstDay = today.minus(DatePeriod(days = daysSinceWeekStart))
        val tomorrow = today.plus(DatePeriod(days = 1))

        val todayRange = today.atStartOfDayIn(zone)..tomorrow.atStartOfDayIn(zone)
        val weekRange = weekFirstDay.atStartOfDayIn(zone)..tomorrow.atStartOfDayIn(zone)

        val streaks = streaks(sessions, today, zone)
        return FocusStats(
            focusToday = sessions.sumOf { it.focusWithin(todayRange, now) },
            focusThisWeek = sessions.sumOf { it.focusWithin(weekRange, now) },
            completed = sessions.count { it.outcome == Outcome.Completed },
            endedEarly = sessions.count { it.outcome == Outcome.EndedEarly },
            broken = sessions.count { it.outcome == Outcome.Broken },
            currentStreak = streaks.first,
            longestStreak = streaks.second,
        )
    }

    /** (current, longest), replaying finished sessions in the order they ended. */
    private fun streaks(sessions: List<FocusSession>, today: LocalDate, zone: TimeZone): Pair<Int, Int> {
        var length = 0
        var lastDay: LocalDate? = null
        var longest = 0
        for (session in sessions.filter { it.endedAt != null }.sortedBy { it.endedAt }) {
            val endedAt = session.endedAt!!
            when (session.outcome) {
                Outcome.Broken -> {
                    length = 0
                    lastDay = null
                }
                Outcome.Completed -> {
                    if (endedAt - session.startedAt < STREAK_MINIMUM) continue
                    val day = endedAt.toLocalDateTime(zone).date
                    length = when (lastDay) {
                        day -> length
                        day.minus(DatePeriod(days = 1)) -> length + 1
                        else -> 1
                    }
                    lastDay = day
                    longest = maxOf(longest, length)
                }
                else -> Unit
            }
        }
        val alive = lastDay == today || lastDay == today.minus(DatePeriod(days = 1))
        return (if (alive) length else 0) to longest
    }

    private fun FocusSession.focusWithin(range: ClosedRange<Instant>, now: Instant): Duration {
        val end = endedAt ?: minOf(now, plannedEnd)
        val blocking = overlap(startedAt, end, range)
        val paused = breaks.fold(Duration.ZERO) { sum, b -> sum + overlap(b.startedAt, minOf(b.endsAt, end), range) }
        return (blocking - paused).coerceAtLeast(Duration.ZERO)
    }

    private fun overlap(start: Instant, end: Instant, range: ClosedRange<Instant>): Duration {
        val from = maxOf(start, range.start)
        val to = minOf(end, range.endInclusive)
        return if (to > from) to - from else Duration.ZERO
    }

    private inline fun <T> List<T>.sumOf(selector: (T) -> Duration): Duration =
        fold(Duration.ZERO) { sum, item -> sum + selector(item) }
}
