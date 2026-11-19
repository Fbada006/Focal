package io.disruption.focal.ui.onboarding

import io.disruption.focal.core.permissions.Permission
import io.disruption.focal.testing.FakePermissionGateway
import io.disruption.focal.testing.FakeSuggestedWebsites
import io.disruption.focal.ui.MainDispatcherTest
import io.disruption.focal.ui.SessionUiFixture
import io.disruption.focal.ui.help.FakeDeviceHelp
import io.disruption.focal.ui.home.HomeEffect
import io.disruption.focal.ui.home.HomeIntent
import io.disruption.focal.ui.home.HomeViewModel
import io.disruption.focal.ui.home.HomeState
import io.disruption.focal.ui.start.StartSessionIntent
import io.disruption.focal.ui.start.StartSessionState
import io.disruption.focal.ui.start.StartSessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingAndGatingTest : MainDispatcherTest() {
    private val f = SessionUiFixture()
    private val permissions = FakePermissionGateway()
    private val ticks = MutableSharedFlow<Unit>()

    @Test
    fun onboardingExplainsThenAsksForEachPermissionAndCreatesTheFirstBlockList() = runTest {
        val vm = OnboardingViewModel(permissions, f.blockLists, f.preferences, FakeSuggestedWebsites, FakeDeviceHelp(oem = null))
        assertEquals(OnboardingStep.Intro, vm.state.value.step)
        assertTrue(permissions.requested.isEmpty(), "nothing is asked before the intro")

        vm.onIntent(OnboardingIntent.Next)
        assertEquals(OnboardingStep.Grant(Permission.AccessibilityService, granted = false), vm.state.value.step)
        vm.onIntent(OnboardingIntent.GrantClicked)
        assertEquals(listOf(Permission.AccessibilityService), permissions.requested)

        permissions.granted += Permission.AccessibilityService
        vm.onIntent(OnboardingIntent.Resumed)
        assertEquals(OnboardingStep.Grant(Permission.AccessibilityService, granted = true), vm.state.value.step)

        vm.onIntent(OnboardingIntent.Next)
        assertEquals(OnboardingStep.Grant(Permission.Notifications, granted = false), vm.state.value.step)
        vm.onIntent(OnboardingIntent.Next) // "Not now"
        assertEquals(OnboardingStep.FirstBlockList, vm.state.value.step)

        vm.onIntent(OnboardingIntent.BlockListNameChanged("Phone"))
        vm.onIntent(OnboardingIntent.SuggestionToggled("instagram"))
        vm.onIntent(OnboardingIntent.CreateBlockListClicked)

        assertEquals(OnboardingEffect.StartFirstSession, vm.effects.first())
        val created = f.blockListRepository.observeAll().first().single { it.name == "Phone" }
        assertEquals(setOf("youtube.com", "youtu.be"), created.blocked.domains, "YouTube is picked by default, Instagram was unticked")
        assertTrue(f.preferences.onboardingCompleted.first())
    }

    @Test
    fun homeShowsMissingPermissionsAndWontStartUntilFixed() = runTest {
        val vm = HomeViewModel(f.sessions, f.clock, permissions, ticks)
        assertEquals(listOf(Permission.AccessibilityService, Permission.Notifications), vm.state.value.missingPermissions)
        assertFalse(vm.state.value.canStart)

        vm.onIntent(HomeIntent.FixPermissionClicked(Permission.AccessibilityService))
        assertEquals(listOf(Permission.AccessibilityService), permissions.requested)

        permissions.granted += setOf(Permission.AccessibilityService, Permission.Notifications)
        vm.onIntent(HomeIntent.Resumed)
        assertTrue(vm.state.value.canStart)
        vm.onIntent(HomeIntent.StartClicked)
        assertEquals(HomeEffect.OpenStart, vm.effects.first())
    }

    @Test
    fun theStartScreenIsDisabledWhileAPermissionIsMissing() = runTest {
        val vm = StartSessionViewModel(f.blockLists, f.sessions, f.clock, f.zone, permissions)
        assertTrue(vm.state.value.permissionMissing)
        assertFalse(vm.state.value.canStart)
    }

    @Test
    fun theStartScreenChecksAgainWhenFocalComesBack() = runTest {
        val vm = StartSessionViewModel(f.blockLists, f.sessions, f.clock, f.zone, permissions)
        vm.onIntent(StartSessionIntent.FixPermissionClicked(Permission.Notifications))
        assertEquals(listOf(Permission.Notifications), permissions.requested)

        permissions.granted += setOf(Permission.AccessibilityService, Permission.Notifications)
        vm.onIntent(StartSessionIntent.Resumed)

        assertFalse(vm.state.value.permissionMissing)
        assertTrue(vm.state.value.canStart, "the fixture's Social list is preselected")
    }

    @Test
    fun startIsDisabledUntilPermissionsHaveBeenChecked() {
        assertFalse(HomeState(missingPermissions = emptyList()).canStart)
        assertFalse(StartSessionState().canStart)
    }

    @Test
    fun theFirstBlockListMustBlockSomething() = runTest {
        val vm = OnboardingViewModel(permissions, f.blockLists, f.preferences, FakeSuggestedWebsites, FakeDeviceHelp(oem = null))
        vm.state.value.suggestions.filter { it.selected }.forEach { vm.onIntent(OnboardingIntent.SuggestionToggled(it.id)) }
        val before = f.blockListRepository.observeAll().first().size

        vm.onIntent(OnboardingIntent.CreateBlockListClicked)

        assertEquals(before, f.blockListRepository.observeAll().first().size)
        assertTrue(vm.state.value.error != null)
    }

    @Test
    fun aDoubleTapOnCreateMakesOneBlockList() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = OnboardingViewModel(permissions, f.blockLists, f.preferences, FakeSuggestedWebsites, FakeDeviceHelp(oem = null))
        val before = f.blockListRepository.observeAll().first().size

        vm.onIntent(OnboardingIntent.CreateBlockListClicked)
        vm.onIntent(OnboardingIntent.CreateBlockListClicked)
        advanceUntilIdle()

        assertEquals(before + 1, f.blockListRepository.observeAll().first().size)
    }
}
