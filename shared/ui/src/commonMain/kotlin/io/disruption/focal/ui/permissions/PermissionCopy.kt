package io.disruption.focal.ui.permissions

import io.disruption.focal.core.permissions.Permission

data class PermissionCopy(val title: String, val explanation: String, val action: String, val missing: String)

/** What each permission is for, in the user's words (stories 2–4, 9). */
fun Permission.copy(): PermissionCopy = when (this) {
    Permission.AccessibilityService -> PermissionCopy(
        title = "Let Focal see which app is open",
        explanation = "Focal uses Android's accessibility service to notice when you open an app or website on your " +
            "Block List and show the Shield instead. It reads which app is in front and, in supported browsers, " +
            "the address bar. It doesn't read your messages, passwords, or anything you type, and nothing leaves your phone.",
        action = "Open accessibility settings",
        missing = "Focal can't block apps: its accessibility service is off.",
    )
    Permission.ScreenTime -> PermissionCopy(
        title = "Allow Screen Time access",
        explanation = "Focal uses Apple's Screen Time to block apps and websites on this iPhone. Apple keeps your " +
            "choices private: Focal only sees opaque tokens, and nothing leaves your device.",
        action = "Allow Screen Time",
        missing = "Focal can't block apps: Screen Time access is off.",
    )
    Permission.Notifications -> PermissionCopy(
        title = "Allow notifications",
        explanation = "Focal shows a notification with the time left while a Focus Session runs, and uses one to " +
            "take you back to Focal from the Shield.",
        action = "Allow notifications",
        missing = "Notifications are off, so Focal can't show the running session.",
    )
    Permission.ExactAlarms -> PermissionCopy(
        title = "Allow alarms for Schedules",
        explanation = "Schedules start your Focus Sessions on time, even when Focal is closed. Android needs the " +
            "\"Alarms & reminders\" permission for that.",
        action = "Open alarm settings",
        missing = "Schedules may start late: alarms are off.",
    )
}

/**
 * The warning shown when revoking a permission blocking depends on made running Locked sessions
 * Broken (story 41, ADR 0002), on Home and in Android's notification.
 */
fun brokenWarning(sessionNames: List<String>): String {
    val which = when (sessionNames.size) {
        1 -> "Your Locked session \"${sessionNames.single()}\" was"
        else -> "${sessionNames.size} Locked sessions were"
    }
    return "$which recorded as Broken: Focal's blocking was switched off. Turn it back on so Focal can block again."
}
