package io.disruption.focal

import io.disruption.focal.core.ios.DeviceActivityPlan
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId

/**
 * The name of the `ManagedSettingsStore` that holds [session]'s block (T16, ADR 0006). Naming it in
 * Kotlin keeps the id opaque to Swift: Kotlin ids are value classes, which Swift sees as `Any`.
 */
fun managedSettingsStoreName(session: FocusSession): String = storeName(session.id.value)

/** The same name, from a [FocusSessionId] when a session is being lifted. */
fun managedSettingsStoreName(sessionId: FocusSessionId): String = storeName(sessionId.value)

private fun storeName(id: String): String = "focal-session-$id"

/**
 * The name of the DeviceActivity activity whose interval ends when [sessionId]'s Break ends (T20).
 * The DeviceActivityMonitor extension recognises it by the `focal-break-` prefix (`BreakResumer`).
 */
fun breakActivityName(sessionId: FocusSessionId): String = DeviceActivityPlan.breakActivityName(sessionId.value)

/** [sessionId] as the plain id the extensions see in the snapshot (`FocalEndedSessions`, T20). */
fun sessionIdString(sessionId: FocusSessionId): String = sessionId.value

/**
 * The id of the Schedule that started [session], or null, as the extensions see it (T31): the
 * engine records it with the session's store so the monitor never starts a second session in a
 * window the app already started.
 */
fun scheduleIdString(session: FocusSession): String? = session.scheduleId?.value
