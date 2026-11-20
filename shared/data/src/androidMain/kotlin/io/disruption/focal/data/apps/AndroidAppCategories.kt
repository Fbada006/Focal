package io.disruption.focal.data.apps

import android.content.pm.ApplicationInfo
import android.os.Build

/**
 * Maps Android's own [ApplicationInfo.category] to Focal's App Category keys, so both the Apps tab
 * and the blocking engine resolve categories the same way (T13). Focal's curated list still wins;
 * see `AppCategoryResolver`.
 */
object AndroidAppCategories {
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
