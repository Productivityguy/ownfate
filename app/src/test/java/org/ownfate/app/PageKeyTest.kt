package org.ownfate.app

import org.ownfate.app.guard.PageKey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * One identity: the place a pass covers. The cases below are the difference between a pass that
 * is usable on a live page and one that ends every second.
 */
class PageKeyTest {

    @Test fun thePlaceIsTheAddressWhenThereIsOne() {
        assertEquals(
            PageKey.place("https://example.com/watch?v=1", 7),
            PageKey.place("https://example.com/watch?v=1", 99)
        )
    }

    @Test fun aDifferentAddressIsADifferentPlace() {
        assertNotEquals(
            PageKey.place("https://example.com/a", 7),
            PageKey.place("https://example.com/b", 7)
        )
    }

    @Test fun aWindowIsThePlaceWhenThereIsNoAddress() {
        assertEquals(PageKey.place(null, 42), PageKey.place("", 42))
        assertNotEquals(PageKey.place(null, 42), PageKey.place(null, 43))
    }

    @Test fun aDescriptionPlaceDropsTheTitle() {
        assertEquals(
            PageKey.place("Watch https://example.com/watch?v=1. Search or enter address", 1),
            PageKey.place("Another title https://example.com/watch?v=1. Search or enter address", 2)
        )
    }

    @Test fun aDescriptionWithoutAnAddressFallsBackToTheWindow() {
        assertEquals(PageKey.place("Search or enter address", 5), PageKey.place("", 5))
        assertNotEquals(
            PageKey.place("Search or enter address", 5),
            PageKey.place("Search or enter address", 6)
        )
    }
}
