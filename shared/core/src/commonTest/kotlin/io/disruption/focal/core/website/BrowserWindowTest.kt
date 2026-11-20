package io.disruption.focal.core.website

import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserWindowTest {
    private val chrome = "com.android.chrome"
    private val instagram = "com.instagram.android"
    private val focal = "io.disruption.focal"

    @Test
    fun readsTheBarOnlyWhenTheBrowserIsTheForegroundApp() {
        assertEquals(BrowserWindow.Active, BrowserWindow.of(chrome, foregroundApp = chrome, shieldedApp = null))
        // Switching from a Shielded app to Chrome: the Shield still covers the screen, but the app
        // window underneath is Chrome's.
        assertEquals(BrowserWindow.Active, BrowserWindow.of(chrome, foregroundApp = chrome, shieldedApp = instagram))
    }

    @Test
    fun ignoresABrowserEventWhenAnotherAppIsInFront() {
        // A late window event from Chrome while Instagram is in front: Instagram stays in front.
        assertEquals(BrowserWindow.OtherApp, BrowserWindow.of(chrome, foregroundApp = instagram, shieldedApp = null))
        assertEquals(BrowserWindow.OtherApp, BrowserWindow.of(chrome, foregroundApp = "org.mozilla.firefox", shieldedApp = null))
        // Focal's own screen in front (not its Shield, which is no app window) is another app too.
        assertEquals(BrowserWindow.OtherApp, BrowserWindow.of(chrome, foregroundApp = focal, shieldedApp = null))
    }

    @Test
    fun aBackgroundBrowserEventNeverLiftsTheShieldOfTheAppInFront() {
        // Instagram is Shielded and still in front; Chrome's late event must not replace it.
        assertEquals(BrowserWindow.OtherApp, BrowserWindow.of(chrome, foregroundApp = instagram, shieldedApp = instagram))
        // Even when the foreground window can't be read, the Shield stays until Chrome is confirmed.
        assertEquals(BrowserWindow.Unconfirmed, BrowserWindow.of(chrome, foregroundApp = null, shieldedApp = instagram))
    }

    @Test
    fun trustsTheBrowserWhileTheForegroundIsUnknownAndNothingElseIsShielded() {
        assertEquals(BrowserWindow.NotReady, BrowserWindow.of(chrome, foregroundApp = null, shieldedApp = null))
        // The Shield is over a blocked site in this same browser: its word is as good as any.
        assertEquals(BrowserWindow.NotReady, BrowserWindow.of(chrome, foregroundApp = null, shieldedApp = chrome))
    }
}
