package io.disruption.focal.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState

/** Handle a view model's one-off Effects while the screen is shown. */
@Composable
fun <Effect> CollectEffects(viewModel: MviViewModel<*, *, Effect>, onEffect: suspend (Effect) -> Unit) {
    val handler = rememberUpdatedState(onEffect)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { handler.value(it) }
    }
}
