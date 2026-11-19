package io.disruption.focal.ui.apps

import io.disruption.focal.core.apps.AppCategoryResolver
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

    private fun vm() = AppPickerViewModel(social.id.value, f.blockLists, installed, FakeSuggestedWebsites, AppCategoryResolver(emptyMap()))

    @Test
    fun listsInstalledAppsWithSelectedOnesFirst() = runTest {
        val apps = vm().state.value.apps
        assertEquals(listOf("Instagram", "Chrome", "TikTok"), apps.map { it.label })
        assertEquals(listOf(true, false, false), apps.map { it.selected })
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
        val vm = AppPickerViewModel(social.id.value, f.blockLists, installed, FakeSuggestedWebsites, AppCategoryResolver(emptyMap()))
        vm.onIntent(AppPickerIntent.Toggled("com.google.android.youtube"))
        vm.onIntent(AppPickerIntent.Toggled("com.android.chrome"))

        assertEquals(setOf("youtube.com", "youtu.be"), f.blockListRepository.get(social.id)!!.blocked.domains)
    }

    @Test
    fun unpickingAnAppKeepsItsWebsites() = runTest {
        val vm = AppPickerViewModel(social.id.value, f.blockLists, installed, FakeSuggestedWebsites, AppCategoryResolver(emptyMap()))
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
        val vm = AppPickerViewModel(social.id.value, f.blockLists, installed, FakeSuggestedWebsites, resolver)
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
