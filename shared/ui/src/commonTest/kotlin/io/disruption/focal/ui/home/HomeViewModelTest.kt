package io.disruption.focal.ui.home

import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Outcome
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.permissions.Permission
import io.disruption.focal.core.session.SessionLength
import io.disruption.focal.ui.MainDispatcherTest
import io.disruption.focal.ui.SessionUiFixture
import io.disruption.focal.ui.social
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class HomeViewModelTest : MainDispatcherTest() {
    private val f = SessionUiFixture()
    private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Test
    fun showsEachRunningSessionWithItsStrictnessAndTimeLeft() = runTest {
        f.sessions.start(social.id, SessionLength.For(25.minutes), Strictness.Normal)
        f.sessions.start(social.id, SessionLength.For(90.minutes), Strictness.Locked)
        val vm = HomeViewModel(f.sessions, f.clock, f.permissions, ticks)

        val rows = vm.state.value.running
        assertEquals(listOf("25 min 00 s left", "1 h 30 min left"), rows.map { it.timeLeft })
        assertEquals(listOf(Strictness.Normal, Strictness.Locked), rows.map { it.strictness })
        assertEquals(listOf(true, false), rows.map { it.canEnd })
    }

    @Test
    fun timeLeftUpdatesOnEachTick() = runTest {
        f.sessions.start(social.id, SessionLength.For(25.minutes), Strictness.Normal)
        val vm = HomeViewModel(f.sessions, f.clock, f.permissions, ticks)

        f.clock.advanceBy(61.seconds)
        ticks.emit(Unit)

        assertEquals("23 min 59 s left", vm.state.value.running.single().timeLeft)
    }

    @Test
    fun endingANormalSessionRecordsEndedEarly() = runTest {
        f.sessions.start(social.id, SessionLength.For(25.minutes), Strictness.Normal)
        val vm = HomeViewModel(f.sessions, f.clock, f.permissions, ticks)

        vm.onIntent(HomeIntent.EndClicked("s1"))

        assertTrue(vm.state.value.running.isEmpty())
        assertEquals(Outcome.EndedEarly, f.sessionRepository.get(FocusSessionId("s1"))?.outcome)
    }

    @Test
    fun sessionsThatRunOutMoveToCompleted() = runTest {
        f.sessions.start(social.id, SessionLength.For(25.minutes), Strictness.Delayed)
        val vm = HomeViewModel(f.sessions, f.clock, f.permissions, ticks)

        f.clock.advanceBy(25.minutes)
        ticks.emit(Unit)

        assertTrue(vm.state.value.running.isEmpty())
        assertEquals(Outcome.Completed, f.sessionRepository.get(FocusSessionId("s1"))?.outcome)
        assertTrue(f.engine.applied.isEmpty())
    }

    @Test
    fun startOpensTheStartScreen() = runTest {
        val vm = HomeViewModel(f.sessions, f.clock, f.permissions, ticks)
        vm.onIntent(HomeIntent.StartClicked)
        assertEquals(HomeEffect.OpenStart, vm.effects.first())
    }

    @Test
    fun formatsTimeLeft() {
        assertEquals("2 h 05 min left", formatTimeLeft(125.minutes))
        assertEquals("59 s left", formatTimeLeft(59.seconds))
        assertEquals("Ending…", formatTimeLeft(0.seconds))
    }
}

class HomeBreaksTest : MainDispatcherTest() {
    private val f = SessionUiFixture()
    private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Test
    fun delayedSessionsShowBreaksLeftAndCanTakeOne() = runTest {
        f.sessions.start(social.id, SessionLength.For(1.hours), Strictness.Delayed)
        val vm = HomeViewModel(f.sessions, f.clock, f.permissions, ticks)
        assertEquals(3, vm.state.value.running.single().breaksLeft)
        assertTrue(vm.state.value.running.single().canTakeBreak)

        vm.onIntent(HomeIntent.TakeBreakClicked("s1"))

        val row = vm.state.value.running.single()
        assertEquals(2, row.breaksLeft)
        assertEquals("On a Break · 5 min 00 s left", row.onBreak)
        assertEquals(false, row.canTakeBreak)
        assertEquals("1 h 00 min left", row.timeLeft, "a Break doesn't move the end")
    }

