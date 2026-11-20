package io.disruption.focal.core.website

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingAddressBarCheckTest {
    private val chrome = "com.android.chrome"
    private val firefox = "org.mozilla.firefox"

    @Test
    fun schedulesOneCheckPerBrowserUntilItRuns() {
        val pending = PendingAddressBarCheck()
        assertTrue(pending.offer(chrome, BrowserWindow.Active))
        assertFalse(pending.offer(chrome, BrowserWindow.Active))
        assertEquals(chrome, pending.take())
        assertNull(pending.browser)
        assertTrue(pending.offer(chrome, BrowserWindow.Active))
    }

    @Test
    fun aBackgroundBrowserCannotReplaceTheForegroundBrowsersCheck() {
        // Chrome is in front and just opened a page; Firefox, in the background, keeps changing.
        val pending = PendingAddressBarCheck()
        pending.offer(chrome, BrowserWindow.Active)
        assertFalse(pending.offer(firefox, BrowserWindow.OtherApp))
        assertFalse(pending.offer(firefox, BrowserWindow.NotReady))
        assertFalse(pending.offer(firefox, BrowserWindow.Unconfirmed))
        assertEquals(chrome, pending.browser)
    }

    @Test
    fun aBrowserConfirmedInFrontTakesOverThePendingCheck() {
        val pending = PendingAddressBarCheck()
        pending.offer(chrome, BrowserWindow.NotReady)
        assertTrue(pending.offer(firefox, BrowserWindow.Active))
        assertEquals(firefox, pending.browser)
    }

    @Test
    fun ignoresEventsFromABrowserInTheBackground() {
        val pending = PendingAddressBarCheck()
        assertFalse(pending.offer(firefox, BrowserWindow.OtherApp))
        assertNull(pending.browser)
    }

    @Test
    fun checksAgainWhileTheForegroundIsUnknown() {
        assertTrue(PendingAddressBarCheck().offer(chrome, BrowserWindow.NotReady))
        assertTrue(PendingAddressBarCheck().offer(chrome, BrowserWindow.Unconfirmed))
    }

    @Test
    fun leavingDropsThePendingCheck() {
        val pending = PendingAddressBarCheck()
        pending.offer(chrome, BrowserWindow.Active)
        pending.clear()
        assertNull(pending.take())
    }
}
