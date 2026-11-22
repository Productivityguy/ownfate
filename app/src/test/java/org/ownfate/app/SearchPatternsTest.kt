package org.ownfate.app

import org.ownfate.app.guard.SearchPatterns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The image/video search switch is narrow on purpose. It must follow a query URL, not a site:
 * blocking every YouTube watch page made the switch unusable and had nothing to do with search.
 */
class SearchPatternsTest {

    @Test fun googleImageSearch() =
        assertEquals("image search", SearchPatterns.hit("https://www.google.com/search?q=cats&tbm=isch"))

    @Test fun googleVideoSearch() =
        assertEquals("video search", SearchPatterns.hit("https://www.google.com/search?q=cats&tbm=vid"))

    @Test fun bingImageSearch() =
        assertEquals("image search", SearchPatterns.hit("https://www.bing.com/images/search?q=cats"))

    @Test fun bingVideoSearch() =
        assertEquals("video search", SearchPatterns.hit("https://www.bing.com/videos/search?q=cats"))

    @Test fun duckDuckGoImages() =
        assertEquals("image search", SearchPatterns.hit("https://duckduckgo.com/?q=cats&iax=images&ia=images"))

    @Test fun duckDuckGoVideos() =
        assertEquals("video search", SearchPatterns.hit("https://duckduckgo.com/?q=cats&iax=videos&ia=videos"))

    @Test fun youTubeWatchPageIsNotASearch() =
        assertNull(SearchPatterns.hit("https://www.youtube.com/watch?v=abc123"))

    @Test fun youTubeSearchIsNotAnImageOrVideoSearchPage() =
        assertNull(SearchPatterns.hit("https://www.youtube.com/results?search_query=cats"))

    @Test fun ordinaryPageIsNotASearch() =
        assertNull(SearchPatterns.hit("https://example.com/article/about-cats"))

    @Test fun blankIsNotASearch() = assertNull(SearchPatterns.hit("  "))
}
