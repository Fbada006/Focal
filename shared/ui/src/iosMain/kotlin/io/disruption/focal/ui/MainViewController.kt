package io.disruption.focal.ui

import androidx.compose.ui.window.ComposeUIViewController
import io.disruption.focal.ui.apps.IosAppsTab

fun MainViewController() = ComposeUIViewController {
    App(PlatformContent(appsTab = { blockListId, state -> IosAppsTab(blockListId, state) }))
}
