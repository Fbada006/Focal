package io.disruption.focal.ui.start

import androidx.lifecycle.viewModelScope
import io.disruption.focal.core.blocklist.BlockListService
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.permissions.PermissionGateway
import io.disruption.focal.core.session.FocusSessionService
import io.disruption.focal.core.session.SessionLength
import io.disruption.focal.core.session.StartResult
import io.disruption.focal.core.time.TimeZoneProvider
import io.disruption.focal.ui.mvi.MviViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class BlockListOption(val id: String, val name: String, val isEmpty: Boolean)

enum class LengthMode { Duration, EndTime }

data class StartSessionState(
    val loading: Boolean = true,
    val blockLists: List<BlockListOption> = emptyList(),
    val selectedBlockListId: String? = null,
    val lengthMode: LengthMode = LengthMode.Duration,
    val duration: Duration = 25.minutes,
    val endTime: LocalTime = LocalTime(18, 0),
    val strictness: Strictness = Strictness.Normal,
    val error: String? = null,
    val starting: Boolean = false,
    /** A required permission is missing, so a session couldn't block anything (story 10). */
    val permissionMissing: Boolean = false,
) {
    val canStart: Boolean
        get() = !starting && !permissionMissing && blockLists.any { it.id == selectedBlockListId && !it.isEmpty }
}

sealed interface StartSessionIntent {
    data class BlockListSelected(val id: String) : StartSessionIntent
    data class LengthModeSelected(val mode: LengthMode) : StartSessionIntent
    data class DurationSelected(val duration: Duration) : StartSessionIntent
    data class EndTimeSelected(val time: LocalTime) : StartSessionIntent
    data class StrictnessSelected(val strictness: Strictness) : StartSessionIntent
    data object StartClicked : StartSessionIntent
    data object CreateBlockListClicked : StartSessionIntent
}

sealed interface StartSessionEffect {
    data object Started : StartSessionEffect
    data object OpenBlockLists : StartSessionEffect
}

class StartSessionViewModel(
    blockLists: BlockListService,
    private val sessions: FocusSessionService,
    private val clock: Clock,
    private val timeZone: TimeZoneProvider,
    private val permissions: PermissionGateway,
) : MviViewModel<StartSessionState, StartSessionIntent, StartSessionEffect>(StartSessionState()) {

    init {
        viewModelScope.launch {
            val missing = permissions.missing().isNotEmpty()
            setState {
                copy(
                    permissionMissing = missing,
                    error = if (missing) "Fix Focal's permissions on Home before starting a session." else error,
                )
            }
        }
        viewModelScope.launch {
            blockLists.observeAll().collect { lists ->
                val options = lists.map { BlockListOption(it.id.value, it.name, it.blocked.isEmpty) }
                setState {
                    val keep = selectedBlockListId?.takeIf { id -> options.any { it.id == id } }
                    copy(
                        loading = false,
                        blockLists = options,
                        selectedBlockListId = keep ?: options.firstOrNull { !it.isEmpty }?.id,
                    )
                }
            }
        }
    }

    override fun onIntent(intent: StartSessionIntent) {
        when (intent) {
            is StartSessionIntent.BlockListSelected -> setState { copy(selectedBlockListId = intent.id, error = null) }
            is StartSessionIntent.LengthModeSelected -> setState { copy(lengthMode = intent.mode, error = null) }
            is StartSessionIntent.DurationSelected ->
                setState { copy(duration = intent.duration.coerceIn(MIN_DURATION, MAX_DURATION), error = null) }
            is StartSessionIntent.EndTimeSelected -> setState { copy(endTime = intent.time, error = null) }
            is StartSessionIntent.StrictnessSelected -> setState { copy(strictness = intent.strictness) }
            StartSessionIntent.StartClicked -> start()
            StartSessionIntent.CreateBlockListClicked -> emit(StartSessionEffect.OpenBlockLists)
        }
    }

    private fun start() {
        val state = currentState
        val id = state.selectedBlockListId ?: return
        if (!state.canStart) return
        val length = when (state.lengthMode) {
            LengthMode.Duration -> SessionLength.For(state.duration)
            LengthMode.EndTime -> SessionLength.untilNext(state.endTime, clock.now(), timeZone.current())
        }
        setState { copy(starting = true) }
        viewModelScope.launch {
            val result = sessions.start(BlockListId(id), length, state.strictness)
            setState { copy(starting = false) }
            when (result) {
                is StartResult.Started -> emit(StartSessionEffect.Started)
                StartResult.EmptyBlockList -> setState { copy(error = "Add something to block to this Block List first.") }
                StartResult.BlockListNotFound -> setState { copy(error = "That Block List no longer exists.") }
                StartResult.EndNotInFuture -> setState { copy(error = "Pick an end time in the future.") }
            }
        }
    }

    companion object {
        val MIN_DURATION = 5.minutes
        val MAX_DURATION = (24 * 60).minutes
        val DURATION_PRESETS = listOf(15.minutes, 25.minutes, 45.minutes, 60.minutes, 90.minutes, 120.minutes)
    }
}
