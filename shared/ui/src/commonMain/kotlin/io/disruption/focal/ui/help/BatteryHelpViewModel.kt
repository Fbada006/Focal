package io.disruption.focal.ui.help

import io.disruption.focal.ui.mvi.MviViewModel

data class BatteryHelpState(val deviceName: String?, val steps: List<HelpStep>)

sealed interface BatteryHelpIntent {
    data class LinkClicked(val link: HelpLink) : BatteryHelpIntent
}

/** "Why did my session stop?" (story 8). */
class BatteryHelpViewModel(
    private val device: DeviceHelp,
) : MviViewModel<BatteryHelpState, BatteryHelpIntent, Nothing>(
    BatteryHelpState(device.oem?.label, BatteryGuidance.steps(device.oem)),
) {
    override fun onIntent(intent: BatteryHelpIntent) {
        when (intent) {
            is BatteryHelpIntent.LinkClicked -> device.open(intent.link)
        }
    }
}
