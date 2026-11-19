package io.disruption.focal.core.stats

import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.BlockedSet
import io.disruption.focal.core.model.Break
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Outcome
import io.disruption.focal.core.model.ScheduleId
import io.disruption.focal.core.model.Strictness
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class StatsCalculatorTest {
    private val zone = TimeZone.of("Europe/Berlin")
    private var ids = 0

    private fun at(text: String): Instant = LocalDateTime.parse(text).toInstant(zone)

    private fun session(
        start: String,
        length: Duration,
        outcome: Outcome? = Outcome.Completed,
        breaks: List<Break> = emptyList(),
        scheduled: Boolean = false,
    ): FocusSession {
        val startedAt = at(start)
        return FocusSession(
            id = FocusSessionId("s${++ids}"),
            blockListId = BlockListId("b"),
            name = "S",
            strictness = Strictness.Normal,
            startedAt = startedAt,
            plannedEnd = startedAt + length,
            blocked = BlockedSet.Empty,
            scheduleId = if (scheduled) ScheduleId("sch") else null,
            breaks = breaks,
            endedAt = outcome?.let { startedAt + length },
            outcome = outcome,
        )
    }

    private fun stats(now: String, vararg sessions: FocusSession) =
        StatsCalculator.calculate(sessions.toList(), at(now), zone)

    // 2026-01-07 is a Wednesday.

    @Test
    fun focusTimeTodayAndThisWeek() {
        val s = stats(
            "2026-01-07T20:00",
            session("2026-01-07T09:00", 1.hours),
            session("2026-01-07T14:00", 30.minutes, Outcome.EndedEarly),
            session("2026-01-05T09:00", 2.hours), // Monday, this week
            session("2026-01-04T09:00", 3.hours), // Sunday, last week
        )
        assertEquals(90.minutes, s.focusToday)
        assertEquals(210.minutes, s.focusThisWeek)
    }

    @Test
    fun focusTimeExcludesBreaksAndCountsRunningSessionsSoFar() {
        val withBreak = session(
            "2026-01-07T09:00",
            1.hours,
            breaks = listOf(Break(at("2026-01-07T09:10"), at("2026-01-07T09:15"))),
        )
        val running = session("2026-01-07T19:30", 2.hours, outcome = null)
        val s = stats("2026-01-07T20:00", withBreak, running)
        assertEquals(55.minutes + 30.minutes, s.focusToday)
    }

    @Test
    fun aSessionCrossingMidnightIsSplitForFocusTime() {
        val s = stats("2026-01-07T10:00", session("2026-01-06T23:00", 2.hours))
        assertEquals(1.hours, s.focusToday)
    }

    @Test
    fun countsOutcomes() {
        val s = stats(
            "2026-01-07T20:00",
            session("2026-01-07T09:00", 1.hours),
            session("2026-01-06T09:00", 1.hours),
            session("2026-01-06T12:00", 1.hours, Outcome.EndedEarly),
            session("2026-01-05T12:00", 1.hours, Outcome.Broken),
            session("2026-01-07T19:00", 2.hours, outcome = null),
        )
        assertEquals(Triple(2, 1, 1), Triple(s.completed, s.endedEarly, s.broken))
    }

    @Test
    fun theStreakCountsConsecutiveDaysWithACompletedSessionOfFifteenMinutes() {
        val s = stats(
            "2026-01-07T20:00",
            session("2026-01-05T09:00", 15.minutes),
            session("2026-01-06T09:00", 1.hours),
            session("2026-01-07T09:00", 20.minutes),
        )
        assertEquals(3, s.currentStreak)
        assertEquals(3, s.longestStreak)
    }

    @Test
    fun breaksDoNotCountTowardTheStreakMinimum() {
        val fifteenWithABreak = session(
            "2026-01-07T09:00",
            15.minutes,
            breaks = listOf(Break(at("2026-01-07T09:05"), at("2026-01-07T09:10"))),
        )
        val twentyWithABreak = session(
            "2026-01-07T10:00",
            20.minutes,
            breaks = listOf(Break(at("2026-01-07T10:05"), at("2026-01-07T10:10"))),
        )

        assertEquals(0, stats("2026-01-07T20:00", fifteenWithABreak).currentStreak)
        assertEquals(1, stats("2026-01-07T20:00", twentyWithABreak).currentStreak)
    }

    @Test
    fun shortSessionsDoNotCount() {
        val s = stats(
            "2026-01-07T20:00",
            session("2026-01-06T09:00", 1.hours),
            session("2026-01-07T09:00", 14.minutes),
        )
        assertEquals(1, s.currentStreak, "today has only a 14-minute session; yesterday still counts")
    }

    @Test
    fun theStreakSurvivesUntilTheEndOfTodayAndBreaksOnAMissedDay() {
        val sessions = arrayOf(session("2026-01-05T09:00", 1.hours), session("2026-01-06T09:00", 1.hours))
        assertEquals(2, stats("2026-01-07T23:00", *sessions).currentStreak, "today isn't over yet")
        assertEquals(0, stats("2026-01-08T09:00", *sessions).currentStreak, "Wednesday was missed")
        assertEquals(2, stats("2026-01-08T09:00", *sessions).longestStreak)
    }

    @Test
    fun aSessionCrossingMidnightCountsForTheDayItEnds() {
        val s = stats(
            "2026-01-07T20:00",
            session("2026-01-05T23:30", 1.hours), // ends Tuesday 00:30
            session("2026-01-07T09:00", 1.hours),
        )
        assertEquals(2, s.currentStreak)
    }

    @Test
    fun endedEarlySessionsNeitherCountNorReset() {
        val s = stats(
            "2026-01-07T20:00",
            session("2026-01-06T09:00", 1.hours),
            session("2026-01-07T09:00", 1.hours, Outcome.EndedEarly),
        )
        assertEquals(1, s.currentStreak)
    }

    @Test
    fun aBrokenSessionResetsTheStreakAtOnce() {
        val s = stats(
            "2026-01-07T20:00",
            session("2026-01-05T09:00", 1.hours),
            session("2026-01-06T09:00", 1.hours),
            session("2026-01-07T09:00", 1.hours),
            session("2026-01-07T12:00", 1.hours, Outcome.Broken),
        )
        assertEquals(0, s.currentStreak)
        assertEquals(3, s.longestStreak)
    }

    @Test
    fun daysCompletedAfterABrokenSessionStartANewStreak() {
        val s = stats(
            "2026-01-07T20:00",
            session("2026-01-06T09:00", 1.hours),
            session("2026-01-07T08:00", 1.hours, Outcome.Broken),
            session("2026-01-07T12:00", 1.hours),
        )
        assertEquals(1, s.currentStreak)
    }

    @Test
    fun scheduleStartedSessionsCountLikeManualOnes() {
        val s = stats(
            "2026-01-07T20:00",
            session("2026-01-06T09:00", 1.hours, scheduled = true),
            session("2026-01-07T09:00", 1.hours),
        )
        assertEquals(2, s.currentStreak)
        assertEquals(2, s.completed)
    }
}
