package io.disruption.focal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.disruption.focal.ui.App
import io.disruption.focal.ui.PlatformContent
import io.disruption.focal.ui.apps.AndroidAppsTab

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(androidContent)
        }
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