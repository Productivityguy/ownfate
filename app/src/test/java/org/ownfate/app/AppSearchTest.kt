package org.ownfate.app

import org.ownfate.app.guard.AppSearch
import org.ownfate.app.guard.SearchSurface
import org.ownfate.app.guard.Switches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The add-on rules without an accessibility binding: which packages belong to which app, and when
 * a field counts as a search box that is actually in use.
 *
 * The field checks are the pure form of the node reads, which keeps the JVM tests free of the
 * Android framework. The card wording and the BACK path are exercised on a device.
 */
class AppSearchTest {

    // ---- package mapping --------------------------------------------------

    @Test fun instagramPackagesMapToInstagram() {
        for (pkg in listOf("com.instagram.android", "com.instagram.lite")) {
            assertEquals(pkg, SearchSurface.INSTAGRAM, SearchSurface.of(pkg))
        }
    }

    @Test fun twitterPackagesMapToX() {
        assertEquals(SearchSurface.X, SearchSurface.of("com.twitter.android"))
    }

    @Test fun redditPackagesMapToReddit() {
        for (pkg in listOf("com.reddit.frontpage", "com.reddit.frontpage.beta")) {
            assertEquals(pkg, SearchSurface.REDDIT, SearchSurface.of(pkg))
        }
    }

    @Test fun telegramVariantsMapToTelegram() {
        for (pkg in listOf(
            "org.telegram.messenger",
            "org.telegram.messenger.beta",
            "org.telegram.messenger.web",
            "org.telegram.plus",
            "org.telegram.messenger.huawei",
            "org.thunderdog.challegram"
        )) {
            assertEquals(pkg, SearchSurface.TELEGRAM, SearchSurface.of(pkg))
        }
    }

    @Test fun ordinaryAppsAreNotSearchSurfaces() {
        assertNull(SearchSurface.of("com.android.chrome"))
        assertNull(SearchSurface.of("org.ownfate.app"))
        assertNull(SearchSurface.of(""))
    }

    // ---- screen ids -------------------------------------------------------

    @Test fun redditSearchScreenIdMapsToReddit() {
        assertEquals(
            SearchSurface.REDDIT,
            SearchSurface.screenOf("com.reddit.frontpage:id/expanded_search_field")
        )
        assertEquals(
            SearchSurface.REDDIT,
            SearchSurface.screenOf("com.reddit.frontpage.beta:id/expanded_search_field")
        )
        // Reddit's Compose screen reports a bare test tag, with no package prefix.
        assertEquals(SearchSurface.REDDIT, SearchSurface.screenOf("expanded_search_field"))
    }

    @Test fun telegramSearchScreenIdMapsToTelegram() {
        assertEquals(
            SearchSurface.TELEGRAM,
            SearchSurface.screenOf("org.telegram.messenger:id/search_section_global")
        )
        assertEquals(
            SearchSurface.TELEGRAM,
            SearchSurface.screenOf("org.thunderdog.challegram:id/search_section_global")
        )
    }

    @Test fun screenIdMatchingIsExact() {
        assertNull(SearchSurface.screenOf("com.reddit.frontpage:id/not_expanded_search_field"))
        assertNull(SearchSurface.screenOf("com.reddit.frontpage:id/expanded_search_field_extra"))
        assertNull(SearchSurface.screenOf("not_expanded_search_field"))
        assertNull(SearchSurface.screenOf("com.example:id/search"))
        assertNull(SearchSurface.screenOf(null))
    }

    @Test fun instagramAndXHaveNoScreenIds() {
        assertTrue(SearchSurface.INSTAGRAM.screenIds.isEmpty())
        assertTrue(SearchSurface.X.screenIds.isEmpty())
    }

    @Test fun everySurfaceHasALabel() {
        for (surface in SearchSurface.values()) {
            assertTrue(surface.name, surface.label.isNotBlank())
            assertTrue(surface.name, surface.packages.isNotEmpty())
        }
    }

    // ---- what counts as a search field ------------------------------------

    @Test fun thePlatformSearchIdIsASearchField() =
        assertTrue(
            AppSearch.isSearchField(
                className = "android.widget.EditText",
                editable = true,
                id = "com.instagram.android:id/search_src_text",
                hint = null,
                description = null
            )
        )

