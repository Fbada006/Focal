package io.disruption.focal.ui.help

/** Phone makers known to stop background apps even with a foreground service (stories 7, 8). */
enum class Oem(val label: String) {
    Xiaomi("Xiaomi"),
    Samsung("Samsung"),
    Huawei("Huawei"),
    OnePlus("OnePlus"),
    OtherAndroid("Android"),
}

/** Settings screens a help step can open. Each falls back to Focal's app settings if missing. */
enum class HelpLink {
    BatteryOptimization,
    AppDetails,
    Autostart,
    OemBattery,
}

/** What the platform knows about the device and which settings screens it can open. */
interface DeviceHelp {
    /** The phone maker on Android, or null on iOS. */
    val oem: Oem?
    fun open(link: HelpLink)
}

data class HelpStep(val text: String, val link: HelpLink? = null, val linkLabel: String? = null)

/** Device-specific steps to keep Focal running (stories 7, 8). */
object BatteryGuidance {
    fun needsOnboardingStep(oem: Oem?): Boolean = oem != null && oem != Oem.OtherAndroid

    fun steps(oem: Oem?): List<HelpStep> = when (oem) {
        null -> listOf(
            HelpStep(
                "On iPhone, Screen Time keeps your sessions blocking even when Focal is closed. If blocking " +
                    "stopped, check that Screen Time access for Focal is still on in Settings › Screen Time.",
            ),
        )
        Oem.Xiaomi -> listOf(
            HelpStep("Turn on Autostart for Focal.", HelpLink.Autostart, "Open Autostart"),
            HelpStep("In Battery saver, set Focal to \"No restrictions\".", HelpLink.AppDetails, "Open Focal's settings"),
            HelpStep("Open Recent apps, press and hold Focal, and tap the lock so it isn't cleared."),
        )
        Oem.Samsung -> listOf(
            HelpStep(
                "Make sure Focal isn't in \"Sleeping apps\" or \"Deep sleeping apps\" (Battery › Background usage limits).",
                HelpLink.OemBattery,
                "Open battery settings",
            ),
            HelpStep("Set Focal's battery usage to \"Unrestricted\".", HelpLink.AppDetails, "Open Focal's settings"),
        )
        Oem.Huawei -> listOf(
            HelpStep(
                "In App launch, turn off \"Manage automatically\" for Focal and allow auto-launch, secondary launch " +
                    "and running in the background.",
                HelpLink.Autostart,
                "Open App launch",
            ),
            HelpStep("Allow Focal to ignore battery optimisation.", HelpLink.BatteryOptimization, "Open battery optimisation"),
        )
        Oem.OnePlus -> listOf(
            HelpStep("Set Focal to \"Don't optimise\" in Battery optimisation.", HelpLink.BatteryOptimization, "Open battery optimisation"),
            HelpStep("Allow Focal to auto-launch.", HelpLink.Autostart, "Open auto-launch"),
            HelpStep("Open Recent apps and lock Focal so it isn't cleared."),
        )
        Oem.OtherAndroid -> listOf(
            HelpStep(
                "Set Focal's battery usage to \"Unrestricted\" or \"Not optimised\".",
                HelpLink.BatteryOptimization,
                "Open battery optimisation",
            ),
        )
    }
}
