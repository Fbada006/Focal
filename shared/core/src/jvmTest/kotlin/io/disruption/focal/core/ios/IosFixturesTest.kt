package io.disruption.focal.core.ios

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The checked-in fixtures in `iosApp/Fixtures` are what the Swift extensions test against. This
 * keeps them identical to what the Kotlin side writes and reads.
 */
class IosFixturesTest {
    private val dir = File(System.getProperty("focal.fixtures") ?: "../../iosApp/Fixtures")

    @Test
    fun snapshotFixtureMatchesTheEncoder() {
        val fixture = File(dir, "snapshot-v1.json").readText()
        assertEquals(fixture.trim(), IosContract.encodeSnapshot(ContractSamples.snapshot))
        assertEquals(ContractSamples.snapshot, IosContract.decodeSnapshot(fixture))
    }

    @Test
    fun eventsFixtureMatchesTheEncoder() {
        val fixture = File(dir, "events-v1.json").readText()
        assertEquals(fixture.trim(), IosContract.encodeEvents(ContractSamples.events))
        assertEquals(ContractSamples.events.events, IosContract.decodeEvents(fixture))
    }
}
