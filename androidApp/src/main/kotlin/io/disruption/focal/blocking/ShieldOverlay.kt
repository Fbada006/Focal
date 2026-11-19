package io.disruption.focal.blocking

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.disruption.focal.core.blocking.ShieldContent
import io.disruption.focal.ui.theme.FocalTheme
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The Shield drawn over a blocked app as a `TYPE_ACCESSIBILITY_OVERLAY` window (T13). The window
 * is attached once and hidden while nothing is blocked, so showing it costs no inflate or
 * first-layout time at block time.
 */
internal class ShieldOverlay(
    private val service: AccessibilityService,
    private val onClose: () -> Unit,
    private val onOpenFocal: () -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val owner = ShieldOwner()
    private val content = mutableStateOf<ShieldContent?>(null)
    private var view: ComposeView? = null

    fun attach() {
        val view = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val shield = content.value
                if (shield != null) {
                    FocalTheme {
                        ShieldScreen(
                            content = shield,
                            onClose = {
                                onClose()
                                render(null)
                            },
                            onOpenFocal = onOpenFocal,
                        )
                    }
                }
            }
            visibility = View.GONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        windowManager.addView(view, params)
        this.view = view
        owner.resume()
    }

    fun render(shield: ShieldContent?) {
        content.value = shield
        view?.visibility = if (shield == null) View.GONE else View.VISIBLE
        if (shield == null) owner.pause() else owner.resume()
    }

    fun detach() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
    }
}

@Composable
private fun ShieldScreen(content: ShieldContent, onClose: () -> Unit, onOpenFocal: () -> Unit) {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(content.sessionId) {
        while (true) {
            delay(1.seconds)
            now = Clock.System.now()
        }
    }
    val left = (content.plannedEnd - now).coerceAtLeast(Duration.ZERO)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "BLOCKED",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = content.sessionName,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            if (content.otherSessions > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "+${content.otherSessions} more ${if (content.otherSessions == 1) "session" else "sessions"} also blocking",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = formatTimeLeft(left),
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "left in this session",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = content.motivationalLine,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            Button(onClick = onClose) {
                Text("Close")
            }
            TextButton(onClick = onOpenFocal) {
                Text("Open Focal")
            }
        }
    }
}

/** `mm:ss`, or `h:mm:ss` for long sessions. */
internal fun formatTimeLeft(left: Duration): String {
    val total = left.inWholeSeconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Compose needs a `LifecycleOwner` and a `SavedStateRegistryOwner` when hosted outside an Activity
 * (docs/android-blocking-engine.md). The overlay is paused while hidden so the timer does not tick.
 */
private class ShieldOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun resume() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun pause() {
        registry.currentState = Lifecycle.State.CREATED
    }
}
