package io.disruption.focal.core.blocklist

import io.disruption.focal.core.model.BlockList
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.repository.BlockListRepository
import io.disruption.focal.core.session.FocusSessionService
import io.disruption.focal.core.session.SessionLength
import io.disruption.focal.core.session.SessionLock
import io.disruption.focal.core.session.StartResult
import io.disruption.focal.core.session.socialList
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.testing.FakeClock
import io.disruption.focal.testing.InMemoryBlockListRepository
import io.disruption.focal.testing.InMemoryFocusSessionRepository
import io.disruption.focal.testing.RecordingBlockingEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.hours

/** A repository that suspends on every read, so concurrent callers interleave as they would on Room. */
private class YieldingBlockListRepository(
    private val inner: BlockListRepository,
) : BlockListRepository by inner {
    override suspend fun get(id: BlockListId): BlockList? {
        yield()
        return inner.get(id).also { yield() }
    }
}

class BlockListDeleteLockTest {
    @Test
    fun aListCannotBeDeletedWhileASessionIsStartingOnIt() = runTest {
        val blockLists = YieldingBlockListRepository(InMemoryBlockListRepository(socialList))
        val sessions = InMemoryFocusSessionRepository()
        val lock = SessionLock()
        val sessionService = FocusSessionService(blockLists, sessions, RecordingBlockingEngine(), FakeClock(), lock = lock)
        val listService = BlockListService(blockLists, sessions, lock = lock)

        // The delete checks for running sessions first; the start lands while it is still deciding.
        val delete = async { listService.delete(socialList.id) }
        val start = async { sessionService.start(socialList.id, SessionLength.For(1.hours), Strictness.Normal) }

        val started = start.await()
        val deleted = delete.await()

        // Either order is fine, as long as a running session never ends up without its list.
        if (started is StartResult.Started) {
            assertIs<DeleteResult.InUse>(deleted)
            assertNotNull(blockLists.get(socialList.id))
        } else {
            assertIs<DeleteResult.Deleted>(deleted)
        }
    }
}