    @Test
    fun aBreakThatCantBeScheduledIsRefusedAndSaysSo() = runTest {
        f.sessions.start(social.id, SessionLength.For(1.hours), Strictness.Delayed)
        f.engine.canScheduleBreakEnds = false
        val vm = HomeViewModel(f.sessions, f.clock, f.permissions, ticks)

        vm.onIntent(HomeIntent.TakeBreakClicked("s1"))

        assertEquals("s1", vm.state.value.breakUnavailableFor)
        val row = vm.state.value.running.single()
        assertEquals(null, row.onBreak)
        assertEquals(3, row.breaksLeft)
    }

    @Test
    fun breaksAreNotOfferedForNormalOrLockedSessions() = runTest {
        f.sessions.start(social.id, SessionLength.For(1.hours), Strictness.Normal)
        f.sessions.start(social.id, SessionLength.For(2.hours), Strictness.Locked)
        val vm = HomeViewModel(f.sessions, f.clock, f.permissions, ticks)

        assertTrue(vm.state.value.running.none { it.canTakeBreak || it.breaksLeft != null })
    }
}

class HomeLockedTest : MainDispatcherTest() {
    private val f = SessionUiFixture()
    private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Test
    fun aLockedSessionOffersNoWayToEndOrPause() = runTest {
        f.sessions.start(social.id, SessionLength.For(1.hours), Strictness.Locked)
        val vm = HomeViewModel(f.sessions, f.clock, f.permissions, ticks)
        val row = vm.state.value.running.single()
        assertEquals(false, row.canEnd)
        assertEquals(false, row.canTakeBreak)

        vm.onIntent(HomeIntent.EndClicked("s1"))
        vm.onIntent(HomeIntent.TakeBreakClicked("s1"))

        assertTrue(f.sessionRepository.get(io.disruption.focal.core.model.FocusSessionId("s1"))!!.isRunning)
    }

    @Test
    fun switchingBlockingOffWhileAwayBreaksLockedSessionsAndWarns() = runTest {
        f.sessions.start(social.id, SessionLength.For(1.hours), Strictness.Locked)
        f.sessions.start(social.id, SessionLength.For(1.hours), Strictness.Normal)
        val vm = HomeViewModel(f.sessions, f.clock, f.permissions, ticks)
        assertEquals(null, vm.state.value.brokenWarning)

        f.permissions.granted -= Permission.AccessibilityService
        vm.onIntent(HomeIntent.Resumed)

        assertEquals(Outcome.Broken, f.sessionRepository.get(FocusSessionId("s1"))?.outcome)
        assertEquals(listOf("s2"), vm.state.value.running.map { it.id })
        assertEquals(listOf(Permission.AccessibilityService), vm.state.value.missingPermissions)
        assertTrue(vm.state.value.brokenWarning!!.contains("Broken"))

        vm.onIntent(HomeIntent.BrokenWarningDismissed)
        assertEquals(null, vm.state.value.brokenWarning)
    }

    @Test
    fun homeWarnsAboutASessionAnotherCheckBroke() = runTest {
        // Android's process-start check broke the session before Home was shown.
        f.sessions.start(social.id, SessionLength.For(1.hours), Strictness.Locked)
        f.sessions.checkPermissions(listOf(Permission.AccessibilityService))

        val vm = HomeViewModel(f.sessions, f.clock, f.permissions, ticks)

        assertTrue(vm.state.value.brokenWarning!!.contains("Broken"))
    }

    @Test
    fun turningNotificationsOffDoesNotBreakALockedSession() = runTest {
        f.sessions.start(social.id, SessionLength.For(1.hours), Strictness.Locked)
        val vm = HomeViewModel(f.sessions, f.clock, f.permissions, ticks)

        f.permissions.granted -= Permission.Notifications
        vm.onIntent(HomeIntent.Resumed)

        assertEquals(listOf("s1"), vm.state.value.running.map { it.id })
        assertEquals(null, vm.state.value.brokenWarning)
    }
}
