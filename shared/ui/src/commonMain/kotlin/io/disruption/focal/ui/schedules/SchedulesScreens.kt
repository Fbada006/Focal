@file:OptIn(ExperimentalLayoutApi::class)

package io.disruption.focal.ui.schedules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.permissions.Permission
import io.disruption.focal.ui.components.EmptyMessage
import io.disruption.focal.ui.components.FocalTopBar
import io.disruption.focal.ui.components.SectionTitle
import io.disruption.focal.ui.home.description
import io.disruption.focal.ui.home.label
import io.disruption.focal.ui.mvi.CollectEffects
import io.disruption.focal.ui.permissions.copy
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SchedulesRoute(
    onOpenEditor: (id: String?) -> Unit,
    onBack: () -> Unit,
    viewModel: SchedulesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel) { effect ->
        when (effect) {
            is SchedulesEffect.OpenEditor -> onOpenEditor(effect.id)
        }
    }
    Scaffold(
        topBar = { FocalTopBar("Schedules", onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { viewModel.onIntent(SchedulesIntent.NewClicked) }) { Text("New Schedule") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (!state.loading && state.schedules.isEmpty()) {
                item { EmptyMessage("Schedules start Focus Sessions for you, such as every weekday 9:00–17:00.") }
            }
            items(state.schedules, key = { it.id }) { row ->
                ListItem(
                    headlineContent = { Text(row.name) },
                    supportingContent = { Text(row.summary) },
                    trailingContent = {
                        Switch(
                            checked = row.enabled,
                            onCheckedChange = { viewModel.onIntent(SchedulesIntent.EnabledChanged(row.id, it)) },
                        )
                    },
                    modifier = Modifier.clickable { viewModel.onIntent(SchedulesIntent.Open(row.id)) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun ScheduleEditorRoute(
    scheduleId: String?,
    onClose: () -> Unit,
    viewModel: ScheduleEditorViewModel = koinViewModel(key = "schedule-${scheduleId ?: "new"}") { parametersOf(scheduleId ?: "") },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel) { effect ->
        when (effect) {
            ScheduleEditorEffect.Close -> onClose()
        }
    }
    ScheduleEditorScreen(state, viewModel::onIntent, onClose)
}

@Composable
fun ScheduleEditorScreen(state: ScheduleEditorState, onIntent: (ScheduleEditorIntent) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            FocalTopBar(
                title = if (state.isNew) "New Schedule" else "Edit Schedule",
                onBack = onBack,
                actions = {
                    if (!state.isNew) TextButton(onClick = { onIntent(ScheduleEditorIntent.DeleteClicked) }) { Text("Delete") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { onIntent(ScheduleEditorIntent.NameChanged(it)) },
                label = { Text("Name") },
                placeholder = { Text("Work hours, Wind down, …") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )

            SectionTitle("Days")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in state.days,
                        onClick = { onIntent(ScheduleEditorIntent.DayToggled(day)) },
                        label = { Text(day.short()) },
                    )
                }
            }

            SectionTitle("Time")
            TimeStepper("Starts", state.start) { onIntent(ScheduleEditorIntent.StartChanged(it)) }
            TimeStepper("Ends", state.end) { onIntent(ScheduleEditorIntent.EndChanged(it)) }
            if (state.isOvernight) {
                Text(
                    "Ends the next morning (overnight).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            SectionTitle("Block List")
            state.blockLists.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = option.id == state.blockListId,
                            onClick = { onIntent(ScheduleEditorIntent.BlockListSelected(option.id)) },
                        )
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option.id == state.blockListId, onClick = null)
                    Text(if (option.isEmpty) "${option.name} (empty)" else option.name)
                }
            }

            SectionTitle("Strictness")
            Strictness.entries.forEach { strictness ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.strictness == strictness,
                            onClick = { onIntent(ScheduleEditorIntent.StrictnessSelected(strictness)) },
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

            if (!state.isNew) {
                Text(
                    "Changes apply to sessions this Schedule starts from now on. A session it already started keeps running.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            state.errors.forEach {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            Button(
                onClick = { onIntent(ScheduleEditorIntent.SaveClicked) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text("Save") }
        }
    }

    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = { onIntent(ScheduleEditorIntent.DeleteDismissed) },
            title = { Text("Delete ${state.name}?") },
            text = { Text("A session it already started keeps running until it ends.") },
            confirmButton = { TextButton(onClick = { onIntent(ScheduleEditorIntent.DeleteConfirmed) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { onIntent(ScheduleEditorIntent.DeleteDismissed) }) { Text("Cancel") } },
        )
    }

    if (state.askingForAlarms) {
        val copy = Permission.ExactAlarms.copy()
        AlertDialog(
            onDismissRequest = { onIntent(ScheduleEditorIntent.AlarmsNotNowClicked) },
            title = { Text(copy.title) },
            text = { Text(copy.explanation) },
            confirmButton = { TextButton(onClick = { onIntent(ScheduleEditorIntent.AllowAlarmsClicked) }) { Text(copy.action) } },
            dismissButton = { TextButton(onClick = { onIntent(ScheduleEditorIntent.AlarmsNotNowClicked) }) { Text("Not now") } },
        )
    }
}

@Composable
private fun TimeStepper(label: String, time: LocalTime, onChange: (LocalTime) -> Unit) {
    fun shift(minutes: Int) {
        val day = 24 * 60
        val total = ((time.hour * 60 + time.minute + minutes) % day + day) % day
        onChange(LocalTime(total / 60, total % 60))
    }
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.padding(end = 8.dp))
        OutlinedButton(onClick = { shift(-15) }) { Text("−") }
        Text(time.hhmm(), style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { shift(15) }) { Text("+") }
    }
}
