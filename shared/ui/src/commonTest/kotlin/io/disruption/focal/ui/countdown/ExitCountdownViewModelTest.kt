package io.disruption.focal.ui.countdown

import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Outcome
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.session.SessionLength
import io.disruption.focal.ui.MainDispatcherTest
import io.disruption.focal.ui.SessionUiFixture
import io.disruption.focal.ui.home.HomeEffect
import io.disruption.focal.ui.home.HomeIntent
import io.disruption.focal.ui.home.HomeViewModel
import io.disruption.focal.ui.social
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class ExitCountdownViewModelTest : MainDispatcherTest() {
    private val f = SessionUiFixture()
    private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val sessionId = FocusSessionId("s1")

    private suspend fun startDelayed() {
        f.sessions.start(social.id, SessionLength.For(1.hours), Strictness.Delayed)
    }

    private suspend fun tickAfter(seconds: Int) {
        f.clock.advanceBy(seconds.seconds)
        ticks.emit(Unit)
    }

    @Test
    fun homeSendsDelayedSessionsToTheCountdown() = runTest {
        startDelayed()
        val home = HomeViewModel(f.sessions, f.clock, ticks)
        home.onIntent(HomeIntent.EndClicked("s1"))

        assertEquals(HomeEffect.OpenExitCountdown("s1"), home.effects.first())
        assertTrue(f.sessionRepository.get(sessionId)!!.isRunning)
    }

    @Test
    fun confirmStaysDisabledUntilTheCountdownFinishes() = runTest {
        startDelayed()
        val vm = ExitCountdownViewModel("s1", f.sessions, f.clock, ticks)
        vm.onIntent(ExitCountdownIntent.Shown)
        assertEquals(30, vm.state.value.secondsLeft)
        assertFalse(vm.state.value.canConfirm)

        tickAfter(29)
        assertEquals(1, vm.state.value.secondsLeft)
        assertFalse(vm.state.value.canConfirm)

        tickAfter(1)
        assertTrue(vm.state.value.canConfirm)
        vm.onIntent(ExitCountdownIntent.ConfirmClicked)

        assertEquals(ExitCountdownEffect.Ended, vm.effects.first())
        assertEquals(Outcome.EndedEarly, f.sessionRepository.get(sessionId)!!.outcome)
    }

    @Test
    fun leavingCancelsTheCountdownAndItMustBeRestarted() = runTest {
        startDelayed()
        val vm = ExitCountdownViewModel("s1", f.sessions, f.clock, ticks)
        vm.onIntent(ExitCountdownIntent.Shown)
        tickAfter(20)
        vm.onIntent(ExitCountdownIntent.Left)
        tickAfter(60)
        vm.onIntent(ExitCountdownIntent.Shown) // back in the foreground

        assertTrue(vm.state.value.cancelled)
        assertFalse(vm.state.value.canConfirm)
        vm.onIntent(ExitCountdownIntent.ConfirmClicked)
        assertTrue(f.sessionRepository.get(sessionId)!!.isRunning)

        vm.onIntent(ExitCountdownIntent.RestartClicked)
        assertEquals(30, vm.state.value.secondsLeft)
        assertFalse(vm.state.value.cancelled)
    }

    @Test
    fun keepGoingLeavesTheSessionRunning() = runTest {
        startDelayed()
        val vm = ExitCountdownViewModel("s1", f.sessions, f.clock, ticks)
        vm.onIntent(ExitCountdownIntent.Shown)
        tickAfter(31)
        vm.onIntent(ExitCountdownIntent.KeepGoingClicked)

        assertEquals(ExitCountdownEffect.Close, vm.effects.first())
        assertTrue(f.sessionRepository.get(sessionId)!!.isRunning)
    }

    @Test
    fun leavingWhileTheCountdownIsStillStartingCancelsIt() = runTest {
        startDelayed()
        // A real dispatcher: the start hasn't come back from the service when the user leaves.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = ExitCountdownViewModel("s1", f.sessions, f.clock, ticks)
        vm.onIntent(ExitCountdownIntent.Shown)
        vm.onIntent(ExitCountdownIntent.Left)
        advanceUntilIdle()

        f.clock.advanceBy(60.seconds)
        vm.onIntent(ExitCountdownIntent.Shown)
        advanceUntilIdle()
        assertTrue(vm.state.value.cancelled)
        vm.onIntent(ExitCountdownIntent.ConfirmClicked)
        advanceUntilIdle()

        assertTrue(f.sessionRepository.get(sessionId)!!.isRunning)
    }
}
