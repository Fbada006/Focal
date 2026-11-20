package io.disruption.focal.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import io.disruption.focal.core.repository.FocusSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Redraws every placed [StatsWidget] when Focus Sessions change: started, ended, edited or a Break
 * taken. Changes arriving together (a session ending and the next one starting) are coalesced, and
 * with no widget placed `updateAll` does nothing. Time passing is left to the 30-minute refresh.
 */
object StatsWidgetUpdates {
    @OptIn(FlowPreview::class)
    fun follow(context: Context, sessions: FocusSessionRepository, scope: CoroutineScope) {
        scope.launch {
            sessions.observeAll()
                .distinctUntilChanged()
                .debounce(1.seconds)
                .collect { runCatching { StatsWidget().updateAll(context) } }
        }
    }
}
