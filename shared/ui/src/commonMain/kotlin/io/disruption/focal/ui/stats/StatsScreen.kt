package io.disruption.focal.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.disruption.focal.ui.components.FocalTopBar
import io.disruption.focal.ui.components.SectionTitle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StatsRoute(onBack: () -> Unit, viewModel: StatsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatsScreen(state, onBack)
}

@Composable
fun StatsScreen(state: StatsState, onBack: () -> Unit) {
    Scaffold(topBar = { FocalTopBar("Stats", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionTitle("Focus time")
            TileRow(Tile("Today", state.focusToday), Tile("This week", state.focusThisWeek))
            SectionTitle("Streak")
            TileRow(Tile("Current", state.currentStreak), Tile("Longest", state.longestStreak))
            Text(
                "A day counts when it has a Completed Focus Session of 15 minutes or more. A Broken session resets it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SectionTitle("Outcomes")
            TileRow(
                Tile("Completed", state.completed.toString()),
                Tile("Ended Early", state.endedEarly.toString()),
                Tile("Broken", state.broken.toString()),
            )
        }
    }
}

private data class Tile(val label: String, val value: String)

@Composable
private fun TileRow(vararg tiles: Tile) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tiles.forEach { tile ->
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text(tile.value, style = MaterialTheme.typography.headlineSmall)
                    Text(tile.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
