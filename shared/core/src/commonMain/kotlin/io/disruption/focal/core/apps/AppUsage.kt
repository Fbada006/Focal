package io.disruption.focal.core.apps

import kotlin.time.Duration

/** How long an app was in the foreground in the period Focal asked about. */
data class AppUsage(val packageName: String, val foreground: Duration)

/**
 * The system's own app usage, used only to rank the Apps tab's suggestions (story 16a). Android
 * implements it with `UsageStatsManager`; iOS has no equivalent because Apple's picker never
 * reveals which apps the user uses. Usage data is read on the device and never stored.
 */
interface AppUsageSource {
    /** Whether Focal may read usage stats. Android grants this in Settings > Usage access. */
    suspend fun isGranted(): Boolean

    /** Open the system screen where usage access is granted. */
    fun requestAccess()

    /**
     * Every package used since [days] ago, most used first. This includes packages the user can't
     * pick (System UI, the keyboard, Focal itself), so callers match it against the launchable
     * apps before capping it. Empty when usage access isn't granted or there is no usage to report.
     */
    suspend fun mostUsed(days: Int): List<AppUsage>
}
