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
