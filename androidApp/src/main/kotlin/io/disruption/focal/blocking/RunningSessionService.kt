package io.disruption.focal.blocking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.disruption.focal.MainActivity
import io.disruption.focal.R
import io.disruption.focal.core.blocking.BlockingEngine
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.session.FocusSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.context.GlobalContext
import android.os.Build
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Keeps Focal alive while any Focus Session runs and shows the ongoing timer notification (T13,
 * docs/android-blocking-engine.md §3). It also completes sessions whose planned end passes while
 * the UI is closed, which lifts their blocking.
 */
class RunningSessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engine: AndroidBlockingEngine? = null
    private var notifications: NotificationManager? = null
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NotificationManager::class.java)
        notifications?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Running Focus Sessions", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the time left in running Focus Sessions."
                setShowBadge(false)
            },
        )
        engine = runCatching {
            GlobalContext.get().get<BlockingEngine>() as AndroidBlockingEngine
        }.getOrNull()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = engine ?: return START_NOT_STICKY
        startForeground(NOTIFICATION_ID, buildNotification(engine.running.value))
        job?.cancel()
        job = scope.launch {
            engine.running.collectLatest { sessions ->
                if (sessions.isEmpty()) {
                    stopSelf()
                    return@collectLatest
                }
                notifications?.notify(NOTIFICATION_ID, buildNotification(sessions))
                // Wake up when the next session ends, and re-plan if the set changes meanwhile.
                val wait = (sessions.first().plannedEnd - Clock.System.now()).coerceAtLeast(1.seconds)
                delay(wait)
                runCatching { sessionService().completeDueSessions() }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun sessionService(): FocusSessionService = GlobalContext.get().get()

    private fun buildNotification(sessions: List<FocusSession>): Notification {
        val holding = sessions.minByOrNull { it.plannedEnd }
        val others = sessions.size - 1
        val title = when {
            holding == null -> "Focus Session"
            others > 0 -> "${sessions.size} Focus Sessions"
            else -> holding.name
        }
        val text = when {
            holding == null -> "Blocking until the session ends"
            others > 0 -> "Blocking until ${clockTime(holding.plannedEnd)} (+$others more)"
            else -> "Blocking until ${clockTime(holding.plannedEnd)}"
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_session)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setShowWhen(holding != null)
            .setWhen(holding?.plannedEnd?.toEpochMilliseconds() ?: Clock.System.now().toEpochMilliseconds())
            .setUsesChronometer(holding != null)
            .apply {
                if (Build.VERSION.SDK_INT >= 24) setChronometerCountDown(true)
            }
            .build()
    }

    private fun clockTime(instant: Instant): String {
        val time = instant.toLocalDateTime(TimeZone.currentSystemDefault()).time
        return "%02d:%02d".format(time.hour, time.minute)
    }

    companion object {
        private const val CHANNEL_ID = "running_sessions"
        private const val NOTIFICATION_ID = 4102
    }
}
