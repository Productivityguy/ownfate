package org.ownfate.app.guard

/**
 * Which switches are on, read once per event.
 *
 * A single snapshot keeps one decision consistent: the same event cannot see a list switched on
 * halfway through. Defaults are off, so a fresh install reads the page and changes nothing.
 */
internal data class Switches(
    val adultSites: Boolean,
    val adultWords: Boolean,
    val imageVideoSearch: Boolean,
    val gamblingSites: Boolean,
    val predictionMarkets: Boolean,
    val protect: Boolean,
    val power: Boolean,
    val instagramSearch: Boolean,
    val xSearch: Boolean,
    val redditSearch: Boolean,
    val telegramSearch: Boolean
) {
    /** The add-on switch that owns [surface]. */
    fun search(surface: SearchSurface): Boolean = when (surface) {
        SearchSurface.INSTAGRAM -> instagramSearch
        SearchSurface.X -> xSearch
        SearchSurface.REDDIT -> redditSearch
        SearchSurface.TELEGRAM -> telegramSearch
    }
}
