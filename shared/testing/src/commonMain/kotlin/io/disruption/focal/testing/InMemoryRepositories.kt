package io.disruption.focal.testing

import io.disruption.focal.core.model.BlockList
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.repository.BlockListRepository
import io.disruption.focal.core.repository.FocusSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class InMemoryBlockListRepository(vararg initial: BlockList) : BlockListRepository {
    private val lists = MutableStateFlow(initial.associateBy { it.id })

    override fun observeAll(): Flow<List<BlockList>> = lists.map { it.values.sortedBy { list -> list.name } }
    override suspend fun get(id: BlockListId): BlockList? = lists.value[id]
    override suspend fun save(blockList: BlockList) = lists.update { it + (blockList.id to blockList) }
    override suspend fun delete(id: BlockListId) = lists.update { it - id }
}

class InMemoryFocusSessionRepository(vararg initial: FocusSession) : FocusSessionRepository {
    private val sessions = MutableStateFlow(initial.associateBy { it.id })

    val all: List<FocusSession> get() = sessions.value.values.toList()

    override fun observeRunning(): Flow<List<FocusSession>> = sessions.map { running(it.values) }
    override fun observeAll(): Flow<List<FocusSession>> =
        sessions.map { it.values.sortedByDescending { session -> session.startedAt } }
    override suspend fun getRunning(): List<FocusSession> = running(sessions.value.values)
    override suspend fun get(id: FocusSessionId): FocusSession? = sessions.value[id]
    override suspend fun save(session: FocusSession) = sessions.update { it + (session.id to session) }

    private fun running(all: Collection<FocusSession>) = all.filter { it.isRunning }.sortedBy { it.plannedEnd }
}
