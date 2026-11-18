package io.disruption.focal.ui.countdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.disruption.focal.ui.components.FocalTopBar
import io.disruption.focal.ui.mvi.CollectEffects
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ExitCountdownRoute(
    sessionId: String,
    onClose: () -> Unit,
    viewModel: ExitCountdownViewModel = koinViewModel(key = "countdown-$sessionId") { parametersOf(sessionId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel) { effect ->
        when (effect) {
            ExitCountdownEffect.Ended, ExitCountdownEffect.Close -> onClose()
        }
    }
    // The countdown only runs while this screen is in the foreground: going to the background or
    // leaving the screen cancels it, so it can't be waited out inside a blocked app.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onIntent(ExitCountdownIntent.Shown)
                Lifecycle.Event.ON_PAUSE -> viewModel.onIntent(ExitCountdownIntent.Left)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onIntent(ExitCountdownIntent.Left)
        }
    }
    ExitCountdownScreen(state, viewModel::onIntent, onBack = { viewModel.onIntent(ExitCountdownIntent.KeepGoingClicked) })
}

@Composable
fun ExitCountdownScreen(state: ExitCountdownState, onIntent: (ExitCountdownIntent) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { FocalTopBar("End ${state.sessionName} early?", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (state.canConfirm) "You can end it now." else "Take a moment. Do you really want to stop?",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                if (state.canConfirm) "0" else state.secondsLeft.toString(),
                style = MaterialTheme.typography.displayLarge,
            )
            if (state.cancelled) {
                Text(
                    "The countdown was cancelled because you left it.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(onClick = { onIntent(ExitCountdownIntent.RestartClicked) }) { Text("Start the countdown again") }
            }
            Button(
                onClick = { onIntent(ExitCountdownIntent.ConfirmClicked) },
                enabled = state.canConfirm,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("End session") }
            OutlinedButton(
                onClick = { onIntent(ExitCountdownIntent.KeepGoingClicked) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Keep going") }
        }
    }
}
