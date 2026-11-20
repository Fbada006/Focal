package io.disruption.focal.core.session

import io.disruption.focal.core.model.Outcome
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.permissions.Permission
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
    fun revokingTheAccessibilityServiceBreaksRunningLockedSessions() = runTest {
        val locked = f.startFor(1.hours, strictness = Strictness.Locked)
        val delayed = f.startFor(1.hours, list = newsList, strictness = Strictness.Delayed)
        f.clock.advanceBy(10.minutes)

        val broken = f.service.checkPermissions(listOf(Permission.AccessibilityService))

        assertEquals(listOf(locked.id), broken.map { it.id })
        assertEquals(Outcome.Broken, f.stored(locked).outcome)
        assertTrue(locked.id !in f.engine.applied)
        assertTrue(f.stored(delayed).isRunning)
    }

    @Test
    fun brokenSessionsWaitToBeAcknowledged() = runTest {
        val locked = f.startFor(1.hours, strictness = Strictness.Locked)
        assertTrue(f.service.brokenToAcknowledge.value.isEmpty())

        f.service.checkPermissions(listOf(Permission.AccessibilityService))
        assertEquals(listOf(locked.id), f.service.brokenToAcknowledge.value.map { it.id })

        f.service.acknowledgeBroken()
        assertTrue(f.service.brokenToAcknowledge.value.isEmpty())
    }

    @Test
    fun revokingScreenTimeBreaksRunningLockedSessions() = runTest {
        val locked = f.startFor(1.hours, strictness = Strictness.Locked)

        f.service.checkPermissions(listOf(Permission.ScreenTime))

        assertEquals(Outcome.Broken, f.stored(locked).outcome)
    }

    @Test
    fun missingPermissionsBlockingDoesNotDependOnLeaveLockedSessionsRunning() = runTest {
        val locked = f.startFor(1.hours, strictness = Strictness.Locked)

        assertTrue(f.service.checkPermissions(emptyList()).isEmpty())
        assertTrue(f.service.checkPermissions(listOf(Permission.Notifications, Permission.ExactAlarms)).isEmpty())
        assertTrue(f.stored(locked).isRunning)
        assertTrue(locked.id in f.engine.applied)
    }

    @Test
    fun aRevocationFoundAfterTheSessionRanOutCompletesIt() = runTest {
        // Focal's process was dead past the planned end; the check on next start finds it revoked.
        val locked = f.startFor(1.hours, strictness = Strictness.Locked)
        f.clock.advanceBy(3.hours)

        assertTrue(f.service.checkPermissions(listOf(Permission.AccessibilityService)).isEmpty())
        assertEquals(Outcome.Completed, f.stored(locked).outcome)
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
