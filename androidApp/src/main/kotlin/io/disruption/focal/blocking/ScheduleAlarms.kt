package io.disruption.focal.blocking

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.disruption.focal.FocalApplication
import kotlin.time.Instant

/**
 * The one alarm that wakes Focal for the next Schedule start or session end, so Schedules run with
 * Focal closed (T30, docs/android-blocking-engine.md §3). When to wake is `ScheduleService.nextWakeUp`
 * in `shared/core`; this only sets or cancels the alarm.
 *
 * Exact alarms (`setExactAndAllowWhileIdle`) need `SCHEDULE_EXACT_ALARM`, which the user can deny
 * or revoke on Android 12+. Without it the alarm is inexact (`setAndAllowWhileIdle`): Doze may
 * delay it, so a Schedule can start late, but the start is caught up for what is left of its window.
 */
class ScheduleAlarms(private val context: Context) {
    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)

    /** Wake Focal at [at], replacing the previous alarm; null cancels it. */
    fun arm(at: Instant?) {
        val wakeUp = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ScheduleAlarmReceiver::class.java).setAction(ACTION_WAKE_UP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (at == null) {
            alarms.cancel(wakeUp)
            return
        }
        val millis = at.toEpochMilliseconds()
        val exact = canScheduleExactAlarms() &&
            runCatching { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, wakeUp) }.isSuccess
        if (!exact) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, wakeUp)
    }

    private fun canScheduleExactAlarms(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    companion object {
        const val ACTION_WAKE_UP = "io.disruption.focal.action.SCHEDULE_WAKE_UP"
    }
}

/**
 * Catches Schedules up and re-arms the alarm when it fires, when the clock or time zone changes
 * (wall-clock Schedule times move), and when the user grants exact alarms (T30). Boot and app
 * updates go through `BootReceiver`, which does the same as part of restoring sessions.
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ScheduleAlarms.ACTION_WAKE_UP,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> {
                val catchUp = (context.applicationContext as? FocalApplication)?.catchUpSchedules() ?: return
                val pending = goAsync()
                catchUp.invokeOnCompletion { pending.finish() }
            }
        }
    }
}
