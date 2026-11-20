package io.disruption.focal.blocking

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import io.disruption.focal.FocalApplication
import io.disruption.focal.MainActivity
import io.disruption.focal.core.blocking.BlockingEngine
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
 */
class FocalAccessibilityService : AccessibilityService() {
    private var engine: AndroidBlockingEngine? = null
    private var overlay: ShieldOverlay? = null
    private var scope: CoroutineScope? = null
    private lateinit var noise: NoisePackages

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
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        val engine = engine ?: return
        // The Shield window is Focal's own and fires window events when shown. Only a Focal
        // Activity means the user really came to Focal; anything else keeps the current block.
        if (packageName == this.packageName && !isFocalActivity(event.className)) return
        // The keyboard and System UI open over the app without leaving it: keep the app in front.
        if (noise.isTransient(packageName)) return
        // Leaving the app (launcher, Settings) or returning to Focal lifts the Shield.
        if (noise.isLeaving(packageName)) {
            engine.onForeground(null)
            return
        }
        engine.onForeground(packageName)
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
        scope?.cancel()
        overlay?.detach()
        engine?.onForeground(null)
        super.onDestroy()
    }
}
