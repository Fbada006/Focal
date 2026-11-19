package io.disruption.focal.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.ui.components.FocalTopBar
import io.disruption.focal.ui.components.SectionTitle
import io.disruption.focal.ui.home.description
import io.disruption.focal.ui.home.label
import io.disruption.focal.ui.mvi.CollectEffects
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Composable
fun EditSessionRoute(
    sessionId: String,
    onClose: () -> Unit,
    viewModel: EditSessionViewModel = koinViewModel(key = "edit-$sessionId") { parametersOf(sessionId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel) { effect ->
        when (effect) {
            EditSessionEffect.Close -> onClose()
        }
    }
    EditSessionScreen(state, viewModel::onIntent, onClose)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSessionScreen(state: EditSessionState, onIntent: (EditSessionIntent) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { FocalTopBar("Edit ${state.name}", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SectionTitle("Ends at ${state.endsAt}")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                if (state.canWeaken) {
                    OutlinedButton(onClick = { onIntent(EditSessionIntent.ShiftEnd((-15).minutes)) }) { Text("−15 min") }
                }
                OutlinedButton(onClick = { onIntent(EditSessionIntent.ShiftEnd(15.minutes)) }) { Text("+15 min") }
                OutlinedButton(onClick = { onIntent(EditSessionIntent.ShiftEnd(1.hours)) }) { Text("+1 h") }
            }
            if (!state.canWeaken) {
                Text(
                    "A running ${state.strictness} session can only get stricter: it can be extended but not shortened.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            SectionTitle("Strictness")
            Strictness.entries.forEach { strictness ->
                val enabled = state.canWeaken || !state.strictness.isStricterThan(strictness)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.strictness == strictness,
                            enabled = enabled,
                            onClick = { onIntent(EditSessionIntent.StrictnessSelected(strictness)) },
                        )
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.strictness == strictness, onClick = null, enabled = enabled)
                    Column {
                        Text(strictness.label())
                        Text(
                            strictness.description(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
