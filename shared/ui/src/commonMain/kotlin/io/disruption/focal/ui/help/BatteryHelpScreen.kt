package io.disruption.focal.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.disruption.focal.ui.components.FocalTopBar
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BatteryHelpRoute(onBack: () -> Unit, viewModel: BatteryHelpViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { FocalTopBar("Why did my session stop?", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            BatteryHelpContent(state) { viewModel.onIntent(BatteryHelpIntent.LinkClicked(it)) }
        }
    }
}

/** The steps for this device; also shown during onboarding on phones that need them. */
@Composable
fun BatteryHelpContent(state: BatteryHelpState, onLink: (HelpLink) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.deviceName != null) {
            Text(
                "${state.deviceName} phones can stop apps running in the background to save battery, which can " +
                    "end blocking early. These settings keep Focal running:",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        state.steps.forEachIndexed { index, step ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (state.steps.size > 1) "${index + 1}. ${step.text}" else step.text,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (step.link != null && step.linkLabel != null) {
                    OutlinedButton(onClick = { onLink(step.link) }) { Text(step.linkLabel) }
                }
            }
        }
    }
}
