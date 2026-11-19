package io.disruption.focal.ui.help

/** iOS has no OEM battery killers; Screen Time keeps blocking while Focal is closed. */
class IosDeviceHelp : DeviceHelp {
    override val oem: Oem? = null
    override fun open(link: HelpLink) = Unit
}
