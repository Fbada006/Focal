package io.disruption.focal.ui.onboarding

import io.disruption.focal.core.permissions.Permission
import io.disruption.focal.testing.FakePermissionGateway
import io.disruption.focal.testing.FakeSuggestedWebsites
import io.disruption.focal.ui.MainDispatcherTest
import io.disruption.focal.ui.SessionUiFixture
import io.disruption.focal.ui.home.HomeEffect
import io.disruption.focal.ui.home.HomeIntent
import io.disruption.focal.ui.home.HomeViewModel
import io.disruption.focal.ui.start.StartSessionViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
        val vm = OnboardingViewModel(permissions, f.blockLists, f.preferences, FakeSuggestedWebsites)
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
}
