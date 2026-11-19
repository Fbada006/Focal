package io.disruption.focal.testing

import io.disruption.focal.core.permissions.Permission
import io.disruption.focal.core.permissions.PermissionGateway

/** Permissions a test can grant and revoke; records what was requested. */
class FakePermissionGateway(
    override val requiredForSessions: List<Permission> = listOf(Permission.AccessibilityService, Permission.Notifications),
    granted: Set<Permission> = emptySet(),
) : PermissionGateway {
    val granted = granted.toMutableSet()
    val requested = mutableListOf<Permission>()

    override suspend fun isGranted(permission: Permission): Boolean = permission in granted

    override fun request(permission: Permission) {
        requested += permission
    }
}
