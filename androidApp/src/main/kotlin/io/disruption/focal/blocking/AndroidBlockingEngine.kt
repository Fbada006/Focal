package io.disruption.focal.blocking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.concurrent.ConcurrentHashMap
import io.disruption.focal.core.apps.AppCategoryResolver
import io.disruption.focal.core.blocking.BlockingEngine
import io.disruption.focal.core.blocking.BlockingState
import io.disruption.focal.core.blocking.ShieldContent
import io.disruption.focal.core.model.Break
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.time.waitUntil
import io.disruption.focal.data.apps.AndroidAppCategories
import io.disruption.focal.data.curated.CuratedAppCategories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The Android [BlockingEngine]: keeps the running sessions the accessibility service checks against
 * and starts/stops the foreground service that keeps Focal alive (T13). It makes no domain
 * decisions; the union of blocked apps and websites comes from [BlockingState].
 */
class AndroidBlockingEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val categories: AppCategoryResolver = AppCategoryResolver(CuratedAppCategories.byPackage),
) : BlockingEngine {
    private val applied = MutableStateFlow<Map<FocusSessionId, FocusSession>>(emptyMap())
    private val foreground = MutableStateFlow<String?>(null)
    private val _shield = MutableStateFlow<ShieldContent?>(null)

    /**
     * Shield refreshes come from the accessibility service on Main and from session and Break-end
     * coroutines on Default. Each refresh reads the latest state, computes and publishes under this
     * lock, so an older calculation can never overwrite a newer one.
     */
    private val shieldLock = Any()

    /** Package name → App Category key (or null), cached so foreground events stay cheap. Guarded by [shieldLock]. */
    private val categoryCache = mutableMapOf<String, String?>()

    /** The pending Shield refreshes for each applied session; replaced on every apply. */
    private val refreshJobs = ConcurrentHashMap<FocusSessionId, Job>()

    /** Running sessions, soonest end first. */
    val running: StateFlow<List<FocusSession>> = applied
        .map { sessions -> sessions.values.sortedBy { it.plannedEnd } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Whether any session is applied right now; [running] follows a moment later. */
    val hasSessions: Boolean get() = applied.value.isNotEmpty()

    /** What the Shield should show over the current foreground app, or null to hide it. */
    val shield: StateFlow<ShieldContent?> = _shield.asStateFlow()

    /** The accessibility service reports each foreground package; null means "left the app". */
    fun onForeground(packageName: String?) {
        foreground.value = packageName
        refreshShield()
    }

    override suspend fun apply(session: FocusSession) {
        applied.update { it + (session.id to session) }
        refreshShield()
        startRunningService()
        scheduleRefreshes(session)
    }

    override suspend fun lift(sessionId: FocusSessionId) {
        applied.update { it - sessionId }
        refreshJobs.remove(sessionId)?.cancel()
        refreshShield()
        if (applied.value.isEmpty()) stopRunningService()
    }

    override suspend fun pauseUntil(sessionId: FocusSessionId, until: Instant) {
        val now = clock.now()
        applied.update { sessions ->
            val session = sessions[sessionId] ?: return@update sessions
            // The session service saved the same Break just before calling this; don't add it twice.
            if (session.currentBreak(now) != null) sessions
            else sessions + (sessionId to session.copy(breaks = session.breaks + Break(startedAt = now, endsAt = until)))
        }
        refreshShield()
        applied.value[sessionId]?.let(::scheduleRefreshes)
    }

    private fun refreshShield() = synchronized(shieldLock) {
        val packageName = foreground.value
        _shield.value = packageName?.let {
            BlockingState(applied.value.values.toList()).forApp(
                packageName = it,
                category = categoryOf(it),
                now = clock.now(),
            )
        }
    }

    private fun categoryOf(packageName: String): String? = categoryCache.getOrPut(packageName) {
        val info = runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull() ?: return@getOrPut null
        categories.categoryOf(packageName, AndroidAppCategories.systemCategoryKey(info))?.key
    }

    /**
     * What is blocked changes on its own when a Break ends and at the planned end, but the user may
     * stay in the app meanwhile and nothing else asks the engine again. Refresh at both moments, so
     * the Shield reappears after a Break (T19) and lifts at the planned end even when the
     * running-session service could not start to complete the session.
     */
    private fun scheduleRefreshes(session: FocusSession) {
        val moments = listOfNotNull(session.currentBreak(clock.now())?.endsAt, session.plannedEnd).sorted()
        val job = scope.launch {
            for (at in moments) {
                clock.waitUntil(at)
                refreshShield()
            }
        }
        refreshJobs.put(session.id, job)?.cancel()
    }

    private fun startRunningService() {
        if (applied.value.isEmpty()) return
        // Background starts are refused on Android 12+ unless an alarm or the UI started us; the
        // accessibility service keeps blocking regardless, so a refused start is not fatal.
        runCatching {
            context.startForegroundService(Intent(context, RunningSessionService::class.java))
        }
    }

    private fun stopRunningService() {
        runCatching { context.stopService(Intent(context, RunningSessionService::class.java)) }
    }
}

/**
 * The package names that are never Shielded, even if a Block List picks them. Two kinds:
 * - [isLeaving]: the launcher, Focal itself, and the screens ADR 0002 keeps reachable (Settings, the
 *   permission controller and the package installer, including OEM variants). Reaching one means the
 *   user left the app, so the Shield lifts.
 * - [isTransient]: windows that open over the app without leaving it (System UI's shade and
 *   dialogs, the keyboard). The app underneath is still in front, so the remembered foreground app
 *   stays; otherwise a Break ending while the user types would find no app to Shield.
 */
internal class NoisePackages(context: Context) {
    private val leaving: Set<String> = buildSet {
        add(context.packageName)
        addAll(SYSTEM_ESCAPE_PACKAGES)
        resolvedPackage(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))?.let(::add)
        resolvedPackage(context, Intent(Settings.ACTION_SETTINGS))?.let(::add)
        resolvedPackage(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            ?.let(::add)
        resolvedPackage(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")))?.let(::add)
    }

    private val transient: Set<String> = buildSet {
        add("com.android.systemui")
        val inputMethods = context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        inputMethods?.enabledInputMethodList?.forEach { add(it.packageName) }
        inputMethods?.inputMethodList?.forEach { add(it.packageName) }
    }

    fun isLeaving(packageName: String): Boolean = packageName in leaving

    // A launcher or Settings that is also an IME still means "left the app".
    fun isTransient(packageName: String): Boolean = packageName in transient && packageName !in leaving

    private companion object {
        /** AOSP and Google builds; OEM Settings and installers are added by resolving their intents. */
        val SYSTEM_ESCAPE_PACKAGES = setOf(
            "com.android.settings",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
        )
    }
}

private fun resolvedPackage(context: Context, intent: Intent): String? {
    val resolved = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(intent, 0)
        }
    }.getOrNull()
    // An ambiguous intent resolves to the system chooser ("android"), which says nothing useful.
    return resolved?.activityInfo?.packageName?.takeUnless { it == "android" }
}
