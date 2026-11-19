package io.disruption.focal.ui.edit

import androidx.lifecycle.viewModelScope
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.session.EditRefusal
import io.disruption.focal.core.session.EditResult
import io.disruption.focal.core.session.FocusSessionService
import io.disruption.focal.core.time.TimeZoneProvider
import io.disruption.focal.ui.mvi.MviViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration

data class EditSessionState(
    val loading: Boolean = true,
    val name: String = "",
    val strictness: Strictness = Strictness.Normal,
    /** Local `HH:mm` of the planned end. */
    val endsAt: String = "",
    /** Only Normal sessions can be shortened or made less strict (ADR 0007). */
    val canWeaken: Boolean = true,
    val error: String? = null,
)

sealed interface EditSessionIntent {
    data class ShiftEnd(val by: Duration) : EditSessionIntent
    data class StrictnessSelected(val strictness: Strictness) : EditSessionIntent
}

sealed interface EditSessionEffect {
    data object Close : EditSessionEffect
}

/** Edit a running Focus Session: it can always get stricter, never weaker unless Normal. */
class EditSessionViewModel(
    sessionId: String,
    private val sessions: FocusSessionService,
    private val clock: Clock,
    private val timeZone: TimeZoneProvider,
) : MviViewModel<EditSessionState, EditSessionIntent, EditSessionEffect>(EditSessionState()) {
    private val id = FocusSessionId(sessionId)
    private var session: FocusSession? = null

    init {
        viewModelScope.launch {
            sessions.running.collect { running ->
                val current = running.firstOrNull { it.id == id }
                if (current == null) {
                    emit(EditSessionEffect.Close)
                    return@collect
                }
                session = current
                setState {
                    copy(
                        loading = false,
                        name = current.name,
                        strictness = current.strictness,
                        endsAt = current.plannedEnd.toLocalDateTime(timeZone.current()).time.toString().take(5),
                        canWeaken = current.strictness == Strictness.Normal,
                    )
                }
            }
        }
    }

    override fun onIntent(intent: EditSessionIntent) {
        val current = session ?: return
        viewModelScope.launch {
            val result = when (intent) {
                is EditSessionIntent.ShiftEnd -> sessions.edit(id, shiftEndBy = intent.by)
                is EditSessionIntent.StrictnessSelected -> sessions.edit(id, strictness = intent.strictness)
            }
            setState {
                copy(
                    error = when (result) {
                        is EditResult.Refused -> result.reason.message(current.strictness)
                        else -> null
                    },
                )
            }
        }
    }
}

internal fun EditRefusal.message(strictness: Strictness): String = when (this) {
    EditRefusal.Shorten -> "A $strictness session can't be shortened. It can only be extended."
    EditRefusal.LowerStrictness -> "A $strictness session can't be made less strict."
    EditRefusal.EndNotInFuture -> "That would end the session now. Use End instead."
}
