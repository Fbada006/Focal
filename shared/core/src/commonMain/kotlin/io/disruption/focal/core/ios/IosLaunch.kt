package io.disruption.focal.core.ios

import io.disruption.focal.core.permissions.Permission
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
 */
suspend fun ingestOnLaunch(
    service: FocusSessionService,
    eventLog: String?,
    missing: Collection<Permission> = emptyList(),
): IngestReport {
    val report = service.ingest(eventLog?.let(IosContract::decodeEvents).orEmpty())
    service.checkPermissions(missing)
    service.restore()
    return report
}
