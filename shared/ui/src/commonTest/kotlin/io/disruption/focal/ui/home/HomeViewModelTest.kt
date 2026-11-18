package io.disruption.focal.ui.home

import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Outcome
import io.disruption.focal.core.model.Strictness
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class HomeViewModelTest : MainDispatcherTest() {
    private val f = SessionUiFixture()
    private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Test
    fun showsEachRunningSessionWithItsStrictnessAndTimeLeft() = runTest {
        f.sessions.start(social.id, SessionLength.For(25.minutes), Strictness.Normal)
        f.sessions.start(social.id, SessionLength.For(90.minutes), Strictness.Locked)
        val vm = HomeViewModel(f.sessions, f.clock, ticks)

        val rows = vm.state.value.running
        assertEquals(listOf("25 min 00 s left", "1 h 30 min left"), rows.map { it.timeLeft })
        assertEquals(listOf(Strictness.Normal, Strictness.Locked), rows.map { it.strictness })
        assertEquals(listOf(true, false), rows.map { it.canEnd })
    }

    @Test
    fun timeLeftUpdatesOnEachTick() = runTest {
        f.sessions.start(social.id, SessionLength.For(25.minutes), Strictness.Normal)
        val vm = HomeViewModel(f.sessions, f.clock, ticks)

        f.clock.advanceBy(61.seconds)
        ticks.emit(Unit)

        assertEquals("23 min 59 s left", vm.state.value.running.single().timeLeft)
    }

    @Test
    fun endingANormalSessionRecordsEndedEarly() = runTest {
        f.sessions.start(social.id, SessionLength.For(25.minutes), Strictness.Normal)
        val vm = HomeViewModel(f.sessions, f.clock, ticks)

        vm.onIntent(HomeIntent.EndClicked("s1"))

        assertTrue(vm.state.value.running.isEmpty())
        assertEquals(Outcome.EndedEarly, f.sessionRepository.get(FocusSessionId("s1"))?.outcome)
    }

    @Test
    fun sessionsThatRunOutMoveToCompleted() = runTest {
        f.sessions.start(social.id, SessionLength.For(25.minutes), Strictness.Delayed)
        val vm = HomeViewModel(f.sessions, f.clock, ticks)

        f.clock.advanceBy(25.minutes)
        ticks.emit(Unit)

        assertTrue(vm.state.value.running.isEmpty())
        assertEquals(Outcome.Completed, f.sessionRepository.get(FocusSessionId("s1"))?.outcome)
        assertTrue(f.engine.applied.isEmpty())
    }

    @Test
    fun startOpensTheStartScreen() = runTest {
        val vm = HomeViewModel(f.sessions, f.clock, ticks)
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
