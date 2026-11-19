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

class BreakTest {
    private val f = SessionTestFixture()

    @Test
    fun aBreakPausesTheSessionForFiveMinutes() = runTest {
        val session = f.startFor(1.hours, strictness = Strictness.Delayed)
        f.clock.advanceBy(10.minutes)

        val result = assertIs<BreakResult.Started>(f.service.takeBreak(session.id))

        assertEquals(f.clock.now + 5.minutes, result.endsAt)
        assertEquals(f.clock.now + 5.minutes, f.engine.pausedUntil[session.id])
        assertEquals(2, result.session.breaksLeft)
        assertEquals(session.plannedEnd, f.stored(session).plannedEnd, "Breaks don't move the end time")
    }

    @Test
    fun atMostThreeBreaks() = runTest {
        val session = f.startFor(2.hours, strictness = Strictness.Delayed)
        repeat(3) {
            assertIs<BreakResult.Started>(f.service.takeBreak(session.id))
            f.clock.advanceBy(10.minutes)
        }

        assertEquals(BreakResult.NoBreaksLeft, f.service.takeBreak(session.id))
        assertEquals(0, f.stored(session).breaksLeft)
    }

    @Test
    fun noSecondBreakWhileOnABreak() = runTest {
        val session = f.startFor(1.hours, strictness = Strictness.Delayed)
        f.service.takeBreak(session.id)
        f.clock.advanceBy(4.minutes)

        assertEquals(BreakResult.AlreadyOnBreak, f.service.takeBreak(session.id))
    }

    @Test
    fun breaksAreOnlyForDelayedSessions() = runTest {
        val normal = f.startFor(1.hours, strictness = Strictness.Normal)
        val locked = f.startFor(1.hours, strictness = Strictness.Locked)

        assertEquals(BreakResult.NotAllowed(Strictness.Normal), f.service.takeBreak(normal.id))
        assertEquals(BreakResult.NotAllowed(Strictness.Locked), f.service.takeBreak(locked.id))
        assertTrue(f.engine.pausedUntil.isEmpty())
    }

    @Test
    fun aSessionWithBreaksIsStillCompleted() = runTest {
        val session = f.startFor(30.minutes, strictness = Strictness.Delayed)
        f.service.takeBreak(session.id)
        f.clock.advanceBy(30.minutes)
        f.service.completeDueSessions()

        assertEquals(Outcome.Completed, f.stored(session).outcome)
        assertEquals(1, f.stored(session).breaks.size)
    }

    @Test
    fun aBreakNearTheEndStopsAtThePlannedEnd() = runTest {
        val session = f.startFor(30.minutes, strictness = Strictness.Delayed)
        f.clock.advanceBy(28.minutes)

        val result = assertIs<BreakResult.Started>(f.service.takeBreak(session.id))

        assertEquals(session.plannedEnd, result.endsAt)
    }

    @Test
    fun restoreKeepsABreakInProgressPaused() = runTest {
        val session = f.startFor(1.hours, strictness = Strictness.Delayed)
        f.service.takeBreak(session.id)
        f.engine.applied.clear()
        f.engine.pausedUntil.clear()
        f.clock.advanceBy(2.minutes)

        f.service.restore()

        assertTrue(session.id in f.engine.applied)
        assertEquals(f.stored(session).breaks.single().endsAt, f.engine.pausedUntil[session.id])
    }
}
