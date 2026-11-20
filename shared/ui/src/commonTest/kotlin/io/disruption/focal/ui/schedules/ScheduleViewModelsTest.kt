package io.disruption.focal.ui.schedules

import io.disruption.focal.core.model.ScheduleId
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.permissions.Permission
import io.disruption.focal.ui.MainDispatcherTest
import io.disruption.focal.ui.SessionUiFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleViewModelsTest : MainDispatcherTest() {
    private val f = SessionUiFixture()

    @Test
    fun createsAScheduleAndListsItWithASummary() = runTest {
        val editor = ScheduleEditorViewModel(null, f.schedules, f.blockLists, f.permissions)
        assertEquals("social", editor.state.value.blockListId, "the first non-empty Block List is picked")
        editor.onIntent(ScheduleEditorIntent.NameChanged("Wind down"))
        DayOfWeek.entries.filter { it in editor.state.value.days }.forEach { editor.onIntent(ScheduleEditorIntent.DayToggled(it)) }
        editor.onIntent(ScheduleEditorIntent.DayToggled(DayOfWeek.SUNDAY))
        editor.onIntent(ScheduleEditorIntent.StartChanged(LocalTime(22, 0)))
        editor.onIntent(ScheduleEditorIntent.EndChanged(LocalTime(7, 0)))
        editor.onIntent(ScheduleEditorIntent.StrictnessSelected(Strictness.Locked))
        assertTrue(editor.state.value.isOvernight)
        editor.onIntent(ScheduleEditorIntent.SaveClicked)

        assertEquals(ScheduleEditorEffect.Close, editor.effects.first())
        val list = SchedulesViewModel(f.schedules)
        assertEquals(
            listOf(ScheduleRow("sch", "Wind down", "Sun · 22:00–07:00 (overnight) · Locked", true)),
            list.state.value.schedules,
        )
    }

    @Test
    fun invalidInputShowsEveryProblem() = runTest {
        val editor = ScheduleEditorViewModel(null, f.schedules, f.blockLists, f.permissions)
        DayOfWeek.entries.filter { it in editor.state.value.days }.forEach { editor.onIntent(ScheduleEditorIntent.DayToggled(it)) }
        editor.onIntent(ScheduleEditorIntent.EndChanged(LocalTime(9, 0)))
        editor.onIntent(ScheduleEditorIntent.SaveClicked)

        assertEquals(
            listOf("Give the Schedule a name.", "Pick at least one day.", "The start and end can't be the same time."),
            editor.state.value.errors,
        )
        assertTrue(f.scheduleRepository.getAll().isEmpty())
    }

    @Test
    fun editsAnExistingScheduleAndSwitchesItOff() = runTest {
        val create = ScheduleEditorViewModel(null, f.schedules, f.blockLists, f.permissions)
        create.onIntent(ScheduleEditorIntent.NameChanged("Work"))
        create.onIntent(ScheduleEditorIntent.SaveClicked)
        create.effects.first()

        val edit = ScheduleEditorViewModel("sch", f.schedules, f.blockLists, f.permissions)
        assertEquals("Work", edit.state.value.name)
        assertEquals(false, edit.state.value.isNew)
        edit.onIntent(ScheduleEditorIntent.EndChanged(LocalTime(18, 0)))
        edit.onIntent(ScheduleEditorIntent.SaveClicked)
        edit.effects.first()
        assertEquals(LocalTime(18, 0), f.scheduleRepository.get(ScheduleId("sch"))!!.end)

        val list = SchedulesViewModel(f.schedules)
        list.onIntent(SchedulesIntent.EnabledChanged("sch", false))
        assertEquals(false, list.state.value.schedules.single().enabled)
    }

    @Test
    fun deletesASchedule() = runTest {
        val create = ScheduleEditorViewModel(null, f.schedules, f.blockLists, f.permissions)
        create.onIntent(ScheduleEditorIntent.NameChanged("Work"))
        create.onIntent(ScheduleEditorIntent.SaveClicked)
        create.effects.first()

        val edit = ScheduleEditorViewModel("sch", f.schedules, f.blockLists, f.permissions)
        edit.onIntent(ScheduleEditorIntent.DeleteClicked)
        edit.onIntent(ScheduleEditorIntent.DeleteConfirmed)

        assertEquals(ScheduleEditorEffect.Close, edit.effects.first())
        assertNull(f.scheduleRepository.get(ScheduleId("sch")))
    }

    @Test
    fun asksForExactAlarmsWhenANewScheduleIsSavedWithoutThem() = runTest {
        f.permissions.granted -= Permission.ExactAlarms
        val editor = ScheduleEditorViewModel(null, f.schedules, f.blockLists, f.permissions)
        editor.onIntent(ScheduleEditorIntent.NameChanged("Work"))
        editor.onIntent(ScheduleEditorIntent.SaveClicked)

        assertTrue(editor.state.value.askingForAlarms)
        assertEquals("Work", f.scheduleRepository.get(ScheduleId("sch"))!!.name, "the Schedule is saved either way")
        editor.onIntent(ScheduleEditorIntent.AllowAlarmsClicked)
        assertEquals(listOf(Permission.ExactAlarms), f.permissions.requested)
        assertEquals(ScheduleEditorEffect.Close, editor.effects.first())
    }

    @Test
    fun notNowClosesWithoutAsking() = runTest {
        f.permissions.granted -= Permission.ExactAlarms
        val editor = ScheduleEditorViewModel(null, f.schedules, f.blockLists, f.permissions)
        editor.onIntent(ScheduleEditorIntent.NameChanged("Work"))
        editor.onIntent(ScheduleEditorIntent.SaveClicked)
        editor.onIntent(ScheduleEditorIntent.AlarmsNotNowClicked)

        assertEquals(ScheduleEditorEffect.Close, editor.effects.first())
        assertTrue(f.permissions.requested.isEmpty())
    }

    @Test
    fun neverAsksForAlarmsWhenGrantedOrWhenEditing() = runTest {
        val create = ScheduleEditorViewModel(null, f.schedules, f.blockLists, f.permissions)
        create.onIntent(ScheduleEditorIntent.NameChanged("Work"))
        create.onIntent(ScheduleEditorIntent.SaveClicked)
        assertEquals(ScheduleEditorEffect.Close, create.effects.first())
        assertFalse(create.state.value.askingForAlarms)

        f.permissions.granted -= Permission.ExactAlarms
        val edit = ScheduleEditorViewModel("sch", f.schedules, f.blockLists, f.permissions)
        edit.onIntent(ScheduleEditorIntent.SaveClicked)
        assertEquals(ScheduleEditorEffect.Close, edit.effects.first())
        assertTrue(f.permissions.requested.isEmpty())
    }
}
