package io.disruption.focal.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.disruption.focal.MainActivity
import io.disruption.focal.core.repository.FocusSessionRepository
import io.disruption.focal.core.stats.FocusStats
import io.disruption.focal.core.stats.StatsCalculator
import io.disruption.focal.core.time.TimeZoneProvider
import io.disruption.focal.ui.EntryScreen
import io.disruption.focal.ui.stats.StatsCopy
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext
import kotlin.time.Clock

/**
 * The home-screen stats widget (story 75): focus today, focus this week and the current Streak,
 * computed and worded exactly as on the Stats screen. Tapping it opens Focal on Stats.
 *
 * It is redrawn when Focus Sessions change ([StatsWidgetUpdates]) and every 30 minutes by the
 * system (`updatePeriodMillis`), so today's focus time and the Streak roll over at midnight.
 */
class StatsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(TINY, ROW, COLUMN))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val koin = GlobalContext.get()
        val repository = koin.get<FocusSessionRepository>()
        val clock = koin.get<Clock>()
        val timeZone = koin.get<TimeZoneProvider>()
        val sessions = repository.observeAll()
        val initial = sessions.first()

        provideContent {
            // An update while this composition is still alive recomposes it rather than calling
            // provideGlance again, so follow the sessions instead of keeping the first read.
            val all by remember { sessions }.collectAsState(initial)
            val stats = StatsCalculator.calculate(all, clock.now(), timeZone.current())
            GlanceTheme { StatsWidgetContent(stats) }
        }
    }

    companion object {
        /** 2x1: focus today only. */
        private val TINY = DpSize(110.dp, 56.dp)

        /** 3x1 and wider: the three numbers side by side. */
        private val ROW = DpSize(180.dp, 56.dp)

        /** 2x2 and taller: the three numbers stacked. */
        private val COLUMN = DpSize(110.dp, 140.dp)
    }

    @Composable
    private fun StatsWidgetContent(stats: FocusStats) {
        val context = LocalContext.current
        val today = StatsCopy.focusTime(stats.focusToday)
        val week = StatsCopy.focusTime(stats.focusThisWeek)
        val streak = StatsCopy.streak(stats.currentStreak)

        var modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(actionStartActivity(MainActivity.intentFor(context, EntryScreen.Stats)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modifier = modifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)
        }

        when (LocalSize.current) {
            COLUMN -> Column(modifier, verticalAlignment = Alignment.CenterVertically) {
                Stat("Focus today", today)
                Spacer(GlanceModifier.height(8.dp))
                Stat("This week", week)
                Spacer(GlanceModifier.height(8.dp))
                Stat("Streak", streak)
            }
            ROW -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                Stat("Focus today", today, GlanceModifier.defaultWeight())
                Stat("This week", week, GlanceModifier.defaultWeight())
                Stat("Streak", streak, GlanceModifier.defaultWeight())
            }
            else -> Column(modifier, verticalAlignment = Alignment.CenterVertically) {
                Stat("Focus today", today)
            }
        }
    }

    @Composable
    private fun Stat(label: String, value: String, modifier: GlanceModifier = GlanceModifier) {
        Column(modifier) {
            Text(
                label,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
            Text(
                value,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}
