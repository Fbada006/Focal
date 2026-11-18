package io.disruption.focal.ui.schedules

import androidx.lifecycle.viewModelScope
import io.disruption.focal.core.blocklist.BlockListService
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.ScheduleId
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.schedule.ScheduleDraft
import io.disruption.focal.core.schedule.ScheduleSaveResult
import io.disruption.focal.core.schedule.ScheduleService
import io.disruption.focal.ui.mvi.MviViewModel
import io.disruption.focal.ui.start.BlockListOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

data class ScheduleEditorState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
    val start: LocalTime = LocalTime(9, 0),
    val end: LocalTime = LocalTime(17, 0),
    val blockLists: List<BlockListOption> = emptyList(),
    val blockListId: String? = null,
    val strictness: Strictness = Strictness.Normal,
    val enabled: Boolean = true,
    val errors: List<String> = emptyList(),
    val confirmingDelete: Boolean = false,
) {
    val isOvernight: Boolean get() = end <= start
}

sealed interface ScheduleEditorIntent {
    data class NameChanged(val name: String) : ScheduleEditorIntent
    data class DayToggled(val day: DayOfWeek) : ScheduleEditorIntent
    data class StartChanged(val time: LocalTime) : ScheduleEditorIntent
    data class EndChanged(val time: LocalTime) : ScheduleEditorIntent
    data class BlockListSelected(val id: String) : ScheduleEditorIntent
    data class StrictnessSelected(val strictness: Strictness) : ScheduleEditorIntent
    data object SaveClicked : ScheduleEditorIntent
    data object DeleteClicked : ScheduleEditorIntent
    data object DeleteConfirmed : ScheduleEditorIntent
    data object DeleteDismissed : ScheduleEditorIntent
}

sealed interface ScheduleEditorEffect {
    data object Close : ScheduleEditorEffect
}

/** Create or edit a Schedule. Saving never weakens a session it already started (ADR 0007). */
class ScheduleEditorViewModel(
    scheduleId: String?,
    private val schedules: ScheduleService,
    blockLists: BlockListService,
) : MviViewModel<ScheduleEditorState, ScheduleEditorIntent, ScheduleEditorEffect>(ScheduleEditorState()) {
    private val id = scheduleId?.let(::ScheduleId)

    init {
        viewModelScope.launch {
            val lists = blockLists.observeAll().first()
            val options = lists.map { BlockListOption(it.id.value, it.name, it.blocked.isEmpty) }
            val existing = id?.let { schedules.get(it) }
            setState {
                if (existing == null) {
                    copy(loading = false, blockLists = options, blockListId = options.firstOrNull { !it.isEmpty }?.id)
                } else {
                    copy(
                        loading = false,
                        isNew = false,
                        name = existing.name,
                        days = existing.days,
                        start = existing.start,
                        end = existing.end,
                        blockLists = options,
                        blockListId = existing.blockListId.value,
                        strictness = existing.strictness,
                        enabled = existing.enabled,
                    )
                }
            }
        }
    }

    override fun onIntent(intent: ScheduleEditorIntent) {
        when (intent) {
            is ScheduleEditorIntent.NameChanged -> setState { copy(name = intent.name, errors = emptyList()) }
            is ScheduleEditorIntent.DayToggled ->
                setState { copy(days = if (intent.day in days) days - intent.day else days + intent.day, errors = emptyList()) }
            is ScheduleEditorIntent.StartChanged -> setState { copy(start = intent.time, errors = emptyList()) }
            is ScheduleEditorIntent.EndChanged -> setState { copy(end = intent.time, errors = emptyList()) }
            is ScheduleEditorIntent.BlockListSelected -> setState { copy(blockListId = intent.id, errors = emptyList()) }
            is ScheduleEditorIntent.StrictnessSelected -> setState { copy(strictness = intent.strictness) }
            ScheduleEditorIntent.SaveClicked -> save()
            ScheduleEditorIntent.DeleteClicked -> setState { copy(confirmingDelete = true) }
            ScheduleEditorIntent.DeleteDismissed -> setState { copy(confirmingDelete = false) }
            ScheduleEditorIntent.DeleteConfirmed -> viewModelScope.launch {
                id?.let { schedules.delete(it) }
                emit(ScheduleEditorEffect.Close)
            }
        }
    }

    private fun save() {
        val state = currentState
        val draft = ScheduleDraft(
            name = state.name,
            days = state.days,
            start = state.start,
            end = state.end,
            blockListId = state.blockListId?.let(::BlockListId),
            strictness = state.strictness,
            enabled = state.enabled,
        )
        viewModelScope.launch {
            val result = if (id == null) schedules.create(draft) else schedules.update(id, draft)
            when (result) {
                is ScheduleSaveResult.Saved, ScheduleSaveResult.NotFound -> emit(ScheduleEditorEffect.Close)
                is ScheduleSaveResult.Invalid ->
                    setState { copy(errors = result.problems.sortedBy { it.ordinal }.map { it.message() }) }
            }
        }
    }
}
