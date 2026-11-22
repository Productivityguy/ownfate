package org.ownfate.app.guard

import android.os.Handler
import android.os.Looper

/**
 * One global input action at a time, shared by every BACK/BACK(/HOME) ladder.
 *
 * Page blocks, add-on search blocks, blocked-app cards and the off-switch guard all answer by
 * pressing global keys. Without a single owner, two ladders can overlap and send four BACKs at one
 * screen, or a card can appear while an earlier BACK is still queued and swallow it.
 *
 * This is not a quiet time. Scanning continues normally; the gate only decides who owns the input.
 * A monotonically increasing token makes every sequence unique, so a callback belonging to a
 * cancelled sequence can never be mistaken for the current one, and every posted callback is
 * removed outright when a new sequence takes over.
 */
internal class ActionGate {
    private val ui = Handler(Looper.getMainLooper())
    private var token = 0L
    private var owner: String? = null
    private val queued = mutableListOf<Runnable>()

    /** Take ownership. Cancels any sequence still in flight. Returns the new sequence's token. */
    fun begin(owner: String): Long {
        cancel()
        token++
        this.owner = owner
        return token
    }

    /** True only while [t] is the current, uncancelled sequence. */
    fun owns(t: Long): Boolean = owner != null && token == t

    /** Post a callback for sequence [t]. Dropped if that sequence is no longer current. */
    fun post(t: Long, delayMs: Long = 0L, block: () -> Unit) {
        if (!owns(t)) return
        lateinit var r: Runnable
        r = Runnable {
            queued.remove(r)
            if (owns(t)) block()
        }
        queued.add(r)
        ui.postDelayed(r, delayMs)
    }

    /** The name that currently owns input, or null when no sequence is in flight. */
    fun current(): String? = owner

    /** Cancel the current sequence and remove every callback it still had queued. */
    fun cancel() {
        queued.forEach(ui::removeCallbacks)
        queued.clear()
        owner = null
    }
}
