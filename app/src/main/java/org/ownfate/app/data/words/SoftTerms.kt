package org.ownfate.app.data.words

/**
 * Ambiguous words: one alone is not enough.
 *
 * Every entry has a life outside porn: the naked eye, a kink in the hose, cum laude, same-sex
 * marriage, anal fissure, a horny toad, Sexy Back. A soft match needs a second signal (another
 * soft term or a nearby [ContextTerms] word) and the card offers the appeal gate.
 */
object SoftTerms {
    /**
     * Ambiguous on their own, so one of these alone is not enough. Every single one has a life
     * outside porn: the naked eye, a kink in the hose, cum laude, same-sex marriage, anal fissure,
     * a horny toad, Sexy Back.
     */
    val terms = listOf(
        "nude", "nudity", "naked","erotic", "fetish", "kink", "kinky",
        "boobs", "tits", "titties", "anal", "cum", "jav", "sex", "sexy", "sexual",
        "horny", "lewd", "hookup", "camming" , "ass", "booty", "dick", "dicks", "cock", "cocks",
        "topless","bikini","boudoir","suggestive",
        "provocative","stripper","stripping","fetishwear",
        "stepmom", "stepson", "stepsis", "stepsister",
        "lingerie", "undies", "thong", "submissive", "dominant", "sugarbaby", "sugardaddy",
        "bondage", "dominatrix", "escort", "escorts", "prostitute", "prostitution", "brothel",
        "sex work", "sex worker", "sex workers", "sugar baby", "sugar daddy",
        // Ordinary words that were whole-page hard blocks. They are real English (selective
        // breeding, currency pegging, garden edging, Cumming in Georgia, the Arsenal Gooners), so
        // they need a second signal and offer the appeal gate instead of a silent ejection.
        "breeding", "pegging", "cumming", "edging", "gooner", "gooners","gooning",
        "orgasm", "orgasms", "orgy", "orgies", "swinger", "swingers", "voyeur", "voyeurs",
        "incest", "incestuous", "erotica", "striptease", "threesome", "threesomes",
        "cuckold", "cuckolds", "cuckolding", "rimming", "squirt", "squirting",
        "facesitting",
        // Demoted from STRONG: real English words and numbered rules. They still block beside
        // adult context, but "babes in arms" and legal "rule 34" text must not eject a page.
        // The folded spellings are listed because digits fold before the SOFT regex runs.
        "babes", "vixen", "vixens", "rule ea", "rule es"
    )
}
