package org.ownfate.app

import org.ownfate.app.filter.Fold

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure folding rules: the obfuscations the keyword engine has to answer without a network. */
class FoldTest {

    @Test fun spacedFoldsCasePunctuationAndLeetDigits() {
        assertEquals("p o r n  ", Fold.spaced("P.O.R.N.."))
        assertEquals("porn", Fold.spaced("p0rn"))
        assertEquals("sex", Fold.spaced("s3x"))
    }

    @Test fun spacedFoldsFullwidthAndCyrillicLookalikes() {
        assertEquals("porn", Fold.spaced("ｐｏｒｎ"))
        // The second letter is a Cyrillic о, not ASCII o.
        assertEquals("porn", Fold.spaced("p\u043Ern"))
    }

    @Test fun spacedDropsZeroWidthJoiners() {
        assertEquals("porn", Fold.spaced("po\u200Brn"))
        assertEquals("porn", Fold.spaced("p\u200Co\u200Drn"))
    }

    @Test fun literalTokensKeepDigitsForNumberedBrands() {
        assertEquals(listOf("cam", "4"), Fold.literalTokens("cam 4"))
        assertEquals(listOf("p0rn"), Fold.literalTokens("p0rn"))
        assertEquals(listOf("f95zone"), Fold.literalTokens("F95Zone"))
    }

    @Test fun foldTrimsTheSpacedForm() {
        assertEquals("porn", Fold.fold("  P0RN..  "))
    }

    @Test fun joinedClosesGapsOnlyAroundShortFragments() {
        assertEquals(listOf("porn"), Fold.joined("p o r n"))
        assertEquals(listOf("watch", "anime"), Fold.joined("watch anime"))
        assertEquals(listOf("mother", "less"), Fold.joined("mother less"))
    }

    @Test fun joinedRawKeepsDigitsLiteral() {
        assertEquals(listOf("cam4"), Fold.joinedRaw("cam 4"))
        assertEquals(listOf("rule34"), Fold.joinedRaw("rule34"))
    }

    @Test fun squashCollapsesOnlyRunsOfThreeOrMore() {
        assertEquals("porn", Fold.squash("poooorn"))
        assertEquals("poorness", Fold.squash("poorness"))
        assertEquals("porn", Fold.squashDoubles("poorn"))
        assertEquals("porty", Fold.squashDoubles("poorty"))
    }
}
