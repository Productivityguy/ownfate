package org.ownfate.app.filter


import java.text.Normalizer

/**
 * Text folding shared by the keyword engine and the page key.
 *
 * A list of forty words is defeated by "p0rn", "p.o.r.n" and "p o r n" within a day, so input
 * is normalised before matching:
 *
 *  - lowercase and punctuation become spaces
 *  - Unicode decomposition and Cyrillic lookalikes fold to Latin, so fullwidth and homoglyph
 *    spellings (ｐｏｒｎ, pоrn with a Cyrillic о) fold like the plain word
 *  - leetspeak folds (p0rn, pr0n, s3x, @, $)
 *  - stretched letters collapse (poooorn -> porn), runs of three or more only, because
 *    collapsing doubles turned "poorness" into "porness"
 *  - separator gaps close only between short tokens, because closing every gap welds ordinary
 *    prose into brand names: "watch anime" -> "hanime", "mother less" -> "motherless"
 *
 * Nothing here touches the network or the disk.
 */
object Fold {
    private fun deleet(c: Char): Char = when (c) {
        '@' -> 'a'; '$' -> 's'; '!' -> 'i'; '|' -> 'i'
        else -> c
    }

    private fun deleetDigit(c: Char): Char = when (c) {
        '0' -> 'o'; '1' -> 'i'; '3' -> 'e'; '4' -> 'a'
        '5' -> 's'; '7' -> 't'; '8' -> 'b'
        else -> c
    }

    /**
     * Cyrillic letters that are visually identical to Latin ones. Obfuscated spellings swap one
     * letter ("pоrn" with a Cyrillic о) and every ASCII rule after this then fails. Only the
     * lookalikes are mapped; ordinary Cyrillic text keeps its own letters.
     */
    private fun homoglyph(c: Char): Char = when (c) {
        '\u0430' -> 'a'; '\u0435' -> 'e'; '\u043E' -> 'o'; '\u0440' -> 'p'
        '\u0441' -> 'c'; '\u0443' -> 'y'; '\u0445' -> 'x'; '\u0456' -> 'i'
        '\u0457' -> 'i'; '\u0458' -> 'j'; '\u0455' -> 's'; '\u0501' -> 'd'
        '\u04BB' -> 'h'; '\u04CF' -> 'l'; '\u051B' -> 'q'; '\u051D' -> 'w'
        '\u0261' -> 'g'
        else -> c
    }

    /** NFKD only when the string is not pure ASCII: the normal case stays a plain scan. */
    private fun normalize(s: String): String =
        if (s.any { it.code > 0x7F }) Normalizer.normalize(s, Normalizer.Form.NFKD) else s

    /** Keeps word gaps, for the boundary-matched lists. Digits map, so "pr0n" reads as a word. */
    fun spaced(s: String): String {
        val sb = StringBuilder(s.length)
        for (raw in normalize(s).lowercase()) {
            // Zero-width joiners are obfuscation, not word breaks: drop them so "po\u200Brn"
            // still meets in the middle instead of becoming two unrelated tokens.
            if (raw == '\u200B' || raw == '\u200C' || raw == '\u200D' || raw == '\uFEFF') continue
            val c = deleetDigit(deleet(homoglyph(raw)))
            sb.append(if (c.isLetterOrDigit()) c else ' ')
        }
        return sb.toString()
    }

    /**
     * Tokens with the digits left literal, so "cam 4" and "f95zone" keep their digits while
     * [spaced] rewrites them (4 -> a, 5 -> s). Zero-width joiners are dropped, not treated as
     * word breaks.
     */
    fun literalTokens(raw: String): List<String> {
        val sb = StringBuilder(raw.length)
        for (ch in normalize(raw).lowercase()) {
            if (ch == '\u200B' || ch == '\u200C' || ch == '\u200D' || ch == '\uFEFF') continue
            val c = deleet(ch)
            sb.append(if (c.isLetterOrDigit()) c else ' ')
        }
        return sb.toString().split(' ').filter { it.isNotEmpty() }
    }

    /** Public form, used to build page keys and pass places so two addresses fold alike. */
    fun fold(s: String): String = spaced(s).trim()

    /** Gaps close only where obfuscation puts them: next to one- and two-character fragments. */
    fun joined(s: String): List<String> {
        val tokens = spaced(s).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        val run = StringBuilder(tokens[0])
        for (i in 1 until tokens.size) {
            if (tokens[i - 1].length <= 2 || tokens[i].length <= 2) run.append(tokens[i])
            else { out.add(run.toString()); run.setLength(0); run.append(tokens[i]) }
        }
        out.add(run.toString())
        return out
    }

    /** Digits left alone, so a brand that contains one ("cam4", "rule34") stays literal. */
    fun joinedRaw(s: String): List<String> {
        val sb = StringBuilder(s.length)
        for (raw in s.lowercase()) {
            val c = deleet(raw)
            sb.append(if (c.isLetterOrDigit()) c else ' ')
        }
        val tokens = sb.toString().split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        val run = StringBuilder(tokens[0])
        for (i in 1 until tokens.size) {
            if (tokens[i - 1].length <= 2 || tokens[i].length <= 2) run.append(tokens[i])
            else { out.add(run.toString()); run.setLength(0); run.append(tokens[i]) }
        }
        out.add(run.toString())
        return out
    }

    /** Collapse stretched letters: poooorn -> porn. Runs of three or more only. */
    private val RUNS = Regex("(.)\\1{2,}")
    fun squash(s: String) = RUNS.replace(s) { it.groupValues[1] }

    /** The same collapse for a doubled letter inside a short brand-like token (poorn -> porn). */
    private val DOUBLES = Regex("(.)\\1+")
    fun squashDoubles(s: String) = DOUBLES.replace(s) { it.groupValues[1] }
}
