package org.ownfate.app.data

/**
 * The one rule for turning a newline-separated preference into terms.
 *
 * Trims each line, drops blanks, collapses duplicates and keeps the first-seen order, so the
 * Custom preview, the lock's change comparison and the guard all read the same list.
 */
internal object WordLines {
    fun of(raw: String): List<String> = raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()
}
