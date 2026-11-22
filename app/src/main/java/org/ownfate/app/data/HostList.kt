package org.ownfate.app.data


import android.content.Context
import android.util.Log
import java.net.IDN
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One bundled host list.
 *
 * The guard reads the browser's address bar and matches what it finds; it does not run a VPN or
 * a DNS proxy. Each list is an asset inside the APK, so the app still asks for no `INTERNET`
 * permission and the list cannot change behind the user's back.
 *
 * A host is matched on its own and on every parent domain, on a label boundary: `m.foo.com` tries
 * `m.foo.com`, then `foo.com`. A bare `notfoo.com` never matches `foo.com`, and a bare TLD is
 * never consulted. The lists also carry the bare form of every `www.`-only entry, so both
 * `foo.com` and `www.foo.com` are covered.
 *
 * Only the address bar is matched. Page text is deliberately ignored here: a page that merely
 * mentions a domain is the keyword engine's job, not the site list's.
 */
class HostList(
    private val asset: String,
    val expectedCount: Int
) {

    @Volatile
    private var domains: HashSet<String>? = null

    private val loading = AtomicBoolean(false)

    /**
     * Read the asset once, off the main thread. Safe to call repeatedly: only the first call
     * starts the load. A browser that opens before the parse finishes simply skips site matching
     * for a moment; the keyword engine still runs.
     */
    fun load(ctx: Context) {
        if (domains != null || !loading.compareAndSet(false, true)) return
        val app = ctx.applicationContext
        Thread({
            try {
                val set = HashSet<String>(expectedCount + 1024)
                app.assets.open(asset).bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (raw in lines) normalizeDomain(raw)?.let { set.add(it) }
                }
                domains = set
            } catch (t: Throwable) {
                Log.w(TAG, "could not read $asset", t)
            } finally {
                loading.set(false)
            }
        }, "ownfate-hostlist").start()
    }

    /** The matched domain, or null. [raw] is usually the address-bar text. */
    fun match(raw: String?): String? = matchHost(hostOf(raw), domains)

    /**
     * The matched domain from an address bar's description, or null.
     *
     * Current Firefox-family bars leave `text` empty and put the page title, the address and their
     * own hint into `contentDescription`, for example
     * `"Example - Wikipedia en.wikipedia.org. Search or enter address"`. The hint is localised,
     * so it is cut off at the last full stop and the remaining words are searched from the end
     * for one that names a host. A plain bar's text still goes through [match].
     */
    fun matchDescription(raw: String?): String? = matchHost(hostFromDescription(raw), domains)

    companion object {

        private const val TAG = "OwnFateHostList"

        /** Punctuation that surrounds a word in a description, never part of a host. */
        private const val PUNCTUATION = "()[]{}<>,.;:!?'\"“”‘’"

        // ---- pure helpers, kept testable without Android ------------------

        internal fun normalizeDomain(raw: String): String? {
            val folded = foldDots(raw.trim()).lowercase(Locale.ROOT).trimEnd('.')
            if (folded.isEmpty() || folded.startsWith("#") || folded.startsWith(".")) return null
            if (!folded.contains('.')) return null
            if (folded.contains("..")) return null
            val d = asciiHost(folded) ?: return null
            if (d.length > 253) return null
            for (c in d) {
                if (!(c.isLetterOrDigit() || c == '.' || c == '-' || c == '_')) return null
            }
            return d
        }

        /**
         * The host part of whatever an address bar is showing, or null when the text is not a host.
         * Handles a scheme, userinfo, port, path, query, fragment, uppercase and a trailing dot.
         */
        internal fun hostOf(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            var s = foldDots(raw.trim()).lowercase(Locale.ROOT)

            val scheme = s.indexOf("://")
            if (scheme >= 0) s = s.substring(scheme + 3)

            s = s.substringBefore('/').substringBefore('?').substringBefore('#')
            s = s.substringAfterLast('@')

            // IPv6 literals are not in the list and a colon inside them is not a port.
            if (s.startsWith('[')) return null
            val colon = s.indexOf(':')
            if (colon >= 0) s = s.substring(0, colon)

            s = s.trimEnd('.')
            if (s.isEmpty() || s.length > 253) return null
            if (!s.contains('.')) return null
            if (s.startsWith('.') || s.contains("..")) return null
            s = asciiHost(s) ?: return null
            if (s.length > 253) return null
            for (c in s) {
                if (!(c.isLetterOrDigit() || c == '.' || c == '-' || c == '_')) return null
            }
            return s
        }

        /** Unicode dot variants that browsers and copied links use in place of ".". */
        private fun foldDots(s: String): String {
            if (s.none { it == '。' || it == '．' || it == '｡' }) return s
            return buildString(s.length) {
                for (c in s) append(if (c == '。' || c == '．' || c == '｡') '.' else c)
            }
        }

        /**
         * Punycode for a non-ASCII host, ASCII unchanged. The bundled lists store punycode, so an
         * address bar showing the Unicode form of a host still matches. An invalid name is rejected.
         */
        private fun asciiHost(s: String): String? {
            if (s.all { it.code < 128 }) return s
            return try {
                IDN.toASCII(s, IDN.ALLOW_UNASSIGNED)
            } catch (_: Exception) {
                null
            }
        }

        /**
         * The address token inside an address-bar description, or null.
         *
         * Firefox-style bars describe the page as `title address hint`, with the hint being the
         * browser's own localised text after a full stop. The address is the word immediately
         * before that hint, so a title that changes while the page does not cannot affect the
         * result. A description with no hint at all is treated as a bare address.
         */
        internal fun addressFromDescription(raw: String?): String? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            val head = text.substringBeforeLast(". ", text).trim()
            val last = head.substringAfterLast(' ').trim { it in PUNCTUATION }
            return last.takeIf { it.isNotEmpty() && hostOf(it) != null }
        }

        /**
         * The host inside an address-bar description, or null.
         *
         * When the bar has a hint the address is the word immediately before it; nothing earlier
         * is read, so a page title that happens to contain a blocked domain cannot be mistaken
         * for one. A description with no hint at all is searched from the end for a host.
         */
        internal fun hostFromDescription(raw: String?): String? {
            addressFromDescription(raw)?.let { return hostOf(it) }

            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null

            // A hint is present but the word before it was not an address: do not go fishing in
            // the title for one.
            if (text.substringBeforeLast(". ", text).trim().length != text.length) return null

            val words = text.split(Regex("\\s+"))
            for (i in words.indices.reversed()) {
                hostOf(words[i].trim { it in PUNCTUATION })?.let { return it }
            }
            return null
        }

        /** Exact host, then each parent domain, stopping before a bare TLD. */
        internal fun matchHost(host: String?, set: Set<String>?): String? {
            var h = host ?: return null
            val domains = set ?: return null
            while (true) {
                if (domains.contains(h)) return h
                val dot = h.indexOf('.')
                if (dot < 0) return null
                h = h.substring(dot + 1)
                if (!h.contains('.')) return null
            }
        }
    }
}

/** The two bundled lists, loaded once per process. */
object HostLists {
    val adult = HostList("blocklist/adult-hosts.txt", 79_383)
    val gambling = HostList("blocklist/gambling-hosts.txt", 6_862)
    val predictionMarkets = HostList("blocklist/prediction-market-hosts.txt", 48)

    fun load(ctx: Context) {
        adult.load(ctx)
        gambling.load(ctx)
        predictionMarkets.load(ctx)
    }
}
