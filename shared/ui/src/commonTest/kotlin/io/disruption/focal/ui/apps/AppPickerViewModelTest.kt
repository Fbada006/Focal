package io.disruption.focal.ui.apps

import io.disruption.focal.core.apps.InstalledApp
import io.disruption.focal.core.apps.InstalledAppsSource
import io.disruption.focal.ui.MainDispatcherTest
import io.disruption.focal.ui.SessionUiFixture
import io.disruption.focal.ui.social
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppPickerViewModelTest : MainDispatcherTest() {
    private val f = SessionUiFixture()
    private val installed = InstalledAppsSource {
        listOf(
            InstalledApp("com.instagram.android", "Instagram"),
            InstalledApp("com.zhiliaoapp.musically", "TikTok"),
            InstalledApp("com.android.chrome", "Chrome"),
        )
    }

    private fun vm() = AppPickerViewModel(social.id.value, f.blockLists, installed)

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
}
