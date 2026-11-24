package io.disruption.focal.ui.apps

import io.disruption.focal.core.apps.AppCategoryResolver
import io.disruption.focal.core.apps.AppUsage
import io.disruption.focal.core.apps.AppUsageSource
import io.disruption.focal.core.apps.InstalledApp
import io.disruption.focal.core.apps.InstalledAppsSource
import io.disruption.focal.ui.MainDispatcherTest
import io.disruption.focal.ui.SessionUiFixture
import io.disruption.focal.ui.social
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.session.SessionLength
import io.disruption.focal.testing.FakeSuggestedWebsites
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.hours

class AppPickerViewModelTest : MainDispatcherTest() {
    private val f = SessionUiFixture()
    private val installed = InstalledAppsSource {
        listOf(
            InstalledApp("com.instagram.android", "Instagram"),
            InstalledApp("com.zhiliaoapp.musically", "TikTok"),
            InstalledApp("com.android.chrome", "Chrome"),
        )
    }

    private fun vm(usage: AppUsageSource = FakeAppUsage()) =
        AppPickerViewModel(social.id.value, f.blockLists, installed, FakeSuggestedWebsites, AppCategoryResolver(emptyMap()), usage)

    @Test
    fun listsInstalledAppsWithSelectedOnesFirst() = runTest {
        val apps = vm().state.value.apps
        assertEquals(listOf("Instagram", "Chrome", "TikTok"), apps.map { it.label })
        assertEquals(listOf(true, false, false), apps.map { it.selected })
    }

    @Test
    fun ranksMostUsedAppsFirstWithAUsageNote() = runTest {
        val usage = FakeAppUsage(
            granted = true,
            used = listOf(
                AppUsage("com.zhiliaoapp.musically", 2.hours),
                AppUsage("com.instagram.android", 1.hours),
            ),
        )

        val state = vm(usage).state.value

        assertEquals(listOf("TikTok", "Instagram"), state.suggestions.map { it.label })
        assertEquals("Suggested from your device's usage over the last 7 days.", state.usageNote)
        assertEquals(listOf("Chrome"), state.apps.map { it.label })
    }

    @Test
    fun usageOfAppsThatAreNotListedDoesNotCrowdOutSuggestions() = runTest {
        // The system reports every package: System UI, the keyboard, Focal itself while picking.
        val unlisted = listOf(
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
            "io.disruption.focal",
            "com.google.android.apps.nexuslauncher",
            "com.android.phone",
        ).map { AppUsage(it, 5.hours) }
        val usage = FakeAppUsage(
            granted = true,
            used = unlisted + AppUsage("com.zhiliaoapp.musically", 2.hours) + AppUsage("com.instagram.android", 1.hours),
        )

        val state = vm(usage).state.value

        assertEquals(listOf("TikTok", "Instagram"), state.suggestions.map { it.label })
    }

    @Test
    fun withoutUsageAccessKeepsTheAlphabeticalListAndOffersAccess() = runTest {
        val usage = FakeAppUsage(granted = false)

        val vm = vm(usage)
        assertEquals(emptyList(), vm.state.value.suggestions)
        assertEquals(null, vm.state.value.usageNote)
        assertEquals(listOf("Instagram", "Chrome", "TikTok"), vm.state.value.apps.map { it.label })

        vm.onIntent(AppPickerIntent.UsageAccessRequested)
        assertEquals(1, usage.requests)
    }

    @Test
    fun checkingUsageAccessAgainLoadsTheRanking() = runTest {
        val usage = FakeAppUsage(granted = false)
        val vm = vm(usage)

        usage.granted = true
        usage.used = listOf(AppUsage("com.zhiliaoapp.musically", 1.hours))
        vm.onIntent(AppPickerIntent.UsageAccessChecked)

        assertEquals(listOf("TikTok"), vm.state.value.suggestions.map { it.label })
    }

    @Test
    fun searchingShowsEveryMatchingAppIncludingSuggestions() = runTest {
        val usage = FakeAppUsage(granted = true, used = listOf(AppUsage("com.zhiliaoapp.musically", 1.hours)))
        val vm = vm(usage)

        vm.onIntent(AppPickerIntent.QueryChanged("tik"))

        assertEquals(emptyList(), vm.state.value.suggestions)
        assertEquals(listOf("TikTok"), vm.state.value.apps.map { it.label })
    }

    @Test
    fun searchesByAppName() = runTest {
        val vm = vm()
        vm.onIntent(AppPickerIntent.QueryChanged("tik"))
        assertEquals(listOf("TikTok"), vm.state.value.apps.map { it.label })
    }

