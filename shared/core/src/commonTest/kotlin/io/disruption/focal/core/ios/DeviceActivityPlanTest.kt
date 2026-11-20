package io.disruption.focal.core.ios

import io.disruption.focal.core.model.Outcome
import io.disruption.focal.core.model.Schedule
import io.disruption.focal.core.model.ScheduleId
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.session.SessionTestFixture
import io.disruption.focal.core.session.socialList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** What Focal registers with DeviceActivity so Schedules and session ends run with Focal closed (T31). */
class DeviceActivityPlanTest {
    private val f = SessionTestFixture()

    private fun schedule(
        days: Set<DayOfWeek>,
        start: LocalTime,
        end: LocalTime,
        id: String = "sch",
        enabled: Boolean = true,
    ) = Schedule(ScheduleId(id), "Work", days, start, end, socialList.id, Strictness.Locked, enabled)

    private fun plan(vararg schedules: Schedule) = DeviceActivityPlan.from(schedules.toList(), emptyList(), f.clock.now)

    @Test
    fun aScheduleRepeatsOnEachOfItsDays() {
        val activities = plan(schedule(setOf(SUNDAY, MONDAY), LocalTime(9, 0), LocalTime(17, 30))).schedules

        assertEquals(
            listOf(
                ScheduleActivity("focal-schedule-2-sch", "sch", 2, 9, 0, 2, 17, 30, 0),
                ScheduleActivity("focal-schedule-1-sch", "sch", 1, 9, 0, 1, 17, 30, 0),
            ),
            activities,
            "Monday first; Apple counts Sunday as 1",
        )
    }

    @Test
    fun anOvernightWindowEndsOnTheNextDay() {
        val activity = plan(schedule(setOf(SATURDAY), LocalTime(22, 0), LocalTime(7, 0))).schedules.single()

        assertEquals(7, activity.weekday)
        assertEquals(1, activity.endWeekday, "Saturday night into Sunday morning")
        assertEquals(7 to 0, activity.endHour to activity.endMinute)
    }

    @Test
    fun aScheduleOnEveryDayIsOneDailyInterval() {
        val activity = plan(schedule(DayOfWeek.entries.toSet(), LocalTime(22, 0), LocalTime(7, 0))).schedules.single()

        assertEquals(ScheduleActivity("focal-schedule-0-sch", "sch", 0, 22, 0, 0, 7, 0, 0), activity)
    }

    @Test
    fun aWindowShorterThanFifteenMinutesEndsAtTheWarning() {
        val activity = plan(schedule(setOf(MONDAY), LocalTime(23, 55), LocalTime(0, 5))).schedules.single()

        // Registered as 23:55–00:10 Tuesday; the warning 5 minutes before marks the real end, 00:05.
        assertEquals(2 to 3, activity.weekday to activity.endWeekday)
        assertEquals(0 to 10, activity.endHour to activity.endMinute)
        assertEquals(5, activity.warningMinutes)
    }

    @Test
    fun switchedOffSchedulesAreNotMonitored() {
        assertEquals(emptyList(), plan(schedule(setOf(MONDAY), LocalTime(9, 0), LocalTime(17, 0), enabled = false)).schedules)
    }

    @Test
    fun eachRunningSessionGetsAnIntervalEndingAtItsPlannedEnd() = runTest {
        val later = f.startFor(2.hours)
        val sooner = f.startFor(10.minutes, strictness = Strictness.Locked)
        val ended = f.startFor(1.hours)
        f.service.endEarly(ended.id)

        val ends = DeviceActivityPlan.from(emptyList(), f.sessions.getRunning(), f.clock.now).sessionEnds

        assertEquals(listOf(sooner.id.value, later.id.value), ends.map { it.sessionId }, "soonest first")
        val first = ends.first()
        assertEquals("focal-end-${sooner.id.value}", first.name)
        assertEquals(sooner.plannedEnd, first.endsAt)
        // A 10-minute session's interval starts in the past, to meet the 15-minute minimum.
        assertEquals(sooner.plannedEnd - 16.minutes, first.startsAt)
        assertEquals(Outcome.EndedEarly, f.sessions.get(ended.id)!!.outcome)
    }

    @Test
    fun aSessionPastItsPlannedEndIsNotMonitored() = runTest {
        f.startFor(10.minutes)
        f.clock.advanceBy(11.minutes)

        assertEquals(emptyList(), DeviceActivityPlan.from(emptyList(), f.sessions.getRunning(), f.clock.now).sessionEnds)
    }

    @Test
    fun namesCoverSchedulesAndSessionEnds() = runTest {
        val session = f.startFor(1.hours)
        val names = DeviceActivityPlan.from(
            listOf(schedule(setOf(MONDAY), LocalTime(9, 0), LocalTime(10, 0))),
            f.sessions.getRunning(),
            f.clock.now,
        ).names

        assertEquals(setOf("focal-end-${session.id.value}", "focal-schedule-2-sch"), names)
    }

    @Test
    fun anExtendedSessionsIntervalMovesToItsNewEnd() = runTest {
        val session = f.startFor(1.hours, strictness = Strictness.Locked)
        val before = DeviceActivityPlan.from(emptyList(), f.sessions.getRunning(), f.clock.now)

        f.service.edit(session.id, shiftEndBy = 1.hours)
        val after = DeviceActivityPlan.from(emptyList(), f.sessions.getRunning(), f.clock.now)

        assertNotEquals(before, after, "a changed plan is registered again")
        assertEquals(session.plannedEnd + 1.hours, after.sessionEnds.single().endsAt)
    }

    @Test
    fun aSessionEndingMidMinuteIsLiftedOnTheMinuteAfterNeverBefore() = runTest {
        f.clock.advanceBy(30.seconds)
        val session = f.startFor(10.minutes)

        val end = DeviceActivityPlan.from(emptyList(), f.sessions.getRunning(), f.clock.now).sessionEnds.single()

        assertEquals(Instant.parse("2026-01-05T09:11:00Z"), end.endsAt)
        assertTrue(end.endsAt >= session.plannedEnd)
    }

    @Test
    fun droppingOrChangingAScheduleActivityReplacesSchedules() {
        val monday = plan(schedule(setOf(MONDAY), LocalTime(9, 0), LocalTime(17, 0)))
        val mondayAndSunday = plan(schedule(setOf(MONDAY, SUNDAY), LocalTime(9, 0), LocalTime(17, 0)))
        val later = plan(schedule(setOf(MONDAY), LocalTime(9, 0), LocalTime(18, 0)))
        val off = plan(schedule(setOf(MONDAY), LocalTime(9, 0), LocalTime(17, 0), enabled = false))

        assertFalse(mondayAndSunday.replacesSchedulesOf(monday), "adding a day stops nothing")
        assertTrue(later.replacesSchedulesOf(monday), "new times replace the activity")
        assertTrue(off.replacesSchedulesOf(monday), "switching off stops it")
        assertTrue(monday.replacesSchedulesOf(null), "nothing known about what is registered")
    }
}
