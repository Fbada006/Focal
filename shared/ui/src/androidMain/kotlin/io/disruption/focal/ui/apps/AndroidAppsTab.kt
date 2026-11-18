package io.disruption.focal.ui.apps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.disruption.focal.ui.blocklists.BlockListEditorState
import io.disruption.focal.ui.components.SectionTitle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The Android Apps tab of the Block List editor. */
@Composable
fun AndroidAppsTab(
    blockListId: String,
    @Suppress("UNUSED_PARAMETER") editorState: BlockListEditorState,
    viewModel: AppPickerViewModel = koinViewModel(key = "apps-$blockListId") { parametersOf(blockListId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column {
        OutlinedTextField(
            value = state.query,
            onValueChange = { viewModel.onIntent(AppPickerIntent.QueryChanged(it)) },
            label = { Text("Search apps") },
            supportingText = { Text("${state.selectedCount} selected") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        state.notice?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (state.loading) {
            CircularProgressIndicator(Modifier.padding(24.dp))
        }
        LazyColumn {
            item { SectionTitle("App Categories") }
            items(state.categories, key = { "category-${it.key}" }) { category ->
                ListItem(
                    headlineContent = { Text(category.label) },
                    supportingContent = { Text("${category.installedCount} installed, and any you install later") },
                    trailingContent = { Checkbox(checked = category.selected, onCheckedChange = null) },
                    modifier = Modifier.toggleable(
                        value = category.selected,
                        onValueChange = { viewModel.onIntent(AppPickerIntent.CategoryToggled(category.key)) },
                    ),
                )
            }
            item { SectionTitle("Apps") }
            items(state.apps, key = { it.packageName }) { app ->
                ListItem(
                    leadingContent = { AppIcon(app.packageName) },
                    headlineContent = { Text(app.label) },
                    supportingContent = app.category?.let { { Text(it) } },
                    trailingContent = { Checkbox(checked = app.selected, onCheckedChange = null) },
                    modifier = Modifier.toggleable(
                        value = app.selected,
                        onValueChange = { viewModel.onIntent(AppPickerIntent.Toggled(app.packageName)) },
                    ),
                )
            }
        }
    }
}
