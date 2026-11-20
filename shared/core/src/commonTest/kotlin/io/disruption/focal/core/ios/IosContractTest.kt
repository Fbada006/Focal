package io.disruption.focal.core.ios

import io.disruption.focal.core.model.Break
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Outcome
import io.disruption.focal.core.model.ScheduleId
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.session.BreakResult
import io.disruption.focal.core.session.SessionTestFixture
import io.disruption.focal.core.session.socialList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val socialApps = socialList.blocked.apps.toList()
private val socialDomains = socialList.blocked.domains.toList()

private fun scheduleStart(
    start: Instant,
    strictness: String = "Delayed",
    blockListId: String = socialList.id.value,
) = ExtensionEvent.ScheduleSessionStarted(
    "ev-start", start, "fs-ios", "sch", "Evening", blockListId, strictness, start + 2.hours,
    socialApps, emptyList(), socialDomains,
)

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
            apps = socialList.blocked.apps.toList(),
            appCategories = socialList.blocked.appCategories.toList(),
            domains = socialList.blocked.domains.toList(),
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
                ExtensionEvent.ScheduleSessionStarted("ev-1", start, "fs-ios", "sch", "Evening", socialList.id.value, "Delayed", start + 2.hours, socialApps, emptyList(), socialDomains),
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
                ExtensionEvent.ScheduleSessionStarted("ev-1", start, "fs-ios", "sch", "Evening", socialList.id.value, "Locked", start + 2.hours, socialApps, emptyList(), socialDomains),
                ExtensionEvent.ScheduleSessionEnded("ev-2", start + 1.hours, "fs-ios"),
            ),
        )
        assertTrue(f.sessions.get(FocusSessionId("fs-ios"))!!.isRunning)
    }

    @Test
    fun anIngestedSessionKeepsTheExtensionsCopyEvenIfTheListIsGone() = runTest {
        val f = SessionTestFixture()
        f.blockLists.delete(socialList.id)

        f.service.ingest(listOf(scheduleStart(f.clock.now, strictness = "Locked")))

        val session = f.sessions.get(FocusSessionId("fs-ios"))!!
        assertEquals(socialList.blocked.apps, session.blocked.apps)
        assertEquals(socialList.blocked.domains, session.blocked.domains)
    }

    @Test
    fun anUnknownStrictnessIsTreatedAsLocked() = runTest {
        val f = SessionTestFixture()

        f.service.ingest(listOf(scheduleStart(f.clock.now, strictness = "locked")))

        assertEquals(Strictness.Locked, f.sessions.get(FocusSessionId("fs-ios"))!!.strictness)
    }

    @Test
    fun aBreakEndedEarlyIsCutShort() = runTest {
        val f = SessionTestFixture()
        val start = f.clock.now - 1.hours
        f.service.ingest(listOf(scheduleStart(start)))
        val stored = f.sessions.get(FocusSessionId("fs-ios"))!!
        f.sessions.save(stored.copy(breaks = listOf(Break(start + 10.minutes, start + 15.minutes))))

        val report = f.service.ingest(listOf(ExtensionEvent.BreakEnded("ev-b", start + 13.minutes, "fs-ios")))

        assertEquals(1, report.breaksEnded)
        assertEquals(listOf(Break(start + 10.minutes, start + 13.minutes)), f.sessions.get(FocusSessionId("fs-ios"))!!.breaks)
    }

    @Test
    fun aBreakEndedOnTimeChangesNothing() = runTest {
        val f = SessionTestFixture()
        val start = f.clock.now - 1.hours
        f.service.ingest(listOf(scheduleStart(start)))
        val stored = f.sessions.get(FocusSessionId("fs-ios"))!!
        val onBreak = stored.copy(breaks = listOf(Break(start + 10.minutes, start + 15.minutes)))
        f.sessions.save(onBreak)

        val report = f.service.ingest(listOf(ExtensionEvent.BreakEnded("ev-b", start + 15.minutes, "fs-ios")))

        assertEquals(0, report.breaksEnded)
        assertEquals(onBreak.breaks, f.sessions.get(FocusSessionId("fs-ios"))!!.breaks)
    }

    @Test
    fun comingBackAfterTheMonitorEndedABreakKeepsTheSessionBlocking() = runTest {
        // T20: the Break's DeviceActivity interval ended (a little early, to the minute) while Focal
        // was suspended; Focal comes back to the foreground before the recorded Break end.
        val f = SessionTestFixture()
        val session = f.startFor(1.hours, strictness = Strictness.Delayed)
        val breakEnd = assertIs<BreakResult.Started>(f.service.takeBreak(session.id)).endsAt
        f.clock.advanceBy(FocusSession.BREAK_LENGTH - 30.seconds)
        val log = IosContract.encodeEvents(
            ExtensionEventLog(events = listOf(ExtensionEvent.BreakEnded("ev-b", breakEnd - 1.minutes, session.id.value))),
        )

        val report = ingestOnLaunch(f.service, log)

        assertEquals(1, report.breaksEnded)
        assertEquals(breakEnd - 1.minutes, f.sessions.get(session.id)!!.breaks.single().endsAt)
        // Re-applied, and not paused again for what is left of the recorded Break.
        assertTrue(session.id in f.engine.applied)
        assertTrue(session.id !in f.engine.pausedUntil)
    }

    @Test
    fun onLaunchTheLogIsIngestedAndRunningSessionsReapplied() = runTest {
        val f = SessionTestFixture()
        val log = IosContract.encodeEvents(ExtensionEventLog(events = listOf(scheduleStart(f.clock.now))))

        val report = ingestOnLaunch(f.service, log)
        ingestOnLaunch(f.service, log)

        assertEquals(1, report.sessionsStarted)
        assertEquals(1, f.sessions.all.size)
        assertTrue(FocusSessionId("fs-ios") in f.engine.applied)
    }

    @Test
    fun onLaunchWithoutALogNothingIsIngested() = runTest {
        val f = SessionTestFixture()
        assertEquals(0, ingestOnLaunch(f.service, null).sessionsStarted)
    }

    @Test
    fun aScheduleStartForAWindowTheAppAlreadyStartedIsSkipped() = runTest {
        val f = SessionTestFixture()
        val start = f.clock.now
        val appStarted = io.disruption.focal.core.model.FocusSession(
            id = FocusSessionId("fs-app"),
            blockListId = socialList.id,
            name = "Evening",
            strictness = Strictness.Delayed,
            startedAt = start,
            plannedEnd = start + 2.hours,
            blocked = socialList.blocked,
            scheduleId = ScheduleId("sch"),
        )
        f.sessions.save(appStarted)

        val report = f.service.ingest(listOf(scheduleStart(start + 1.minutes)))

        assertEquals(0, report.sessionsStarted)
        assertEquals(1, f.sessions.all.size)
    }
}
