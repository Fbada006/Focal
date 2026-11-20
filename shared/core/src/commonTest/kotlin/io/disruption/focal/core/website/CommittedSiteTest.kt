package io.disruption.focal.core.website

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommittedSiteTest {
    private val chrome = "com.android.chrome"
    private val firefox = "org.mozilla.firefox"

    private fun shown(text: String) = AddressBarReading(text, isEditing = false)
    private fun typing(text: String) = AddressBarReading(text, isEditing = true)

    @Test
    fun readsTheHostnameFromWhatTheBarShows() {
        val site = CommittedSite()
        assertEquals("m.youtube.com", site.update(chrome, shown("m.youtube.com/watch?v=1")))
        assertEquals("reddit.com", site.update(chrome, shown("https://www.reddit.com/r/all")))
    }

    @Test
    fun ignoresTextWhileTheUserIsTyping() {
        val site = CommittedSite()
        site.update(chrome, shown("wikipedia.org"))
        assertEquals("wikipedia.org", site.update(chrome, typing("youtu")))
        assertEquals("wikipedia.org", site.update(chrome, typing("youtube.com")))
        // Pressing Go commits: the bar loses focus and shows the new page.
        assertEquals("youtube.com", site.update(chrome, shown("youtube.com")))
    }

    @Test
    fun keepsTheLastSiteWhenTheBarIsNotOnScreen() {
        val site = CommittedSite()
        site.update(firefox, shown("youtube.com"))
        assertEquals("youtube.com", site.update(firefox, null))
    }

    @Test
    fun searchTermsAndEmptyTabsShowNoWebsite() {
        val site = CommittedSite()
        site.update(chrome, shown("youtube.com"))
        assertNull(site.update(chrome, shown("cat videos")))
        assertNull(site.update(chrome, shown("")))
        assertNull(site.update(chrome, shown("about:blank")))
    }

    @Test
    fun anotherBrowserOrAppStartsAfresh() {
        val site = CommittedSite()
        site.update(chrome, shown("youtube.com"))
        assertNull(site.update(firefox, null))

        site.update(chrome, shown("youtube.com"))
        site.reset()
        assertNull(site.update(chrome, typing("you")))
    }

    @Test
    fun everyAddressBarIdBelongsToItsBrowser() {
        assertTrue(SupportedBrowsers.isSupported(chrome))
        assertFalse(SupportedBrowsers.isSupported("com.instagram.android"))
        for ((packageName, ids) in SupportedBrowsers.addressBarIds) {
            assertTrue(ids.isNotEmpty(), packageName)
            for (id in ids) assertTrue(id.startsWith("$packageName:id/"), id)
        }
    }
}
