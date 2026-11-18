package io.disruption.focal.ui.help

import io.disruption.focal.core.permissions.Permission
import io.disruption.focal.testing.FakePermissionGateway
import io.disruption.focal.testing.FakeSuggestedWebsites
import io.disruption.focal.ui.MainDispatcherTest
import io.disruption.focal.ui.SessionUiFixture
import io.disruption.focal.ui.onboarding.OnboardingIntent
import io.disruption.focal.ui.onboarding.OnboardingStep
import io.disruption.focal.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeDeviceHelp(override val oem: Oem?) : DeviceHelp {
    val opened = mutableListOf<HelpLink>()
    override fun open(link: HelpLink) {
        opened += link
    }
}

class BatteryHelpTest : MainDispatcherTest() {
    private val f = SessionUiFixture()
    private val allGranted = FakePermissionGateway(granted = Permission.entries.toSet())

    @Test
    fun eachKnownOemGetsItsOwnStepsWithDeepLinks() {
        assertEquals(HelpLink.Autostart, BatteryGuidance.steps(Oem.Xiaomi).first().link)
        assertEquals(HelpLink.OemBattery, BatteryGuidance.steps(Oem.Samsung).first().link)
        assertEquals(HelpLink.Autostart, BatteryGuidance.steps(Oem.Huawei).first().link)
        assertEquals(HelpLink.BatteryOptimization, BatteryGuidance.steps(Oem.OnePlus).first().link)
        assertEquals(1, BatteryGuidance.steps(Oem.OtherAndroid).size)
        assertTrue(BatteryGuidance.steps(null).single().link == null, "iOS has nothing to open")
    }

    @Test
    fun theHelpScreenOpensTheLinkedSettings() {
        val device = FakeDeviceHelp(Oem.Xiaomi)
        val vm = BatteryHelpViewModel(device)
        assertEquals("Xiaomi", vm.state.value.deviceName)

        vm.onIntent(BatteryHelpIntent.LinkClicked(HelpLink.Autostart))

        assertEquals(listOf(HelpLink.Autostart), device.opened)
    }

    @Test
    fun onboardingShowsBatteryStepsOnlyOnKnownOems() = runTest {
        for ((oem, expected) in listOf(Oem.Samsung to true, Oem.OtherAndroid to false, null to false)) {
            val vm = OnboardingViewModel(allGranted, f.blockLists, f.preferences, FakeSuggestedWebsites, FakeDeviceHelp(oem))
            vm.onIntent(OnboardingIntent.Next) // accessibility
            vm.onIntent(OnboardingIntent.Next) // notifications
            vm.onIntent(OnboardingIntent.Next)

            assertEquals(expected, vm.state.value.step == OnboardingStep.BatteryHelp, "$oem")
            if (expected) {
                vm.onIntent(OnboardingIntent.Next)
                assertEquals(OnboardingStep.FirstBlockList, vm.state.value.step)
            }
        }
    }
}
