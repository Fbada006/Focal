package io.disruption.focal.ui.schedules

import androidx.lifecycle.viewModelScope
import io.disruption.focal.core.model.ScheduleId
import io.disruption.focal.core.schedule.ScheduleService
import io.disruption.focal.ui.mvi.MviViewModel
import kotlinx.coroutines.launch

data class ScheduleRow(val id: String, val name: String, val summary: String, val enabled: Boolean)

data class SchedulesState(val loading: Boolean = true, val schedules: List<ScheduleRow> = emptyList())

sealed interface SchedulesIntent {
    data object NewClicked : SchedulesIntent
    data class Open(val id: String) : SchedulesIntent
    data class EnabledChanged(val id: String, val enabled: Boolean) : SchedulesIntent
}

sealed interface SchedulesEffect {
    /** Open the editor; a null id creates a new Schedule. */
    data class OpenEditor(val id: String?) : SchedulesEffect
}

class SchedulesViewModel(
    private val schedules: ScheduleService,
) : MviViewModel<SchedulesState, SchedulesIntent, SchedulesEffect>(SchedulesState()) {

    init {
        viewModelScope.launch {
            schedules.observeAll().collect { all ->
                setState {
                    copy(loading = false, schedules = all.map { ScheduleRow(it.id.value, it.name, it.summary(), it.enabled) })
                }
            }
        }
    }

    override fun onIntent(intent: SchedulesIntent) {
        when (intent) {
            SchedulesIntent.NewClicked -> emit(SchedulesEffect.OpenEditor(null))
            is SchedulesIntent.Open -> emit(SchedulesEffect.OpenEditor(intent.id))
            is SchedulesIntent.EnabledChanged -> viewModelScope.launch {
                schedules.setEnabled(ScheduleId(intent.id), intent.enabled)
            }
        }
    }
}
