package io.disruption.focal.ui.apps

import androidx.lifecycle.viewModelScope
import io.disruption.focal.core.apps.AppCategory
import io.disruption.focal.core.apps.AppCategoryResolver
import io.disruption.focal.core.apps.InstalledApp
import io.disruption.focal.core.apps.InstalledAppsSource
import io.disruption.focal.core.blocklist.BlockListResult
import io.disruption.focal.core.blocklist.BlockListService
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.ui.blocklists.deferredMessage
import io.disruption.focal.core.website.SuggestedWebsitesCatalog
import io.disruption.focal.ui.mvi.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

data class AppRow(val packageName: String, val label: String, val selected: Boolean, val category: String? = null)

data class CategoryRow(val key: String, val label: String, val selected: Boolean, val installedCount: Int)

data class AppPickerState(
    val loading: Boolean = true,
    val query: String = "",
    /** Installed apps matching [query], selected ones first, then by label. */
    val apps: List<AppRow> = emptyList(),
    val selectedCount: Int = 0,
    /** Set when an unticked app keeps being blocked by a running Delayed or Locked session. */
    val notice: String? = null,
    /** Whole App Categories; picking one also covers apps installed later (story 18). */
    val categories: List<CategoryRow> = emptyList(),
)

sealed interface AppPickerIntent {
    data class QueryChanged(val query: String) : AppPickerIntent
    data class Toggled(val packageName: String) : AppPickerIntent
    data class CategoryToggled(val key: String) : AppPickerIntent
}

/**
 * The Android Apps tab: search the installed apps and pick which to block (story 16). Picking an
 * app also selects the matching Suggested Websites, so blocking Instagram the app blocks
 * instagram.com by default (story 22); they can be unticked in the Websites tab.
 */
class AppPickerViewModel(
    blockListId: String,
    private val blockLists: BlockListService,
    private val installedApps: InstalledAppsSource,
    private val suggested: SuggestedWebsitesCatalog,
    private val categories: AppCategoryResolver,
) : MviViewModel<AppPickerState, AppPickerIntent, Nothing>(AppPickerState()) {
    private val id = BlockListId(blockListId)
    private val installed = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")
    private val notice = MutableStateFlow<String?>(null)
    init {
        viewModelScope.launch { installed.value = installedApps.launchableApps() }
        viewModelScope.launch {
            val blocked = blockLists.observeAll().mapNotNull { lists -> lists.firstOrNull { it.id == id }?.blocked }
            combine(installed, query, blocked, notice) { apps, query, blocked, notice ->
                AppPickerState(
                    loading = apps == null,
                    query = query,
                    apps = rows(apps.orEmpty(), query, blocked.apps),
                    selectedCount = blocked.apps.size,
                    notice = notice,
                    categories = AppCategory.entries.map { category ->
                        CategoryRow(
                            key = category.key,
                            label = category.label,
                            selected = category.key in blocked.appCategories,
                            installedCount = apps.orEmpty().count { categoryOf(it) == category },
                        )
                    },
                )
            }.collect { state -> setState { state } }
        }
    }

    override fun onIntent(intent: AppPickerIntent) {
        when (intent) {
            is AppPickerIntent.QueryChanged -> query.value = intent.query
            is AppPickerIntent.CategoryToggled -> viewModelScope.launch {
                val saved = blockLists.toggleAppCategory(id, intent.key) as? BlockListResult.Saved ?: return@launch
                showDeferred(saved.removalsDeferredFor)
            }
            is AppPickerIntent.Toggled -> viewModelScope.launch {
                val pkg = intent.packageName
                val saved = blockLists.toggleApp(id, pkg) as? BlockListResult.Saved ?: return@launch
                showDeferred(saved.removalsDeferredFor)
                if (pkg in saved.blockList.blocked.apps) {
                    val domains = suggested.forAndroidApps(listOf(pkg)).flatMap { it.domains }.toSet()
                    if (domains.isNotEmpty()) blockLists.addDomains(id, domains)
                }
            }
        }
    }

    /** ADR 0007: a removal a running session defers must say so (story 48). */
    private fun showDeferred(sessionNames: List<String>) {
        if (sessionNames.isNotEmpty()) notice.value = deferredMessage(sessionNames)
    }
    private fun categoryOf(app: InstalledApp) = categories.categoryOf(app.packageName, app.systemCategory)

    private fun rows(apps: List<InstalledApp>, query: String, selection: Set<String>): List<AppRow> {
        val needle = query.trim()
        return apps
            .filter { needle.isEmpty() || it.label.contains(needle, ignoreCase = true) }
            .map { AppRow(it.packageName, it.label, it.packageName in selection, categoryOf(it)?.label) }
            .sortedWith(compareByDescending<AppRow> { it.selected }.thenBy { it.label.lowercase() })
    }
}
