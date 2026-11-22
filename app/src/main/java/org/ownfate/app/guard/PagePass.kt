package org.ownfate.app.guard


/**
 * The unlock that follows a correctly typed sentence.
 *
 * It is deliberately small and deliberately in memory. There is no allowlist to edit and nothing
 * survives a service restart or a reboot: a pass covers one place in one app for five minutes,
 * and then it is gone.
 *
 * A place is built by [PageKey.place]: the folded address when the app has one, otherwise the
 * app's current window. It deliberately does not include the page's words, so scrolling a feed
 * and lazy-loading the next screenful do not end the pass; a new address or a new activity does.
 * A pass granted from typed text has no place yet — the search has not been submitted — so it
 * covers the screen it was granted on and binds to the next place the user moves to, which is the
 * results page.
 */
object PagePass {

    const val TTL_MS = 5 * 60 * 1000L

    data class Pass(
        val pkg: String,
        val place: String?,
        val at: Long,
        /** For a placeless pass: the screen it was granted on, covered until the first move. */
        val seen: String? = null
    )

    private var held: Pass? = null

    fun grant(pkg: String, place: String?, now: Long = System.currentTimeMillis()) {
        held = Pass(pkg, place, now)
    }

    fun clear() {
        held = null
    }

    /** The live pass, or null. Expired passes are dropped here, so callers never see a stale one. */
    fun current(now: Long = System.currentTimeMillis()): Pass? {
        val p = held ?: return null
        if (now - p.at >= TTL_MS) {
            held = null
            return null
        }
        return p
    }

    fun minutesLeft(now: Long = System.currentTimeMillis()): Int {
        val p = current(now) ?: return 0
        val left = TTL_MS - (now - p.at)
        return ((left + 59_999) / 60_000).toInt()
    }

    /**
     * True when [place] is covered. A placeless pass — granted from typed text, before the search
     * was submitted — covers the screen it was granted on and binds to the next place it sees, so
     * "type, unlock, press enter" leaves the results page open without unlocking the place after
     * it.
     */
    fun active(pkg: String, place: String, now: Long = System.currentTimeMillis()): Boolean {
        val p = current(now) ?: return false
        if (p.pkg != pkg) return false
        if (p.place == null) {
            if (p.seen == null) {
                held = p.copy(seen = place)
                return true
            }
            if (p.seen == place) return true
            held = p.copy(place = place, seen = null)
            return true
        }
        return p.place == place
    }
}
