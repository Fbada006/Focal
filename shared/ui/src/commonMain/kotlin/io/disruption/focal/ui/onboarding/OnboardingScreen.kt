@file:OptIn(ExperimentalLayoutApi::class)

package io.disruption.focal.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.disruption.focal.ui.mvi.CollectEffects
import io.disruption.focal.ui.permissions.OnResume
import io.disruption.focal.ui.permissions.copy
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingRoute(onStartFirstSession: () -> Unit, viewModel: OnboardingViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel) { effect ->
        when (effect) {
            OnboardingEffect.StartFirstSession -> onStartFirstSession()
        }
    }
    OnResume { viewModel.onIntent(OnboardingIntent.Resumed) }
    OnboardingScreen(state, viewModel::onIntent)
}

@Composable
fun OnboardingScreen(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeContentPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val step = state.step) {
            OnboardingStep.Intro -> {
                Text("Focal", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Focal blocks the apps and websites that pull you away, for as long as you choose. " +
                        "Group them into Block Lists, start a Focus Session now or on a Schedule, and pick how " +
                        "hard it is to stop. Everything stays on your phone; there's no account.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Next, Focal explains each permission it needs before asking for it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { onIntent(OnboardingIntent.Next) }, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
            }
            is OnboardingStep.Grant -> {
                val copy = step.permission.copy()
                Text(copy.title, style = MaterialTheme.typography.headlineSmall)
                Text(copy.explanation, style = MaterialTheme.typography.bodyLarge)
                if (step.granted) {
                    Text("Done.", color = MaterialTheme.colorScheme.primary)
                    Button(onClick = { onIntent(OnboardingIntent.Next) }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
                } else {
                    Button(onClick = { onIntent(OnboardingIntent.GrantClicked) }, modifier = Modifier.fillMaxWidth()) {
                        Text(copy.action)
                    }
                    TextButton(onClick = { onIntent(OnboardingIntent.Next) }) { Text("Not now") }
                }
            }
            OnboardingStep.FirstBlockList -> {
                Text("Your first Block List", style = MaterialTheme.typography.headlineSmall)
                Text("Pick what distracts you. You can add apps and more websites later.")
                OutlinedTextField(
                    value = state.blockListName,
                    onValueChange = { onIntent(OnboardingIntent.BlockListNameChanged(it)) },
                    label = { Text("Name") },
                    isError = state.error != null,
                    supportingText = state.error?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.suggestions.forEach { suggestion ->
                        FilterChip(
                            selected = suggestion.selected,
                            onClick = { onIntent(OnboardingIntent.SuggestionToggled(suggestion.id)) },
                            label = { Text(suggestion.name) },
                        )
                    }
                }
                Button(onClick = { onIntent(OnboardingIntent.CreateBlockListClicked) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Create and start a Focus Session")
                }
            }
        }
    }
}
