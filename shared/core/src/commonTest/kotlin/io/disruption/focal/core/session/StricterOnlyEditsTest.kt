package io.disruption.focal.core.session

import io.disruption.focal.core.blocklist.BlockListResult
import io.disruption.focal.core.blocklist.BlockListService
import io.disruption.focal.core.blocklist.DomainEditResult
import io.disruption.focal.core.model.Strictness
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class StricterOnlyEditsTest {
    private val f = SessionTestFixture()
    private val blockLists = BlockListService(f.blockLists, f.sessions, f.service)

    @Test
    fun additionsToTheBlockListAreAppliedToRunningSessionsAtOnce() = runTest {
        for (strictness in Strictness.entries) {
            val f = SessionTestFixture()
            val blockLists = BlockListService(f.blockLists, f.sessions, f.service)
            val session = f.startFor(1.hours, strictness = strictness)

            val result = blockLists.addDomain(socialList.id, "youtube.com")

            assertEquals(DomainEditResult.Saved("youtube.com"), result)
            assertTrue("youtube.com" in f.stored(session).blocked.domains, "$strictness")
            assertTrue(f.engine.applied.getValue(session.id).blocked.blocksWebsite("m.youtube.com"), "$strictness")
        }
    }

    @Test
    fun removalsWaitUntilADelayedOrLockedSessionEnds() = runTest {
        val delayed = f.startFor(1.hours, strictness = Strictness.Delayed)
        val locked = f.startFor(2.hours, strictness = Strictness.Locked)

        val result = blockLists.removeDomain(socialList.id, "instagram.com")

        assertEquals(listOf("Social", "Social"), assertIs<BlockListResult.Saved>(result).removalsDeferredFor)
        assertTrue("instagram.com" in f.stored(delayed).blocked.domains)
        assertTrue("instagram.com" in f.stored(locked).blocked.domains)
        assertTrue(f.engine.applied.getValue(locked.id).blocked.blocksWebsite("instagram.com"))

        f.clock.advanceBy(2.hours)
        f.service.completeDueSessions()
        val next = f.startFor(1.hours, strictness = Strictness.Locked)
        assertFalse("instagram.com" in next.blocked.domains, "the next session takes the edited list")
    }

    @Test
    fun normalSessionsAcceptRemovalsAtOnce() = runTest {
        val session = f.startFor(1.hours, strictness = Strictness.Normal)

        val result = blockLists.removeDomain(socialList.id, "instagram.com")

        assertTrue(assertIs<BlockListResult.Saved>(result).removalsDeferredFor.isEmpty())
        assertFalse("instagram.com" in f.stored(session).blocked.domains)
        assertFalse(f.engine.applied.getValue(session.id).blocked.blocksWebsite("instagram.com"))
    }

    @Test
    fun otherBlockListsSessionsAreUntouched() = runTest {
        val news = f.startFor(1.hours, list = newsList, strictness = Strictness.Normal)
        blockLists.addDomain(socialList.id, "youtube.com")
        assertEquals(newsList.blocked, f.stored(news).blocked)
    }

    @Test
    fun extendingAndRaisingStrictnessAreAllowedForEveryStrictness() = runTest {
        for (strictness in Strictness.entries) {
            val session = f.startFor(1.hours, strictness = strictness)
            val later = session.plannedEnd + 30.minutes

            val result = f.service.edit(session.id, plannedEnd = later, strictness = Strictness.Locked)

            val edited = assertIs<EditResult.Applied>(result).session
            assertEquals(later, edited.plannedEnd)
            assertEquals(Strictness.Locked, edited.strictness)
            assertEquals(later, f.engine.applied.getValue(session.id).plannedEnd)
        }
    }

    @Test
    fun delayedAndLockedSessionsRefuseShorteningAndLowerStrictness() = runTest {
        for (strictness in listOf(Strictness.Delayed, Strictness.Locked)) {
            val session = f.startFor(1.hours, strictness = strictness)

            assertEquals(
                EditResult.Refused(EditRefusal.Shorten),
                f.service.edit(session.id, plannedEnd = session.plannedEnd - 10.minutes),
            )
            assertEquals(
                EditResult.Refused(EditRefusal.LowerStrictness),
                f.service.edit(session.id, strictness = Strictness.Normal),
            )
            assertEquals(session, f.stored(session))
        }
    }

    @Test
    fun normalSessionsAcceptEveryEdit() = runTest {
        val session = f.startFor(1.hours, strictness = Strictness.Normal)
        val earlier = session.plannedEnd - 30.minutes

        val edited = assertIs<EditResult.Applied>(f.service.edit(session.id, plannedEnd = earlier)).session

        assertEquals(earlier, edited.plannedEnd)
    }

    @Test
    fun anEndInThePastIsRefused() = runTest {
        val session = f.startFor(1.hours, strictness = Strictness.Normal)
        assertEquals(
            EditResult.Refused(EditRefusal.EndNotInFuture),
            f.service.edit(session.id, plannedEnd = f.clock.now),
        )
    }

    @Test
    fun aLockedSessionCanStillBeMadeStricter() = runTest {
        val session = f.startFor(1.hours, strictness = Strictness.Locked)
        val result = blockLists.addDomain(socialList.id, "tiktok.com")
        assertIs<DomainEditResult.Saved>(result)
        assertIs<EditResult.Applied>(f.service.edit(session.id, plannedEnd = session.plannedEnd + 1.hours))
    }
}
