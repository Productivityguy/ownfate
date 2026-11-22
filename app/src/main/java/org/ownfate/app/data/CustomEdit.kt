package org.ownfate.app.data


/**
 * The pure part of the Custom screen's staged editing.
 *
 * A draft is compared with what is active, or with what a waiting change will write. Lock decides
 * whether the resulting block is stricter (applies at once) or weaker (waits or asks for a
 * password); a mixed add-and-remove is treated as one weaker change so it cannot half-apply.
 */
object CustomEdit {

    data class Split(val added: Set<String>, val removed: Set<String>) {
        val empty: Boolean get() = added.isEmpty() && removed.isEmpty()
    }

    /** One term per line, trimmed, blanks dropped, duplicates collapsed, order kept. */
    fun words(raw: String): Set<String> = WordLines.of(raw).toSet()

    fun split(active: Set<String>, draft: Set<String>): Split =
        Split(added = draft - active, removed = active - draft)

    /**
     * The set the next save should start from: what is active, or the value a waiting change will
     * write. Editing against the active set would silently drop the waiting entry when the new
     * value is written, and would schedule a value that erases the new edit.
     */
    fun effective(active: Set<String>, pendingValue: String?): Set<String> =
        if (pendingValue == null) active else words(pendingValue)

    /**
     * The active block after applying additions immediately: the existing text kept exactly as it
     * was, with the new terms appended. Formatting survives an addition; only a removal, which
     * waits for the lock, rewrites the block.
     */
    fun withAdded(activeRaw: String, added: Set<String>): String {
        if (added.isEmpty()) return activeRaw
        val base = activeRaw.trimEnd()
        val tail = added.joinToString("\n")
        return if (base.isEmpty()) tail else base + "\n" + tail
    }
}
