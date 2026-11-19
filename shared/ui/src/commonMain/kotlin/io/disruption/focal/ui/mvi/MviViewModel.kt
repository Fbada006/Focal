package io.disruption.focal.ui.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The hand-rolled MVI base (ADR 0011): one immutable [state], one [onIntent] entry point, and
 * one-off [effects] such as navigation. Composables render State and send Intents, nothing else.
 * Domain rules stay in `shared/core`; view models only call into it and reduce results to State.
 */
abstract class MviViewModel<State, Intent, Effect>(initialState: State) : ViewModel() {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    protected val currentState: State get() = _state.value

    abstract fun onIntent(intent: Intent)

    protected fun setState(reduce: State.() -> State) = _state.update(reduce)

    protected fun emit(effect: Effect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
