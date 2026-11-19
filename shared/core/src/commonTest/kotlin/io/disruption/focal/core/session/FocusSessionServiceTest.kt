package io.disruption.focal.core.session

import io.disruption.focal.core.model.BlockList
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.BlockedSet
import io.disruption.focal.core.model.Outcome
import io.disruption.focal.core.model.Strictness
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class FocusSessionServiceTest {

    @Test
    fun startingASessionAppliesItsBlockListAndRecordsItAsRunning() = runTest {
        val f = SessionTestFixture()

        val session = f.startFor(25.minutes)

        assertEquals(f.clock.now, session.startedAt)
        assertEquals(f.clock.now + 25.minutes, session.plannedEnd)
        assertEquals("Social", session.name)
        assertTrue(session.isRunning)
        assertEquals(session, f.engine.applied[session.id])
        assertTrue(f.engine.isAppBlocked("com.instagram.android"))
    }

    @Test
    fun aSessionCanRunUntilAnEndTime() = runTest {
        val f = SessionTestFixture()
        val end = f.clock.now + 2.hours

        val result = f.service.start(socialList.id, SessionLength.Until(end), Strictness.Normal)

        assertEquals(end, assertIs<StartResult.Started>(result).session.plannedEnd)
    }

    @Test
    fun aSessionTakesItsOwnCopyOfTheBlockList() = runTest {
        val f = SessionTestFixture()
        val session = f.startFor(1.hours)

        f.blockLists.save(socialList.copy(blocked = BlockedSet(apps = setOf("other.app"))))

        assertEquals(socialList.blocked, f.stored(session).blocked)
        assertTrue(f.engine.isAppBlocked("com.instagram.android"))
    }

    @Test
    fun startingIsRefusedForAMissingOrEmptyBlockListOrAnEndInThePast() = runTest {
        val empty = BlockList(BlockListId("empty"), "Empty", BlockedSet.Empty)
        val f = SessionTestFixture(socialList, empty)

        assertEquals(
            StartResult.BlockListNotFound,
            f.service.start(BlockListId("nope"), SessionLength.For(1.hours), Strictness.Normal),
        )
        assertEquals(
            StartResult.EmptyBlockList,
            f.service.start(empty.id, SessionLength.For(1.hours), Strictness.Normal),
        )
        assertEquals(
            StartResult.EndNotInFuture,
            f.service.start(socialList.id, SessionLength.Until(f.clock.now), Strictness.Normal),
        )
        assertTrue(f.engine.calls.isEmpty())
        assertTrue(f.sessions.all.isEmpty())
    }

    @Test
    fun reachingThePlannedEndRecordsCompletedAndLiftsBlocking() = runTest {
        val f = SessionTestFixture()
        val session = f.startFor(25.minutes)

        f.clock.advanceBy(24.minutes)
        assertTrue(f.service.completeDueSessions().isEmpty())
        assertTrue(f.stored(session).isRunning)

        f.clock.advanceBy(3.minutes)
        val completed = f.service.completeDueSessions()

        assertEquals(listOf(session.id), completed.map { it.id })
        val stored = f.stored(session)
        assertEquals(Outcome.Completed, stored.outcome)
        assertEquals(session.plannedEnd, stored.endedAt, "a late check still ends at the planned end")
        assertFalse(f.engine.isAppBlocked("com.instagram.android"))
    }

    @Test
    fun endingANormalSessionEarlyRecordsEndedEarly() = runTest {
        val f = SessionTestFixture()
        val session = f.startFor(1.hours)
        f.clock.advanceBy(10.minutes)

        val result = f.service.endEarly(session.id)

        val ended = assertIs<EndResult.Ended>(result).session
        assertEquals(Outcome.EndedEarly, ended.outcome)
        assertEquals(f.clock.now, ended.endedAt)
        assertEquals(10.minutes, ended.focusDuration)
        assertNull(f.engine.applied[session.id])
    }

    @Test
    fun endingAfterThePlannedEndRecordsCompletedInstead() = runTest {
        val f = SessionTestFixture()
        val session = f.startFor(30.minutes)
        f.clock.advanceBy(31.minutes)

        val ended = assertIs<EndResult.Ended>(f.service.endEarly(session.id)).session

        assertEquals(Outcome.Completed, ended.outcome)
    }

    @Test
    fun aFinishedSessionCannotBeEndedAgain() = runTest {
        val f = SessionTestFixture()
        val session = f.startFor(1.hours)
        f.service.endEarly(session.id)

        assertEquals(EndResult.NotRunning, f.service.endEarly(session.id))
    }

    @Test
    fun delayedAndLockedSessionsCannotBeEndedDirectly() = runTest {
        val f = SessionTestFixture()
        val delayed = f.startFor(1.hours, strictness = Strictness.Delayed)
        val locked = f.startFor(1.hours, list = newsList, strictness = Strictness.Locked)

        assertEquals(EndResult.NotAllowed(Strictness.Delayed), f.service.endEarly(delayed.id))
        assertEquals(EndResult.NotAllowed(Strictness.Locked), f.service.endEarly(locked.id))
        assertTrue(f.stored(delayed).isRunning)
        assertTrue(f.stored(locked).isRunning)
    }

    @Test
    fun sessionsRunConcurrentlyAndAreAppliedAndLiftedOnTheirOwn() = runTest {
        val f = SessionTestFixture()
        val social = f.startFor(1.hours)
        val alsoSocial = f.startFor(2.hours)
        val news = f.startFor(30.minutes, list = newsList)

        assertEquals(setOf(social.id, alsoSocial.id, news.id), f.engine.applied.keys)

        f.service.endEarly(social.id)
        assertTrue(
            f.engine.isAppBlocked("com.instagram.android"),
            "still blocked while another running session includes it",
        )

        f.clock.advanceBy(30.minutes)
        f.service.completeDueSessions()

        assertEquals(setOf(alsoSocial.id), f.engine.applied.keys)
        assertEquals(Outcome.EndedEarly, f.stored(social).outcome)
        assertEquals(Outcome.Completed, f.stored(news).outcome)
        assertTrue(f.stored(alsoSocial).isRunning)
        assertFalse(f.engine.isAppBlocked("com.news.app"))
    }

    @Test
    fun restoreReappliesRunningSessionsAndCompletesThoseThatRanOut() = runTest {
        val f = SessionTestFixture()
        val short = f.startFor(10.minutes)
        val long = f.startFor(1.hours, list = newsList)
        f.engine.applied.clear() // the process died and the engine lost its state
        f.clock.advanceBy(20.minutes)

        val running = f.service.restore()

        assertEquals(listOf(long.id), running.map { it.id })
        assertEquals(Outcome.Completed, f.stored(short).outcome)
        assertEquals(setOf(long.id), f.engine.applied.keys)
    }
}
