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
     * Launchable apps used since [days] ago, most used first, at most [limit]. Empty when usage
     * access isn't granted or there is no usage to report.
     */
    suspend fun mostUsed(days: Int, limit: Int): List<AppUsage>
}
