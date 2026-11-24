package io.disruption.focal.ui.apps

import androidx.lifecycle.viewModelScope
import io.disruption.focal.core.apps.AppCategory
import io.disruption.focal.core.apps.AppCategoryResolver
import io.disruption.focal.core.apps.AppUsageSource
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
    /** The apps the user uses most, from the system's usage data (story 16a). */
    val suggestions: List<AppRow> = emptyList(),
    /** Whether Focal may read usage stats. False shows how to grant it; suggestions stay empty. */
    val usageAccessGranted: Boolean = false,
    /** Shown above [suggestions] so the user knows where the order comes from. */
    val usageNote: String? = null,
)

sealed interface AppPickerIntent {
    data class QueryChanged(val query: String) : AppPickerIntent
    data class Toggled(val packageName: String) : AppPickerIntent
    data class CategoryToggled(val key: String) : AppPickerIntent

    /** Open the system screen where the optional usage access is granted. */
    data object UsageAccessRequested : AppPickerIntent

    /** Re-read usage access after returning from that screen. */
    data object UsageAccessChecked : AppPickerIntent
}

/** The system's ranking, kept as package names in most-used order. */
private data class UsageRanking(val granted: Boolean, val packages: List<String> = emptyList())

/**
 * The Android Apps tab: search the installed apps and pick which to block (story 16). Picking an
 * app also selects the matching Suggested Websites, so blocking Instagram the app blocks
 * instagram.com by default (story 22); they can be unticked in the Websites tab.
 *
 * The most-used apps come first, ranked from Android's own usage data (story 16a). Usage access is
 * optional: without it the list is alphabetical and the tab explains how to grant it.
 */
class AppPickerViewModel(
    blockListId: String,
    private val blockLists: BlockListService,
    private val installedApps: InstalledAppsSource,
    private val suggested: SuggestedWebsitesCatalog,
    private val categories: AppCategoryResolver,
    private val usage: AppUsageSource,
) : MviViewModel<AppPickerState, AppPickerIntent, Nothing>(AppPickerState()) {
    private val id = BlockListId(blockListId)
    private val installed = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")
    private val notice = MutableStateFlow<String?>(null)
    private val ranking = MutableStateFlow(UsageRanking(granted = false))

    init {
        viewModelScope.launch { installed.value = installedApps.launchableApps() }
        viewModelScope.launch { ranking.value = readUsage() }
        viewModelScope.launch {
            val blocked = blockLists.observeAll().mapNotNull { lists -> lists.firstOrNull { it.id == id }?.blocked }
            combine(installed, query, blocked, notice, ranking) { apps, query, blocked, notice, ranking ->
                val installed = apps.orEmpty()
                // A search covers every app; suggestions only make sense in the unfiltered list.
                val ranked = if (query.isBlank()) rankedSuggestions(installed, ranking, blocked.apps) else emptyList()
                val suggestedPackages = ranked.mapTo(mutableSetOf()) { it.packageName }
                AppPickerState(
                    loading = apps == null,
                    query = query,
                    apps = rows(installed, query, blocked.apps, excluded = suggestedPackages),
                    selectedCount = blocked.apps.size,
                    notice = notice,
                    categories = AppCategory.entries.map { category ->
                        CategoryRow(
                            key = category.key,
                            label = category.label,
                            selected = category.key in blocked.appCategories,
                            installedCount = installed.count { categoryOf(it) == category },
                        )
                    },
                    suggestions = ranked,
                    usageAccessGranted = ranking.granted,
                    usageNote = if (ranking.granted && ranked.isNotEmpty()) USAGE_NOTE else null,
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
            AppPickerIntent.UsageAccessRequested -> usage.requestAccess()
            AppPickerIntent.UsageAccessChecked -> viewModelScope.launch { ranking.value = readUsage() }
        }
    }

    private suspend fun readUsage(): UsageRanking {
        if (!usage.isGranted()) return UsageRanking(granted = false)
        val packages = usage.mostUsed(USAGE_DAYS).map { it.packageName }
        return UsageRanking(granted = true, packages = packages)
    }

    /**
     * Installed apps in the system's order. Packages that aren't launchable apps are skipped before
     * the cap, so System UI or Focal itself never take a suggestion slot.
     */
    private fun rankedSuggestions(
        apps: List<InstalledApp>,
        ranking: UsageRanking,
        selection: Set<String>,
    ): List<AppRow> {
        if (!ranking.granted) return emptyList()
        val byPackage = apps.associateBy { it.packageName }
        return ranking.packages
            .mapNotNull { byPackage[it] }
            .take(SUGGESTION_LIMIT)
            .map { row(it, selection) }
    }

    /** ADR 0007: a removal a running session defers must say so (story 48). */
    private fun showDeferred(sessionNames: List<String>) {
        if (sessionNames.isNotEmpty()) notice.value = deferredMessage(sessionNames)
    }
    private fun categoryOf(app: InstalledApp) = categories.categoryOf(app.packageName, app.systemCategory)
    private fun row(app: InstalledApp, selection: Set<String>) =
        AppRow(app.packageName, app.label, app.packageName in selection, categoryOf(app)?.label)

    private fun rows(
        apps: List<InstalledApp>,
        query: String,
        selection: Set<String>,
        excluded: Set<String> = emptySet(),
    ): List<AppRow> {
        val needle = query.trim()
        return apps
            .filter { it.packageName !in excluded }
            .filter { needle.isEmpty() || it.label.contains(needle, ignoreCase = true) }
            .map { AppRow(it.packageName, it.label, it.packageName in selection, categoryOf(it)?.label) }
            .sortedWith(compareByDescending<AppRow> { it.selected }.thenBy { it.label.lowercase() })
    }

    private companion object {
        const val USAGE_DAYS = 7
        const val SUGGESTION_LIMIT = 5
        const val USAGE_NOTE = "Suggested from your device's usage over the last 7 days."
    }
}
