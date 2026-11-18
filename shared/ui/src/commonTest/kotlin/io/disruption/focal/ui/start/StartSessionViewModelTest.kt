package io.disruption.focal.ui.start

import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.ui.MainDispatcherTest
import io.disruption.focal.ui.SessionUiFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class StartSessionViewModelTest : MainDispatcherTest() {
    private val f = SessionUiFixture()
    private fun vm() = StartSessionViewModel(f.blockLists, f.sessions, f.clock, f.zone)

    @Test
    fun preselectsTheFirstNonEmptyBlockList() = runTest {
        val vm = vm()
        assertEquals("social", vm.state.value.selectedBlockListId)
        assertEquals(setOf("social", "empty"), vm.state.value.blockLists.map { it.id }.toSet())
    }

    @Test
    fun startsASessionForTheChosenDurationAndStrictness() = runTest {
        val vm = vm()
        vm.onIntent(StartSessionIntent.DurationSelected(45.minutes))
        vm.onIntent(StartSessionIntent.StrictnessSelected(Strictness.Delayed))
        vm.onIntent(StartSessionIntent.StartClicked)

        assertEquals(StartSessionEffect.Started, vm.effects.first())
        val session = f.sessionRepository.get(FocusSessionId("s1"))!!
        assertEquals(f.clock.now + 45.minutes, session.plannedEnd)
        assertEquals(Strictness.Delayed, session.strictness)
    }

    @Test
    fun startsASessionUntilAnEndTime() = runTest {
        f.clock.now = Instant.parse("2026-01-05T09:00:00Z")
        val vm = vm()
        vm.onIntent(StartSessionIntent.LengthModeSelected(LengthMode.EndTime))
        vm.onIntent(StartSessionIntent.EndTimeSelected(LocalTime(11, 30)))
        vm.onIntent(StartSessionIntent.StartClicked)

        vm.effects.first()
        assertEquals(Instant.parse("2026-01-05T11:30:00Z"), f.sessionRepository.get(FocusSessionId("s1"))!!.plannedEnd)
    }

    @Test
    fun anEmptyBlockListCannotStart() = runTest {
        val vm = vm()
        vm.onIntent(StartSessionIntent.BlockListSelected("empty"))

        assertFalse(vm.state.value.canStart)
        vm.onIntent(StartSessionIntent.StartClicked)
        assertTrue(f.sessionRepository.all.isEmpty())
    }

    @Test
    fun durationIsKeptWithinBounds() = runTest {
        val vm = vm()
        vm.onIntent(StartSessionIntent.DurationSelected(0.minutes))
        assertEquals(5.minutes, vm.state.value.duration)
        vm.onIntent(StartSessionIntent.DurationSelected(30.hours))
        assertEquals(24.hours, vm.state.value.duration)
    }
}
