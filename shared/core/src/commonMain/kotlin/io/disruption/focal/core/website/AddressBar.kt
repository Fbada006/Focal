package io.disruption.focal.core.website

/**
 * The browsers whose address bar Focal reads on Android to block websites (ADR 0005), as data:
 * package name → the view IDs of its address bar, tried in order. Teaching Focal a new browser, or
 * a browser's renamed view, is a change here only; the accessibility service never names a browser.
 * The list is documented in `docs/android-blocking-engine.md`.
 */
object SupportedBrowsers {
    val addressBarIds: Map<String, List<String>> = buildMap {
        // Chromium browsers keep Chrome's omnibox view, `url_bar`.
        for (chromium in listOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.vivaldi.browser",
            "com.kiwibrowser.browser",
        )) {
            put(chromium, listOf("$chromium:id/url_bar"))
        }
        put(
            "com.sec.android.app.sbrowser",
            listOf(
                "com.sec.android.app.sbrowser:id/location_bar_edit_text",
                "com.sec.android.app.sbrowser:id/custom_tab_toolbar_url_bar_text",
            ),
        )
        for (firefox in listOf("org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix")) {
            put(firefox, listOf("$firefox:id/mozac_browser_toolbar_url_view"))
        }
        put("com.opera.browser", listOf("com.opera.browser:id/url_field"))
        put("com.duckduckgo.mobile.android", listOf("com.duckduckgo.mobile.android:id/omnibarTextInput"))
    }

    val packageNames: Set<String> get() = addressBarIds.keys

    fun isSupported(packageName: String): Boolean = packageName in addressBarIds
}

/** What the accessibility service read from a browser's address bar. */
data class AddressBarReading(
    /** The bar's text; empty when it only shows its hint ("Search or type web address"). */
    val text: String,
    /** The bar has input focus: the user is typing, so [text] is not a page the browser shows. */
    val isEditing: Boolean,
)

/**
 * Which website a supported browser is showing, from successive address-bar readings (T26).
 *
 * Only a committed URL counts: while the user types in the bar, the page underneath is still the
 * last one committed, so the partial text is ignored. Navigation commits when the bar loses focus
 * and the page changes, which the next reading picks up. A reading where the bar can't be found
 * (toolbar scrolled away, full-screen video, Firefox swapping in its edit view) also keeps the last
 * committed site. Text that isn't a hostname (search terms, `about:blank`, an empty new tab) means
 * no website is shown.
 */
class CommittedSite {
    private var browser: String? = null
    private var host: String? = null

    /**
     * Record a reading from [packageName] (null when the bar wasn't found) and return the hostname
     * to check against running sessions, or null when the browser shows no website.
     */
    fun update(packageName: String, reading: AddressBarReading?): String? {
        if (packageName != browser) {
            browser = packageName
            host = null
        }
        if (reading == null || reading.isEditing) return host
        host = Domains.normaliseOrNull(reading.text)
        return host
    }

    /** Another app came to the foreground: the next browser reading starts afresh. */
    fun reset() {
        browser = null
        host = null
    }
}

/**
 * Whether an event from a supported browser can be trusted, given which app owns the foreground
 * window. Accessibility events arrive late and from windows in the background, so only the
 * foreground window says what is really in front.
 */
enum class BrowserWindow {
    /** The browser owns the foreground window: read its address bar. */
    Active,

    /** The foreground window is unknown (views not laid out yet) and nothing is Shielded: take the browser's word for now. */
    NotReady,

    /**
     * The foreground window is unknown while another app is Shielded. Keep that app and its Shield
     * (lifting it needs proof the user left), and check the browser again shortly.
     */
    Unconfirmed,

    /** Another app owns the foreground window: the event is stale or from the background, ignore it. */
    OtherApp,
    ;

    companion object {
        /**
         * [foregroundApp] is the package of the app window in front, underneath Focal's own Shield
         * (which is an overlay, not an app window), or null when it can't be read yet.
         * [shieldedApp] is the app the Shield currently covers, if any.
         */
        fun of(browser: String, foregroundApp: String?, shieldedApp: String?): BrowserWindow =
            when (foregroundApp) {
                browser -> Active
                null -> if (shieldedApp != null && shieldedApp != browser) Unconfirmed else NotReady
                else -> OtherApp
            }
    }
}

/**
 * The one address-bar check waiting to run (content changes arrive many times a second, so checks
 * are throttled). A background browser's event must not replace the foreground browser's check,
 * or the page it just opened would never be read.
 */
class PendingAddressBarCheck {
    /** The browser whose address bar is due a check, or null when none is pending. */
    var browser: String? = null
        private set

    /**
     * An event from [browser], classified by [BrowserWindow.of]. Returns true when a check for
     * [browser] must now be scheduled (replacing any pending one); false when the event is ignored
     * or a check for it is already pending.
     */
    fun offer(browser: String, window: BrowserWindow): Boolean {
        if (window == BrowserWindow.OtherApp || browser == this.browser) return false
        // Only a browser known to be in front may take over another browser's pending check.
        if (this.browser != null && window != BrowserWindow.Active) return false
        this.browser = browser
        return true
    }

    /** The check is running: return its browser and clear it. */
    fun take(): String? = browser.also { browser = null }

    fun clear() {
        browser = null
    }
}
