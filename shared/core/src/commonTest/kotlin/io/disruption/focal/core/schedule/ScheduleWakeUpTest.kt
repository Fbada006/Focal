package io.disruption.focal.core.schedule

import io.disruption.focal.core.model.BlockedSet
import io.disruption.focal.core.model.Outcome
import io.disruption.focal.core.model.Schedule
import io.disruption.focal.core.model.ScheduleId
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.session.SessionLength
import io.disruption.focal.core.session.SessionTestFixture
import io.disruption.focal.core.session.socialList
import io.disruption.focal.core.time.TimeZoneProvider
import io.disruption.focal.testing.InMemoryScheduleRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.TUESDAY
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** When Focal must wake to start and end Schedule sessions with Focal closed (T30). */
class ScheduleWakeUpTest {
    private val f = SessionTestFixture().apply { clock.now = Instant.parse("2026-01-05T10:00:00Z") } // Monday
    private val repo = InMemoryScheduleRepository()
    private var zone: TimeZone = TimeZone.UTC
    private val service = ScheduleService(repo, f.blockLists, f.service, TimeZoneProvider { zone }, newId = { "sch" })

    private val tuesdayWork = ScheduleDraft(
        name = "Work",
        days = setOf(TUESDAY),
        start = LocalTime(9, 0),
        end = LocalTime(17, 0),
        blockListId = socialList.id,
        strictness = Strictness.Locked,
    )

    @Test
    fun nothingToWakeForWithoutSchedulesOrSessions() = runTest {
        assertNull(service.nextWakeUp())
    }

    @Test
    fun wakesAtTheNextWindowStart() = runTest {
        service.create(tuesdayWork)
        assertEquals(Instant.parse("2026-01-06T09:00:00Z"), service.nextWakeUp())
    }

    @Test
    fun aSwitchedOffScheduleNeedsNoWakeUp() = runTest {
        service.create(tuesdayWork.copy(enabled = false))
        assertNull(service.nextWakeUp())
    }

    @Test
    fun wakesForTheRunningSessionsEndFirst() = runTest {
        service.create(tuesdayWork.copy(days = setOf(MONDAY, TUESDAY))) // Monday's window is open: it starts now
        assertEquals(Instant.parse("2026-01-05T17:00:00Z"), service.nextWakeUp())
    }

    @Test
    fun manualSessionsEndingFirstAlsoWakeFocal() = runTest {
        service.create(tuesdayWork)
        f.service.start(socialList.id, SessionLength.For(30.minutes), Strictness.Normal)
        assertEquals(Instant.parse("2026-01-05T10:30:00Z"), service.nextWakeUp())
    }

    @Test
    fun wakesNowForAWindowThatBeganWithoutItsSession() = runTest {
        service.create(tuesdayWork)
        // A catch-up ran just before 09:00; asking just after must not skip to next Tuesday.
        f.clock.now = Instant.parse("2026-01-06T09:00:01Z")
        assertEquals(f.clock.now, service.nextWakeUp())

        service.catchUp()
        assertEquals(Instant.parse("2026-01-06T17:00:00Z"), service.nextWakeUp())
    }

    @Test
    fun wakesNowForASessionPastItsEnd() = runTest {
        f.service.start(socialList.id, SessionLength.For(30.minutes), Strictness.Normal)
        f.clock.now = Instant.parse("2026-01-05T10:30:01Z")
        assertEquals(f.clock.now, service.nextWakeUp())

        service.catchUp()
        assertNull(service.nextWakeUp())
    }

    @Test
    fun aWindowWithNothingToBlockDoesNotWakeNow() = runTest {
        service.create(tuesdayWork)
        f.blockLists.save(socialList.copy(blocked = BlockedSet()))
        f.clock.now = Instant.parse("2026-01-06T10:00:00Z")
        // Catch-up would start nothing, so waking now would only loop: wait for next week's start.
        assertEquals(Instant.parse("2026-01-13T09:00:00Z"), service.nextWakeUp())
    }

    @Test
    fun fillingAnEmptyBlockListDuringItsWindowWakesNow() = runTest {
        service.create(tuesdayWork)
        f.blockLists.save(socialList.copy(blocked = BlockedSet()))
        f.clock.now = Instant.parse("2026-01-06T10:00:00Z")
        val changes = mutableListOf<Unit>()
        val watching = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            service.wakeUpChanges().toList(changes)
        }
        assertEquals(1, changes.size)

        // The skipped window becomes due once the Block List has something to block.
        f.blockLists.save(socialList)
        assertEquals(2, changes.size, "a Block List change asks the platform to re-arm")
        assertEquals(f.clock.now, service.nextWakeUp())
        watching.cancel()
    }

    @Test
    fun theStartAndEndAlarmsRunTheWindow() = runTest {
        service.create(tuesdayWork)
        f.clock.now = Instant.parse("2026-01-06T09:00:00Z")

        val started = service.catchUp().single()
        assertEquals(Instant.parse("2026-01-06T17:00:00Z"), started.plannedEnd)
        assertEquals(started.plannedEnd, service.nextWakeUp())

        f.clock.now = started.plannedEnd
        assertTrue(service.catchUp().isEmpty())
        assertEquals(Outcome.Completed, f.sessions.get(started.id)!!.outcome)
        assertTrue(f.sessions.getRunning().isEmpty())
        assertEquals(Instant.parse("2026-01-13T09:00:00Z"), service.nextWakeUp(), "the next Tuesday")
    }

    @Test
    fun aMissedStartStartsLateAndStillEndsOnTime() = runTest {
        service.create(tuesdayWork)
        f.clock.now = Instant.parse("2026-01-06T11:00:00Z") // the phone was off at 09:00

        val started = service.catchUp().single()
        assertEquals(Instant.parse("2026-01-06T11:00:00Z"), started.startedAt)
        assertEquals(Instant.parse("2026-01-06T17:00:00Z"), started.plannedEnd)
    }

    @Test
    fun aTimeZoneChangeMovesTheNextStart() = runTest {
        service.create(tuesdayWork)
        zone = TimeZone.of("Europe/Berlin")
        assertEquals(Instant.parse("2026-01-06T08:00:00Z"), service.nextWakeUp())
    }

    @Test
    fun nextStartIsTheSoonestAcrossSchedules() {
        val now = Instant.parse("2026-01-05T10:00:00Z")
        val schedules = listOf(
            Schedule(
                id = ScheduleId("a"),
                name = "Later",
                days = setOf(TUESDAY),
                start = LocalTime(9, 0),
                end = LocalTime(10, 0),
                blockListId = socialList.id,
                strictness = Strictness.Normal,
                enabled = true,
            ),
            Schedule(
                id = ScheduleId("b"),
                name = "Tonight",
                days = setOf(MONDAY),
                start = LocalTime(22, 0),
                end = LocalTime(7, 0),
                blockListId = socialList.id,
                strictness = Strictness.Normal,
                enabled = true,
            ),
        )
        assertEquals(Instant.parse("2026-01-05T22:00:00Z"), ScheduleWindows.nextStart(schedules, now, TimeZone.UTC))
        assertEquals(
            Instant.parse("2026-01-06T09:00:00Z"),
            ScheduleWindows.nextStart(schedules.map { if (it.name == "Tonight") it.copy(enabled = false) else it }, now, TimeZone.UTC),
        )
        assertEquals(Instant.parse("2026-01-12T22:00:00Z"), ScheduleWindows.nextStart(schedules.drop(1), now + 13.hours, TimeZone.UTC))
    }
}
