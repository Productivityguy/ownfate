package org.ownfate.app

import org.ownfate.app.guard.PagePass

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A pass covers one place in one app for at most five minutes. The place is built by the guard
 * with [PageKey.place]; here it is just a string.
 */
class PagePassTest {

    @Before fun reset() = PagePass.clear()

    @After fun tearDown() = PagePass.clear()

    @Test fun samePageInSameAppPasses() {
        PagePass.grant("com.android.chrome", "u:example.com/page", now = 0)
        assertTrue(PagePass.active("com.android.chrome", "u:example.com/page", now = 1_000))
    }

    @Test fun scrollingDoesNotEndThePass() {
        // A feed changes its words on every scroll. The place is the window, not the viewport, so
        // the same screen keeps its pass until the user actually navigates.
        PagePass.grant("com.google.android.youtube", "w:42", now = 0)
        assertTrue(PagePass.active("com.google.android.youtube", "w:42", now = 1_000))
        assertTrue(PagePass.active("com.google.android.youtube", "w:42", now = 2_000))
    }

    @Test fun aNewWindowDoesNotPass() {
        PagePass.grant("com.google.android.youtube", "w:42", now = 0)
        assertFalse(PagePass.active("com.google.android.youtube", "w:43", now = 1_000))
    }

    @Test fun differentPageDoesNotPass() {
        PagePass.grant("com.android.chrome", "u:example.com/page", now = 0)
        assertTrue(PagePass.active("com.android.chrome", "u:example.com/page", now = 1_000))
        assertFalse(PagePass.active("com.android.chrome", "u:example.com/other", now = 2_000))
    }

    @Test fun differentAppDoesNotPass() {
        PagePass.grant("com.android.chrome", "u:example.com/page", now = 0)
        assertFalse(PagePass.active("com.brave.browser", "u:example.com/page", now = 1_000))
    }

    @Test fun keylessPassCoversTheSearchScreenAndBindsOnTheNextPage() {
        // A pass granted from typed text has no page yet: the search has not been submitted.
        // It covers the screen it was granted on without binding, so the burst poller cannot
        // pin it to the search box before the user presses enter.
        PagePass.grant("com.android.chrome", null, now = 0)
        assertTrue(PagePass.active("com.android.chrome", "u:search", now = 1_000))
        assertTrue(PagePass.active("com.android.chrome", "u:search", now = 2_000))
        // Submitting the search is the first move: the pass binds to the results page.
        assertTrue(PagePass.active("com.android.chrome", "u:search/results", now = 3_000))
        assertTrue(PagePass.active("com.android.chrome", "u:search/results", now = 4_000))
        assertFalse(PagePass.active("com.android.chrome", "u:search/other", now = 5_000))
    }

    @Test fun expiresAtFiveMinutes() {
        PagePass.grant("com.android.chrome", "u:page", now = 0)
        assertTrue(PagePass.active("com.android.chrome", "u:page", now = PagePass.TTL_MS - 1))
        assertFalse(PagePass.active("com.android.chrome", "u:page", now = PagePass.TTL_MS))
    }

    @Test fun expiryDropsThePass() {
        PagePass.grant("com.android.chrome", "u:page", now = 0)
        assertFalse(PagePass.active("com.android.chrome", "u:page", now = PagePass.TTL_MS))
        assertNull(PagePass.current(now = PagePass.TTL_MS + 1))
    }

    @Test fun clearEndsItEarly() {
        PagePass.grant("com.android.chrome", "u:page", now = 0)
        PagePass.clear()
        assertFalse(PagePass.active("com.android.chrome", "u:page", now = 1_000))
    }

    @Test fun minutesLeftCountsUp() {
        PagePass.grant("com.android.chrome", "u:page", now = 0)
        assertEquals(5, PagePass.minutesLeft(now = 1))          // just under five
        assertEquals(1, PagePass.minutesLeft(now = 4 * 60_000)) // just under one
        assertEquals(0, PagePass.minutesLeft(now = PagePass.TTL_MS))
    }
}
