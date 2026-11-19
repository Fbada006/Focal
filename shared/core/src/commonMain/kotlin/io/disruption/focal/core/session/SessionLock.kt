package io.disruption.focal.core.session

import kotlinx.coroutines.sync.Mutex

/**
 * One lock for rules that span Block Lists and running Focus Sessions, such as "a Block List can't
 * be deleted while a running session uses it" (ADR 0007). Starting a session and deleting a list
 * both take it, so a session can't start on a list between the in-use check and the delete.
 * Share a single instance between [FocusSessionService] and the Block List service.
 */
class SessionLock {
    internal val mutex = Mutex()
}
