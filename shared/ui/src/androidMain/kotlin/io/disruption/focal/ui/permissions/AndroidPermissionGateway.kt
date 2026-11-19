package io.disruption.focal.ui.permissions

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import io.disruption.focal.core.permissions.Permission
import io.disruption.focal.core.permissions.PermissionGateway

/**
 * Android permissions. Notifications use the system prompt on Android 13+ (POST_NOTIFICATIONS) and
 * fall back to the app's notification settings once the user has said no for good; the rest are
 * granted in system settings. Home, Start and onboarding read the result again when Focal comes
 * back to the foreground.
 */
class AndroidPermissionGateway(private val context: Context) : PermissionGateway {
    private val prefs = context.getSharedPreferences("focal_permissions", Context.MODE_PRIVATE)

    /** The Activity in the foreground, for the runtime prompt. */
    private var resumed: Activity? = null

    init {
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) { resumed = activity }
                override fun onActivityPaused(activity: Activity) { if (resumed === activity) resumed = null }
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    /**
     * The accessibility service is required once Focal declares it (the Android engine, T13).
     * Requiring it before then would disable starting forever: there is nothing to switch on.
     */
    override val requiredForSessions: List<Permission>
        get() = buildList {
            if (isAccessibilityServiceDeclared()) add(Permission.AccessibilityService)
            add(Permission.Notifications)
        }

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
            Permission.Notifications -> {
                if (promptForNotifications()) return
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            Permission.ExactAlarms -> if (Build.VERSION.SDK_INT >= 31) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            } else {
                return
            }
            Permission.ScreenTime -> return
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Show the Android 13+ notification prompt if it can still appear: never asked, or denied once
     * (the system then offers it again). After a second denial only settings can grant it.
     */
    private fun promptForNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        val activity = resumed ?: return false
        val askedBefore = prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)
        if (askedBefore && !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) return false
        prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
        activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        return true
    }

    private fun isAccessibilityServiceDeclared(): Boolean = try {
        context.packageManager.getServiceInfo(ComponentName(context.packageName, ACCESSIBILITY_SERVICE_CLASS), 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
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

        private const val KEY_ASKED_NOTIFICATIONS = "asked_post_notifications"
        private const val REQUEST_NOTIFICATIONS = 4101
    }
}
