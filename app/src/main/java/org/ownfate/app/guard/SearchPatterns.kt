package org.ownfate.app.guard


/**
 * Address-bar patterns for image and video search pages.
 *
 * The switch is about a *search*, not about every page that happens to show video: a YouTube
 * watch page is the destination, not a search, and blocking it made the whole site unreachable.
 * Only the query shapes that open a result grid are listed here.
 *
 * Pure and separate from the service so the false-positive cases can be tested without an
 * accessibility binding.
 */
object SearchPatterns {

    /** A short kind for the card, or null when [raw] is not an image or video search URL. */
    fun hit(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val lower = raw.lowercase()
        return when {
            lower.contains("tbm=isch") -> "image search"
            lower.contains("tbm=vid") -> "video search"
            lower.contains("/images/search") -> "image search"
            lower.contains("/videos/search") -> "video search"
            lower.contains("ia=images") || lower.contains("iax=images") -> "image search"
            lower.contains("ia=videos") || lower.contains("iax=videos") -> "video search"
            else -> null
        }
    }
}
