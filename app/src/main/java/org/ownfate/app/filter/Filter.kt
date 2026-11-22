package org.ownfate.app.filter

import org.ownfate.app.data.words.WordLists

/**
 * The keyword engine. The interesting part is not the word list, it is the folding and the tiers.
 *
 * A list of forty words is defeated by "p0rn", "p.o.r.n" and "p o r n" within a day, so input is
 * normalised first:
 *
 *   - lowercase, punctuation to spaces
 *   - Unicode decomposition and Cyrillic lookalikes folded to Latin, so fullwidth and
 *     homoglyph spellings (ｐｏｒｎ, pоrn with a Cyrillic о) fold like the plain word
 *   - leetspeak folded (p0rn, pr0n, s3x, @, $)
 *   - stretched letters collapsed (poooorn -> porn), runs of three or more only, because
 *     collapsing doubles turned "poorness" into "porness"
 *   - separator gaps closed only between short tokens, because closing every gap welds ordinary
 *     prose into brand names: "watch anime" -> "hanime", "mother less" -> "motherless"
 *
 * Terms are tiered rather than one big list:
 *
 *   STRONG  unambiguous stems and brands; blocks on sight
 *   HARD    explicit whole words, verified performers, shows and adult keywords; blocks on sight
 *   CUSTOM  a user-added word or phrase; blocks on sight, never gated by the adult-words switch
 *   WEBSITE a host on a bundled site list; blocks on sight, no appeal
 *   SOFT    ambiguous words and common-name collisions; needs a second signal, offers the gate
 *   RESCUE  explicit help intent; excuses the match it sits next to, never the whole page
 *   GENERIC help-shaped context; only excuses a nearby SOFT match
 *   ALLOW   ordinary life that uses the ambiguous words; excuses a nearby HARD or SOFT match
 *
 * A rescue or allow phrase is local. One "medical" in a footer used to switch the whole engine off
 * for that page; a match elsewhere on the page still counts. ALLOW is a built-in check, not the
 * user's Allowlist. The user's own allowed words are checked first and skip word blocking
 * entirely; site and app blocks run outside this engine. A soft match is released by the card's
 * retype gate, and the release is a short-lived [PagePass], not a list the engine has to trust.
 */
object Filter {

    enum class Tier { STRONG, HARD, CUSTOM, WEBSITE, SOFT }

    data class Match(val term: String, val tier: Tier)

    /**
     * STRONG is matched as whole folded tokens, not as an arbitrary substring. Ordinary short
     * words can weld into a stem ("stop or not" -> "stopornot"), and a bare substring search
     * turned that into "porn". The token windows below still find real obfuscation ("p o r n"),
     * but a stem is only accepted at the start of a word, or as the whole welded run. URL-like
     * text keeps the broad substring match, because long-tail domains are where a stem earns its
     * keep.
     *
     * Inflections are listed explicitly where the old substring behaviour caught them: a whole
     * token looks the term up directly, and the prefix rule catches compounds such as
     * "pornvideos" and "masturbation".
     */
    private val STRONG_VARIANTS: Map<String, String> = buildMap {
        fun add(value: String, canonical: String) {
            // Fold.spaced() lowercases the input. Without the same fold here, every capitalized
            // entry in StrongTerms is dead. The canonical keeps the source spelling for the card.
            val key = value.filter { c -> c.isLetterOrDigit() }.lowercase()
            if (key.isNotEmpty()) putIfAbsent(key, canonical)
        }
        for (term in WordLists.strong) {
            add(term, term)
            add(term + "s", term)
        }
        // Forms the former substring match accepted for free.
        val extra = mapOf(
            "porn" to listOf(
                "porno", "pornos", "pornography", "pornographic", "pornographer",
                "pornographers"
            ),
            "masturbat" to listOf(
                "masturbate", "masturbates", "masturbated", "masturbating", "masturbation",
                "masturbations", "masturbator", "masturbators"
            ),
            "fapp" to listOf("fapping", "fapped", "fapper", "fappers"),
            "nudify" to listOf("nudifies", "nudified", "nudifying"),
            "doujin" to listOf("doujins", "doujinshi", "doujinshis"),
            "camwhore" to listOf("camwhores"),
            "blowjob" to listOf("blowjobs"),
            "handjob" to listOf("handjobs"),
            "gangbang" to listOf("gangbangs"),
            "creampie" to listOf("creampies"),
            "cumshot" to listOf("cumshots"),
            "deepthroat" to listOf("deepthroats"),
            "upskirt" to listOf("upskirts"),
            "jerkoff" to listOf("jerkoffs"),
            "escortservice" to listOf("escortservices"),
            "sexvid" to listOf("sexvids"),
            "sexcam" to listOf("sexcams"),
            "sexchat" to listOf("sexchats"),
            "sextape" to listOf("sextapes"),
            "coomer" to listOf("coomers")
        )
        for ((canonical, forms) in extra) for (form in forms) add(form, canonical)
    }

