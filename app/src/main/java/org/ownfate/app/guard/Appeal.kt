package org.ownfate.app.guard


/**
 * The retype gate on a soft-match card.
 *
 * The sentence is fixed and shown on the card, so this is friction rather than a password. The
 * comparison is character-exact: only leading and trailing whitespace is ignored, because a phone
 * keyboard likes to leave a trailing space and that is not a meaningful difference. A different
 * capital letter, a missing word or a changed word all fail on purpose.
 */
object Appeal {

    const val SENTENCE = "This content was flagged by mistake."

    fun correct(typed: String?): Boolean = typed?.trim() == SENTENCE
}
