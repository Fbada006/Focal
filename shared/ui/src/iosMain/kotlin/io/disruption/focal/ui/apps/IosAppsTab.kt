package io.disruption.focal.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.disruption.focal.ui.blocking.IosScreenTimeBridge
import io.disruption.focal.ui.blocklists.BlockListEditorIntent
import io.disruption.focal.ui.blocklists.BlockListEditorState
import io.disruption.focal.ui.blocklists.BlockListEditorViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The iOS Apps tab: Apple's `FamilyActivityPicker` is presented by Swift, and the chosen tokens are
 * stored on the Block List (T15). Focal never learns which apps were picked, only the tokens.
 */
@Composable
fun IosAppsTab(
    blockListId: String,
    state: BlockListEditorState,
    // The editor's own view model (same key), so the picked tokens go through it and any
    // deferred-removal notice shows in the editor (ADR 0007).
    viewModel: BlockListEditorViewModel = koinViewModel(key = blockListId) { parametersOf(blockListId) },
) {
    DisposableEffect(blockListId, viewModel) {
        IosScreenTimeBridge.onSelection = { pickedFor, apps, categories ->
            if (pickedFor == blockListId) viewModel.onIntent(BlockListEditorIntent.AppSelectionPicked(apps, categories))
        }
        onDispose { IosScreenTimeBridge.onSelection = null }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Apps and App Categories", style = MaterialTheme.typography.titleMedium)
        Text(
            text = when {
                state.apps.isEmpty() && state.appCategories.isEmpty() -> "Nothing picked yet."
                else -> "${state.apps.size} apps and ${state.appCategories.size} App Categories picked."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = { IosScreenTimeBridge.present(blockListId, state.apps, state.appCategories) }) {
            Text("Choose apps and categories")
        }
        Text(
            text = "Uses Apple's Screen Time picker. Focal only stores the tokens Screen Time gives it, not which apps you use.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
