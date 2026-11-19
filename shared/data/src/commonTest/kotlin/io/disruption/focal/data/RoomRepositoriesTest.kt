package io.disruption.focal.data

import io.disruption.focal.core.model.BlockList
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.BlockedSet
import io.disruption.focal.core.model.Break
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Outcome
import io.disruption.focal.core.model.Schedule
import io.disruption.focal.core.model.ScheduleId
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.data.repository.RoomBlockListRepository
import io.disruption.focal.data.repository.RoomFocusSessionRepository
import io.disruption.focal.data.repository.RoomScheduleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

class RoomRepositoriesTest {
    private val db = inMemoryDatabase()
    private val blockLists = RoomBlockListRepository(db.blockListDao())
    private val sessions = RoomFocusSessionRepository(db.focusSessionDao())
    private val schedules = RoomScheduleRepository(db.scheduleDao())

    private val start = Instant.parse("2026-01-05T09:00:00Z")
    private val social = BlockList(
        BlockListId("social"),
        "Social",
        BlockedSet(apps = setOf("a.b", "c.d"), appCategories = setOf("social"), domains = setOf("x.com")),
    )

    private fun session(id: String, end: Instant = start + 1.hours, outcome: Outcome? = null) = FocusSession(
        id = FocusSessionId(id),
        blockListId = social.id,
        name = social.name,
        strictness = Strictness.Delayed,
        startedAt = start,
        plannedEnd = end,
        blocked = social.blocked,
        scheduleId = ScheduleId("evenings"),
        breaks = listOf(Break(start + 10.minutes, start + 15.minutes)),
        endedAt = if (outcome == null) null else end,
        outcome = outcome,
    )

    @AfterTest
    fun close() = db.close()

    @Test
    fun blockListsRoundTripAndAreListedByName() = runTest {
        val work = BlockList(BlockListId("work"), "Advice", BlockedSet(domains = setOf("news.example")))
        blockLists.save(social)
        blockLists.save(work)

        assertEquals(social, blockLists.get(social.id))
        assertEquals(listOf(work, social), blockLists.observeAll().first())

        blockLists.save(social.copy(name = "Feeds"))
        assertEquals("Feeds", blockLists.get(social.id)?.name)

        blockLists.delete(social.id)
        assertNull(blockLists.get(social.id))
    }

    @Test
    fun sessionsRoundTripWithTheirBlockedSetAndBreaks() = runTest {
        val s = session("s1")
        sessions.save(s)

        assertEquals(s, sessions.get(s.id))
    }

    @Test
    fun runningSessionsExcludeFinishedOnesAndAreOrderedByPlannedEnd() = runTest {
        val later = session("later", end = start + 2.hours)
        val sooner = session("sooner", end = start + 1.hours)
        val done = session("done", outcome = Outcome.Completed)
        listOf(later, sooner, done).forEach { sessions.save(it) }

        assertEquals(listOf(sooner, later), sessions.getRunning())
        assertEquals(listOf(sooner, later), sessions.observeRunning().first())
        assertEquals(3, sessions.observeAll().first().size)

        sessions.save(sooner.copy(outcome = Outcome.EndedEarly, endedAt = start + 5.minutes))
        assertEquals(listOf(later), sessions.getRunning())
    }

    @Test
    fun schedulesRoundTripAndCanBeSwitchedAndDeleted() = runTest {
        val night = Schedule(
            ScheduleId("night"),
            "Wind down",
            setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY),
            LocalTime(22, 0),
            LocalTime(7, 30),
            social.id,
            Strictness.Locked,
        )
        schedules.save(night)
        assertEquals(night, schedules.get(night.id))
        assertEquals(listOf(night), schedules.observeAll().first())

        schedules.save(night.copy(enabled = false))
        assertEquals(false, schedules.getAll().single().enabled)

        schedules.delete(night.id)
        assertNull(schedules.get(night.id))
    }

    @Test
    fun sessionsStartedByAScheduleAreFoundSinceAWindowStart() = runTest {
        sessions.save(session("old"))
        val later = session("new").copy(startedAt = start + 2.hours, plannedEnd = start + 3.hours)
        sessions.save(later)

        assertEquals(listOf(later), sessions.getStartedBySchedule(ScheduleId("evenings"), since = start + 1.hours))
    }
}
