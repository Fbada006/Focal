package io.disruption.focal.core.ios

import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.Schedule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The DeviceActivity intervals Focal keeps registered on iOS (T31, ADR 0008), so the
 * DeviceActivityMonitor extension starts and ends sessions while Focal isn't running:
 *
 * - [schedules]: one repeating interval per day a switched-on Schedule runs (or one daily interval
 *   when it runs every day). Its start starts a session from the snapshot, its end lifts it.
 * - [sessionEnds]: one interval per running Focus Session ending at its planned end, whose end lifts
 *   the session's store. Focal records the outcome itself on next launch. A session whose planned end
 *   moves (edit) gets a new plan, so its interval is registered again for the new end.
 *
 * Breaks register their own intervals (`focal-break-`) and are not part of the plan. Everything
 * here is worked out in Kotlin, so the Swift side only turns it into `DeviceActivitySchedule`s.
 *
 * iOS caps how many activities an app may monitor at once (reported as 20, not documented). The
 * plan lists session ends first, since they guard sessions already running; the Swift side
 * registers in order and skips what the system refuses.
 */
data class DeviceActivityPlan(
    val schedules: List<ScheduleActivity>,
    val sessionEnds: List<SessionEndActivity>,
) {
    /** Every activity name in the plan; anything else Focal registered under these prefixes is stopped. */
    val names: Set<String> get() = (sessionEnds.map { it.name } + schedules.map { it.name }).toSet()

    /**
     * Whether registering this plan in place of [registered] would stop or replace a Schedule
     * activity. The monitor may have started a session in that activity's window that Focal hasn't
     * ingested yet, and whose only end callback is that activity's. Focal ingests pending extension
     * events first, so such a session gets its own session-end interval before the Schedule's goes.
     * [registered] is null when Focal doesn't know what is registered (first plan since launch, or the
     * last registration failed).
     */
    fun replacesSchedulesOf(registered: DeviceActivityPlan?): Boolean =
        registered == null || !schedules.containsAll(registered.schedules)

    companion object {
        /** `focal-schedule-<weekday>-<schedule id>`; the extension's `ScheduleSessions` reads it back. */
        const val SCHEDULE_PREFIX = "focal-schedule-"

        /** `focal-end-<session id>`; the extension's `SessionEnder` reads it back. */
        const val SESSION_END_PREFIX = "focal-end-"

        /** DeviceActivity refuses intervals shorter than 15 minutes. */
        const val MINIMUM_INTERVAL_MINUTES = 15

        /** A session end's interval starts this long before the end, in the past if need be (as Breaks do, T20). */
        val SESSION_END_LEAD = 16.minutes

        fun scheduleActivityName(scheduleId: String, weekday: Int): String = "$SCHEDULE_PREFIX$weekday-$scheduleId"

        fun sessionEndActivityName(sessionId: String): String = "$SESSION_END_PREFIX$sessionId"

        /**
         * The plan for the switched-on [schedules] and the [runningSessions] still due to end after
         * [now].
         */
        fun from(schedules: List<Schedule>, runningSessions: List<FocusSession>, now: Instant): DeviceActivityPlan =
            DeviceActivityPlan(
                schedules = schedules.filter { it.enabled }.sortedBy { it.id.value }.flatMap(::activities),
                sessionEnds = runningSessions
                    .filter { it.isRunning && it.plannedEnd > now }
                    .sortedBy { it.plannedEnd }
                    .map { session ->
                        SessionEndActivity(
                            name = sessionEndActivityName(session.id.value),
                            sessionId = session.id.value,
                            startsAt = session.plannedEnd - SESSION_END_LEAD,
                            endsAt = session.plannedEnd.roundedUpToMinute(),
                        )
                    },
            )

        private const val MINUTES_PER_DAY = 24 * 60

        private fun activities(schedule: Schedule): List<ScheduleActivity> {
            val startMinute = schedule.start.hour * 60 + schedule.start.minute
            val endMinute = schedule.end.hour * 60 + schedule.end.minute
            // An end at or before the start runs overnight, as in ScheduleWindows.
            val length = (endMinute - startMinute).let { if (it <= 0) it + MINUTES_PER_DAY else it }
            // A window shorter than DeviceActivity allows is registered at the minimum length; the
            // extension ends it early, at the "interval will end" warning.
            val registered = maxOf(length, MINIMUM_INTERVAL_MINUTES)
            val registeredEnd = startMinute + registered
            val endsNextDay = registeredEnd >= MINUTES_PER_DAY
            val endOfDay = registeredEnd % MINUTES_PER_DAY

            fun activity(weekday: Int, endWeekday: Int) = ScheduleActivity(
                name = scheduleActivityName(schedule.id.value, weekday),
                scheduleId = schedule.id.value,
                weekday = weekday,
                startHour = schedule.start.hour,
                startMinute = schedule.start.minute,
                endWeekday = endWeekday,
                endHour = endOfDay / 60,
                endMinute = endOfDay % 60,
                warningMinutes = registered - length,
            )

            // Every day: one daily interval instead of seven. A full-day window can't be told apart
            // from an empty one without weekdays, so it stays per day.
            if (schedule.days.size == DayOfWeek.entries.size && registered < MINUTES_PER_DAY) {
                return listOf(activity(EVERY_DAY, EVERY_DAY))
            }
            return schedule.days.sortedBy { it.isoDayNumber }.map { day ->
                val weekday = day.appleWeekday
                activity(weekday, if (endsNextDay) weekday % 7 + 1 else weekday)
            }
        }

        /** No weekday: the interval repeats every day. */
        const val EVERY_DAY = 0
    }
}

/**
 * A repeating DeviceActivity interval for one Schedule, in local wall-clock time. Weekdays are
 * Apple's `Calendar` numbering (1 = Sunday … 7 = Saturday), or [DeviceActivityPlan.EVERY_DAY].
 *
 * [warningMinutes] is non-zero only for windows shorter than DeviceActivity's 15-minute minimum:
 * the interval is registered 15 minutes long, and its "will end" warning, that many minutes before
 * the registered end, marks the window's real end.
 */
data class ScheduleActivity(
    val name: String,
    val scheduleId: String,
    val weekday: Int,
    val startHour: Int,
    val startMinute: Int,
    val endWeekday: Int,
    val endHour: Int,
    val endMinute: Int,
    val warningMinutes: Int,
)

/**
 * A one-off interval ending at a running session's planned end; only its end matters. DeviceActivity
 * fires on whole minutes, so [endsAt] is the planned end rounded up to the minute: the callback never
 * comes before the session is due to end, and the extension never lifts a session early.
 */
data class SessionEndActivity(
    val name: String,
    val sessionId: String,
    val startsAt: Instant,
    val endsAt: Instant,
)

private fun Instant.roundedUpToMinute(): Instant {
    val seconds = epochSeconds.mod(60L)
    return if (seconds == 0L && nanosecondsOfSecond == 0) this
    else Instant.fromEpochSeconds(epochSeconds - seconds + 60)
}

/** Apple's `Calendar` weekday: 1 = Sunday … 7 = Saturday. */
private val DayOfWeek.appleWeekday: Int get() = isoDayNumber % 7 + 1
