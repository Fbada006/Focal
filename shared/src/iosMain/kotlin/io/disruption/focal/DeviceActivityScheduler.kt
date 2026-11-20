package io.disruption.focal

import io.disruption.focal.core.ios.DeviceActivityPlan

/**
 * Registers Focal's DeviceActivity intervals (T31). Implemented in Swift (`IosDeviceActivityScheduler`),
 * because DeviceActivity has no Objective-C API Kotlin could call. It registers what [monitor] is
 * given, stops Schedule and session-end activities no longer in the plan, and decides nothing.
 */
interface DeviceActivityScheduler {
    fun monitor(plan: DeviceActivityPlan)
}

/** Until Swift supplies one (previews, tests): nothing is registered. */
internal class NoDeviceActivityScheduler : DeviceActivityScheduler {
    override fun monitor(plan: DeviceActivityPlan) = Unit
}
