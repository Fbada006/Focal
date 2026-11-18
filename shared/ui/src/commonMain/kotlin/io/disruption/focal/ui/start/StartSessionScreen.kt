@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package io.disruption.focal.ui.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.disruption.focal.ui.home.formatMinutes
import io.disruption.focal.ui.home.label
import io.disruption.focal.ui.mvi.CollectEffects
import kotlinx.datetime.LocalTime
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Composable
fun StartSessionRoute(
    onStarted: () -> Unit,
    onOpenBlockLists: () -> Unit,
    onBack: () -> Unit,
    viewModel: StartSessionViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel) { effect ->
        when (effect) {
            StartSessionEffect.Started -> onStarted()
            StartSessionEffect.OpenBlockLists -> onOpenBlockLists()
        }
    }
    StartSessionScreen(state, viewModel::onIntent, onBack)
}

@Composable
fun StartSessionScreen(state: StartSessionState, onIntent: (StartSessionIntent) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { FocalTopBar("Start a Focus Session", onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Block List")
            if (!state.loading && state.blockLists.isEmpty()) {
                TextButton(
                    onClick = { onIntent(StartSessionIntent.CreateBlockListClicked) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text("Create a Block List first") }
            }
            state.blockLists.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = option.id == state.selectedBlockListId,
                            enabled = !option.isEmpty,
                            onClick = { onIntent(StartSessionIntent.BlockListSelected(option.id)) },
                        )
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option.id == state.selectedBlockListId, onClick = null, enabled = !option.isEmpty)
                    Text(if (option.isEmpty) "${option.name} (empty)" else option.name)
                }
            }

            SectionTitle("How long")
            SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 16.dp)) {
                LengthMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.lengthMode == mode,
                        onClick = { onIntent(StartSessionIntent.LengthModeSelected(mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index, LengthMode.entries.size),
                    ) { Text(if (mode == LengthMode.Duration) "For" else "Until") }
                }
            }
            when (state.lengthMode) {
                LengthMode.Duration -> DurationPicker(state.duration) { onIntent(StartSessionIntent.DurationSelected(it)) }
                LengthMode.EndTime -> EndTimePicker(state.endTime) { onIntent(StartSessionIntent.EndTimeSelected(it)) }
            }

            SectionTitle("Strictness")
            Strictness.entries.forEach { strictness ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.strictness == strictness,
                            onClick = { onIntent(StartSessionIntent.StrictnessSelected(strictness)) },
                        )
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.strictness == strictness, onClick = null)
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
            Button(
                onClick = { onIntent(StartSessionIntent.StartClicked) },
                enabled = state.canStart,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text("Start") }
        }
    }
}

@Composable
private fun DurationPicker(duration: Duration, onChange: (Duration) -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StartSessionViewModel.DURATION_PRESETS.forEach { preset ->
                FilterChip(
                    selected = duration == preset,
                    onClick = { onChange(preset) },
                    label = { Text(formatMinutes(preset)) },
                )
            }
        }
        Stepper(formatMinutes(duration), onMinus = { onChange(duration - 5.minutes) }, onPlus = { onChange(duration + 5.minutes) })
    }
}

@Composable
private fun EndTimePicker(time: LocalTime, onChange: (LocalTime) -> Unit) {
    fun shift(minutes: Int) {
        val total = ((time.hour * 60 + time.minute + minutes) % (24 * 60) + 24 * 60) % (24 * 60)
        onChange(LocalTime(total / 60, total % 60))
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Stepper(
            "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}",
            onMinus = { shift(-15) },
            onPlus = { shift(15) },
        )
        Text(
            "Ends the next time it's this time, today or tomorrow.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(onClick = onMinus) { Text("−") }
        Text(value, style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = onPlus) { Text("+") }
    }
}
