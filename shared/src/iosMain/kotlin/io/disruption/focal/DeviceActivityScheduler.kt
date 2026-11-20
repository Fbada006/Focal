package io.disruption.focal

import io.disruption.focal.core.ios.DeviceActivityPlan
import io.disruption.focal.core.ios.RegisteredBreak
import kotlin.time.Instant

/**
 * Registers Focal's DeviceActivity intervals (T31). Implemented in Swift (`IosDeviceActivityScheduler`),
 * because DeviceActivity has no Objective-C API Kotlin could call. It registers what [monitor] is
 * given, stops Schedule and session-end activities no longer in the plan, and decides nothing.
 */
interface DeviceActivityScheduler {
    /**
     * Register as much of [plan] as fits in the activity limit (`DeviceActivityPlan.fitting`: session
     * ends first, Schedule activities dropped to make room). Returns false if any of it isn't
     * registered (no Screen Time access, Schedule activities dropped or refused), so Focal tries
     * again later instead of taking the plan as registered.
     */
    fun monitor(plan: DeviceActivityPlan): Boolean
}

/** Until Swift supplies one (previews, tests): nothing is registered. */
internal class NoDeviceActivityScheduler : DeviceActivityScheduler {
    override fun monitor(plan: DeviceActivityPlan) = true
}

/** A registered Break activity for [DeviceActivityPlan.finishedBreaks], from Swift's epoch milliseconds. */
fun registeredBreak(name: String, endsAtEpochMillis: Long?): RegisteredBreak =
    RegisteredBreak(name, endsAtEpochMillis?.let(Instant::fromEpochMilliseconds))

/** [DeviceActivityPlan.finishedBreaks] at [nowEpochMillis], for Swift. */
fun finishedBreakActivities(
    registered: List<RegisteredBreak>,
    recordedSessionIds: Set<String>,
    nowEpochMillis: Long,
): List<String> = DeviceActivityPlan.finishedBreaks(registered, recordedSessionIds, Instant.fromEpochMilliseconds(nowEpochMillis))