    /** Longest accepted term. Bounds the token-window walk. */
    private val STRONG_MAX = STRONG_VARIANTS.keys.maxOf { it.length }

    /** Cheap reject before building prefixes of every token on a long page. */
    private val STRONG_PREFIX3 = STRONG_VARIANTS.keys.map { it.take(3) }.toSet()

    private const val URL_TOKEN_CHARS = "/:?=&#"
    private val WHITESPACE = Regex("""\s+""")

    /**
     * How close an allow or rescue phrase has to be to excuse a match. About one sentence:
     * wide enough for "sex education is compulsory in schools", narrow enough that "medical" in
     * a sidebar cannot excuse an explicit term in the article. A whole page was too wide.
     */
    private const val EXCUSE_RADIUS = 60

    /**
     * A soft word needs a context word in the same sentence. Kept separate from [EXCUSE_RADIUS]
     * so narrowing the excuse window does not weaken what counts as a real soft match.
     */
    private const val CONTEXT_RADIUS = 100

    /**
     * Two SOFT signals must sit close enough to describe the same thing. The old rule paired any
     * two soft words on the whole page, so "nude" in a header and "naked" in a footer ejected an
     * otherwise ordinary page.
     */
    private const val SOFT_PAIR_RADIUS = 100

    private fun word(list: List<String>) = Regex(
        list.joinToString("|") { """\b${Regex.escape(it)}\b""" }, RegexOption.IGNORE_CASE
    )

    private val HARD_RE = word(WordLists.hard)
    private val SOFT_RE = word(WordLists.soft)
    private val CONTEXT_RE = word(WordLists.context)
    private val RESCUE_RE = word(WordLists.rescue)
    private val GENERIC_RESCUE_RE = word(WordLists.genericRescue)
    private val ALLOW_RE = word(WordLists.allow)

    // ---- names ------------------------------------------------------------

