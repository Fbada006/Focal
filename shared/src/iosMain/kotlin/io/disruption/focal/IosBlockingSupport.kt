package io.disruption.focal

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
