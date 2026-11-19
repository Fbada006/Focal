package io.disruption.focal.core.ios

import io.disruption.focal.core.session.FocusSessionService
import io.disruption.focal.core.session.IngestReport

/**
 * What the iOS app does with the extensions' event log when it starts (ADR 0008): ingest the
 * events, then re-apply every running session. [eventLog] is the log file's text, or null when
 * there is none yet. Ingesting is idempotent, so the log can be read again on every launch.
 */
suspend fun ingestOnLaunch(service: FocusSessionService, eventLog: String?): IngestReport {
    val report = service.ingest(eventLog?.let(IosContract::decodeEvents).orEmpty())
    service.restore()
    return report
}
