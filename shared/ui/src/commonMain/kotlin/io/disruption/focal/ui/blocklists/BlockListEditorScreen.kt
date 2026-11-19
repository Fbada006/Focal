package io.disruption.focal.ui.blocklists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.disruption.focal.ui.components.EmptyMessage
import io.disruption.focal.ui.components.FocalTopBar
import io.disruption.focal.ui.mvi.CollectEffects
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Content of the Apps tab. Each platform supplies its own: Apple's picker on iOS, the installed
 * apps list on Android.
 */
typealias AppsTabContent = @Composable (blockListId: String, state: BlockListEditorState) -> Unit

@Composable
fun BlockListEditorRoute(
    blockListId: String,
    onClose: () -> Unit,
    appsTab: AppsTabContent = { _, _ -> EmptyMessage("Picking apps is coming soon.") },
    viewModel: BlockListEditorViewModel = koinViewModel(key = blockListId) { parametersOf(blockListId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel) { effect ->
        when (effect) {
            BlockListEditorEffect.Closed -> onClose()
        }
    }
    BlockListEditorScreen(state, viewModel::onIntent, onClose) { appsTab(blockListId, state) }
}

@Composable
fun BlockListEditorScreen(
    state: BlockListEditorState,
    onIntent: (BlockListEditorIntent) -> Unit,
    onBack: () -> Unit,
    appsTab: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            FocalTopBar(
                title = "Edit Block List",
                onBack = onBack,
                actions = {
                    TextButton(onClick = { onIntent(BlockListEditorIntent.DeleteClicked) }) { Text("Delete") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { onIntent(BlockListEditorIntent.NameChanged(it)) },
                label = { Text("Name") },
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onIntent(BlockListEditorIntent.NameCommitted) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .onFocusChanged { if (!it.isFocused && !state.loading) onIntent(BlockListEditorIntent.NameCommitted) },
            )
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                EditorTab.entries.forEach { tab ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { onIntent(BlockListEditorIntent.TabSelected(tab)) },
                        text = { Text(tab.name) },
                    )
                }
            }
            when (state.tab) {
                EditorTab.Apps -> appsTab()
                EditorTab.Websites -> WebsitesTab(state, onIntent)
            }
        }
    }

    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = { onIntent(BlockListEditorIntent.DeleteDismissed) },
            title = { Text("Delete ${state.name}?") },
            text = { Text("Schedules and future sessions won't be able to use it.") },
            confirmButton = {
                TextButton(onClick = { onIntent(BlockListEditorIntent.DeleteConfirmed) }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(BlockListEditorIntent.DeleteDismissed) }) { Text("Cancel") }
            },
        )
    }
    state.deleteRefusal?.let { message ->
        AlertDialog(
            onDismissRequest = { onIntent(BlockListEditorIntent.DeleteRefusalDismissed) },
            title = { Text("Block List in use") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { onIntent(BlockListEditorIntent.DeleteRefusalDismissed) }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun WebsitesTab(state: BlockListEditorState, onIntent: (BlockListEditorIntent) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = state.domainInput,
                onValueChange = { onIntent(BlockListEditorIntent.DomainInputChanged(it)) },
                label = { Text(if (state.editingDomain == null) "Add a website" else "Edit ${state.editingDomain}") },
                placeholder = { Text("youtube.com") },
                isError = state.domainError != null,
                supportingText = {
                    Text(state.domainError ?: "Subdomains are blocked too, such as m.youtube.com")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onIntent(BlockListEditorIntent.DomainSubmitted) }),
                modifier = Modifier.weight(1f),
            )
            Column {
                Button(onClick = { onIntent(BlockListEditorIntent.DomainSubmitted) }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(if (state.editingDomain == null) "Add" else "Save")
                }
                if (state.editingDomain != null) {
                    TextButton(onClick = { onIntent(BlockListEditorIntent.CancelDomainEdit) }) { Text("Cancel") }
                }
            }
        }
        if (state.domains.isEmpty()) {
            EmptyMessage("No websites yet.")
        }
        LazyColumn {
            items(state.domains, key = { it }) { domain ->
                ListItem(
                    headlineContent = { Text(domain) },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { onIntent(BlockListEditorIntent.EditDomain(domain)) }) { Text("Edit") }
                            TextButton(onClick = { onIntent(BlockListEditorIntent.RemoveDomain(domain)) }) { Text("Remove") }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}
