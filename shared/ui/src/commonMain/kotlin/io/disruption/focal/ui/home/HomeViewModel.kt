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
    /** Only Normal sessions can be ended straight from Home. */
    val canEnd: Boolean,
    val fromSchedule: Boolean,
)

data class HomeState(
    val loading: Boolean = true,
    val running: List<RunningSessionRow> = emptyList(),
)

sealed interface HomeIntent {
    data object StartClicked : HomeIntent
    data object BlockListsClicked : HomeIntent
    data class EndClicked(val sessionId: String) : HomeIntent
}

sealed interface HomeEffect {
    data object OpenStart : HomeEffect
    data object OpenBlockLists : HomeEffect
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
            is HomeIntent.EndClicked -> viewModelScope.launch { sessions.endEarly(FocusSessionId(intent.sessionId)) }
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
                        canEnd = session.strictness == Strictness.Normal,
                        fromSchedule = session.scheduleId != null,
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
