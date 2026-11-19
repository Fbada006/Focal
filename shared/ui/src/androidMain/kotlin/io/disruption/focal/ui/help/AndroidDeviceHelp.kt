package io.disruption.focal.ui.help

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Opens battery and autostart settings, using OEM screens where they exist. OEMs move these
 * screens between versions, so each candidate is tried in turn and Focal's app settings is the
 * fallback. Candidates are launched rather than resolved first: under Android 11+ package
 * visibility, OEM packages without a launcher activity never resolve, so the check would always
 * fall back. Focal never requests `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (Play policy); it only
 * opens the settings list.
 */
class AndroidDeviceHelp(private val context: Context) : DeviceHelp {
    override val oem: Oem = detectOem(Build.MANUFACTURER)

    override fun open(link: HelpLink) {
        val candidates = when (link) {
            HelpLink.BatteryOptimization -> listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            HelpLink.AppDetails -> emptyList()
            HelpLink.Autostart -> autostartScreens(oem)
            HelpLink.OemBattery -> oemBatteryScreens(oem)
        }
        for (candidate in candidates + appDetails()) {
            if (tryStart(candidate)) return
        }
    }

    /** False when the screen doesn't exist on this device, or isn't exported to other apps. */
    private fun tryStart(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun appDetails() =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    private fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))

    private fun autostartScreens(oem: Oem): List<Intent> = when (oem) {
        Oem.Xiaomi -> listOf(component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
        Oem.Huawei -> listOf(
            component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        )
        Oem.OnePlus -> listOf(
            // OxygenOS 12+ is built on ColorOS, whose startup manager moved to (o)plus safecenter.
            component("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
            component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        )
        else -> emptyList()
    }

    private fun oemBatteryScreens(oem: Oem): List<Intent> = when (oem) {
        Oem.Samsung -> listOf(
            component("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            component("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        )
        else -> listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    companion object {
        fun detectOem(manufacturer: String): Oem = when (manufacturer.lowercase()) {
            "xiaomi", "redmi", "poco" -> Oem.Xiaomi
            "samsung" -> Oem.Samsung
            "huawei", "honor" -> Oem.Huawei
            "oneplus" -> Oem.OnePlus
            else -> Oem.OtherAndroid
        }
    }
}
