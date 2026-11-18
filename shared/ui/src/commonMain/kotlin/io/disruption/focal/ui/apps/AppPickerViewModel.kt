package io.disruption.focal.ui.apps

import androidx.lifecycle.viewModelScope
import io.disruption.focal.core.apps.InstalledApp
import io.disruption.focal.core.apps.InstalledAppsSource
import io.disruption.focal.core.blocklist.BlockListService
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.ui.mvi.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

data class AppRow(val packageName: String, val label: String, val selected: Boolean)

data class AppPickerState(
    val loading: Boolean = true,
    val query: String = "",
    /** Installed apps matching [query], selected ones first, then by label. */
    val apps: List<AppRow> = emptyList(),
    val selectedCount: Int = 0,
)

sealed interface AppPickerIntent {
    data class QueryChanged(val query: String) : AppPickerIntent
    data class Toggled(val packageName: String) : AppPickerIntent
}

/** The Android Apps tab: search the installed apps and pick which to block (story 16). */
class AppPickerViewModel(
    blockListId: String,
    private val blockLists: BlockListService,
    private val installedApps: InstalledAppsSource,
) : MviViewModel<AppPickerState, AppPickerIntent, Nothing>(AppPickerState()) {
    private val id = BlockListId(blockListId)
    private val installed = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")
    private var selected: Set<String> = emptySet()

    init {
        viewModelScope.launch { installed.value = installedApps.launchableApps() }
        viewModelScope.launch {
            val selection = blockLists.observeAll().mapNotNull { lists -> lists.firstOrNull { it.id == id }?.blocked?.apps }
            combine(installed, query, selection) { apps, query, selection ->
                selected = selection
                AppPickerState(
                    loading = apps == null,
                    query = query,
                    apps = rows(apps.orEmpty(), query, selection),
                    selectedCount = selection.size,
                )
            }.collect { state -> setState { state } }
        }
    }

    override fun onIntent(intent: AppPickerIntent) {
        when (intent) {
            is AppPickerIntent.QueryChanged -> query.value = intent.query
            is AppPickerIntent.Toggled -> viewModelScope.launch {
                val pkg = intent.packageName
                blockLists.setApps(id, if (pkg in selected) selected - pkg else selected + pkg)
            }
        }
    }

    private fun rows(apps: List<InstalledApp>, query: String, selection: Set<String>): List<AppRow> {
        val needle = query.trim()
        return apps
            .filter { needle.isEmpty() || it.label.contains(needle, ignoreCase = true) }
            .map { AppRow(it.packageName, it.label, it.packageName in selection) }
            .sortedWith(compareByDescending<AppRow> { it.selected }.thenBy { it.label.lowercase() })
    }
}
