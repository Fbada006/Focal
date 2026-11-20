package io.disruption.focal.core.ios

import io.disruption.focal.core.permissions.Permission
import io.disruption.focal.core.schedule.ScheduleService
import io.disruption.focal.core.session.FocusSessionService
import io.disruption.focal.core.session.IngestReport

/**
 * What the iOS app does with the extensions' event log when it starts or comes to the foreground
 * (ADR 0008): ingest the events, check permissions, then re-apply every running session. [eventLog]
 * is the log file's text, or null when there is none yet. Ingesting is idempotent, so the log can be
 * read again on every launch.
 *
 * [missing] is what the permission gateway reports missing now. Revoking Screen Time access clears
 * every ManagedSettings store, so running Locked sessions are recorded as Broken before the rest are
 * re-applied (T23, ADR 0002).
 *
 * Last, [schedules] starts any Schedule window open now that has no session yet: a backstop for a
 * window whose DeviceActivity start didn't fire (T31). It runs after ingesting, so a window the
 * extension already started isn't started twice. Without a permission blocking depends on (Screen
 * Time access), nothing is started: the session couldn't block, and a Locked one would escape the
 * check that makes it Broken (T23). The window starts once access is back, if still open.
 */
suspend fun ingestOnLaunch(
    service: FocusSessionService,
    eventLog: String?,
    missing: Collection<Permission> = emptyList(),
    schedules: ScheduleService? = null,
): IngestReport {
    val report = service.ingest(eventLog?.let(IosContract::decodeEvents).orEmpty())
    service.checkPermissions(missing)
    service.restore()
    if (missing.none { it.blockingDependsOn }) schedules?.evaluate()
    return report
}
