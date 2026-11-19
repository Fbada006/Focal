package io.disruption.focal.blocking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import io.disruption.focal.core.apps.AppCategoryResolver
import io.disruption.focal.core.blocking.BlockingEngine
import io.disruption.focal.core.blocking.BlockingState
import io.disruption.focal.core.blocking.ShieldContent
import io.disruption.focal.core.model.Break
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.data.apps.AndroidAppCategories
import io.disruption.focal.data.curated.CuratedAppCategories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
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

    /** Package name → App Category key (or null), cached so foreground events stay cheap. */
    private val categoryCache = mutableMapOf<String, String?>()

    /** Running sessions, soonest end first. */
    val running: StateFlow<List<FocusSession>> = applied
        .map { sessions -> sessions.values.sortedBy { it.plannedEnd } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

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
        scheduleRefreshAtBreakEnd(session)
    }

    override suspend fun lift(sessionId: FocusSessionId) {
        applied.update { it - sessionId }
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
        applied.value[sessionId]?.let(::scheduleRefreshAtBreakEnd)
    }

    private fun refreshShield() {
        val packageName = foreground.value
        if (packageName == null) {
            _shield.value = null
            return
        }
        _shield.value = BlockingState(applied.value.values.toList()).forApp(
            packageName = packageName,
            category = categoryOf(packageName),
            now = clock.now(),
        )
    }

    private fun categoryOf(packageName: String): String? = categoryCache.getOrPut(packageName) {
        val info = runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull() ?: return@getOrPut null
        categories.categoryOf(packageName, AndroidAppCategories.systemCategoryKey(info))?.key
    }

    /**
     * A Break lifts blocking, but the user may stay in the blocked app. Nothing asks the engine
     * again when the Break ends, so refresh then and let the Shield reappear on its own (T19).
     */
    private fun scheduleRefreshAtBreakEnd(session: FocusSession) {
        val end = session.currentBreak(clock.now())?.endsAt ?: return
        scope.launch {
            delay((end - clock.now()).coerceAtLeast(0.seconds))
            refreshShield()
        }
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

/** The package names that fire window events but are never worth blocking. */
internal class NoisePackages(context: Context) {
    private val packages: Set<String> = buildSet {
        add(context.packageName)
        add("com.android.systemui")
        homePackage(context)?.let(::add)
        val inputMethods = context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        inputMethods?.enabledInputMethodList?.forEach { add(it.packageName) }
        inputMethods?.inputMethodList?.forEach { add(it.packageName) }
    }

    operator fun contains(packageName: String): Boolean = packageName in packages
}

private fun homePackage(context: Context): String? {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolved = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.resolveActivity(intent, 0)
    }
    return resolved?.activityInfo?.packageName
}
