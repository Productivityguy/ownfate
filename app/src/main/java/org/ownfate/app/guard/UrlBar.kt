package org.ownfate.app.guard


import android.view.accessibility.AccessibilityNodeInfo

/**
 * Where each browser keeps its address bar, and how to read it.
 *
 * The table is a package map rather than a bare id list on purpose: a random app's `url_bar`
 * field must never be mistaken for a browser, so only the packages named here are read.
 *
 * Two kinds of id are supported. A normal view id (`id/url_bar`) is looked up directly. A Compose
 * test tag (`ADDRESSBAR_URL_BOX`) cannot be found by id, because it is not a resource id, so the
 * window is walked for it instead. Current Firefox, Fennec, IronFox, IceRaven and Focus use the
 * Compose bar and put the address in the node's `contentDescription` rather than its `text`.
 *
 * Several ids per browser are tried in order. Browsers rename their address bar now and then, and
 * an id that is gone simply finds nothing, so old entries can stay for older versions.
 */
object UrlBar {

    /** Current Firefox-family address bar: a Compose test tag, found by walking the tree. */
    private const val COMPOSE_TAG = "ADDRESSBAR_URL_BOX"

    /**
     * How many nodes a walk for a Compose tag looks at before giving up. The address bar sits at
     * the top or the bottom of the window and turns up early, while the page itself can run to
     * thousands of nodes.
     */
    private const val MAX_WALKED_NODES = 400

    private val CHROMIUM = listOf("id/url_bar")

    /** Older Firefox draws a real view; current Firefox draws a Compose bar. Try both. */
    private val FIREFOX = listOf("id/mozac_browser_toolbar_url_view", COMPOSE_TAG)

    private val BARS: Map<String, List<String>> = buildMap {
        // Chromium and the browsers built on it.
        for (pkg in listOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "com.google.android.apps.chrome",
            "org.chromium.chrome",
            "com.brave.browser",
            "com.brave.browser_beta",
            "com.brave.browser_dev",
            "com.brave.browser_nightly",
            "com.vivaldi.browser",
            "com.vivaldi.browser.snapshot",
            "com.microsoft.emmx",
            "com.microsoft.emmx.beta",
            "com.microsoft.emmx.dev",
            "com.microsoft.emmx.canary",
            "com.kiwibrowser.browser",
            "com.ecosia.android",
            "com.naver.whale",
            "org.cromite.cromite",
            "org.bromite.bromite",
            "app.vanadium.browser",
            "org.calyxos.chromium",
            "org.ungoogled.chromium.stable",
            "org.ungoogled.chromium.extensions.stable",
            "io.github.jqssun.helium",
            "foundation.e.browser"
        )) {
            put(pkg, CHROMIUM)
        }

        // Firefox and the browsers built on it. The address is usually a description, not text.
        for (pkg in listOf(
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.fenix",
            "org.mozilla.fennec_fdroid",
            "org.ironfoxoss.ironfox",
            "org.ironfoxoss.ironfox.nightly",
            "us.spotco.fennec_dos",
            "io.github.forkmaintainers.iceraven",
            "org.torproject.torbrowser",
            "org.torproject.torbrowser_alpha",
            "org.gnu.icecat",
            "com.qwant.liberty",
            "com.cookiejarapps.android.smartcookieweb",
            "com.iode.firefox",
            "org.mozilla.focus",
            "org.mozilla.focus.beta",
            "org.mozilla.focus.nightly",
            "org.mozilla.klar"
        )) {
            put(pkg, FIREFOX)
        }

        // The rest, each with an address bar of its own.
        put("com.sec.android.app.sbrowser", listOf("id/location_bar_edit_text", "id/url_bar"))
        put("com.sec.android.app.sbrowser.beta", listOf("id/location_bar_edit_text", "id/url_bar"))
        put("com.opera.browser", listOf("id/url_field", "id/address_field"))
        put("com.opera.browser.beta", listOf("id/url_field", "id/address_field"))
        put("com.opera.gx", listOf("id/addressbarEdit"))
        put("com.opera.mini.native", listOf("id/url_field"))
        put("com.opera.mini.native.beta", listOf("id/url_field"))
        put("com.duckduckgo.mobile.android", listOf("id/omnibarTextInput", "id/omniboxTextInput"))
        put("com.stoutner.privacybrowser.standard", listOf("id/url_edittext"))
        put("com.stoutner.privacybrowser.free", listOf("id/url_edittext"))
    }

    /** Whether [pkg] is a browser whose address bar OwnFate knows how to read. */
    fun isBrowser(pkg: String): Boolean = BARS.containsKey(pkg)

    /**
     * The address bar of [pkg] in [root] or, failing that, in [source], or null when neither shows
     * one.
     *
     * [source] is the node the accessibility event came from. A bar that only appears as the
     * event source, and is not reachable from the active window, is still the bar, and it costs
     * nothing to try.
     */
    fun find(
        root: AccessibilityNodeInfo?,
        source: AccessibilityNodeInfo?,
        pkg: String
    ): AccessibilityNodeInfo? {
        val ids = BARS[pkg] ?: return null
        for (id in ids) {
            findIn(root, pkg, id)?.let { return it }
            findIn(source, pkg, id)?.let { return it }
        }
        return null
    }

    /**
     * What [bar] says right now. Browsers show the address in `text`; the Compose bar leaves `text`
     * empty and describes itself instead, so fall back to `contentDescription`. Null when the bar
     * carries neither.
     */
    fun read(bar: AccessibilityNodeInfo): String? {
        val text = safe { bar.text?.toString() }
        if (!text.isNullOrBlank()) return text
        return safe { bar.contentDescription?.toString()?.takeIf { it.isNotBlank() } }
    }

    private fun findIn(
        node: AccessibilityNodeInfo?,
        pkg: String,
        id: String
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        // A dialog of another app, or the split-screen partner, can be the node we were handed.
        if (safe { node.packageName?.toString() } != pkg) return null
        return if (id.startsWith("id/")) {
            val nodes = runCatching { node.findAccessibilityNodeInfosByViewId("$pkg:$id") }
                .getOrNull().orEmpty()
            nodes.firstOrNull()
        } else {
            walkFor(node, id)
        }
    }

    /**
     * Finds the node whose view id is the Compose tag [tag], breadth first, so the bars at the
     * edges of the window come before the depths of the page.
     */
    private fun walkFor(root: AccessibilityNodeInfo, tag: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val rootCount = safe { root.childCount } ?: return null
        for (i in 0 until rootCount) safe { root.getChild(i) }?.let(queue::add)
        var walked = 0
        while (queue.isNotEmpty() && walked < MAX_WALKED_NODES) {
            val node = queue.removeFirst()
            walked++
            if (safe { node.viewIdResourceName } == tag) return node
            val count = safe { node.childCount } ?: continue
            for (i in 0 until count) safe { node.getChild(i) }?.let(queue::add)
        }
        return null
    }

    /** The system can recycle a node mid-walk; skip that node instead of failing the read. */
    private inline fun <T> safe(block: () -> T): T? =
        try {
            block()
        } catch (_: Exception) {
            null
        }
}
