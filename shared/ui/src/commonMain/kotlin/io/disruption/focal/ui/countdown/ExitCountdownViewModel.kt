package io.disruption.focal.ui.countdown

import androidx.lifecycle.viewModelScope
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.session.CountdownResult
import io.disruption.focal.core.session.EndResult
import io.disruption.focal.core.session.ExitCountdown
import io.disruption.focal.core.session.FocusSessionService
import io.disruption.focal.ui.home.everySecond
import io.disruption.focal.ui.mvi.MviViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class ExitCountdownState(
    val sessionName: String = "",
    val secondsLeft: Int = 30,
    /** Confirm stays disabled until the countdown has finished. */
    val canConfirm: Boolean = false,
    /** The countdown was cancelled by leaving it; it must be started again. */
    val cancelled: Boolean = false,
)

sealed interface ExitCountdownIntent {
    /** The screen is visible: start the countdown, unless an earlier one was cancelled. */
    data object Shown : ExitCountdownIntent

    /** Start again after a cancelled countdown. */
    data object RestartClicked : ExitCountdownIntent

    /** The user left the screen or Focal went to the background: cancel the countdown. */
    data object Left : ExitCountdownIntent
    data object ConfirmClicked : ExitCountdownIntent
    data object KeepGoingClicked : ExitCountdownIntent
}

sealed interface ExitCountdownEffect {
    data object Ended : ExitCountdownEffect
    data object Close : ExitCountdownEffect
}

/** Ending a Delayed Focus Session early: wait 30 seconds, then confirm (stories 31–34). */
class ExitCountdownViewModel(
    sessionId: String,
    private val sessions: FocusSessionService,
    private val clock: Clock,
    ticks: Flow<Unit> = everySecond(),
) : MviViewModel<ExitCountdownState, ExitCountdownIntent, ExitCountdownEffect>(ExitCountdownState()) {
    private val id = FocusSessionId(sessionId)
    private var countdown: ExitCountdown? = null

    init {
        viewModelScope.launch {
            sessions.running.collect { running ->
                val session = running.firstOrNull { it.id == id }
                if (session == null) emit(ExitCountdownEffect.Close) else setState { copy(sessionName = session.name) }
            }
        }
        viewModelScope.launch { ticks.collect { render() } }
    }

    override fun onIntent(intent: ExitCountdownIntent) {
        when (intent) {
            ExitCountdownIntent.Shown -> if (countdown == null && !currentState.cancelled) begin()
            ExitCountdownIntent.RestartClicked -> begin()
            ExitCountdownIntent.Left -> cancel()
            ExitCountdownIntent.KeepGoingClicked -> {
                cancel()
                emit(ExitCountdownEffect.Close)
            }
            ExitCountdownIntent.ConfirmClicked -> viewModelScope.launch {
                when (sessions.confirmEndEarly(id)) {
                    is EndResult.Ended, EndResult.NotRunning -> emit(ExitCountdownEffect.Ended)
                    else -> render()
                }
            }
        }
    }

    private fun begin() {
        viewModelScope.launch {
            when (val result = sessions.beginExitCountdown(id)) {
                is CountdownResult.Started -> {
                    countdown = result.countdown
                    setState { copy(cancelled = false) }
                    render()
                }
                else -> emit(ExitCountdownEffect.Close)
            }
        }
    }

    private fun cancel() {
        if (countdown == null) return
        countdown = null
        setState { copy(cancelled = true, canConfirm = false, secondsLeft = 30) }
        viewModelScope.launch { sessions.cancelExitCountdown(id) }
    }

    private fun render() {
        val countdown = countdown ?: return
        val now = clock.now()
        val left = (countdown.endsAt - now).inWholeMilliseconds
        setState {
            copy(
                secondsLeft = ((left + 999) / 1000).coerceAtLeast(0).toInt(),
                canConfirm = countdown.isFinished(now),
            )
        }
    }
}
