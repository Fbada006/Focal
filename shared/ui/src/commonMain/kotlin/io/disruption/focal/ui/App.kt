package io.disruption.focal.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.disruption.focal.ui.blocklists.AppsTabContent
import io.disruption.focal.ui.blocklists.BlockListEditorRoute
import io.disruption.focal.ui.blocklists.BlockListsRoute
import io.disruption.focal.ui.navigation.BlockListEditor
import io.disruption.focal.ui.navigation.BlockLists
import io.disruption.focal.ui.theme.FocalTheme

/** Platform-specific pieces the shared screens host. */
class PlatformContent(
    val appsTab: AppsTabContent? = null,
)

@Composable
fun App(platform: PlatformContent = PlatformContent()) {
    FocalTheme {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = BlockLists) {
            composable<BlockLists> {
                BlockListsRoute(onOpenEditor = { nav.navigate(BlockListEditor(it)) })
            }
            composable<BlockListEditor> { entry ->
                val route = entry.toRoute<BlockListEditor>()
                if (platform.appsTab != null) {
                    BlockListEditorRoute(route.id, onClose = { nav.popBackStack() }, appsTab = platform.appsTab)
                } else {
                    BlockListEditorRoute(route.id, onClose = { nav.popBackStack() })
                }
            }
        }
    }
}
