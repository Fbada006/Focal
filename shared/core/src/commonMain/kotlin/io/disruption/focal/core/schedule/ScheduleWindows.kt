package io.disruption.focal.core.schedule

import io.disruption.focal.core.model.Schedule
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** One occurrence of a Schedule: from [start] to [end]. */
data class ScheduleWindow(val start: Instant, val end: Instant) {
    operator fun contains(instant: Instant): Boolean = instant >= start && instant < end
}

/**
 * When a Schedule's windows fall, in local time. A window belongs to the day it starts on, so an
 * overnight "Sunday 22:00–07:00" runs from Sunday night into Monday morning. Wall-clock times are
 * resolved in [TimeZone] on each date, so windows follow DST changes: a start in a skipped hour
 * moves forward, and in a repeated hour the earlier one is used.
 */
object ScheduleWindows {

    /** The window of [schedule] that contains [now], if any. Ignores whether it is switched on. */
    fun current(schedule: Schedule, now: Instant, zone: TimeZone): ScheduleWindow? {
        val today = now.toLocalDateTime(zone).date
        return listOf(today, today.minus(DatePeriod(days = 1)))
            .mapNotNull { window(schedule, it, zone) }
            .firstOrNull { now in it }
    }

    /** The first window of [schedule] starting after [after], looking at most a week ahead. */
    fun next(schedule: Schedule, after: Instant, zone: TimeZone): ScheduleWindow? {
        val today = after.toLocalDateTime(zone).date
        return (0..7)
            .map { today.plus(DatePeriod(days = it)) }
            .mapNotNull { window(schedule, it, zone) }
            .firstOrNull { it.start > after }
    }

    /** The soonest window start after [after] among the switched-on [schedules], or null if none. */
    fun nextStart(schedules: List<Schedule>, after: Instant, zone: TimeZone): Instant? = schedules
        .filter { it.enabled }
        .mapNotNull { next(it, after, zone)?.start }
        .minOrNull()

    /** The window that starts on [date], or null if the Schedule doesn't run that day. */
    fun window(schedule: Schedule, date: LocalDate, zone: TimeZone): ScheduleWindow? {
        if (date.dayOfWeek !in schedule.days) return null
        val start = date.atTime(schedule.start).toInstant(zone)
        val endDate = if (schedule.isOvernight) date.plus(DatePeriod(days = 1)) else date
        val end = endDate.atTime(schedule.end).toInstant(zone)
        return ScheduleWindow(start, end).takeIf { it.end > it.start }
    }
}
