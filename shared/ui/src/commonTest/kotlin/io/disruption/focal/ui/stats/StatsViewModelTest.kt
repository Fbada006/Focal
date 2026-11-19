package io.disruption.focal.ui.stats

import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.session.SessionLength
import io.disruption.focal.ui.MainDispatcherTest
import io.disruption.focal.ui.SessionUiFixture
import io.disruption.focal.ui.social
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class StatsViewModelTest : MainDispatcherTest() {
    private val f = SessionUiFixture()
    private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Test
    fun showsFocusTimeOutcomesAndStreak() = runTest {
        f.sessions.start(social.id, SessionLength.For(1.hours), Strictness.Normal)
        f.clock.advanceBy(1.hours)
        f.sessions.completeDueSessions()
        f.sessions.start(social.id, SessionLength.For(1.hours), Strictness.Normal)
        f.clock.advanceBy(10.minutes)
        f.sessions.endEarly(FocusSessionId("s2"))
        f.sessions.start(social.id, SessionLength.For(1.hours), Strictness.Locked)
        f.clock.advanceBy(5.minutes)
        f.sessions.markBlockingDefeated()

        val vm = StatsViewModel(f.sessionRepository, f.clock, f.zone, ticks)

        val state = vm.state.value
        assertEquals("1 h 15 min", state.focusToday)
        assertEquals("1 h 15 min", state.focusThisWeek)
        assertEquals(Triple(1, 1, 1), Triple(state.completed, state.endedEarly, state.broken))
        assertEquals("0 days", state.currentStreak)
        assertEquals("1 day", state.longestStreak)
    }

    @Test
    fun runningSessionsAddFocusTimeAsTheClockTicks() = runTest {
        f.sessions.start(social.id, SessionLength.For(2.hours), Strictness.Normal)
        val vm = StatsViewModel(f.sessionRepository, f.clock, f.zone, ticks)
        assertEquals("0 min", vm.state.value.focusToday)

        f.clock.advanceBy(25.minutes)
        ticks.emit(Unit)

        assertEquals("25 min", vm.state.value.focusToday)
    }
}
