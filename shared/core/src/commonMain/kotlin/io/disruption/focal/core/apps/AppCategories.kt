package io.disruption.focal.core.apps

/** An App Category that can be added to a Block List as a whole (Android). */
enum class AppCategory(val key: String, val label: String) {
    Social("social", "Social"),
    Games("games", "Games"),
    Video("video", "Video"),
    News("news", "News"),
    Shopping("shopping", "Shopping"),
    Music("music", "Music & audio"),
    Dating("dating", "Dating");

    companion object {
        fun fromKey(key: String?): AppCategory? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Works out an app's App Category on Android: Focal's curated list first, because many apps leave
 * Android's own `ApplicationInfo.category` unset or set it loosely; otherwise Android's category.
 * Apps installed after a session started are covered too, since the category is resolved whenever
 * the app comes to the foreground.
 */
class AppCategoryResolver(private val curated: Map<String, AppCategory>) {
    fun categoryOf(packageName: String, systemCategory: String?): AppCategory? =
        curated[packageName] ?: AppCategory.fromKey(systemCategory)

    /** Whether [blockedCategories] (category keys) cover the app. */
    fun isBlocked(packageName: String, systemCategory: String?, blockedCategories: Set<String>): Boolean =
        categoryOf(packageName, systemCategory)?.key?.let { it in blockedCategories } ?: false
}
