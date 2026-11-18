package io.disruption.focal.ui.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import io.disruption.focal.core.apps.InstalledApp
import io.disruption.focal.core.apps.InstalledAppsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Launchable apps, found through the launcher intent. The app's manifest declares a `<queries>`
 * entry for `MAIN`/`LAUNCHER`, which is enough to see every app a user could open, so Focal does
 * not need `QUERY_ALL_PACKAGES` (see docs/android-blocking-engine.md).
 */
class AndroidInstalledApps(private val context: Context) : InstalledAppsSource {
    override suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcher, 0)
        }
        activities
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    systemCategory = systemCategoryKey(info),
                )
            }
    }

    companion object {
        /** Android's own category as an [io.disruption.focal.core.apps.AppCategory] key, if it maps to one. */
        fun systemCategoryKey(info: ApplicationInfo): String? {
            if (Build.VERSION.SDK_INT < 26) return null
            return when (info.category) {
                ApplicationInfo.CATEGORY_GAME -> "games"
                ApplicationInfo.CATEGORY_AUDIO -> "music"
                ApplicationInfo.CATEGORY_VIDEO -> "video"
                ApplicationInfo.CATEGORY_SOCIAL -> "social"
                ApplicationInfo.CATEGORY_NEWS -> "news"
                else -> null
            }
        }
    }
}
