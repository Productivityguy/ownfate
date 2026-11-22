package org.ownfate.app.guard

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

/**
 * One guarded read of whatever window is in front.
 *
 * Accessibility nodes are owned by the system and can be recycled while a walk is running, so
 * every read is guarded separately: one stale node is skipped, the rest of the window is still
 * read. A single unguarded read would take the whole service down.
 *
 * The reader also owns the walk limits. The text cap keeps a pathological page from copying
 * megabytes into a match buffer, and the toggle flag answers "did this Settings page actually
 * carry a switch?" for the off-switch check.
 */
internal class PageReader(private val service: AccessibilityService) {

    /**
     * A Compose screen can put a field twenty or more containers below the window root, so the
     * old depth cap of 18 stopped before Reddit's search field existed. The node and text caps
     * still bound the walk; only the depth is raised.
     */
    private companion object {
        const val MAX_DEPTH = 60
        const val MAX_NODES = 900
        const val MAX_TEXT = 3500
    }

    private var nodes = 0

    /** Set by the last walk: the window carried a checkable toggle. */
    var sawToggle = false
        private set

    /** Set by the last walk: a search field is present and focused or already holds a query. */
    var searchActive = false
        private set

    /**
     * Set by the last walk: a visible search field exists, whether or not it is focused.
     *
     * Dismissing the keyboard can take focus off Instagram's field while its search screen is
     * still open. The recheck needs presence for those apps, or it would stop after the one BACK
     * that only closed the IME.
     */
    var searchFieldPresent = false
        private set

    /**
     * Set by the last walk: a visible node id named one of the add-on search screens.
     *
     * Reddit's Compose field reports a bare test tag (`expanded_search_field`) as its view id, so
     * the id is compared by its entry after the last slash, not through the view-id lookup API.
     * An invisible node is ignored: apps keep nodes for screens that have already been left.
     */
    var searchScreen: SearchSurface? = null
        private set

    /** Set by [readSearch] once the answer is known, so the rest of the tree is skipped. */
    private var stopWalk = false

    /** Clear the walk flags before a fresh walk whose result is reused by another check. */
    fun reset() {
        nodes = 0
        sawToggle = false
        searchActive = false
        searchFieldPresent = false
        searchScreen = null
        stopWalk = false
    }

    // ---- reading ----------------------------------------------------------

    fun ownWindow(root: AccessibilityNodeInfo?): Boolean =
        safe { root?.packageName?.toString() } == service.packageName

    /** The package of the window in front, or null when the system will not say. */
    fun activePackage(): String? =
        safe { service.rootInActiveWindow?.packageName?.toString() }

    /**
     * Accessibility nodes are owned by the system and can be recycled while a walk is running.
     * Every read is guarded separately: one stale node is skipped, the rest of the window is still
     * read. A single unguarded read here would take the whole service down.
     */
    inline fun <T> safe(block: () -> T): T? =
        try {
            block()
        } catch (_: Exception) {
            null
        }

    fun collect(
        n: AccessibilityNodeInfo?,
        out: StringBuilder,
        depth: Int,
        stopAtSearch: Boolean = false
    ) {
        if (stopWalk || n == null || depth > MAX_DEPTH || out.length > MAX_TEXT || nodes > MAX_NODES) return
        nodes++
        if (safe { n.isCheckable } == true) sawToggle = true
        val cls = safe { n.className?.toString() }
        if (cls != null && (cls.endsWith("Switch") || cls.contains("ToggleButton") ||
                cls.contains("CompoundButton"))
        ) {
            sawToggle = true
        }
        // A screen the user cannot see is not the screen in front. Compose keeps nodes for
        // screens that have been left (or are behind another one) in the tree for a while; those
        // nodes must not set either search flag, or a normal feed is read as a search screen.
        val visible = safe { n.isVisibleToUser } == true
        if (visible && searchScreen == null) {
            searchScreen = SearchSurface.screenOf(safe { n.viewIdResourceName })
        }
        if (visible && AppSearch.isSearchField(n, cls)) {
            searchFieldPresent = true
            if (AppSearch.isActive(n)) searchActive = true
        }
        // A password field is never page text: its contents are not evidence of anything the
        // guard is meant to see, and reading it would put a credential in the match buffer.
        if (safe { n.isPassword } != true) {
            safe { n.text }?.let { if (it.isNotEmpty()) out.append(it).append('\n') }
            safe { n.contentDescription }?.let { if (it.isNotEmpty()) out.append(it).append('\n') }
            safe { n.hintText }?.let { if (it.isNotEmpty()) out.append(it).append('\n') }
        }
        // A search screen has been found, which is the whole question [readSearch] was asked.
        // Do not spend the rest of the tree on a page that is about to be left.
        if (stopAtSearch && (searchActive || searchScreen != null)) {
            stopWalk = true
            return
        }
        val count = safe { n.childCount } ?: return
        for (i in 0 until count) collect(safe { n.getChild(i) }, out, depth + 1, stopAtSearch)
    }

    /** A short window around the match, so the card can name what it saw without a wall of text. */
    fun around(text: String, term: String): String {
        val flat = text.replace('\n', ' ').trim()
        if (flat.length <= 140) return flat
        val needle = term.substringBefore('+').lowercase()
        val i = flat.lowercase().indexOf(needle)
        if (i < 0) return flat.take(140)
        val start = (i - 60).coerceAtLeast(0)
        val end = (i + 100).coerceAtMost(flat.length)
        return (if (start > 0) "…" else "") + flat.substring(start, end) +
            (if (end < flat.length) "…" else "")
    }

    /** One full walk of the window's text, reused for the deep match and the log line. */
    fun readText(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        nodes = 0
        searchActive = false
        searchFieldPresent = false
        searchScreen = null
        stopWalk = false
        val sb = StringBuilder()
        collect(root, sb, 0)
        return sb.toString()
    }

    /**
     * The fastest possible look for an add-on search screen: the walk stops the moment the
     * answer is known. Used by the always-on search poll, where the next few milliseconds are
     * better spent leaving than reading the rest of a feed.
     */
    fun readSearch(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        nodes = 0
        sawToggle = false
        searchActive = false
        searchFieldPresent = false
        searchScreen = null
        stopWalk = false
        val sb = StringBuilder()
        collect(root, sb, 0, stopAtSearch = true)
        return sb.toString()
    }
}
