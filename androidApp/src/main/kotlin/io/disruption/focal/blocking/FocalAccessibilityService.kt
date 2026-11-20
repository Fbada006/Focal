package io.disruption.focal.blocking

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import io.disruption.focal.FocalApplication
import io.disruption.focal.MainActivity
import io.disruption.focal.core.blocking.BlockingEngine
import io.disruption.focal.core.website.AddressBarReading
import io.disruption.focal.core.website.BrowserWindow
import io.disruption.focal.core.website.CommittedSite
import io.disruption.focal.core.website.PendingAddressBarCheck
import io.disruption.focal.core.website.SupportedBrowsers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.context.GlobalContext

/**
 * Detects the foreground app and shows the Shield for anything blocked by a running Focus Session
 * (T13, docs/android-blocking-engine.md). Android binds this service itself, so it survives better
 * than a normal service; it makes no domain decisions, it asks [AndroidBlockingEngine].
 *
 * In supported browsers it also reads the address bar (T26, ADR 0005). Navigation inside the
 * browser need not change the window, so content and text changes from browsers trigger a re-check
 * too, throttled to one per [ADDRESS_BAR_CHECK_DELAY_MS]. Which site counts (committed, not typed)
 * is [CommittedSite]'s rule; which view is the address bar is [SupportedBrowsers]' data.
 */
class FocalAccessibilityService : AccessibilityService() {
    private var engine: AndroidBlockingEngine? = null
    private var overlay: ShieldOverlay? = null
    private var scope: CoroutineScope? = null
    private lateinit var noise: NoisePackages
    private val site = CommittedSite()
    private val handler = Handler(Looper.getMainLooper())

    private val pendingCheck = PendingAddressBarCheck()
    private val runPendingCheck = Runnable {
        val browser = pendingCheck.take() ?: return@Runnable
        checkAddressBar(browser)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val engine = runCatching {
            GlobalContext.get().get<BlockingEngine>() as AndroidBlockingEngine
        }.getOrNull() ?: return
        this.engine = engine
        noise = NoisePackages(this)

        val overlay = ShieldOverlay(
            service = this,
            onClose = {
                performGlobalAction(GLOBAL_ACTION_HOME)
                engine.onForeground(null)
            },
            onOpenFocal = {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
                engine.onForeground(null)
            },
        )
        overlay.attach()
        this.overlay = overlay

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        engine.shield.onEach(overlay::render).launchIn(scope)
        this.scope = scope
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        val engine = engine ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindowChanged(engine, packageName, event.className)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            -> if (SupportedBrowsers.isSupported(packageName)) {
                scheduleAddressBarCheck(packageName, browserWindow(packageName, foregroundAppRoot()))
            }
        }
    }

    private fun onWindowChanged(engine: AndroidBlockingEngine, packageName: String, className: CharSequence?) {
        // The Shield window is Focal's own and fires window events when shown. Only a Focal
        // Activity means the user really came to Focal; anything else keeps the current block.
        if (packageName == this.packageName && !isFocalActivity(className)) return
        // The keyboard and System UI open over the app without leaving it: keep the app in front.
        // The keyboard also opens over a browser's address bar, so this keeps the browser's site.
        if (noise.isTransient(packageName)) return
        // Leaving the app (launcher, Settings) or returning to Focal lifts the Shield.
        if (noise.isLeaving(packageName)) {
            forgetBrowser()
            engine.onForeground(null)
            return
        }
        if (!SupportedBrowsers.isSupported(packageName)) {
            forgetBrowser()
            engine.onForeground(packageName)
            return
        }
        // The new window's views may not be laid out yet: check now, and again once they settle.
        // A late event from a browser that is not the foreground app must not replace the app
        // really in front (or its Shield), so it is ignored ([BrowserWindow]).
        val window = checkAddressBar(packageName)
        if (window == BrowserWindow.NotReady) engine.onForeground(packageName, site.update(packageName, null))
        scheduleAddressBarCheck(packageName, window)
    }

    /** The user left the browser: its site and pending check no longer describe what is in front. */
    private fun forgetBrowser() {
        site.reset()
        cancelAddressBarCheck()
    }

    /**
     * Read [browser]'s address bar and report the committed site to the engine, but only when
     * [browser] is the foreground app; otherwise do nothing and say why ([BrowserWindow]).
     */
    private fun checkAddressBar(browser: String): BrowserWindow {
        val root = foregroundAppRoot()
        val window = browserWindow(browser, root)
        if (window == BrowserWindow.Active && root != null) {
            engine?.onForeground(browser, site.update(browser, readAddressBar(root, browser)))
        }
        return window
    }

    private fun browserWindow(browser: String, foregroundRoot: AccessibilityNodeInfo?): BrowserWindow =
        BrowserWindow.of(browser, foregroundRoot?.packageName?.toString(), engine?.shieldedApp)

    /**
     * The root of the app window in front, looking underneath Focal's Shield: the Shield is an
     * accessibility overlay and becomes the active window while shown, so the active window alone
     * can't say which app it covers. Null when no app window can be read yet.
     */
    private fun foregroundAppRoot(): AccessibilityNodeInfo? {
        val apps = runCatching { windows }.getOrNull().orEmpty()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val window = apps.firstOrNull { it.isActive } ?: apps.maxByOrNull { it.layer }
        return window?.root
            ?: rootInActiveWindow?.takeUnless { it.packageName?.toString() == packageName }
    }

    private fun readAddressBar(root: AccessibilityNodeInfo, browser: String): AddressBarReading? {
        for (id in SupportedBrowsers.addressBarIds[browser].orEmpty()) {
            val bar = root.findAccessibilityNodeInfosByViewId(id).firstOrNull() ?: continue
            val text = if (bar.isShowingHintText) "" else bar.text?.toString().orEmpty()
            return AddressBarReading(text = text, isEditing = bar.isFocused)
        }
        return null
    }

    /**
     * Content changes arrive many times a second: read the bar at most once per delay, after it.
     * Which event may (re)schedule the check is [PendingAddressBarCheck]'s rule.
     */
    private fun scheduleAddressBarCheck(browser: String, window: BrowserWindow) {
        if (!pendingCheck.offer(browser, window)) return
        handler.removeCallbacks(runPendingCheck)
        handler.postDelayed(runPendingCheck, ADDRESS_BAR_CHECK_DELAY_MS)
    }

    private fun cancelAddressBarCheck() {
        pendingCheck.clear()
        handler.removeCallbacks(runPendingCheck)
    }

    private fun isFocalActivity(className: CharSequence?): Boolean {
        val name = className?.toString() ?: return false
        return runCatching { packageManager.getActivityInfo(ComponentName(this, name), 0) }.isSuccess
    }

    override fun onInterrupt() {
        engine?.onForeground(null)
    }

    /**
     * Android unbinds the service when the user switches it off in accessibility settings (or the
     * system stops it). Blocking is gone either way, so check whether it was switched off: if so,
     * running Locked sessions are Broken (ADR 0002, T22). Revocations while Focal's process is dead
     * are caught when it next starts (`FocalApplication.restoreSessions`).
     */
    override fun onUnbind(intent: Intent?): Boolean {
        (application as? FocalApplication)?.checkBlockingPermissions()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cancelAddressBarCheck()
        scope?.cancel()
        overlay?.detach()
        engine?.onForeground(null)
        super.onDestroy()
    }
}

/** How long browser content changes are gathered before the address bar is read again. */
private const val ADDRESS_BAR_CHECK_DELAY_MS = 250L
