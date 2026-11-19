package io.disruption.focal.core.schedule

import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.session.SessionTestFixture
import io.disruption.focal.core.session.socialList
import io.disruption.focal.core.time.TimeZoneProvider
import io.disruption.focal.testing.InMemoryScheduleRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.TUESDAY
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ScheduleServiceTest {
    private val f = SessionTestFixture().apply { clock.now = Instant.parse("2026-01-05T10:00:00Z") } // Monday
    private val repo = InMemoryScheduleRepository()
    private val service = ScheduleService(repo, f.blockLists, f.service, TimeZoneProvider { TimeZone.UTC }, newId = { "sch" })

    private val draft = ScheduleDraft(
        name = " Work hours ",
        days = setOf(TUESDAY),
        start = LocalTime(9, 0),
        end = LocalTime(17, 0),
        blockListId = socialList.id,
        strictness = Strictness.Delayed,
    )

    @Test
    fun savesAValidSchedule() = runTest {
        val saved = assertIs<ScheduleSaveResult.Saved>(service.create(draft)).schedule
        assertEquals("Work hours", saved.name)
        assertEquals(saved, repo.get(saved.id))
    }

    @Test
    fun reportsEveryProblem() = runTest {
        val result = service.create(draft.copy(name = " ", days = emptySet(), end = LocalTime(9, 0), blockListId = null))
        assertEquals(
            ScheduleSaveResult.Invalid(
                setOf(ScheduleProblem.NoName, ScheduleProblem.NoDays, ScheduleProblem.SameStartAndEnd, ScheduleProblem.NoBlockList),
            ),
            result,
        )
        assertTrue(repo.getAll().isEmpty())
    }

    @Test
    fun aWindowOpenNowStartsItsSessionOnSave() = runTest {
        service.create(draft.copy(days = setOf(MONDAY)))
        val session = f.sessions.getRunning().single()
        assertEquals(Instant.parse("2026-01-05T17:00:00Z"), session.plannedEnd)
    }

    @Test
    fun editingOrSwitchingOffDoesNotWeakenTheRunningSession() = runTest {
        val schedule = assertIs<ScheduleSaveResult.Saved>(service.create(draft.copy(days = setOf(MONDAY)))).schedule
        val running = f.sessions.getRunning().single()

        service.update(schedule.id, draft.copy(days = setOf(MONDAY), end = LocalTime(11, 0), strictness = Strictness.Normal))
        service.setEnabled(schedule.id, false)
        service.delete(schedule.id)

        assertEquals(listOf(running), f.sessions.getRunning())
    }

    @Test
    fun editsApplyToFutureSessions() = runTest {
        val schedule = assertIs<ScheduleSaveResult.Saved>(service.create(draft)).schedule
        service.update(schedule.id, draft.copy(end = LocalTime(12, 0), strictness = Strictness.Locked))
        f.clock.advanceBy(24.hours) // Tuesday 10:00

        val session = service.evaluate().single()
        assertEquals(Strictness.Locked, session.strictness)
        assertEquals(Instant.parse("2026-01-06T12:00:00Z"), session.plannedEnd)
    }

    @Test
    fun aScheduleOnAnEmptyBlockListIsRefused() = runTest {
        val empty = io.disruption.focal.core.model.BlockList(
            io.disruption.focal.core.model.BlockListId("empty"), "Empty", io.disruption.focal.core.model.BlockedSet.Empty,
        )
        f.blockLists.save(empty)

        assertEquals(
            ScheduleSaveResult.Invalid(setOf(ScheduleProblem.EmptyBlockList)),
            service.create(draft.copy(blockListId = empty.id)),
        )
    }

    @Test
    fun movingTheStartOfARunningSchedulesWindowDoesNotStartASecondSession() = runTest {
        val saved = assertIs<ScheduleSaveResult.Saved>(service.create(draft.copy(days = setOf(MONDAY)))).schedule
        f.clock.now = f.clock.now + 1.hours

        service.update(saved.id, draft.copy(days = setOf(MONDAY), start = LocalTime(10, 30)))

        assertEquals(1, f.sessions.getRunning().size)
    }

    @Test
    fun aBlockListUsedByAScheduleCannotBeDeleted() = runTest {
        service.create(draft)
        val lists = io.disruption.focal.core.blocklist.BlockListService(f.blockLists, f.sessions, f.service, schedules = repo)

        assertEquals(
            io.disruption.focal.core.blocklist.DeleteResult.UsedBySchedules(listOf("Work hours")),
            lists.delete(socialList.id),
        )
    }
}
