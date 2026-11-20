package io.disruption.focal.blocking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.disruption.focal.MainActivity
import io.disruption.focal.R
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.ui.permissions.brokenWarning

/**
 * Warns the user that revoking a permission blocking depends on made their Locked sessions Broken
 * (story 41, T22). Tapping it opens Home, which shows how to turn the permission back on. If
 * notifications are off too, Home's own warning is all the user gets.
 */
object BrokenSessionNotifier {
    private const val CHANNEL_ID = "broken_sessions"
    private const val NOTIFICATION_ID = 4103

    fun notify(context: Context, broken: List<FocusSession>) {
        val notifications = context.getSystemService(NotificationManager::class.java) ?: return
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Broken sessions", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tells you when a Locked session is Broken because Focal's blocking was switched off."
            },
        )
        val text = brokenWarning(broken.map { it.name })
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_session)
            .setContentTitle("Locked session Broken")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { notifications.notify(NOTIFICATION_ID, notification) }
    }
}
