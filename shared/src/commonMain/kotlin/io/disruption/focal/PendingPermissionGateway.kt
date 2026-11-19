package io.disruption.focal

import io.disruption.focal.core.permissions.Permission
import io.disruption.focal.core.permissions.PermissionGateway

/**
 * Stand-in for iOS until Screen Time authorisation lands in Swift (T15): it requires nothing, so
 * onboarding skips permission steps and starting isn't gated. Replace it with a Swift
 * implementation that checks `AuthorizationCenter` and notification settings.
 */
class PendingPermissionGateway : PermissionGateway {
    override val requiredForSessions: List<Permission> = emptyList()
    override suspend fun isGranted(permission: Permission): Boolean = true
    override fun request(permission: Permission) = Unit
}
