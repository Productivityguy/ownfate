package org.ownfate.app

import org.ownfate.app.data.CustomEdit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Custom screen's add-now, remove-under-lock split. */
class CustomEditTest {

    @Test fun wordsAreTrimmedAndDeduplicated() =
        assertEquals(linkedSetOf("a", "b"), CustomEdit.words(" a \n\nb\na\n "))

    @Test fun addingOnlyIsAnAddition() {
        val split = CustomEdit.split(setOf("a"), setOf("a", "b"))
        assertEquals(setOf("b"), split.added)
        assertTrue(split.removed.isEmpty())
        assertFalse(split.empty)
    }

    @Test fun removingOnlyIsARemoval() {
        val split = CustomEdit.split(setOf("a", "b"), setOf("a"))
        assertTrue(split.added.isEmpty())
        assertEquals(setOf("b"), split.removed)
        assertFalse(split.empty)
    }

    @Test fun mixedEditsSplitBothWays() {
        val split = CustomEdit.split(setOf("a", "b"), setOf("b", "c"))
        assertEquals(setOf("c"), split.added)
        assertEquals(setOf("a"), split.removed)
    }

    @Test fun identicalDraftsAreEmpty() =
        assertTrue(CustomEdit.split(setOf("a", "b"), setOf("b", "a")).empty)

    @Test fun addedWordsAppendAndKeepExistingFormatting() =
        assertEquals("a\n\nb\nc", CustomEdit.withAdded("a\n\nb\n", setOf("c")))

    @Test fun addingToAnEmptyBlockHasNoLeadingBlank() =
        assertEquals("a\nb", CustomEdit.withAdded("", linkedSetOf("a", "b")))

    @Test fun effectiveUsesTheActiveSetWithoutAPendingValue() =
        assertEquals(setOf("a", "b"), CustomEdit.effective(setOf("a", "b"), null))

    @Test fun effectiveUsesTheWaitingValueWhenOneExists() =
        assertEquals(setOf("a", "c"), CustomEdit.effective(setOf("a", "b"), "a\nc"))
}