    @Test fun aComposeFieldIsFoundByItsDescription() =
        assertTrue(
            AppSearch.isSearchField(
                className = "android.view.View",
                editable = true,
                id = null,
                hint = null,
                description = "Search"
            )
        )

    @Test fun aSearchHintIsEnough() =
        assertTrue(
            AppSearch.isSearchField(
                className = "android.widget.EditText",
                editable = true,
                id = null,
                hint = "Search for people",
                description = null
            )
        )

    @Test fun matchingIgnoresCase() =
        assertTrue(
            AppSearch.isSearchField(
                className = "android.widget.EditText",
                editable = true,
                id = null,
                hint = "SEARCH",
                description = null
            )
        )

    @Test fun aCommentBoxIsNotASearchField() =
        assertFalse(
            AppSearch.isSearchField(
                className = "android.widget.EditText",
                editable = true,
                id = "com.instagram.android:id/comment_text",
                hint = "Add a comment…",
                description = null
            )
        )

    @Test fun aMessageBoxIsNotASearchField() =
        assertFalse(
            AppSearch.isSearchField(
                className = "android.widget.EditText",
                editable = true,
                id = "org.telegram.messenger:id/chat_message_input",
                hint = "Message",
                description = null
            )
        )

    @Test fun aSearchLabeledTextViewIsNotEditable() =
        assertFalse(
            AppSearch.isSearchField(
                className = "android.widget.TextView",
                editable = false,
                id = "com.twitter.android:id/search",
                hint = null,
                description = null
            )
        )

    // ---- when the search box is in use ------------------------------------

    @Test fun aFocusedEmptyFieldIsActive() =
        assertTrue(AppSearch.isActive(true, text = null, hint = "Search", description = null))

    @Test fun anUnfocusedEmptyFieldIsIdle() =
        assertFalse(AppSearch.isActive(false, text = null, hint = "Search", description = null))

    @Test fun aBlankQueryIsIdle() =
        assertFalse(AppSearch.isActive(false, text = "   ", hint = null, description = null))

    @Test fun aRealQueryIsActive() =
        assertTrue(AppSearch.isActive(false, text = "cats", hint = null, description = null))

    @Test fun thePlaceholderIsNotAQuery() =
        assertFalse(AppSearch.isActive(false, text = "Search", hint = "Search", description = null))

    @Test fun thePlaceholderTextIsNotAQueryEvenWithoutAHint() =
        assertFalse(AppSearch.isActive(false, text = "search", hint = null, description = null))

    @Test fun theDescriptionAsTextIsNotAQuery() =
        assertFalse(
            AppSearch.isActive(false, text = "Search", hint = null, description = "Search")
        )

    @Test fun aPaddedPlaceholderIsNotAQuery() =
        assertFalse(AppSearch.isActive(false, text = "  Search  ", hint = "Search", description = null))

    // ---- the switch each surface reads ------------------------------------

    @Test fun eachSurfaceReadsItsOwnSwitch() {
        val allOff = switches()
        for (surface in SearchSurface.values()) {
            assertFalse(surface.name, allOff.search(surface))
        }
        assertTrue(switches(instagramSearch = true).search(SearchSurface.INSTAGRAM))
        assertTrue(switches(xSearch = true).search(SearchSurface.X))
        assertTrue(switches(redditSearch = true).search(SearchSurface.REDDIT))
        assertTrue(switches(telegramSearch = true).search(SearchSurface.TELEGRAM))
    }

    @Test fun oneAddOnDoesNotEnableTheOthers() {
        val onlyReddit = switches(redditSearch = true)
        assertTrue(onlyReddit.search(SearchSurface.REDDIT))
        assertFalse(onlyReddit.search(SearchSurface.INSTAGRAM))
        assertFalse(onlyReddit.search(SearchSurface.X))
        assertFalse(onlyReddit.search(SearchSurface.TELEGRAM))
    }

    private fun switches(
        instagramSearch: Boolean = false,
        xSearch: Boolean = false,
        redditSearch: Boolean = false,
        telegramSearch: Boolean = false
    ) = Switches(
        adultSites = false,
        adultWords = false,
        imageVideoSearch = false,
        gamblingSites = false,
        predictionMarkets = false,
        protect = false,
        power = false,
        instagramSearch = instagramSearch,
        xSearch = xSearch,
        redditSearch = redditSearch,
        telegramSearch = telegramSearch
    )
}
