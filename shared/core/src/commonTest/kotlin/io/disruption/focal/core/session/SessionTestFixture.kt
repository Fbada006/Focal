package io.disruption.focal.core.session

import io.disruption.focal.core.model.BlockList
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.BlockedSet
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.testing.FakeClock
import io.disruption.focal.testing.InMemoryBlockListRepository
import io.disruption.focal.testing.InMemoryFocusSessionRepository
import io.disruption.focal.testing.RecordingBlockingEngine
import kotlin.test.assertIs
import kotlin.time.Duration

val socialList = BlockList(
    id = BlockListId("social"),
    name = "Social",
    blocked = BlockedSet(apps = setOf("com.instagram.android"), domains = setOf("instagram.com")),
)
val newsList = BlockList(
    id = BlockListId("news"),
    name = "News",
    blocked = BlockedSet(apps = setOf("com.news.app"), domains = setOf("news.example")),
)

/** The session service wired to fakes, as every service test uses it. */
class SessionTestFixture(vararg lists: BlockList = arrayOf(socialList, newsList)) {
    val clock = FakeClock()
    val blockLists = InMemoryBlockListRepository(*lists)
    val sessions = InMemoryFocusSessionRepository()
    val engine = RecordingBlockingEngine()
    private var nextId = 0
    val service = FocusSessionService(blockLists, sessions, engine, clock, newId = { "s${++nextId}" })

    suspend fun startFor(
        duration: Duration,
        list: BlockList = socialList,
        strictness: Strictness = Strictness.Normal,
    ): FocusSession {
        val result = service.start(list.id, SessionLength.For(duration), strictness)
        return assertIs<StartResult.Started>(result).session
    }

    suspend fun stored(session: FocusSession): FocusSession = sessions.get(session.id)!!
}
