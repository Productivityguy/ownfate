package org.ownfate.app

import org.ownfate.app.guard.UrlBar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only known browser packages have their address bars read. A random app with a field called
 * `url_bar` must never be treated as a browser just because its id happens to match.
 */
class UrlBarTest {

    @Test fun recognizesChromiumAndFirefoxFamilies() {
        for (pkg in listOf(
            "com.android.chrome",
            "com.brave.browser",
            "org.mozilla.firefox",
            "org.mozilla.fenix",
            "org.ironfoxoss.ironfox",
            "org.mozilla.focus",
            "com.duckduckgo.mobile.android"
        )) {
            assertTrue(pkg, UrlBar.isBrowser(pkg))
        }
    }

    @Test fun leavesOtherAppsAlone() {
        for (pkg in listOf(
            "com.instagram.android",
            "org.telegram.messenger",
            "com.reddit.frontpage",
            "com.android.settings",
            "org.ownfate.app",
            ""
        )) {
            assertFalse(pkg, UrlBar.isBrowser(pkg))
        }
    }
}
