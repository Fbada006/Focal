package io.disruption.focal.core.apps

/** A launchable app installed on an Android device, identified by package name. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    /** Android's own `ApplicationInfo.category` as a key such as "social", or null if unset. */
    val systemCategory: String? = null,
)

/** The launchable apps on the device. Android only: iOS uses Apple's picker instead. */
fun interface InstalledAppsSource {
    suspend fun launchableApps(): List<InstalledApp>
}
