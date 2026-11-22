package org.ownfate.app.data.words

/**
 * Looking for help must never be blocked, but help intent has to name what is being quit.
 *
 * There are two tiers here. [explicit] names the behaviour and wins over every built-in word
 * tier, so "how to quit porn" reaches the search engine. [generic] is only help-shaped context;
 * it excused "help me find porn videos" and "porn addiction stories" when it was allowed to
 * excuse STRONG, so it now applies to SOFT matches only. Bare "quit" and "appeal" stay absent:
 * a game menu's Quit button and a court ruling about porn are not help requests.
 */
object RescuePhrases {
    /** Explicit help intent. Excuses STRONG, HARD and SOFT matches. */
    val explicit = listOf(
        "quit porn", "quit watching porn", "how to quit porn", "help me quit porn",
        "help quit porn", "stop watching porn", "how to stop watching porn",
        "porn addiction help", "porn addiction recovery", "porn addiction support",
        "sex addiction help", "sex addiction recovery", "porn recovery", "recovery from porn",
        "porn rehab", "sex rehab", "nofap", "pornfree", "sexaholics", "twelve steps",
        "block porn", "blocking porn", "porn blocker", "porn blocking app",
        "dopamine detox", "brain rewiring"
    )

    /**
     * Generic help context. Excuses SOFT matches only: a word like "addiction" or "blocker" is
     * too easy to find next to an unrelated explicit term to be allowed to excuse STRONG/HARD.
     */
    val generic = listOf(
        "addiction", "addictions", "addicted", "addict", "recovery", "recovering",
        "quitting", "quit watching", "rehab", "therapy", "therapist", "therapists",
        "counselling", "counseling", "counsellor", "counselor", "relapse", "relapsed",
        "relapsing", "abstain", "abstinence", "support group", "help me", "how to stop",
        "stop watching", "blocker", "blocking app", "sobriety", "accountability",
        "mental health"
    )

    /** The strong-excuse list, kept as the default view of this file. */
    val terms: List<String> = explicit
}
