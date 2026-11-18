package io.disruption.focal.ui.blocklists

import io.disruption.focal.core.blocklist.BlockListService
import io.disruption.focal.core.model.BlockList
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.BlockedSet
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Strictness
import io.disruption.focal.core.session.FocusSessionService
import io.disruption.focal.testing.FakeClock
import io.disruption.focal.testing.FakeSuggestedWebsites
import io.disruption.focal.testing.InMemoryBlockListRepository
import io.disruption.focal.testing.RecordingBlockingEngine
import io.disruption.focal.testing.InMemoryFocusSessionRepository
import io.disruption.focal.ui.MainDispatcherTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class BlockListViewModelsTest : MainDispatcherTest() {
    private val social = BlockList(BlockListId("social"), "Social", BlockedSet(apps = setOf("a"), domains = setOf("x.com", "y.com")))
    private val blockLists = InMemoryBlockListRepository(social)
    private val sessions = InMemoryFocusSessionRepository()
    private val sessionService = FocusSessionService(blockLists, sessions, RecordingBlockingEngine(), FakeClock())
    private val service = BlockListService(blockLists, sessions, sessionService, newId = { "new" })

    @Test
    fun listsAreShownWithASummary() = runTest {
        val vm = BlockListsViewModel(service)

        assertEquals(listOf(BlockListRow("social", "Social", "1 app · 2 websites")), vm.state.value.lists)
    }

    @Test
    fun creatingAListOpensItsEditor() = runTest {
        val vm = BlockListsViewModel(service)
        vm.onIntent(BlockListsIntent.NewClicked)
        vm.onIntent(BlockListsIntent.NewNameChanged("Work"))
        vm.onIntent(BlockListsIntent.CreateConfirmed)

        assertEquals(BlockListsEffect.OpenEditor("new"), vm.effects.first())
        assertNull(vm.state.value.newList)
        assertEquals("Work", blockLists.get(BlockListId("new"))?.name)
    }

    @Test
    fun aBlankNameKeepsTheDialogOpenWithAnError() = runTest {
        val vm = BlockListsViewModel(service)
        vm.onIntent(BlockListsIntent.NewClicked)
        vm.onIntent(BlockListsIntent.CreateConfirmed)

        assertNotNull(vm.state.value.newList?.error)
    }

    @Test
    fun typedDomainsAreAddedInTheirNormalisedForm() = runTest {
        val vm = BlockListEditorViewModel("social", service, FakeSuggestedWebsites)
        vm.onIntent(BlockListEditorIntent.DomainInputChanged("https://www.Reddit.com/r/all"))
        vm.onIntent(BlockListEditorIntent.DomainSubmitted)

        assertEquals(listOf("reddit.com", "x.com", "y.com"), vm.state.value.domains)
        assertEquals("", vm.state.value.domainInput)
        assertNull(vm.state.value.domainError)
    }

    @Test
    fun invalidDomainInputShowsAnErrorAndKeepsTheText() = runTest {
        val vm = BlockListEditorViewModel("social", service, FakeSuggestedWebsites)
        vm.onIntent(BlockListEditorIntent.DomainInputChanged("reddit"))
        vm.onIntent(BlockListEditorIntent.DomainSubmitted)

        assertEquals("Include the ending, such as .com", vm.state.value.domainError)
        assertEquals("reddit", vm.state.value.domainInput)
        assertEquals(listOf("x.com", "y.com"), vm.state.value.domains)
    }

    @Test
    fun domainsCanBeEditedAndRemoved() = runTest {
        val vm = BlockListEditorViewModel("social", service, FakeSuggestedWebsites)
        vm.onIntent(BlockListEditorIntent.EditDomain("x.com"))
        vm.onIntent(BlockListEditorIntent.DomainInputChanged("m.x.com"))
        vm.onIntent(BlockListEditorIntent.DomainSubmitted)
        vm.onIntent(BlockListEditorIntent.RemoveDomain("y.com"))

        assertEquals(listOf("m.x.com"), vm.state.value.domains)
        assertNull(vm.state.value.editingDomain)
    }

    @Test
    fun deletingAListInUseIsRefusedWithAnExplanation() = runTest {
        val start = Instant.parse("2026-01-05T09:00:00Z")
        sessions.save(
            FocusSession(FocusSessionId("s"), social.id, "Social", Strictness.Locked, start, start + 1.hours, social.blocked),
        )
        val vm = BlockListEditorViewModel("social", service, FakeSuggestedWebsites)
        vm.onIntent(BlockListEditorIntent.DeleteClicked)
        vm.onIntent(BlockListEditorIntent.DeleteConfirmed)

        val refusal = assertNotNull(vm.state.value.deleteRefusal)
        assertTrue("Social is running" in refusal)
        assertFalse(vm.state.value.confirmingDelete)
        assertNotNull(blockLists.get(social.id))
    }

    @Test
    fun deletingAnUnusedListClosesTheEditor() = runTest {
        val vm = BlockListEditorViewModel("social", service, FakeSuggestedWebsites)
        vm.onIntent(BlockListEditorIntent.DeleteClicked)
        vm.onIntent(BlockListEditorIntent.DeleteConfirmed)

        assertEquals(BlockListEditorEffect.Closed, vm.effects.first())
        assertNull(blockLists.get(social.id))
    }

    @Test
    fun renamingTrimsAndRefusesBlankNames() = runTest {
        val vm = BlockListEditorViewModel("social", service, FakeSuggestedWebsites)
        vm.onIntent(BlockListEditorIntent.NameChanged("  "))
        vm.onIntent(BlockListEditorIntent.NameCommitted)
        assertNotNull(vm.state.value.nameError)

        vm.onIntent(BlockListEditorIntent.NameChanged(" Feeds "))
        vm.onIntent(BlockListEditorIntent.NameCommitted)
        assertEquals("Feeds", blockLists.get(social.id)?.name)
        assertEquals("Feeds", vm.state.value.name)
    }
}

class SuggestedWebsitesTest : MainDispatcherTest() {
    private val f = io.disruption.focal.ui.SessionUiFixture()

    @Test
    fun aSuggestionIsSelectedWhenAllItsDomainsAreBlockedAndTogglesThem() = runTest {
        val vm = BlockListEditorViewModel("social", f.blockLists, FakeSuggestedWebsites)
        assertEquals(listOf(false, false), vm.state.value.suggestions.map { it.selected })

        vm.onIntent(BlockListEditorIntent.SuggestionToggled("youtube"))
        assertEquals(listOf("youtu.be", "youtube.com"), vm.state.value.domains)
        assertEquals(true, vm.state.value.suggestions.single { it.id == "youtube" }.selected)

        vm.onIntent(BlockListEditorIntent.SuggestionToggled("youtube"))
        assertEquals(emptyList(), vm.state.value.domains)
    }
}
