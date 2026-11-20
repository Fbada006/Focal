package io.disruption.focal.core.time

import io.disruption.focal.testing.FakeClock
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class WaitUntilTest {
    @Test
    fun returnsAtOnceWhenTheMomentHasPassed() = runTest {
        val clock = FakeClock()
        val waiter = launch { clock.waitUntil(clock.now - 1.minutes) }
        runCurrent()
        assertTrue(waiter.isCompleted)
    }

    @Test
    fun keepsWaitingWhenTheClockMovedBackwardDuringTheWait() = runTest {
        val clock = FakeClock()
        val at = clock.now + 10.minutes
        val waiter = launch { clock.waitUntil(at) }

        // Ten minutes pass, but the wall clock was set back by ten minutes meanwhile.
        advanceTimeBy(10.minutes)
        runCurrent()
        assertFalse(waiter.isCompleted)

        clock.advanceBy(10.minutes)
        advanceTimeBy(10.minutes)
        runCurrent()
        assertTrue(waiter.isCompleted)
    }
}
