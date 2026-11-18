package io.disruption.focal.ui.home

import androidx.lifecycle.viewModelScope
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.session.FocusSessionService
import io.disruption.focal.ui.mvi.MviViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

data class RunningSessionRow(
    val id: String,
    val name: String,
    val strictness: Strictness,
    val timeLeft: String,
    /** Normal sessions end at once; Delayed ones go through the Exit Countdown. Locked can't end. */
    val canEnd: Boolean,
    val fromSchedule: Boolean,
    /** Breaks left for a Delayed session, or null when the session can't take Breaks. */
    val breaksLeft: Int? = null,
    /** "On a Break · 3 min 12 s left" while a Break is in progress. */
    val onBreak: String? = null,
) {
    val canTakeBreak: Boolean get() = breaksLeft != null && breaksLeft > 0 && onBreak == null
}

data class HomeState(
    val loading: Boolean = true,
    val running: List<RunningSessionRow> = emptyList(),
)

sealed interface HomeIntent {
    data object StartClicked : HomeIntent
    data object BlockListsClicked : HomeIntent
    data class EndClicked(val sessionId: String) : HomeIntent
    data class TakeBreakClicked(val sessionId: String) : HomeIntent
}

sealed interface HomeEffect {
    data object OpenStart : HomeEffect
    data object OpenBlockLists : HomeEffect
    data class OpenExitCountdown(val sessionId: String) : HomeEffect
}

class HomeViewModel(
    private val sessions: FocusSessionService,
    private val clock: Clock,
    ticks: Flow<Unit> = everySecond(),
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    init {
        viewModelScope.launch {
            combine(sessions.running, ticks.onStart { emit(Unit) }) { running, _ -> running }
                .collect { running -> render(running) }
        }
    }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.StartClicked -> emit(HomeEffect.OpenStart)
            HomeIntent.BlockListsClicked -> emit(HomeEffect.OpenBlockLists)
            is HomeIntent.EndClicked -> end(intent.sessionId)
            is HomeIntent.TakeBreakClicked -> viewModelScope.launch {
                sessions.takeBreak(FocusSessionId(intent.sessionId))
            }
        }
    }

    private fun end(sessionId: String) {
        val row = currentState.running.firstOrNull { it.id == sessionId } ?: return
        when (row.strictness) {
            Strictness.Normal -> viewModelScope.launch { sessions.endEarly(FocusSessionId(sessionId)) }
            Strictness.Delayed -> emit(HomeEffect.OpenExitCountdown(sessionId))
            Strictness.Locked -> Unit
        }
    }

    private suspend fun render(running: List<FocusSession>) {
        val now = clock.now()
        if (running.any { it.plannedEnd <= now }) {
            // Sessions that ran out are recorded as Completed; the repository flow re-renders.
            sessions.completeDueSessions()
        }
        setState {
            copy(
                loading = false,
                running = running.filter { it.plannedEnd > now }.map { session ->
                    RunningSessionRow(
                        id = session.id.value,
                        name = session.name,
                        strictness = session.strictness,
                        timeLeft = formatTimeLeft(session.timeLeft(now)),
                        canEnd = session.strictness != Strictness.Locked,
                        fromSchedule = session.scheduleId != null,
                        breaksLeft = session.breaksLeft.takeIf { session.strictness == Strictness.Delayed },
                        onBreak = session.currentBreak(now)?.let { "On a Break · ${formatTimeLeft(it.endsAt - now)}" },
                    )
                },
            )
        }
    }
}

internal fun everySecond(): Flow<Unit> = flow {
    while (true) {
        delay(1.seconds)
        emit(Unit)
    }
}
