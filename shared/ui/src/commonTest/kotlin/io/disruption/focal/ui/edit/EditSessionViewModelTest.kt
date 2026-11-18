package io.disruption.focal.ui.edit

import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.session.SessionLength
import io.disruption.focal.ui.MainDispatcherTest
import io.disruption.focal.ui.SessionUiFixture
import io.disruption.focal.ui.blocklists.BlockListEditorIntent
import io.disruption.focal.ui.blocklists.BlockListEditorViewModel
import io.disruption.focal.ui.social
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class EditSessionViewModelTest : MainDispatcherTest() {
    private val f = SessionUiFixture()
    private val id = FocusSessionId("s1")

    private suspend fun start(strictness: Strictness) {
        f.sessions.start(social.id, SessionLength.For(1.hours), strictness) // 09:00 UTC + 1 h
    }

    @Test
    fun extendingAndRaisingStrictnessApply() = runTest {
        start(Strictness.Delayed)
        val vm = EditSessionViewModel("s1", f.sessions, f.clock, f.zone)
        assertEquals("10:00", vm.state.value.endsAt)
        assertEquals(false, vm.state.value.canWeaken)

        vm.onIntent(EditSessionIntent.ShiftEnd(15.minutes))
        vm.onIntent(EditSessionIntent.StrictnessSelected(Strictness.Locked))

        assertEquals("10:15", vm.state.value.endsAt)
        assertEquals(Strictness.Locked, vm.state.value.strictness)
        assertNull(vm.state.value.error)
    }

    @Test
    fun weakeningALockedSessionIsRefusedWithAnExplanation() = runTest {
        start(Strictness.Locked)
        val vm = EditSessionViewModel("s1", f.sessions, f.clock, f.zone)

        vm.onIntent(EditSessionIntent.ShiftEnd((-15).minutes))
        assertEquals("A Locked session can't be shortened. It can only be extended.", vm.state.value.error)

        vm.onIntent(EditSessionIntent.StrictnessSelected(Strictness.Normal))
        assertEquals("A Locked session can't be made less strict.", vm.state.value.error)
        assertEquals(f.clock.now + 1.hours, f.sessionRepository.get(id)!!.plannedEnd)
    }

    @Test
    fun normalSessionsCanBeShortened() = runTest {
        start(Strictness.Normal)
        val vm = EditSessionViewModel("s1", f.sessions, f.clock, f.zone)
        vm.onIntent(EditSessionIntent.ShiftEnd((-15).minutes))
        assertEquals("09:45", vm.state.value.endsAt)
    }

    @Test
    fun theBlockListEditorExplainsDeferredRemovals() = runTest {
        f.blockLists.addDomain(social.id, "youtube.com")
        start(Strictness.Locked)
        val vm = BlockListEditorViewModel(social.id.value, f.blockLists)

        vm.onIntent(BlockListEditorIntent.RemoveDomain("youtube.com"))

        assertEquals("Removals take effect after Social ends. Additions apply now.", vm.state.value.notice)
        assertEquals(setOf("youtube.com"), f.sessionRepository.get(id)!!.blocked.domains)
    }
}
