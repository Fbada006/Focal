package io.disruption.focal.core.blocking

import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.BlockedSet
import io.disruption.focal.core.model.Break
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Outcome
import io.disruption.focal.core.model.Strictness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class BlockingStateTest {
    private val now = Instant.parse("2026-11-19T09:30:00Z")

    private fun session(
        id: String,
        name: String = id,
        endIn: kotlin.time.Duration = 30.minutes,
        blocked: BlockedSet = BlockedSet(apps = setOf("com.example.app")),
        breaks: List<Break> = emptyList(),
    ) = FocusSession(
        id = FocusSessionId(id),
        blockListId = BlockListId("list"),
        name = name,
        strictness = Strictness.Normal,
        startedAt = now - 5.minutes,
        plannedEnd = now + endIn,
        blocked = blocked,
        breaks = breaks,
    )

    @Test
    fun aBlockedAppNamesTheSessionThatHoldsIt() {
        val content = BlockingState(listOf(session("s1", name = "Deep work"))).forApp("com.example.app", null, now)

        assertEquals("s1", content?.sessionId)
        assertEquals("Deep work", content?.sessionName)
        assertEquals(0, content?.otherSessions)
        assertEquals(now + 30.minutes, content?.plannedEnd)
    }

    @Test
    fun sessionsAreAUnionAndTheSoonestEndIsShown() {
        val state = BlockingState(
            listOf(
                session("later", endIn = 60.minutes),
                session("sooner", endIn = 10.minutes),
            ),
        )

        val content = state.forApp("com.example.app", null, now)

        assertEquals("sooner", content?.sessionId)
        assertEquals(1, content?.otherSessions)
        assertEquals(now + 10.minutes, content?.plannedEnd)
    }

    @Test
    fun aSessionOnABreakDoesNotBlock() {
        val onBreak = session(
            "s1",
            breaks = listOf(Break(startedAt = now - 1.minutes, endsAt = now + 4.minutes)),
        )

        assertNull(BlockingState(listOf(onBreak)).forApp("com.example.app", null, now))
        // Once the Break is over the same session blocks again.
        assertEquals("s1", BlockingState(listOf(onBreak)).forApp("com.example.app", null, now + 4.minutes)?.sessionId)
    }

    @Test
    fun anAppCategoryBlocksAppsThatWereNotPicked() {
        val state = BlockingState(
            listOf(session("s1", blocked = BlockedSet(appCategories = setOf("social")))),
        )

        assertEquals("s1", state.forApp("com.new.social.app", "social", now)?.sessionId)
        assertNull(state.forApp("com.new.social.app", "games", now))
        assertNull(state.forApp("com.new.social.app", null, now))
    }

    @Test
    fun websitesMatchSubdomainsAndIgnoreUnblockedHosts() {
        val state = BlockingState(
            listOf(session("s1", blocked = BlockedSet(domains = setOf("youtube.com")))),
        )

        assertEquals("s1", state.forWebsite("https://m.youtube.com/watch?v=1", now)?.sessionId)
        assertEquals("s1", state.forWebsite("youtube.com", now)?.sessionId)
        assertNull(state.forWebsite("notyoutube.com", now))
    }

    @Test
    fun nothingIsBlockedWhenNoSessionMatchesOrSessionsHaveEnded() {
        val ended = session("s1", endIn = -1.minutes)

        assertNull(BlockingState(emptyList()).forApp("com.example.app", null, now))
        assertNull(BlockingState(listOf(ended)).forApp("com.example.app", null, now))
        assertNull(BlockingState(listOf(session("s1"))).forApp("com.other.app", null, now))
    }

    @Test
    fun theMotivationalLineIsStableForASession() {
        val state = BlockingState(listOf(session("s1")))

        assertEquals(
            state.forApp("com.example.app", null, now)?.motivationalLine,
            state.forApp("com.example.app", null, now + 1.minutes)?.motivationalLine,
        )
    }

    @Test
    fun theNextChangeIsTheSoonestSessionEndOrBreakEnd() {
        val onBreak = session(
            "s1",
            endIn = 30.minutes,
            breaks = listOf(Break(startedAt = now - 1.minutes, endsAt = now + 4.minutes)),
        )
        val other = session("s2", endIn = 10.minutes)

        assertEquals(now + 4.minutes, BlockingState(listOf(onBreak, other)).nextChange(now))
        // Once the Break is over, the next change is the soonest planned end.
        assertEquals(now + 10.minutes, BlockingState(listOf(onBreak, other)).nextChange(now + 4.minutes))
    }

    @Test
    fun thereIsNoNextChangeWhenNothingRuns() {
        assertNull(BlockingState(emptyList()).nextChange(now))
        assertNull(BlockingState(listOf(session("s1", endIn = -1.minutes).copy(outcome = Outcome.Completed))).nextChange(now))
    }

    @Test
    fun anOverduePlannedEndComesBeforeAnyFutureChange() {
        val overdue = session("s1", endIn = -1.minutes)
        val onBreak = session(
            "s2",
            endIn = 30.minutes,
            breaks = listOf(Break(startedAt = now - 1.minutes, endsAt = now + 4.minutes)),
        )

        assertEquals(now - 1.minutes, BlockingState(listOf(onBreak, overdue)).nextChange(now))
        assertEquals(now - 1.minutes, BlockingState(listOf(overdue)).nextChange(now))
    }

    @Test
    fun theSummaryDescribesABlockingSessionBeforeOneOnABreak() {
        val onBreak = session(
            "s1",
            endIn = 10.minutes,
            breaks = listOf(Break(startedAt = now - 1.minutes, endsAt = now + 4.minutes)),
        )
        val blocking = session("s2", endIn = 30.minutes)

        val summary = BlockingState(listOf(onBreak, blocking)).summary(now)

        assertEquals("s2", summary?.session?.id?.value)
        assertNull(summary?.breakEndsAt)
        assertEquals(1, summary?.otherSessions)
    }

    @Test
    fun whenAllAreOnABreakTheSummaryNamesTheOneThatResumesFirst() {
        val later = session("s1", endIn = 10.minutes, breaks = listOf(Break(startedAt = now - 1.minutes, endsAt = now + 4.minutes)))
        val sooner = session("s2", endIn = 30.minutes, breaks = listOf(Break(startedAt = now - 3.minutes, endsAt = now + 2.minutes)))

        val summary = BlockingState(listOf(later, sooner)).summary(now)

        assertEquals("s2", summary?.session?.id?.value)
        assertEquals(now + 2.minutes, summary?.breakEndsAt)
        assertEquals(true, summary?.resumesAfterBreak)
    }

    @Test
    fun aBreakThatRunsToTheSessionsEndDoesNotResumeBlocking() {
        val endsOnBreak = session("s1", endIn = 3.minutes, breaks = listOf(Break(startedAt = now - 2.minutes, endsAt = now + 3.minutes)))

        val summary = BlockingState(listOf(endsOnBreak)).summary(now)

        assertEquals(now + 3.minutes, summary?.breakEndsAt)
        assertEquals(false, summary?.resumesAfterBreak)
        assertNull(BlockingState(emptyList()).summary(now))
    }
}
