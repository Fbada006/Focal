package io.disruption.focal.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.disruption.focal.ui.components.EmptyMessage
import io.disruption.focal.ui.components.FocalTopBar
import io.disruption.focal.ui.components.SectionTitle
import io.disruption.focal.ui.mvi.CollectEffects
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeRoute(
    onOpenStart: () -> Unit,
    onOpenBlockLists: () -> Unit,
    sessionActions: @Composable (RunningSessionRow) -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel) { effect ->
        when (effect) {
            HomeEffect.OpenStart -> onOpenStart()
            HomeEffect.OpenBlockLists -> onOpenBlockLists()
        }
    }
    HomeScreen(state, viewModel::onIntent, sessionActions)
}

/**
 * Home lists every running Focus Session. [sessionActions] lets other features (Exit Countdown,
 * Breaks) add their own controls to a session's card.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    sessionActions: @Composable (RunningSessionRow) -> Unit = {},
) {
    Scaffold(
        topBar = {
            FocalTopBar(
                title = "Focal",
                actions = { TextButton(onClick = { onIntent(HomeIntent.BlockListsClicked) }) { Text("Block Lists") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Button(
                onClick = { onIntent(HomeIntent.StartClicked) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text("Start a Focus Session") }

            SectionTitle("Running")
            if (!state.loading && state.running.isEmpty()) {
                EmptyMessage("Nothing is blocked right now.")
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                items(state.running, key = { it.id }) { row ->
                    RunningSessionCard(row, onEnd = { onIntent(HomeIntent.EndClicked(row.id)) }) { sessionActions(row) }
                }
            }
        }
    }
}

@Composable
private fun RunningSessionCard(row: RunningSessionRow, onEnd: () -> Unit, actions: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium)
            Text(
                buildString {
                    append(row.strictness.label())
                    if (row.fromSchedule) append(" · Scheduled")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(row.timeLeft, style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (row.canEnd) OutlinedButton(onClick = onEnd) { Text("End session") }
                actions()
            }
        }
    }
}
