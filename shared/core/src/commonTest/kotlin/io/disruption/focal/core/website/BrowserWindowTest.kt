package io.disruption.focal.core.website

import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserWindowTest {
    private val chrome = "com.android.chrome"
    private val focal = "io.disruption.focal"

    @Test
    fun readsTheBarOnlyWhenTheBrowserIsTheActiveWindow() {
        assertEquals(BrowserWindow.Active, BrowserWindow.of(chrome, chrome, focal))
    }

    @Test
    fun ignoresABrowserEventWhenAnotherAppIsInFront() {
        // A late window event from Chrome while Instagram is the active window: Instagram stays in front.
        assertEquals(BrowserWindow.OtherApp, BrowserWindow.of(chrome, "com.instagram.android", focal))
        assertEquals(BrowserWindow.OtherApp, BrowserWindow.of(chrome, "org.mozilla.firefox", focal))
    }

    @Test
    fun trustsTheBrowserWhileTheActiveWindowIsNotReady() {
        assertEquals(BrowserWindow.NotReady, BrowserWindow.of(chrome, null, focal))
    }

    @Test
    fun trustsTheBrowserWhileTheShieldIsTheActiveWindow() {
        // Switching from a Shielded app to Chrome: the Shield is still on top when Chrome's event arrives.
        assertEquals(BrowserWindow.NotReady, BrowserWindow.of(chrome, focal, focal))
    }
}
