package io.disruption.focal.ui

import io.disruption.focal.core.blocklist.BlockListService
import io.disruption.focal.core.model.BlockList
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.BlockedSet
import io.disruption.focal.core.schedule.ScheduleService
import io.disruption.focal.core.session.FocusSessionService
import io.disruption.focal.core.time.TimeZoneProvider
import io.disruption.focal.testing.FakeClock
import io.disruption.focal.testing.InMemoryBlockListRepository
import io.disruption.focal.testing.InMemoryFocusSessionRepository
import io.disruption.focal.testing.InMemoryScheduleRepository
import io.disruption.focal.testing.RecordingBlockingEngine
import kotlinx.datetime.TimeZone

val social = BlockList(BlockListId("social"), "Social", BlockedSet(apps = setOf("com.instagram.android")))
val empty = BlockList(BlockListId("empty"), "Empty", BlockedSet.Empty)

/** Real domain services over in-memory fakes, for view model tests. */
class SessionUiFixture(vararg lists: BlockList = arrayOf(social, empty)) {
    val clock = FakeClock()
    val zone = TimeZoneProvider { TimeZone.UTC }
    val blockListRepository = InMemoryBlockListRepository(*lists)
    val sessionRepository = InMemoryFocusSessionRepository()
    val engine = RecordingBlockingEngine()
    private var ids = 0
    val sessions = FocusSessionService(blockListRepository, sessionRepository, engine, clock, newId = { "s${++ids}" })
    val blockLists = BlockListService(blockListRepository, sessionRepository, sessions)
    val scheduleRepository = InMemoryScheduleRepository()
    val schedules = ScheduleService(scheduleRepository, blockListRepository, sessions, zone, newId = { "sch" })
}
