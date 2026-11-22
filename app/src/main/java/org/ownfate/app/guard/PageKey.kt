package org.ownfate.app.guard

import org.ownfate.app.data.HostList
import org.ownfate.app.filter.Fold
import org.ownfate.app.ui.page

/**
 * Where the user is, for a pass.
 *
 * A place deliberately ignores the page's words, because a feed changes them on every scroll: a
 * pass that ended as soon as the next card scrolled into view was re-asking the user to appeal
 * the same screen. A place is the folded address when one is readable, otherwise the app's
 * current window. Scrolling keeps it; a real navigation (a new address, or a new activity window)
 * does not.
 */
object PageKey {

    /**
     * The place a pass covers. The address is enough: navigating changes it, while scrolling and
     * lazy-loaded content do not. A Firefox-style description carries the page title too, so only
     * its address word is used; a title that changes while the page does not must not end a pass.
     * An app with no readable address bar has no URL to follow, so its current window is the
     * place; the window survives a scroll but not a new activity.
     */
    fun place(address: String?, windowId: Int): String {
        val page = if (address.isNullOrBlank()) {
            null
        } else {
            HostList.addressFromDescription(address)
                ?: HostList.hostOf(address)?.let { address }
        }
        return if (page != null) "u:" + Fold.fold(page) else "w:$windowId"
    }
}