    @Test
    fun togglingSavesTheSelectionOnTheBlockList() = runTest {
        val vm = vm()
        vm.onIntent(AppPickerIntent.Toggled("com.zhiliaoapp.musically"))
        vm.onIntent(AppPickerIntent.Toggled("com.instagram.android"))

        assertEquals(setOf("com.zhiliaoapp.musically"), f.blockListRepository.get(social.id)!!.blocked.apps)
        assertEquals(1, vm.state.value.selectedCount)
    }

    @Test
    fun unpickingAnAppARunningDelayedSessionUsesSaysTheRemovalIsDeferred() = runTest {
        f.sessions.start(social.id, SessionLength.For(1.hours), Strictness.Delayed)
        val vm = vm()

        vm.onIntent(AppPickerIntent.Toggled("com.instagram.android"))

        assertNotNull(vm.state.value.notice)
    }

    @Test
    fun unpickingACategoryARunningDelayedSessionUsesSaysTheRemovalIsDeferred() = runTest {
        val vm = vm()
        vm.onIntent(AppPickerIntent.CategoryToggled("games"))
        f.sessions.start(social.id, SessionLength.For(1.hours), Strictness.Delayed)

        vm.onIntent(AppPickerIntent.CategoryToggled("games"))

        assertNotNull(vm.state.value.notice)
    }
}

class AppPickerSuggestionsTest : MainDispatcherTest() {
    private val f = SessionUiFixture()
    private val installed = InstalledAppsSource {
        listOf(InstalledApp("com.google.android.youtube", "YouTube"), InstalledApp("com.android.chrome", "Chrome"))
    }

    @Test
    fun pickingAnAppPreselectsItsSuggestedWebsites() = runTest {
        val vm = AppPickerViewModel(social.id.value, f.blockLists, installed, FakeSuggestedWebsites, AppCategoryResolver(emptyMap()), FakeAppUsage())
        vm.onIntent(AppPickerIntent.Toggled("com.google.android.youtube"))
        vm.onIntent(AppPickerIntent.Toggled("com.android.chrome"))

        assertEquals(setOf("youtube.com", "youtu.be"), f.blockListRepository.get(social.id)!!.blocked.domains)
    }

    @Test
    fun unpickingAnAppKeepsItsWebsites() = runTest {
        val vm = AppPickerViewModel(social.id.value, f.blockLists, installed, FakeSuggestedWebsites, AppCategoryResolver(emptyMap()), FakeAppUsage())
        vm.onIntent(AppPickerIntent.Toggled("com.google.android.youtube"))
        vm.onIntent(AppPickerIntent.Toggled("com.google.android.youtube"))

        assertEquals(setOf("youtube.com", "youtu.be"), f.blockListRepository.get(social.id)!!.blocked.domains)
    }
}

class AppCategoriesPickerTest : MainDispatcherTest() {
    private val f = SessionUiFixture()
    private val installed = InstalledAppsSource {
        listOf(
            InstalledApp("com.instagram.android", "Instagram"),
            InstalledApp("com.example.game", "Some Game", systemCategory = "games"),
            InstalledApp("com.example.notes", "Notes"),
        )
    }
    private val resolver = AppCategoryResolver(mapOf("com.instagram.android" to io.disruption.focal.core.apps.AppCategory.Social))

    @Test
    fun showsCategoriesWithInstalledCountsAndSavesPickedOnes() = runTest {
        val vm = AppPickerViewModel(social.id.value, f.blockLists, installed, FakeSuggestedWebsites, resolver, FakeAppUsage())
        val games = vm.state.value.categories.single { it.key == "games" }
        assertEquals(1, games.installedCount)
        assertEquals("Social", vm.state.value.apps.single { it.label == "Instagram" }.category)

        vm.onIntent(AppPickerIntent.CategoryToggled("games"))
        vm.onIntent(AppPickerIntent.CategoryToggled("social"))
        vm.onIntent(AppPickerIntent.CategoryToggled("social"))

        assertEquals(setOf("games"), f.blockListRepository.get(social.id)!!.blocked.appCategories)
        assertEquals(true, vm.state.value.categories.single { it.key == "games" }.selected)
    }
}

/** Stand-in for Android's usage stats: tests choose the access state and ranking. */
private class FakeAppUsage(
    var granted: Boolean = false,
    var used: List<AppUsage> = emptyList(),
) : AppUsageSource {
    var requests = 0

    override suspend fun isGranted(): Boolean = granted

    override fun requestAccess() {
        requests++
    }

    override suspend fun mostUsed(days: Int): List<AppUsage> = used
}
