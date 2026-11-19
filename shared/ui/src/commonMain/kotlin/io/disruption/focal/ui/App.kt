package io.disruption.focal.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.disruption.focal.ui.blocklists.AppsTabContent
import io.disruption.focal.ui.blocklists.BlockListEditorRoute
import io.disruption.focal.ui.blocklists.BlockListsRoute
import io.disruption.focal.ui.countdown.ExitCountdownRoute
import io.disruption.focal.ui.edit.EditSessionRoute
import io.disruption.focal.ui.home.HomeRoute
import io.disruption.focal.ui.navigation.BlockListEditor
import io.disruption.focal.ui.navigation.BlockLists
import io.disruption.focal.ui.navigation.EditSession
import io.disruption.focal.ui.navigation.ExitCountdown
import io.disruption.focal.ui.navigation.Home
import io.disruption.focal.ui.navigation.ScheduleEditor
import io.disruption.focal.ui.navigation.Schedules
import io.disruption.focal.ui.navigation.StartSession
import io.disruption.focal.ui.navigation.Stats
import io.disruption.focal.ui.stats.StatsRoute
import io.disruption.focal.ui.schedules.ScheduleEditorRoute
import io.disruption.focal.ui.schedules.SchedulesRoute
import io.disruption.focal.ui.start.StartSessionRoute
import io.disruption.focal.ui.theme.FocalTheme

/** Platform-specific pieces the shared screens host. */
class PlatformContent(
    val appsTab: AppsTabContent? = null,
)

@Composable
fun App(platform: PlatformContent = PlatformContent()) {
    FocalTheme {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = Home) {
            composable<Home> {
                HomeRoute(
                    onOpenStart = { nav.navigate(StartSession) },
                    onOpenBlockLists = { nav.navigate(BlockLists) },
                    onOpenExitCountdown = { nav.navigate(ExitCountdown(it)) },
                    onOpenEditSession = { nav.navigate(EditSession(it)) },
                    onOpenSchedules = { nav.navigate(Schedules) },
                    onOpenStats = { nav.navigate(Stats) },
                )
            }
            composable<Stats> { StatsRoute(onBack = { nav.popBackStack() }) }
            composable<Schedules> {
                SchedulesRoute(onOpenEditor = { nav.navigate(ScheduleEditor(it)) }, onBack = { nav.popBackStack() })
            }
            composable<ScheduleEditor> { entry ->
                ScheduleEditorRoute(entry.toRoute<ScheduleEditor>().id, onClose = { nav.popBackStack() })
            }
            composable<EditSession> { entry ->
                EditSessionRoute(entry.toRoute<EditSession>().sessionId, onClose = { nav.popBackStack() })
            }
            composable<ExitCountdown> { entry ->
                ExitCountdownRoute(entry.toRoute<ExitCountdown>().sessionId, onClose = { nav.popBackStack() })
            }
            composable<StartSession> {
                StartSessionRoute(
                    onStarted = { nav.popBackStack() },
                    onOpenBlockLists = { nav.navigate(BlockLists) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<BlockLists> {
                BlockListsRoute(
                    onOpenEditor = { nav.navigate(BlockListEditor(it)) },
                    onBack = { nav.popBackStack() },
                )
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
