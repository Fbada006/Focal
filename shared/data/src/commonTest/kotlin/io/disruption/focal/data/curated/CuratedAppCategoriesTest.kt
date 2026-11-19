package io.disruption.focal.data.curated

import io.disruption.focal.core.apps.AppCategory
import io.disruption.focal.core.apps.AppCategoryResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CuratedAppCategoriesTest {
    private val resolver = AppCategoryResolver(CuratedAppCategories.byPackage)

    @Test
    fun curatedCategoryWinsOverAndroidsOwn() {
        // YouTube may report itself as video or nothing; Focal knows it.
        assertEquals(AppCategory.Video, resolver.categoryOf("com.google.android.youtube", systemCategory = null))
        assertEquals(AppCategory.Social, resolver.categoryOf("com.reddit.frontpage", systemCategory = "news"))
    }

    @Test
    fun fallsBackToAndroidsCategory() {
        assertEquals(AppCategory.Games, resolver.categoryOf("com.example.newgame", systemCategory = "games"))
        assertNull(resolver.categoryOf("com.example.notes", systemCategory = null))
        assertNull(resolver.categoryOf("com.example.maps", systemCategory = "maps"))
    }

    @Test
    fun anAppInABlockedCategoryIsBlockedEvenIfInstalledLater() {
        val blocked = setOf(AppCategory.Games.key)
        assertTrue(resolver.isBlocked("com.example.installedtoday", "games", blocked))
        assertFalse(resolver.isBlocked("com.instagram.android", "social", blocked))
    }

    @Test
    fun everySuggestedWebsitesAppHasACategory() {
        val packages = CuratedSuggestedWebsites.services.flatMap { it.androidPackages }
        assertEquals(emptyList(), packages.filter { it !in CuratedAppCategories.byPackage })
    }
}
