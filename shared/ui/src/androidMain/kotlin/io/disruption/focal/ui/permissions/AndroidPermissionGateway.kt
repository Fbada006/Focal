package io.disruption.focal.ui.permissions

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.disruption.focal.core.permissions.Permission
import io.disruption.focal.core.permissions.PermissionGateway

/**
 * Android permissions, granted in system settings. Each request opens the matching settings screen;
 * Home and onboarding read the result again when Focal comes back to the foreground.
 */
class AndroidPermissionGateway(private val context: Context) : PermissionGateway {
    override val requiredForSessions = listOf(Permission.AccessibilityService, Permission.Notifications)

    override suspend fun isGranted(permission: Permission): Boolean = when (permission) {
        Permission.AccessibilityService -> isAccessibilityServiceEnabled()
        Permission.Notifications -> context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        Permission.ExactAlarms ->
            Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        Permission.ScreenTime -> true
    }

    override fun request(permission: Permission) {
        val intent = when (permission) {
            Permission.AccessibilityService -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            Permission.Notifications -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            Permission.ExactAlarms -> if (Build.VERSION.SDK_INT >= 31) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            } else {
                return
            }
            Permission.ScreenTime -> return
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val component = ComponentName(context.packageName, ACCESSIBILITY_SERVICE_CLASS)
        return enabled.split(':').any { entry ->
            ComponentName.unflattenFromString(entry) == component
        }
    }

    companion object {
        /** The accessibility service the Android blocking engine (T13) must declare under this name. */
        const val ACCESSIBILITY_SERVICE_CLASS = "io.disruption.focal.blocking.FocalAccessibilityService"
    }
}
