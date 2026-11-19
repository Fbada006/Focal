package io.disruption.focal.core.blocklist

import io.disruption.focal.core.model.BlockList
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.BlockedSet
import io.disruption.focal.core.repository.BlockListRepository
import io.disruption.focal.core.repository.FocusSessionRepository
import io.disruption.focal.core.session.SessionLock
import io.disruption.focal.core.website.Domains
import io.disruption.focal.core.website.InvalidDomainReason
import io.disruption.focal.core.website.NormalisedDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/**
 * Rules for creating and editing Block Lists. Each edit is saved at once.
 *
 * Running Focus Sessions hold their own copy of what they block (ADR 0007), so editing a Block
 * List never weakens a running session. Deleting one that a running session uses is refused.
 */
class BlockListService(
    private val blockLists: BlockListRepository,
    private val sessions: FocusSessionRepository,
    private val newId: () -> String = { Uuid.random().toString() },
    private val lock: SessionLock = SessionLock(),
) {
    /** Serialises read-modify-write edits, so two quick edits can't overwrite each other. */
    private val edits = Mutex()

    fun observeAll(): Flow<List<BlockList>> = blockLists.observeAll()

    suspend fun get(id: BlockListId): BlockList? = blockLists.get(id)

    suspend fun create(name: String): BlockListResult {
        val cleanName = cleanName(name) ?: return BlockListResult.InvalidName
        val blockList = BlockList(BlockListId(newId()), cleanName, BlockedSet.Empty)
        blockLists.save(blockList)
        return BlockListResult.Saved(blockList)
    }

    suspend fun rename(id: BlockListId, name: String): BlockListResult {
        val cleanName = cleanName(name) ?: return BlockListResult.InvalidName
        return update(id) { it.copy(name = cleanName) }
    }

    /**
     * Refused while a running Focus Session uses the list, so deleting it can't end blocking. Holds
     * the [SessionLock], so no session can start on the list between the check and the delete.
     */
    suspend fun delete(id: BlockListId): DeleteResult = lock.mutex.withLock {
        edits.withLock {
            blockLists.get(id) ?: return DeleteResult.NotFound
            val users = sessions.getRunning().filter { it.blockListId == id }
            if (users.isNotEmpty()) return DeleteResult.InUse(users.map { it.name })
            blockLists.delete(id)
            DeleteResult.Deleted
        }
    }

    /** Add a typed domain, normalised first (`https://www.YouTube.com/x` becomes `youtube.com`). */
    suspend fun addDomain(id: BlockListId, input: String): DomainEditResult {
        val hostname = when (val normalised = Domains.normalise(input)) {
            is NormalisedDomain.Invalid -> return DomainEditResult.Invalid(normalised.reason)
            is NormalisedDomain.Valid -> normalised.hostname
        }
        return edits.withLock {
            val list = blockLists.get(id) ?: return DomainEditResult.NotFound
            if (hostname in list.blocked.domains) return DomainEditResult.AlreadyBlocked(hostname)
            blockLists.save(list.withDomains(list.blocked.domains + hostname))
            DomainEditResult.Saved(hostname)
        }
    }

    /** Replace [old] with a newly typed domain. */
    suspend fun editDomain(id: BlockListId, old: String, input: String): DomainEditResult {
        val hostname = when (val normalised = Domains.normalise(input)) {
            is NormalisedDomain.Invalid -> return DomainEditResult.Invalid(normalised.reason)
            is NormalisedDomain.Valid -> normalised.hostname
        }
        return edits.withLock {
            val list = blockLists.get(id) ?: return DomainEditResult.NotFound
            if (hostname != old && hostname in list.blocked.domains) return DomainEditResult.AlreadyBlocked(hostname)
            blockLists.save(list.withDomains(list.blocked.domains - old + hostname))
            DomainEditResult.Saved(hostname)
        }
    }

    suspend fun removeDomain(id: BlockListId, hostname: String): BlockListResult =
        update(id) { it.withDomains(it.blocked.domains - hostname) }

    /**
     * Pick or unpick one app. Reads the stored list under the edit lock, so quick taps on several
     * apps each land, instead of the last write overwriting the others.
     */
    suspend fun toggleApp(id: BlockListId, packageName: String): BlockListResult =
        update(id) {
            val apps = it.blocked.apps
            it.copy(blocked = it.blocked.copy(apps = if (packageName in apps) apps - packageName else apps + packageName))
        }

    /** Replace the list's individually picked apps. */
    suspend fun setApps(id: BlockListId, apps: Set<String>): BlockListResult =
        update(id) { it.copy(blocked = it.blocked.copy(apps = apps)) }

    /** Replace the list's App Categories. */
    suspend fun setAppCategories(id: BlockListId, categories: Set<String>): BlockListResult =
        update(id) { it.copy(blocked = it.blocked.copy(appCategories = categories)) }

    private suspend fun update(id: BlockListId, change: (BlockList) -> BlockList): BlockListResult = edits.withLock {
        val list = blockLists.get(id) ?: return BlockListResult.NotFound
        val updated = change(list)
        blockLists.save(updated)
        BlockListResult.Saved(updated)
    }

    private fun BlockList.withDomains(domains: Set<String>) = copy(blocked = blocked.copy(domains = domains))

    private fun cleanName(name: String): String? =
        name.trim().takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH }

    companion object {
        const val MAX_NAME_LENGTH = 40
    }
}

sealed interface BlockListResult {
    data class Saved(val blockList: BlockList) : BlockListResult
    data object InvalidName : BlockListResult
    data object NotFound : BlockListResult
}

sealed interface DeleteResult {
    data object Deleted : DeleteResult
    data object NotFound : DeleteResult

    /** Names of the running Focus Sessions using the list. */
    data class InUse(val sessionNames: List<String>) : DeleteResult
}

sealed interface DomainEditResult {
    data class Saved(val hostname: String) : DomainEditResult
    data class Invalid(val reason: InvalidDomainReason) : DomainEditResult
    data class AlreadyBlocked(val hostname: String) : DomainEditResult
    data object NotFound : DomainEditResult
}