    /**
     * Folded name -> the spelling to show on the card. Common names are inserted first, so a
     * string that is both a performer and an ordinary name resolves to the soft entry.
     */
    private val SOFT_NAME_KEYS: Set<String> = WordLists.commonNames
        .map { Fold.spaced(it).trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    private val HARD_NAME_KEYS: Set<String> =
        (WordLists.adultPerformers + WordLists.adultShows + WordLists.adultKeywords)
            .map { Fold.spaced(it).trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    private val NAME_CANON: Map<String, String> = buildMap {
        for (n in WordLists.commonNames + WordLists.adultPerformers + WordLists.adultShows + WordLists.adultKeywords) {
            val k = Fold.spaced(n).trim()
            if (k.isNotEmpty()) putIfAbsent(k, n)
        }
    }

    private data class Token(val text: String, val start: Int, val end: Int)

    private data class NameHit(val term: String, val start: Int, val end: Int, val soft: Boolean)

    private class NameScan(val hard: List<NameHit>, val soft: List<NameHit>)

    /**
     * Whole-token windows, longest first. Names are 1-4 words in the source list, so this is at
     * most four window lengths over the folded text. A set lookup is far cheaper than compiling
     * 3,000 alternatives into one regex, and it cannot match inside a longer word the way a
     * naive substring search would ("Alice" must not fire inside "Aliceville").
     *
     * Every occurrence is returned with its span. The old scan kept only the first hard and soft
     * name, which let a rescued first occurrence hide a second, unrescued one further down a
     * page. Empty folded tokens are skipped, so "Mr. Pete" does not lose its match to the double
     * space its period leaves behind.
     */
    private fun scanNames(spaced: String): NameScan {
        val tokens = ArrayList<Token>()
        var i = 0
        while (i < spaced.length) {
            while (i < spaced.length && spaced[i] == ' ') i++
            val start = i
            while (i < spaced.length && spaced[i] != ' ') i++
            if (i > start) tokens.add(Token(spaced.substring(start, i), start, i))
        }
        if (tokens.isEmpty()) return NameScan(emptyList(), emptyList())

        val hits = ArrayList<NameHit>()
        for (n in 4 downTo 1) {
            if (tokens.size < n) continue
            for (k in 0..tokens.size - n) {
                val w = tokens.subList(k, k + n).joinToString(" ") { it.text }
                val canon = NAME_CANON[w] ?: continue
                val soft = w in SOFT_NAME_KEYS
                if (!soft && w !in HARD_NAME_KEYS) continue
                hits.add(NameHit(canon, tokens[k].start, tokens[k + n - 1].end, soft))
            }
        }

        // "Red Fox" inside "The Red Fox" must not count as a second soft signal. Keep outermost
        // occurrences, then restore reading order so excuse checks see them in page order.
        val unique = hits.distinctBy { Triple(it.start, it.end, it.term) }
        val outer = unique
            .sortedWith(compareByDescending<NameHit> { it.end - it.start }.thenBy { it.start })
            .filter { h ->
                unique.none { other -> other !== h && other.start <= h.start && other.end >= h.end }
            }
            .sortedBy { it.start }

        return NameScan(outer.filter { !it.soft }, outer.filter { it.soft })
    }

    // ---- candidates -------------------------------------------------------

    /** Every separator gone. Used for the URL segments that keep substring matching. */
    private fun compactAll(s: String) = Fold.spaced(s).filter { !it.isWhitespace() }

    /**
     * Structural URL punctuation. Letters either side of one of these are never the same word,
     * so URL compaction must not weld across them. This is the fix for the bug that made
     * "&sxsrf=APpeQ..." contain "fapp" and blocked ordinary Google searches.
     *
     * A query's `+` and `%20` are word gaps too. Without them, "watch+anime" welds into the
     * STRONG brand "hanime" and "benefits+of+app" into "fapp", which is the URL version of the
     * prose-welding bug this engine already fixed.
     */
    private val URL_DELIM = Regex("""(?:/|\?|&|=|#|:|\+|%20)+""", RegexOption.IGNORE_CASE)

    /** Word boundaries inside a URL segment, so a short stem cannot match inside a longer word. */
    private val NON_ALNUM = Regex("""[^\p{L}\p{N}]+""")

    /**
     * A STRONG hit, or null.
     *
     * Prose is read as folded tokens. A term is accepted when a run of adjacent tokens equals it
     * ("p o r n", "cam 4", "only fans"), or when it is the prefix of one token ("pornvideos",
     * "masturbation"). It is never accepted from the middle of an ordinary word, which is what
     * made "stop or not" and "benefits of app" false positives.
     *
     * Address bars, hosts and URL-like tokens keep substring matching, so a long-tail domain
     * such as "pornvideos.example" still resolves to its stem.
     */
    private fun strongHit(raw: String, spaced: String): String? {
        val tokens = spaced.split(' ').filter { it.isNotEmpty() }

        // Exact token or welded run. [joined] keeps the short-gap rule, so "cam 4" and
        // "x video" still work, while two ordinary long words are not welded into "cream pie"
        // -> "creampie".
        for (run in Fold.joined(raw) + Fold.joinedRaw(raw)) {
            runHit(run)?.let { return it }
            prefixHit(run)?.let { return it }
        }

        // A cluster made only of one- and two-letter fragments. This is the obfuscation path:
        // "watch p o r n" gives "porn" even though the run above starts with "watch".
        for (i in tokens.indices) {
            if (tokens[i].length > 2) continue
            val sb = StringBuilder()
            for (j in i until tokens.size) {
                if (tokens[j].length > 2) break
                sb.append(tokens[j])
                if (sb.length > STRONG_MAX) break
                runHit(sb.toString())?.let { return it }
            }
        }

        // A stem at the START of a full token is a compound: "pornvideos", "masturbation".
        for (token in tokens) prefixHit(token)?.let { return it }

        // URL-like tokens are the one place a term may sit inside a longer word.
        if (raw.any { it in URL_TOKEN_CHARS || it == '.' }) {
            for (token in raw.split(WHITESPACE)) {
                if (!isUrlToken(token)) continue
                for (seg in URL_DELIM.split(token)) {
                    if (seg.length < 3) continue
                    val compact = compactAll(seg)
                    for ((variant, canonical) in STRONG_VARIANTS) {
                        if (compact.contains(variant)) return canonical
                    }
                }
            }
        }

        return null
    }

    private fun runHit(run: String): String? {
        STRONG_VARIANTS[run]?.let { return it }
        val squashed = Fold.squash(run)
        if (squashed != run) STRONG_VARIANTS[squashed]?.let { return it }
        // A doubled letter in an otherwise brand-like token is common obfuscation. This only
        // applies to the STRONG list, so ordinary doubled words in prose keep their own tier.
        val doubled = Fold.squashDoubles(run)
        if (doubled != squashed) STRONG_VARIANTS[doubled]?.let { return it }
        return null
    }

    private fun prefixHit(word: String): String? {
        if (word.length < 3 || word.take(3) !in STRONG_PREFIX3) return null
        val limit = minOf(word.length, STRONG_MAX)
        for (k in 3..limit) {
            STRONG_VARIANTS[word.substring(0, k)]?.let { return it }
        }
        return null
    }

    /** A host, path or query token, not a sentence-ending period. */
    private fun isUrlToken(token: String): Boolean {
        if (token.any { it in URL_TOKEN_CHARS }) return true
        val dot = token.indexOf('.')
        return dot >= 0 && dot < token.length - 1
    }

    // ---- decision ---------------------------------------------------------

    /** A strong term with the span it occupies in the folded page, used to scope allow/rescue. */
    private data class Hit(val term: String, val start: Int, val end: Int)

    /**
     * Tokens with the digits left literal, so "cam 4" and "f95zone" keep their digits while
     * [Fold.spaced] rewrites them (4 -> a, 5 -> s). Empty when the token count would not line up
     * with the folded text, in which case the literal path is skipped rather than misaligned.
     */
    private fun literalTokens(raw: String): List<String> = Fold.literalTokens(raw)

    /**
     * Every strong hit in [spaced], in reading order.
     *
     * [strongHit] stops at the first hit because it only had to answer yes/no. The decision below
     * needs the span as well: a help phrase next to one term must not excuse a different explicit
     * term elsewhere on the page.
     */
    private fun strongHits(raw: String, spaced: String): List<Hit> {
        val tokens = ArrayList<Token>()
        var i = 0
        while (i < spaced.length) {
            while (i < spaced.length && spaced[i] == ' ') i++
            val start = i
            while (i < spaced.length && spaced[i] != ' ') i++
            if (i > start) tokens.add(Token(spaced.substring(start, i), start, i))
        }
        if (tokens.isEmpty()) return emptyList()

        // The literal tokens share the folded token boundaries, so their spans are the same. When
        // they do not line up the literal path is dropped: a wrong span would excuse the wrong
        // match, which is worse than missing a digit brand.
        val literal = literalTokens(raw)
        val lit = if (literal.size == tokens.size) literal else null

        val out = ArrayList<Hit>()
        fun check(text: String, start: Int, end: Int) {
            runHit(text)?.let { out.add(Hit(it, start, end)) }
            prefixHit(text)?.let { out.add(Hit(it, start, end)) }
        }

        for (k in tokens.indices) {
            check(tokens[k].text, tokens[k].start, tokens[k].end)
            lit?.let { check(it[k], tokens[k].start, tokens[k].end) }
        }

        // Weld only where obfuscation puts a gap: next to a one- or two-character fragment. Each
        // window starts at every token, so "watch p o r n" still finds "porn" even though the
        // first run is "watchporn". The literal run keeps "cam 4" -> "cam4" and "rule 34".
        for (k in tokens.indices) {
            val sb = StringBuilder(tokens[k].text)
            val rawSb = lit?.let { StringBuilder(it[k]) }
            var j = k
            while (j + 1 < tokens.size &&
                (tokens[j].text.length <= 2 || tokens[j + 1].text.length <= 2)
            ) {
                j++
                if (sb.length + tokens[j].text.length > STRONG_MAX) break
                sb.append(tokens[j].text)
                check(sb.toString(), tokens[k].start, tokens[j].end)
                rawSb?.let {
                    it.append(lit[j])
                    check(it.toString(), tokens[k].start, tokens[j].end)
                }
            }
        }

        // A URL is the one place a stem may sit inside a word, and its span is not useful for
        // scoping. A hit with no span is never excused; a named host always blocks.
        if (out.isEmpty() && raw.any { it in URL_TOKEN_CHARS || it == '.' }) {
            strongHit(raw, spaced)?.let { out.add(Hit(it, -1, -1)) }
        }
        return out.distinctBy { it.term to it.start }.sortedBy { it.start }
    }

    /**
     * True when an excuse sits near this match.
     *
     * Explicit help intent wins at every tier, because "how to quit porn" must reach the search
     * engine. Generic help context and the built-in allow list only soften HARD/SOFT matches:
     * "help me", "addiction" and "doctor" must not erase an unambiguous STRONG stem.
     */
    private fun excused(spaced: String, start: Int, end: Int, tier: Tier): Boolean {
        if (start < 0) return false
        if (near(spaced, start, end, RESCUE_RE)) return true
        if (tier == Tier.SOFT && near(spaced, start, end, GENERIC_RESCUE_RE)) return true
        return (tier == Tier.HARD || tier == Tier.SOFT) && near(spaced, start, end, ALLOW_RE)
    }

    private fun near(spaced: String, start: Int, end: Int, re: Regex): Boolean {
        for (m in re.findAll(spaced)) {
            if (nearRanges(start, end, m.range.first, m.range.last + 1)) return true
        }
        return false
    }

    private fun nearRanges(
        aStart: Int, aEnd: Int, bStart: Int, bEnd: Int, radius: Int = EXCUSE_RADIUS
    ): Boolean = bStart <= aEnd + radius && bEnd >= aStart - radius

    /**
     * A user-supplied stem, from a custom block word or an allow word. Short words are whole
     * tokens (so "bet" cannot block "better"); longer words may be a prefix (so "casino" still
     * blocks "casinos"). A host or path keeps the broad substring match, because a long-tail
     * domain is exactly where a custom stem earns its keep.
     */
    private fun customStemHit(raw: String, spaced: String, stem: String): Boolean {
        if (stem.isEmpty()) return false
        for (t in spaced.split(' ')) {
            if (t.isEmpty()) continue
            val hit = if (stem.length >= 5) {
                t.startsWith(stem)
            } else {
                t == stem || t == stem + "s" || t == stem + "es"
            }
            if (hit) return true
        }
        if (raw.any { it in URL_TOKEN_CHARS || it == '.' }) {
            for (token in raw.split(WHITESPACE)) {
                if (!isUrlToken(token)) continue
                for (seg in URL_DELIM.split(token)) {
                    if (seg.length < 3) continue
                    val compact = compactAll(seg)
                    if (compact.isEmpty()) continue
                    if (stem.length >= 5) {
                        if (compact.contains(stem)) return true
                    } else {
                        // A short stem stays on word boundaries even inside a URL: "sex.com" and
                        // "place-a-bet" match, while "Sussex" and "better" do not.
                        for (part in seg.split(NON_ALNUM)) {
                            val p = part.lowercase().filter { it.isLetterOrDigit() }
                            if (p == stem || p == stem + "s" || p == stem + "es") return true
                        }
                    }
                }
            }
        }
        return false
    }

    /**
     * The matched term and its tier, or null.
     *
     * @param custom       the user's own extra keywords (phrase if it has a space, stem otherwise)
     * @param includeWords whether the built-in adult word and name lists run. The custom list
     *                     runs either way: it is the user's own hard list.
     * @param allowWords   the user's own allowed words or phrases; any match skips word blocking
     */
    fun match(
        raw: String?,
        custom: List<String> = emptyList(),
        includeWords: Boolean = true,
        allowWords: List<String> = emptyList()
    ): Match? {
        if (raw == null || raw.length < 3) return null

        val spaced = Fold.spaced(raw)

        if (allowWords.isNotEmpty() && allowHit(raw, spaced, allowWords)) return null

        if (includeWords) {
            for (h in strongHits(raw, spaced)) {
                if (!excused(spaced, h.start, h.end, Tier.STRONG)) {
                    return Match(h.term, Tier.STRONG)
                }
            }
        }

        for (t in custom) {
            val n = Fold.spaced(t).trim()
            if (n.length < 3) continue
            if (n.contains(' ')) {
                if (spaced.contains(n)) return Match(t, Tier.CUSTOM)
            } else {
                val stem = n.filter { c -> c.isLetterOrDigit() }
                if (customStemHit(raw, spaced, stem)) return Match(t, Tier.CUSTOM)
            }
        }

        if (!includeWords) return null

        val names = scanNames(spaced)
        for (name in names.hard) {
            if (!excused(spaced, name.start, name.end, Tier.HARD)) {
                return Match(name.term, Tier.HARD)
            }
        }

        for (m in HARD_RE.findAll(spaced)) {
            val r = m.range
            if (!excused(spaced, r.first, r.last + 1, Tier.HARD)) {
                return Match(m.value, Tier.HARD)
            }
        }

        val soft = ArrayList<Pair<String, IntRange>>()
        for (m in SOFT_RE.findAll(spaced)) {
            val r = m.range
            if (!excused(spaced, r.first, r.last + 1, Tier.SOFT)) soft.add(m.value to r)
        }
        for (name in names.soft) {
            if (!excused(spaced, name.start, name.end, Tier.SOFT)) {
                soft.add(name.term to (name.start until name.end))
            }
        }

        // Fold before de-duplicating: an entry that is both a soft word and a common name (for
        // example "sugar baby") would otherwise be counted twice and block on its own.
        val distinct = soft.distinctBy { Fold.spaced(it.first).trim() }
        if (distinct.isEmpty()) return null
        if (distinct.size >= 2) {
            // A pair only counts when the two words are close. Far-apart soft words are two
            // unrelated signals on one long page; they still get a chance below via their own
            // nearby context word.
            for (i in distinct.indices) {
                for (j in i + 1 until distinct.size) {
                    val (a, aRange) = distinct[i]
                    val (b, bRange) = distinct[j]
                    if (nearRanges(
                            aRange.first, aRange.last + 1,
                            bRange.first, bRange.last + 1,
                            SOFT_PAIR_RADIUS
                        )
                    ) return Match("$a+$b", Tier.SOFT)
                }
            }
        }
        for ((term, range) in distinct) {
            val context = CONTEXT_RE.findAll(spaced).firstOrNull {
                nearRanges(
                    range.first, range.last + 1,
                    it.range.first, it.range.last + 1,
                    CONTEXT_RADIUS
                )
            }
            if (context != null) return Match("$term+${context.value}", Tier.SOFT)
        }
        return null
    }

    fun hit(
        raw: String?,
        custom: List<String> = emptyList(),
        allowWords: List<String> = emptyList()
    ): Boolean = match(raw, custom, allowWords = allowWords) != null

    /**
     * An allow entry is a global bypass, so it has to be as precise as the rule it excuses. A
     * plain substring test let an allow word such as "sex" excuse "Sussex"; reusing the custom
     * stem rule keeps short entries on whole tokens while still letting a longer entry cover its
     * plural and a host or path match a long-tail domain.
     */
    private fun allowHit(raw: String, spaced: String, allowWords: List<String>): Boolean {
        for (t in allowWords) {
            val n = Fold.spaced(t).trim()
            if (n.length < 3) continue
            if (n.contains(' ')) {
                if (spaced.contains(n)) return true
            } else {
                val stem = n.filter { c -> c.isLetterOrDigit() }
                if (stem.length >= 3 && customStemHit(raw, spaced, stem)) return true
            }
        }
        return false
    }
}
