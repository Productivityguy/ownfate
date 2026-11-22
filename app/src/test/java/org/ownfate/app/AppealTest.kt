package org.ownfate.app

import org.ownfate.app.filter.Filter
import org.ownfate.app.guard.Appeal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate is intentionally literal. Only the invisible padding a phone keyboard likes to leave
 * around a line is forgiven; every other difference is a failure, including capitalisation.
 *
 * Every negative case is built from [Appeal.SENTENCE] itself, so changing the sentence cannot
 * silently turn these into vacuous assertions.
 */
class AppealTest {

    @Test fun exactSentencePasses() = assertTrue(Appeal.correct(Appeal.SENTENCE))

    @Test fun surroundingWhitespaceIsForgiven() {
        assertTrue(Appeal.correct("  ${Appeal.SENTENCE}\n"))
        assertTrue(Appeal.correct("${Appeal.SENTENCE}   "))
    }

    @Test fun differentCaseFails() {
        assertFalse(Appeal.correct(Appeal.SENTENCE.lowercase()))
        assertFalse(Appeal.correct(Appeal.SENTENCE.replaceFirstChar { it.lowercaseChar() }))
    }

    @Test fun missingWordFails() =
        assertFalse(Appeal.correct(Appeal.SENTENCE.replace(" flagged", "")))

    @Test fun extraWordFails() =
        assertFalse(Appeal.correct(Appeal.SENTENCE.dropLast(1) + " today."))

    @Test fun changedWordFails() =
        assertFalse(Appeal.correct(Appeal.SENTENCE.replace("mistake", "error")))

    @Test fun punctuationFails() =
        assertFalse(Appeal.correct(Appeal.SENTENCE.replace(".", "")))

    @Test fun internalSpacingFails() =
        assertFalse(Appeal.correct(Appeal.SENTENCE.replace(" ", "  ")))

    @Test fun emptyAndNullFail() {
        assertFalse(Appeal.correct(""))
        assertFalse(Appeal.correct(null))
    }

    // The sentence is displayed in our own card. It must not be something the engine would flag.
    @Test fun sentenceIsNotItselfAMatch() = assertFalse(Filter.hit(Appeal.SENTENCE))
}
