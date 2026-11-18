package io.disruption.focal.ui.blocklists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.disruption.focal.ui.components.EmptyMessage
import io.disruption.focal.ui.components.FocalTopBar
import io.disruption.focal.ui.mvi.CollectEffects
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BlockListsRoute(
    onOpenEditor: (id: String) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: BlockListsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel) { effect ->
        when (effect) {
            is BlockListsEffect.OpenEditor -> onOpenEditor(effect.id)
        }
    }
    BlockListsScreen(state, viewModel::onIntent, onBack)
}

@Composable
fun BlockListsScreen(
    state: BlockListsState,
    onIntent: (BlockListsIntent) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = { FocalTopBar("Block Lists", onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { onIntent(BlockListsIntent.NewClicked) }) {
                Text("New Block List")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!state.loading && state.lists.isEmpty()) {
                EmptyMessage("Group the apps and websites that distract you into a Block List.")
            }
            LazyColumn {
                items(state.lists, key = { it.id }) { row ->
                    ListItem(
                        headlineContent = { Text(row.name) },
                        supportingContent = { Text(row.summary) },
                        modifier = Modifier.clickable { onIntent(BlockListsIntent.Open(row.id)) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    state.newList?.let { dialog ->
        AlertDialog(
            onDismissRequest = { onIntent(BlockListsIntent.CreateDismissed) },
            title = { Text("New Block List") },
            text = {
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = { onIntent(BlockListsIntent.NewNameChanged(it)) },
                    label = { Text("Name") },
                    placeholder = { Text("Social, Work hours, …") },
                    isError = dialog.error != null,
                    supportingText = dialog.error?.let { { Text(it) } },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onIntent(BlockListsIntent.CreateConfirmed) }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(BlockListsIntent.CreateDismissed) }) { Text("Cancel") }
            },
        )
    }
}
