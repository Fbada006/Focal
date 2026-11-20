package io.disruption.focal.ui.home

import androidx.lifecycle.viewModelScope
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.permissions.Permission
import io.disruption.focal.core.permissions.PermissionGateway
import io.disruption.focal.core.session.BreakResult
import io.disruption.focal.core.session.FocusSessionService
import io.disruption.focal.ui.permissions.brokenWarning
import io.disruption.focal.ui.mvi.MviViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

data class RunningSessionRow(
    val id: String,
    val name: String,
    val strictness: Strictness,
    val timeLeft: String,
    /** Normal sessions end at once; Delayed ones go through the Exit Countdown. Locked can't end. */
    val canEnd: Boolean,
    val fromSchedule: Boolean,
    /** Breaks left for a Delayed session, or null when the session can't take Breaks. */
    val breaksLeft: Int? = null,
    /** "On a Break · 3 min 12 s left" while a Break is in progress. */
    val onBreak: String? = null,
) {
    val canTakeBreak: Boolean get() = breaksLeft != null && breaksLeft > 0 && onBreak == null
}

data class HomeState(
    val loading: Boolean = true,
    val running: List<RunningSessionRow> = emptyList(),
    /** Required permissions that are missing; starting a session is disabled until fixed. */
    val missingPermissions: List<Permission> = emptyList(),
    /** Until the first check returns, Start stays disabled rather than briefly looking available. */
    val permissionsChecked: Boolean = false,
    /** The session whose last Break was refused because Focal couldn't schedule its end. */
    val breakUnavailableFor: String? = null,
    /** Locked sessions just recorded as Broken because blocking was switched off (story 41). */
    val brokenWarning: String? = null,
) {
    val canStart: Boolean get() = permissionsChecked && missingPermissions.isEmpty()
}

sealed interface HomeIntent {
    data object StartClicked : HomeIntent
    data object BlockListsClicked : HomeIntent
    data object SchedulesClicked : HomeIntent
    data object StatsClicked : HomeIntent
    data object HelpClicked : HomeIntent
    data class EndClicked(val sessionId: String) : HomeIntent
    data class TakeBreakClicked(val sessionId: String) : HomeIntent
    data class EditClicked(val sessionId: String) : HomeIntent
    data class FixPermissionClicked(val permission: Permission) : HomeIntent
    data object BrokenWarningDismissed : HomeIntent

    /** Focal came back to the foreground; permissions may have changed. */
    data object Resumed : HomeIntent
}

sealed interface HomeEffect {
    data object OpenStart : HomeEffect
    data object OpenBlockLists : HomeEffect
    data object OpenSchedules : HomeEffect
    data object OpenStats : HomeEffect
    data object OpenHelp : HomeEffect
    data class OpenExitCountdown(val sessionId: String) : HomeEffect
    data class OpenEditSession(val sessionId: String) : HomeEffect
}

class HomeViewModel(
    private val sessions: FocusSessionService,
    private val clock: Clock,
    private val permissions: PermissionGateway,
    ticks: Flow<Unit> = everySecond(),
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    init {
        refreshPermissions()
        viewModelScope.launch {
            sessions.brokenToAcknowledge.collect { broken ->
                setState { copy(brokenWarning = broken.takeIf { it.isNotEmpty() }?.let { warningFor(it) }) }
            }
        }
        viewModelScope.launch {
            combine(sessions.running, ticks.onStart { emit(Unit) }) { running, _ -> running }
                .collect { running -> render(running) }
        }
    }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.StartClicked -> if (currentState.canStart) emit(HomeEffect.OpenStart)
            HomeIntent.Resumed -> refreshPermissions()
            HomeIntent.BrokenWarningDismissed -> viewModelScope.launch { sessions.acknowledgeBroken() }
            is HomeIntent.FixPermissionClicked -> permissions.request(intent.permission)
            HomeIntent.BlockListsClicked -> emit(HomeEffect.OpenBlockLists)
            HomeIntent.SchedulesClicked -> emit(HomeEffect.OpenSchedules)
            HomeIntent.StatsClicked -> emit(HomeEffect.OpenStats)
            HomeIntent.HelpClicked -> emit(HomeEffect.OpenHelp)
            is HomeIntent.EndClicked -> end(intent.sessionId)
            is HomeIntent.EditClicked -> emit(HomeEffect.OpenEditSession(intent.sessionId))
            is HomeIntent.TakeBreakClicked -> viewModelScope.launch {
                val result = sessions.takeBreak(FocusSessionId(intent.sessionId))
                setState { copy(breakUnavailableFor = intent.sessionId.takeIf { result == BreakResult.Unavailable }) }
            }
        }
    }

    private fun refreshPermissions() {
        viewModelScope.launch {
            val missing = permissions.missing()
            // A permission blocking depends on was revoked while Focal was away (ADR 0002).
            // A permission blocking depends on was revoked while Focal was away (ADR 0002). The
            // warning itself comes from `brokenToAcknowledge`, whichever check broke the session.
            sessions.checkPermissions(missing)
            setState { copy(missingPermissions = missing, permissionsChecked = true) }
        }
    }

    private fun end(sessionId: String) {
        val row = currentState.running.firstOrNull { it.id == sessionId } ?: return
        when (row.strictness) {
            Strictness.Normal -> viewModelScope.launch { sessions.endEarly(FocusSessionId(sessionId)) }
            Strictness.Delayed -> emit(HomeEffect.OpenExitCountdown(sessionId))
            Strictness.Locked -> Unit
        }
    }

    private suspend fun render(running: List<FocusSession>) {
        val now = clock.now()
        if (running.any { it.plannedEnd <= now }) {
            // Sessions that ran out are recorded as Completed; the repository flow re-renders.
            sessions.completeDueSessions()
        }
        setState {
            copy(
                loading = false,
                running = running.filter { it.plannedEnd > now }.map { session ->
                    RunningSessionRow(
                        id = session.id.value,
                        name = session.name,
                        strictness = session.strictness,
                        timeLeft = formatTimeLeft(session.timeLeft(now)),
                        canEnd = session.strictness != Strictness.Locked,
                        fromSchedule = session.scheduleId != null,
                        breaksLeft = session.breaksLeft.takeIf { session.strictness == Strictness.Delayed },
                        onBreak = session.currentBreak(now)?.let { "On a Break · ${formatTimeLeft(it.endsAt - now)}" },
                    )
                },
            )
        }
    }
}

private fun warningFor(broken: List<FocusSession>): String = brokenWarning(broken.map { it.name })

internal fun everySecond(): Flow<Unit> = flow {
    while (true) {
        delay(1.seconds)
        emit(Unit)
    }
}
