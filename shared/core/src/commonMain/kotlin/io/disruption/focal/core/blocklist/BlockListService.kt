package io.disruption.focal.core.blocklist

import io.disruption.focal.core.model.BlockList
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.BlockedSet
import io.disruption.focal.core.repository.BlockListRepository
import io.disruption.focal.core.repository.FocusSessionRepository
import io.disruption.focal.core.repository.ScheduleRepository
import io.disruption.focal.core.session.BlockListChangeReport
import io.disruption.focal.core.session.FocusSessionService
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
 * Running Focus Sessions hold their own copy of what they block (ADR 0007). Additions reach the
 * running sessions that use the list at once; removals reach Delayed and Locked sessions only
 * after they end, and results say which sessions deferred them. Deleting a list that a running
 * session uses is refused.
 */
class BlockListService(
    private val blockLists: BlockListRepository,
    private val sessions: FocusSessionRepository,
    private val sessionService: FocusSessionService,
    private val newId: () -> String = { Uuid.random().toString() },
    private val lock: SessionLock = SessionLock(),
    /** Schedules that would start sessions on a list; deleting a list they use is refused. */
    private val schedules: ScheduleRepository? = null,
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
    suspend fun delete(id: BlockListId): DeleteResult = edits.withLock {
        // Same order as edits, which call into the session service: edits, then the session lock.
        lock.mutex.withLock {
            blockLists.get(id) ?: return DeleteResult.NotFound
            val users = sessions.getRunning().filter { it.blockListId == id }
            if (users.isNotEmpty()) return DeleteResult.InUse(users.map { it.name })
            val scheduled = schedules?.getAll().orEmpty().filter { it.blockListId == id }
            if (scheduled.isNotEmpty()) return DeleteResult.UsedBySchedules(scheduled.map { it.name })
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
            val report = saveAndPropagate(list.withDomains(list.blocked.domains + hostname))
            DomainEditResult.Saved(hostname, report.removalsDeferredFor)
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
            val report = saveAndPropagate(list.withDomains(list.blocked.domains - old + hostname))
            DomainEditResult.Saved(hostname, report.removalsDeferredFor)
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

    /** Add already-normalised domains, such as a Suggested Website's. */
    suspend fun addDomains(id: BlockListId, hostnames: Set<String>): BlockListResult =
        update(id) { it.withDomains(it.blocked.domains + hostnames.mapNotNull(Domains::normaliseOrNull)) }

    suspend fun removeDomains(id: BlockListId, hostnames: Set<String>): BlockListResult =
        update(id) { it.withDomains(it.blocked.domains - hostnames) }

    /** Replace the list's individually picked apps. */
    suspend fun setApps(id: BlockListId, apps: Set<String>): BlockListResult =
        update(id) { it.copy(blocked = it.blocked.copy(apps = apps)) }

    /** Pick or unpick one App Category, reading the stored list under the edit lock (see [toggleApp]). */
    suspend fun toggleAppCategory(id: BlockListId, key: String): BlockListResult =
        update(id) {
            val categories = it.blocked.appCategories
            it.copy(blocked = it.blocked.copy(appCategories = if (key in categories) categories - key else categories + key))
        }

    /** Replace the list's App Categories. */
    suspend fun setAppCategories(id: BlockListId, categories: Set<String>): BlockListResult =
        update(id) { it.copy(blocked = it.blocked.copy(appCategories = categories)) }

    private suspend fun update(id: BlockListId, change: (BlockList) -> BlockList): BlockListResult = edits.withLock {
        val list = blockLists.get(id) ?: return BlockListResult.NotFound
        val updated = change(list)
        val report = saveAndPropagate(updated)
        BlockListResult.Saved(updated, report.removalsDeferredFor)
    }

    private suspend fun saveAndPropagate(list: BlockList): BlockListChangeReport {
        blockLists.save(list)
        return sessionService.blockListChanged(list)
    }

    private fun BlockList.withDomains(domains: Set<String>) = copy(blocked = blocked.copy(domains = domains))

    private fun cleanName(name: String): String? =
        name.trim().takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH }

    companion object {
        const val MAX_NAME_LENGTH = 40
    }
}

sealed interface BlockListResult {
    /**
     * [removalsDeferredFor] names running Delayed or Locked sessions that keep blocking removed
     * items until they end ("Changes take effect after *Wind down* ends").
     */
    data class Saved(val blockList: BlockList, val removalsDeferredFor: List<String> = emptyList()) : BlockListResult
    data object InvalidName : BlockListResult
    data object NotFound : BlockListResult
}

sealed interface DeleteResult {
    data object Deleted : DeleteResult
    data object NotFound : DeleteResult

    /** Names of the running Focus Sessions using the list. */
    data class InUse(val sessionNames: List<String>) : DeleteResult

    /** Names of the Schedules that start sessions on the list; it would leave them with nothing. */
    data class UsedBySchedules(val scheduleNames: List<String>) : DeleteResult
}

sealed interface DomainEditResult {
    data class Saved(val hostname: String, val removalsDeferredFor: List<String> = emptyList()) : DomainEditResult
    data class Invalid(val reason: InvalidDomainReason) : DomainEditResult
    data class AlreadyBlocked(val hostname: String) : DomainEditResult
    data object NotFound : DomainEditResult
}
