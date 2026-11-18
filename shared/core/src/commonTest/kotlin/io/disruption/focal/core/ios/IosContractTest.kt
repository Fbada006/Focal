package io.disruption.focal.core.ios

import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Outcome
import io.disruption.focal.core.model.ScheduleId
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.session.SessionTestFixture
import io.disruption.focal.core.session.socialList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class IosContractTest {

    @Test
    fun snapshotRoundTrips() {
        val text = IosContract.encodeSnapshot(ContractSamples.snapshot)
        assertEquals(ContractSamples.snapshot, IosContract.decodeSnapshot(text))
    }

    @Test
    fun snapshotLeavesOutDisabledSchedulesAndKeepsTheBreakEnd() {
        val snapshot = ContractSamples.snapshot
        assertEquals(listOf("sch-wind-down", "sch-work"), snapshot.schedules.map { it.id })
        assertEquals(listOf("MONDAY", "FRIDAY"), snapshot.schedules.last().days)
        assertEquals("22:00" to "07:00", snapshot.schedules.first().let { it.start to it.end })
        assertEquals(ContractSamples.running.breaks.single().endsAt, snapshot.runningSessions.single().breakEndsAt)
    }

    @Test
    fun aSnapshotFromANewerVersionIsRejected() {
        val text = IosContract.encodeSnapshot(ContractSamples.snapshot.copy(version = IosSnapshot.CURRENT_VERSION + 1))
        assertNull(IosContract.decodeSnapshot(text))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val text = IosContract.encodeSnapshot(ContractSamples.snapshot)
            .replaceFirst("{", "{\n  \"addedLater\": true,")
        assertEquals(ContractSamples.snapshot, IosContract.decodeSnapshot(text))
    }

    @Test
    fun eventLogRoundTrips() {
        val text = IosContract.encodeEvents(ContractSamples.events)
        assertEquals(ContractSamples.events.events, IosContract.decodeEvents(text))
    }

    @Test
    fun unknownAndMalformedEventsAreSkipped() {
        val text = """
            {"version": 1, "events": [
              {"type": "somethingNew", "id": "x", "at": "2026-01-05T09:00:00Z"},
              {"type": "breakEnded", "id": "ev-2", "at": "not a time", "sessionId": "fs-2"},
              {"type": "breakEnded", "id": "ev-3", "at": "2026-01-05T10:00:00Z", "sessionId": "fs-2"}
            ]}
        """.trimIndent()
        assertEquals(
            listOf(ExtensionEvent.BreakEnded("ev-3", Instant.parse("2026-01-05T10:00:00Z"), "fs-2")),
            IosContract.decodeEvents(text),
        )
    }

    @Test
    fun anEventLogFromANewerVersionOrGarbageYieldsNothing() {
        assertTrue(IosContract.decodeEvents("""{"version": 2, "events": []}""").isEmpty())
        assertTrue(IosContract.decodeEvents("not json").isEmpty())
    }

    @Test
    fun ingestingAScheduleStartCreatesTheSessionWithTheBlockListsCopy() = runTest {
        val f = SessionTestFixture()
        val event = ExtensionEvent.ScheduleSessionStarted(
            id = "ev-1",
            at = f.clock.now - 1.hours,
            sessionId = "fs-ios",
            scheduleId = "sch-work",
            name = "Work hours",
            blockListId = socialList.id.value,
            strictness = "Locked",
            plannedEnd = f.clock.now + 2.hours,
        )

        val report = f.service.ingest(listOf(event))
        f.service.ingest(listOf(event))

        assertEquals(1, report.sessionsStarted)
        val session = f.sessions.get(FocusSessionId("fs-ios"))!!
        assertEquals("Work hours", session.name)
        assertEquals(Strictness.Locked, session.strictness)
        assertEquals(ScheduleId("sch-work"), session.scheduleId)
        assertEquals(socialList.blocked, session.blocked)
        assertEquals(event.at, session.startedAt)
        assertTrue(session.isRunning)
        assertEquals(1, f.sessions.all.size)

        f.service.restore()
        assertTrue(FocusSessionId("fs-ios") in f.engine.applied)
    }

    @Test
    fun ingestingTheWindowEndRecordsCompleted() = runTest {
        val f = SessionTestFixture()
        val start = f.clock.now - 3.hours
        f.service.ingest(
            listOf(
                ExtensionEvent.ScheduleSessionStarted("ev-1", start, "fs-ios", "sch", "Evening", socialList.id.value, "Delayed", start + 2.hours),
                ExtensionEvent.BreakEnded("ev-2", start + 1.hours, "fs-ios"),
                ExtensionEvent.ScheduleSessionEnded("ev-3", start + 2.hours, "fs-ios"),
            ),
        )

        val session = f.sessions.get(FocusSessionId("fs-ios"))!!
        assertEquals(Outcome.Completed, session.outcome)
        assertEquals(start + 2.hours, session.endedAt)
    }

    @Test
    fun anEarlyEndEventDoesNotEndASession() = runTest {
        val f = SessionTestFixture()
        val start = f.clock.now
        f.service.ingest(
            listOf(
                ExtensionEvent.ScheduleSessionStarted("ev-1", start, "fs-ios", "sch", "Evening", socialList.id.value, "Locked", start + 2.hours),
                ExtensionEvent.ScheduleSessionEnded("ev-2", start + 1.hours, "fs-ios"),
            ),
        )
        assertTrue(f.sessions.get(FocusSessionId("fs-ios"))!!.isRunning)
    }
}
