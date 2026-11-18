package io.disruption.focal.core.schedule

import io.disruption.focal.core.model.Outcome
import io.disruption.focal.core.model.Schedule
import io.disruption.focal.core.model.ScheduleId
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.session.SessionTestFixture
import io.disruption.focal.core.session.newsList
import io.disruption.focal.core.session.socialList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.FRIDAY
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ScheduleEvaluationTest {
    private val berlin = TimeZone.of("Europe/Berlin")
    private val f = SessionTestFixture()

    private fun at(text: String): Instant = LocalDateTime.parse(text).toInstant(berlin)

    private fun schedule(
        id: String,
        days: Set<DayOfWeek>,
        start: String,
        end: String,
        strictness: Strictness = Strictness.Delayed,
        enabled: Boolean = true,
        list: io.disruption.focal.core.model.BlockList = socialList,
    ) = Schedule(ScheduleId(id), id, days, LocalTime.parse(start), LocalTime.parse(end), list.id, strictness, enabled)

    private val workHours = schedule("Work hours", setOf(MONDAY, FRIDAY), "09:00", "17:30")
    private val windDown = schedule("Wind down", setOf(SUNDAY), "22:00", "07:00", Strictness.Locked)

    // 2026-01-05 is a Monday.

    @Test
    fun startsASessionInsideTheWindowEndingAtTheWindowsEnd() = runTest {
        f.clock.now = at("2026-01-05T09:00")

        val started = f.service.evaluateSchedules(listOf(workHours), berlin).single()

        assertEquals("Work hours", started.name)
        assertEquals(ScheduleId("Work hours"), started.scheduleId)
        assertEquals(Strictness.Delayed, started.strictness)
        assertEquals(at("2026-01-05T09:00"), started.startedAt)
        assertEquals(at("2026-01-05T17:30"), started.plannedEnd)
        assertTrue(started.id in f.engine.applied)
    }

    @Test
    fun startsNothingOutsideTheWindowOrOnOtherDays() = runTest {
        for (now in listOf("2026-01-05T08:59", "2026-01-05T17:30", "2026-01-06T10:00", "2026-01-04T10:00")) {
            f.clock.now = at(now)
            assertTrue(f.service.evaluateSchedules(listOf(workHours), berlin).isEmpty(), now)
        }
    }

    @Test
    fun anOvernightWindowBelongsToTheDayItStarts() = runTest {
        f.clock.now = at("2026-01-05T06:00") // Monday morning, inside Sunday's window

        val started = f.service.evaluateSchedules(listOf(windDown), berlin).single()
        assertEquals(at("2026-01-05T07:00"), started.plannedEnd)

        f.clock.now = at("2026-01-05T23:00") // Monday night: no Monday window
        assertTrue(f.service.evaluateSchedules(listOf(windDown), berlin).isEmpty())
    }

    @Test
    fun aMissedStartStartsNowAndStillEndsAtTheScheduledEnd() = runTest {
        f.clock.now = at("2026-01-05T11:20") // phone was off at 09:00

        val started = f.service.evaluateSchedules(listOf(workHours), berlin).single()

        assertEquals(at("2026-01-05T11:20"), started.startedAt)
        assertEquals(at("2026-01-05T17:30"), started.plannedEnd)
    }

    @Test
    fun aFullyMissedWindowStartsNothing() = runTest {
        f.clock.now = at("2026-01-05T18:00")
        assertTrue(f.service.evaluateSchedules(listOf(workHours), berlin).isEmpty())
    }

    @Test
    fun neverStartsADuplicateInTheSameWindow() = runTest {
        f.clock.now = at("2026-01-05T09:00")
        f.service.evaluateSchedules(listOf(workHours), berlin)
        f.clock.advanceBy(5.minutes)

        assertTrue(f.service.evaluateSchedules(listOf(workHours), berlin).isEmpty())
        assertEquals(1, f.sessions.all.size)
    }

    @Test
    fun aSessionEndedEarlyIsNotStartedAgainInTheSameWindow() = runTest {
        val normal = workHours.copy(strictness = Strictness.Normal)
        f.clock.now = at("2026-01-05T09:00")
        val session = f.service.evaluateSchedules(listOf(normal), berlin).single()
        f.service.endEarly(session.id)
        f.clock.advanceBy(1.hours)

        assertTrue(f.service.evaluateSchedules(listOf(normal), berlin).isEmpty())
        assertEquals(Outcome.EndedEarly, f.stored(session).outcome)
    }

    @Test
    fun theNextWindowStartsItsOwnSession() = runTest {
        f.clock.now = at("2026-01-05T09:00")
        f.service.evaluateSchedules(listOf(workHours), berlin)
        f.clock.now = at("2026-01-05T17:30")
        f.service.completeDueSessions()
        f.clock.now = at("2026-01-09T09:00") // Friday

        assertEquals(1, f.service.evaluateSchedules(listOf(workHours), berlin).size)
        assertEquals(2, f.sessions.all.size)
    }

    @Test
    fun overlappingSchedulesEachStartTheirOwnSession() = runTest {
        val news = schedule("News", setOf(MONDAY), "08:00", "12:00", Strictness.Normal, list = newsList)
        f.clock.now = at("2026-01-05T10:00")

        val started = f.service.evaluateSchedules(listOf(workHours, news), berlin)

        assertEquals(setOf("Work hours", "News"), started.map { it.name }.toSet())
        assertEquals(2, f.engine.applied.size)
    }

    @Test
    fun switchedOffSchedulesStartNothing() = runTest {
        f.clock.now = at("2026-01-05T10:00")
        assertTrue(f.service.evaluateSchedules(listOf(workHours.copy(enabled = false)), berlin).isEmpty())
    }

    @Test
    fun windowsFollowDaylightSavingTime() {
        // Berlin springs forward at 02:00 on Sunday 2026-03-29 and falls back at 03:00 on 2026-10-25.
        val night = schedule("Night", setOf(SATURDAY, SUNDAY), "22:00", "07:00")
        val spring = ScheduleWindows.window(night, kotlinx.datetime.LocalDate(2026, 3, 28), berlin)!!
        assertEquals(8.hours, spring.end - spring.start, "one hour shorter")
        val autumn = ScheduleWindows.window(night, kotlinx.datetime.LocalDate(2026, 10, 24), berlin)!!
        assertEquals(10.hours, autumn.end - autumn.start, "one hour longer")

        val skipped = schedule("Skipped", setOf(SUNDAY), "02:30", "04:00")
        val window = ScheduleWindows.window(skipped, kotlinx.datetime.LocalDate(2026, 3, 29), berlin)!!
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), window.start, "02:30 doesn't exist; it moves to 03:30")
    }

    @Test
    fun nextFindsTheUpcomingWindow() {
        val next = ScheduleWindows.next(workHours, at("2026-01-05T10:00"), berlin)!!
        assertEquals(at("2026-01-09T09:00"), next.start)
        assertNull(ScheduleWindows.next(workHours.copy(days = emptySet()), at("2026-01-05T10:00"), berlin))
    }
}
