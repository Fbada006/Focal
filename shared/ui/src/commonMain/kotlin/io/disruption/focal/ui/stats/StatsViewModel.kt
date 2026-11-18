package io.disruption.focal.ui.stats

import androidx.lifecycle.viewModelScope
import io.disruption.focal.core.repository.FocusSessionRepository
import io.disruption.focal.core.stats.StatsCalculator
import io.disruption.focal.core.time.TimeZoneProvider
import io.disruption.focal.ui.mvi.MviViewModel
import io.disruption.focal.ui.home.formatMinutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class StatsState(
    val loading: Boolean = true,
    val focusToday: String = "0 min",
    val focusThisWeek: String = "0 min",
    val completed: Int = 0,
    val endedEarly: Int = 0,
    val broken: Int = 0,
    val currentStreak: String = "0 days",
    val longestStreak: String = "0 days",
)

/** The Stats screen only shows numbers; it has no Intents or Effects. */
class StatsViewModel(
    sessions: FocusSessionRepository,
    private val clock: Clock,
    private val timeZone: TimeZoneProvider,
    ticks: Flow<Unit> = everyMinute(),
) : MviViewModel<StatsState, Nothing, Nothing>(StatsState()) {

    init {
        viewModelScope.launch {
            combine(sessions.observeAll(), ticks.onStart { emit(Unit) }) { all, _ -> all }.collect { all ->
                val stats = StatsCalculator.calculate(all, clock.now(), timeZone.current())
                setState {
                    StatsState(
                        loading = false,
                        focusToday = stats.focusToday.text(),
                        focusThisWeek = stats.focusThisWeek.text(),
                        completed = stats.completed,
                        endedEarly = stats.endedEarly,
                        broken = stats.broken,
                        currentStreak = days(stats.currentStreak),
                        longestStreak = days(stats.longestStreak),
                    )
                }
            }
        }
    }

    override fun onIntent(intent: Nothing) = Unit

    private fun Duration.text(): String = formatMinutes(inWholeMinutes.minutes)

    private fun days(n: Int) = if (n == 1) "1 day" else "$n days"
}

private fun everyMinute(): Flow<Unit> = flow {
    while (true) {
        delay(1.minutes)
        emit(Unit)
    }
}
