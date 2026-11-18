package io.disruption.focal.ui.apps

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

    private fun vm() = AppPickerViewModel(social.id.value, f.blockLists, installed, FakeSuggestedWebsites)

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
}

class AppPickerSuggestionsTest : MainDispatcherTest() {
    private val f = SessionUiFixture()
    private val installed = InstalledAppsSource {
        listOf(InstalledApp("com.google.android.youtube", "YouTube"), InstalledApp("com.android.chrome", "Chrome"))
    }

    @Test
    fun pickingAnAppPreselectsItsSuggestedWebsites() = runTest {
        val vm = AppPickerViewModel(social.id.value, f.blockLists, installed, FakeSuggestedWebsites)
        vm.onIntent(AppPickerIntent.Toggled("com.google.android.youtube"))
        vm.onIntent(AppPickerIntent.Toggled("com.android.chrome"))

        assertEquals(setOf("youtube.com", "youtu.be"), f.blockListRepository.get(social.id)!!.blocked.domains)
    }

    @Test
    fun unpickingAnAppKeepsItsWebsites() = runTest {
        val vm = AppPickerViewModel(social.id.value, f.blockLists, installed, FakeSuggestedWebsites)
        vm.onIntent(AppPickerIntent.Toggled("com.google.android.youtube"))
        vm.onIntent(AppPickerIntent.Toggled("com.google.android.youtube"))

        assertEquals(setOf("youtube.com", "youtu.be"), f.blockListRepository.get(social.id)!!.blocked.domains)
    }
}
