package org.ownfate.app.ui


import android.view.View

/**
 * One kept-alive bottom-tab screen. The activity builds each screen once and only ever hides it,
 * so scroll position, a half-typed word list and the tab's view tree survive a tab switch.
 */
interface Screen {
    val view: View

    /** Rebuild the screen's data in place, keeping its scroll position. */
    fun refresh() = Unit

    /** The tab is being left. A transient confirmation or timer must not survive the switch. */
    fun onHide() = Unit
}
