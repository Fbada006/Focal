package io.disruption.focal.ui.apps

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import io.disruption.focal.core.apps.AppUsage
import io.disruption.focal.core.apps.AppUsageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Android's own usage data (`UsageStatsManager`), used only to rank the Apps tab's suggestions
 * (story 16a). Reading it needs the optional `PACKAGE_USAGE_STATS` app-op, granted in Settings >
 * Usage access; without it this reports nothing and the tab stays alphabetical.
 */
class AndroidAppUsage(private val context: Context) : AppUsageSource {
    @Suppress("DEPRECATION")
    override suspend fun isGranted(): Boolean = withContext(Dispatchers.IO) {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return@withContext false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    }

    override fun requestAccess() {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override suspend fun mostUsed(days: Int, limit: Int): List<AppUsage> = withContext(Dispatchers.IO) {
        if (!isGranted()) return@withContext emptyList()
        val manager = context.getSystemService(android.app.usage.UsageStatsManager::class.java)
            ?: return@withContext emptyList()
        val end = System.currentTimeMillis()
        val begin = end - days.toLong() * DAY_MILLIS
        val stats = runCatching { manager.queryAndAggregateUsageStats(begin, end) }.getOrNull()
            ?: return@withContext emptyList()
        stats
            .values
            .asSequence()
            .filter { it.totalTimeInForeground > 0 }
            .map { AppUsage(it.packageName, it.totalTimeInForeground.milliseconds) }
            .sortedByDescending { it.foreground }
            .take(limit)
            .toList()
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
