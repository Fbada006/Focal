package io.disruption.focal.core.permissions

/** Platform permissions Focal depends on. */
enum class Permission {
    /** Android: the accessibility service that detects and blocks apps and websites. */
    AccessibilityService,

    /** iOS: Screen Time (FamilyControls) authorisation for the user's own device. */
    ScreenTime,

    /** Powers the running-session notification and, on iOS, the Shield's "Open Focal". */
    Notifications,

    /** Android: exact alarms for Schedules, asked only when the first Schedule is created. */
    ExactAlarms,
}

/**
 * What the platform can tell and do about permissions. Android implements it in `shared/ui`
 * androidMain; iOS in Swift (FamilyControls is Swift-only) and passes it to `startFocal`.
 */
interface PermissionGateway {
    /** Permissions without which a Focus Session can't block anything, in the order to ask. */
    val requiredForSessions: List<Permission>

    suspend fun isGranted(permission: Permission): Boolean

    /**
     * Ask for [permission]: show the system prompt, or open the settings screen where it is
     * granted. The result is read again with [isGranted] when Focal returns to the foreground.
     */
    fun request(permission: Permission)

    /** The permissions in [requiredForSessions] that are missing right now. */
    suspend fun missing(): List<Permission> = requiredForSessions.filterNot { isGranted(it) }
}
