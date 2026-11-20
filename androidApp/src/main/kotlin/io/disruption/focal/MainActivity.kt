package io.disruption.focal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import io.disruption.focal.ui.App
import io.disruption.focal.ui.EntryScreen
import io.disruption.focal.ui.PlatformContent
import io.disruption.focal.ui.apps.AndroidAppsTab

class MainActivity : ComponentActivity() {
    /** The screen the launching Intent asked for, until App has opened it. */
    private var entry by mutableStateOf<EntryScreen?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A recreated activity restores a request App hadn't opened yet; its Intent was read before.
        entry = if (savedInstanceState == null) intent.entryScreen() else savedInstanceState.entryScreen()

        setContent {
            App(androidContent, entry = entry, onEntryHandled = { entry = null })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        entry?.let { outState.putString(EXTRA_ENTRY_SCREEN, it.name) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        entry = intent.entryScreen()
    }

    companion object {
        private const val EXTRA_ENTRY_SCREEN = "io.disruption.focal.ENTRY_SCREEN"

        /** Opens Focal on [screen], on top of Home, reusing the running activity if there is one. */
        fun intentFor(context: Context, screen: EntryScreen): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_ENTRY_SCREEN, screen.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        private fun Intent.entryScreen(): EntryScreen? = entryScreenNamed(getStringExtra(EXTRA_ENTRY_SCREEN))

        private fun Bundle.entryScreen(): EntryScreen? = entryScreenNamed(getString(EXTRA_ENTRY_SCREEN))

        private fun entryScreenNamed(name: String?): EntryScreen? = EntryScreen.entries.firstOrNull { it.name == name }
    }
}

private val androidContent = PlatformContent(
    appsTab = { blockListId, state -> AndroidAppsTab(blockListId, state) },
)

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
