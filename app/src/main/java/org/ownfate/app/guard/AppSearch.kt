package org.ownfate.app.guard

import android.view.accessibility.AccessibilityNodeInfo

/**
 * The in-app search screens that the page word lists cannot see.
 *
 * Instagram, X, Reddit and Telegram each hide an unbounded feed behind their own search box. The
 * page itself may look innocent until the query is submitted, so these add-ons answer the search
 * intent instead: when one of these apps puts a search field in front and that field is focused or
 * already carries a query, the guard steps back before the results arrive.
 *
 * Only a field that names itself as a search box counts. Every one of these apps also has editable
 * comment, caption and message boxes, and blocking those would make the app unusable. The id, hint
 * or content description has to contain "search", which covers the platform SearchView id
 * (`search_src_text`) and the per-app ids built on it.
 */
internal enum class SearchSurface(
    val label: String,
    val packages: Set<String>,
    /**
     * Ids that name a search *screen* rather than an editable field. Reddit and Telegram use ids
     * that appear before (or instead of) a field that reports focus, so those ids are evidence on
     * their own. Empty when the field name is enough.
     */
    val screenIds: Set<String> = emptySet()
) {
    INSTAGRAM("Instagram", setOf("com.instagram.android", "com.instagram.lite")),
    X("X (Twitter)", setOf("com.twitter.android")),
    REDDIT(
        "Reddit",
        setOf("com.reddit.frontpage", "com.reddit.frontpage.beta"),
        setOf("expanded_search_field")
    ),
    TELEGRAM(
        "Telegram",
        setOf(
            "org.telegram.messenger",
            "org.telegram.messenger.beta",
            "org.telegram.messenger.web",
            "org.telegram.plus",
            "org.telegram.messenger.huawei",
            "org.thunderdog.challegram"
        ),
        setOf("search_section_global")
    );

    companion object {
        private val ALL = entries

        /** The add-on [pkg] belongs to, or null for every other app. */
        fun of(pkg: String): SearchSurface? = ALL.firstOrNull { pkg in it.packages }

        /**
         * The surface named by an accessibility `package:id/entry` string, or null.
         *
         * The entry is matched exactly (after the package prefix): `expanded_search_field` is a
         * Reddit screen, while some other app's `not_expanded_search_field` is not. Callers still
         * compare the result with [of] for the foreground package, so an id copied by another app
         * cannot borrow this app's switch.
         */
        fun screenOf(id: String?): SearchSurface? {
            if (id == null) return null
            // Compose screens expose a bare test tag as the view id ("expanded_search_field"),
            // while classic Views report "package:id/entry". Compare the entry in both cases.
            val entry = id.substringAfterLast('/')
            return ALL.firstOrNull { entry in it.screenIds }
        }
    }
}

internal object AppSearch {

    /**
     * True when the field is editable and names itself as a search box.
     *
     * This is the whole rule, with the node's reads already done, so it can be tested on the JVM.
     * An editable flag is passed as well as the class name because a Compose field may report the
     * generic View class.
     */
    fun isSearchField(
        className: String?,
        editable: Boolean,
        id: String?,
        hint: String?,
        description: String?
    ): Boolean {
        val editBox = className?.contains("EditText") == true || editable
        return editBox && namesSearch(id, hint, description)
    }

    /**
     * True while the search box is in use: it has input focus, or it holds a query that is not
     * just the placeholder. A search field that is merely present on the feed does not count, so
     * opening the app is not mistaken for opening search.
     */
    fun isActive(
        focused: Boolean,
        text: String?,
        hint: String?,
        description: String?
    ): Boolean {
        if (focused) return true
        val query = text?.trim().orEmpty()
        if (query.isEmpty()) return false
        return !query.equals("search", ignoreCase = true) &&
            !query.equals(hint?.trim(), ignoreCase = true) &&
            !query.equals(description?.trim(), ignoreCase = true)
    }

    /** The id, hint or description has to name a search box. */
    fun namesSearch(id: String?, hint: String?, description: String?): Boolean =
        listOf(id, hint, description).any { it?.contains("search", ignoreCase = true) == true }

    // ---- node adapters ----------------------------------------------------

    /** True when [node] is an editable field that names itself as a search box. */
    fun isSearchField(node: AccessibilityNodeInfo?, className: String? = null): Boolean {
        if (node == null) return false
        val cls = className ?: safe { node.className?.toString() }
        return isSearchField(
            cls,
            safe { node.isEditable } == true,
            safe { node.viewIdResourceName },
            safe { node.hintText?.toString() },
            safe { node.contentDescription?.toString() }
        )
    }

    /** The node form of the active-search rule. */
    fun isActive(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        return isActive(
            safe { node.isFocused } == true,
            safe { node.text?.toString() },
            safe { node.hintText?.toString() },
            safe { node.contentDescription?.toString() }
        )
    }

    private inline fun <T> safe(block: () -> T): T? =
        try {
            block()
        } catch (_: Exception) {
            null
        }
}
