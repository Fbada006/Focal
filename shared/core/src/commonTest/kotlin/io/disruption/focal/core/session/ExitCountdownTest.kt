package io.disruption.focal.core.session

import io.disruption.focal.core.model.Outcome
import io.disruption.focal.core.model.Strictness
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ExitCountdownTest {
    private val f = SessionTestFixture()

    @Test
    fun aDelayedSessionEndsEarlyOnlyAfterTheCountdownAndAConfirmation() = runTest {
        val session = f.startFor(1.hours, strictness = Strictness.Delayed)
        f.clock.advanceBy(10.minutes)

        val countdown = assertIs<CountdownResult.Started>(f.service.beginExitCountdown(session.id)).countdown
        assertEquals(f.clock.now + 30.seconds, countdown.endsAt)

        f.clock.advanceBy(29.seconds)
        assertEquals(EndResult.CountdownRequired, f.service.confirmEndEarly(session.id))
        assertTrue(f.stored(session).isRunning)

        f.clock.advanceBy(1.seconds)
        val ended = assertIs<EndResult.Ended>(f.service.confirmEndEarly(session.id)).session
        assertEquals(Outcome.EndedEarly, ended.outcome)
        assertEquals(f.clock.now, ended.endedAt)
        assertTrue(f.engine.applied.isEmpty())
    }

    @Test
    fun confirmingWithoutACountdownChangesNothing() = runTest {
        val session = f.startFor(1.hours, strictness = Strictness.Delayed)

        assertEquals(EndResult.CountdownRequired, f.service.confirmEndEarly(session.id))
        assertEquals(EndResult.NotAllowed(Strictness.Delayed), f.service.endEarly(session.id))
        assertTrue(f.stored(session).isRunning)
    }

    @Test
    fun leavingTheCountdownCancelsIt() = runTest {
        val session = f.startFor(1.hours, strictness = Strictness.Delayed)
        f.service.beginExitCountdown(session.id)
        f.service.cancelExitCountdown(session.id)
        f.clock.advanceBy(1.minutes)

        assertEquals(EndResult.CountdownRequired, f.service.confirmEndEarly(session.id))
        assertTrue(f.stored(session).isRunning)
    }

    @Test
    fun notConfirmingLeavesTheSessionRunning() = runTest {
        val session = f.startFor(1.hours, strictness = Strictness.Delayed)
        f.service.beginExitCountdown(session.id)
        f.clock.advanceBy(5.minutes)
        f.service.completeDueSessions()

        assertTrue(f.stored(session).isRunning)
    }

    @Test
    fun restartingTheCountdownStartsTheWaitAgain() = runTest {
        val session = f.startFor(1.hours, strictness = Strictness.Delayed)
        f.service.beginExitCountdown(session.id)
        f.clock.advanceBy(20.seconds)
        f.service.beginExitCountdown(session.id)
        f.clock.advanceBy(20.seconds)

        assertEquals(EndResult.CountdownRequired, f.service.confirmEndEarly(session.id))
    }

    @Test
    fun onlyDelayedSessionsHaveACountdown() = runTest {
        val normal = f.startFor(1.hours, strictness = Strictness.Normal)
        val locked = f.startFor(1.hours, strictness = Strictness.Locked)

        assertEquals(CountdownResult.NotAllowed(Strictness.Normal), f.service.beginExitCountdown(normal.id))
        assertEquals(CountdownResult.NotAllowed(Strictness.Locked), f.service.beginExitCountdown(locked.id))
    }

    @Test
    fun confirmingAfterThePlannedEndRecordsCompleted() = runTest {
        val session = f.startFor(10.minutes, strictness = Strictness.Delayed)
        f.clock.advanceBy(9.minutes + 50.seconds)
        f.service.beginExitCountdown(session.id)
        f.clock.advanceBy(30.seconds)

        val ended = assertIs<EndResult.Ended>(f.service.confirmEndEarly(session.id)).session
        assertEquals(Outcome.Completed, ended.outcome)
    }
}
