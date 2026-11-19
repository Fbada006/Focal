package io.disruption.focal.core.blocklist

import io.disruption.focal.core.session.SessionTestFixture
import io.disruption.focal.core.session.newsList
import io.disruption.focal.core.session.socialList
import io.disruption.focal.core.website.InvalidDomainReason
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours

class BlockListServiceTest {
    private val f = SessionTestFixture()
    private val service = BlockListService(f.blockLists, f.sessions, f.service, newId = { "new" })

    @Test
    fun createsAndRenamesWithATrimmedName() = runTest {
        val created = assertIs<BlockListResult.Saved>(service.create("  Evenings ")).blockList
        assertEquals("Evenings", created.name)
        assertEquals(true, created.blocked.isEmpty)

        val renamed = assertIs<BlockListResult.Saved>(service.rename(created.id, "Nights")).blockList
        assertEquals("Nights", renamed.name)
    }

    @Test
    fun refusesABlankOrOverlongName() = runTest {
        assertEquals(BlockListResult.InvalidName, service.create("   "))
        assertEquals(BlockListResult.InvalidName, service.create("x".repeat(41)))
        assertEquals(BlockListResult.InvalidName, service.rename(socialList.id, ""))
    }

    @Test
    fun deletingAListUsedByARunningSessionIsRefused() = runTest {
        f.startFor(1.hours, list = socialList)

        assertEquals(DeleteResult.InUse(listOf("Social")), service.delete(socialList.id))
        assertNotNull(f.blockLists.get(socialList.id))
    }

    @Test
    fun aListCanBeDeletedOnceItsSessionsHaveEnded() = runTest {
        val session = f.startFor(1.hours, list = socialList)
        f.service.endEarly(session.id)

        assertEquals(DeleteResult.Deleted, service.delete(socialList.id))
        assertNull(f.blockLists.get(socialList.id))
        assertEquals(DeleteResult.Deleted, service.delete(newsList.id))
    }

    @Test
    fun theIosPickerReplacesAppsAndCategoriesTogether() = runTest {
        val saved = assertIs<BlockListResult.Saved>(
            service.setAppSelection(socialList.id, setOf("app-token"), setOf("social")),
        ).blockList

        assertEquals(setOf("app-token"), saved.blocked.apps)
        assertEquals(setOf("social"), saved.blocked.appCategories)
    }

    @Test
    fun addedDomainsAreNormalised() = runTest {
        assertEquals(
            DomainEditResult.Saved("youtube.com"),
            service.addDomain(newsList.id, "https://www.YouTube.com/watch?v=1"),
        )
        assertEquals(setOf("news.example", "youtube.com"), f.blockLists.get(newsList.id)!!.blocked.domains)
    }

    @Test
    fun invalidAndDuplicateDomainsAreRefused() = runTest {
        assertEquals(DomainEditResult.Invalid(InvalidDomainReason.NoDot), service.addDomain(newsList.id, "youtube"))
        assertEquals(DomainEditResult.AlreadyBlocked("news.example"), service.addDomain(newsList.id, "NEWS.example"))
        assertEquals(setOf("news.example"), f.blockLists.get(newsList.id)!!.blocked.domains)
    }

    @Test
    fun domainsCanBeEditedAndRemoved() = runTest {
        service.addDomain(newsList.id, "reddit.com")

        assertEquals(DomainEditResult.Saved("old.reddit.com"), service.editDomain(newsList.id, "reddit.com", "old.reddit.com"))
        assertEquals(
            DomainEditResult.AlreadyBlocked("news.example"),
            service.editDomain(newsList.id, "old.reddit.com", "news.example"),
        )
        service.removeDomain(newsList.id, "news.example")

        assertEquals(setOf("old.reddit.com"), f.blockLists.get(newsList.id)!!.blocked.domains)
    }
}
