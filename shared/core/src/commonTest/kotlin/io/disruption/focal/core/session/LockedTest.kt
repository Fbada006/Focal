package io.disruption.focal.core.session

import io.disruption.focal.core.model.Outcome
import io.disruption.focal.core.model.Strictness
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class LockedTest {
    private val f = SessionTestFixture()

    @Test
    fun aLockedSessionCannotBeEndedOrPausedInFocal() = runTest {
        val session = f.startFor(1.hours, strictness = Strictness.Locked)

        assertEquals(EndResult.NotAllowed(Strictness.Locked), f.service.endEarly(session.id))
        assertEquals(CountdownResult.NotAllowed(Strictness.Locked), f.service.beginExitCountdown(session.id))
        assertEquals(EndResult.NotAllowed(Strictness.Locked), f.service.confirmEndEarly(session.id))
        assertEquals(BreakResult.NotAllowed(Strictness.Locked), f.service.takeBreak(session.id))
        assertTrue(f.stored(session).isRunning)
        assertTrue(session.id in f.engine.applied)
    }

    @Test
    fun aLockedSessionThatRunsOutIsCompleted() = runTest {
        val session = f.startFor(1.hours, strictness = Strictness.Locked)
        f.clock.advanceBy(1.hours)
        f.service.completeDueSessions()

        assertEquals(Outcome.Completed, f.stored(session).outcome)
    }

    @Test
    fun defeatedBlockingBreaksRunningLockedSessionsAndLiftsThem() = runTest {
        val locked = f.startFor(1.hours, strictness = Strictness.Locked)
        val normal = f.startFor(1.hours, list = newsList, strictness = Strictness.Normal)
        val delayed = f.startFor(1.hours, list = newsList, strictness = Strictness.Delayed)
        f.clock.advanceBy(20.minutes)

        val broken = f.service.markBlockingDefeated()

        assertEquals(listOf(locked.id), broken.map { it.id })
        assertEquals(Outcome.Broken, f.stored(locked).outcome)
        assertEquals(f.clock.now, f.stored(locked).endedAt)
        assertTrue(locked.id !in f.engine.applied)
        assertTrue(f.stored(normal).isRunning)
        assertTrue(f.stored(delayed).isRunning)
    }

    @Test
    fun aLockedSessionThatHadAlreadyRunOutIsCompletedNotBroken() = runTest {
        val session = f.startFor(1.hours, strictness = Strictness.Locked)
        f.clock.advanceBy(2.hours)

        assertTrue(f.service.markBlockingDefeated().isEmpty())
        assertEquals(Outcome.Completed, f.stored(session).outcome)
    }

    @Test
    fun aBrokenSessionStaysBroken() = runTest {
        val session = f.startFor(1.hours, strictness = Strictness.Locked)
        f.service.markBlockingDefeated()
        f.clock.advanceBy(2.hours)
        f.service.completeDueSessions()

        assertEquals(Outcome.Broken, f.stored(session).outcome)
    }

    @Test
    fun raisingADelayedSessionToLockedMidCountdownStopsItEndingEarly() = runTest {
        val session = f.startFor(1.hours, strictness = Strictness.Delayed)
        f.service.beginExitCountdown(session.id)
        f.service.edit(session.id, strictness = Strictness.Locked)
        f.clock.advanceBy(31.seconds)

        assertEquals(EndResult.NotAllowed(Strictness.Locked), f.service.confirmEndEarly(session.id))
        assertTrue(f.stored(session).isRunning)
    }
}
