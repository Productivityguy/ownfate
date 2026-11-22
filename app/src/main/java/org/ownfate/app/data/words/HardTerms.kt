package org.ownfate.app.data.words

/**
 * Explicit whole words that are unambiguous in isolation. HARD tier: blocks on sight.
 *
 * A term lands here rather than in [StrongTerms] when a substring match would poison ordinary
 * text: jav/java, nude/denude, milf/milfoil, anal/analysis, sex/Sussex. Whole-token matching
 * only. Performer, show and keyword names live in their own files and are merged into this
 * tier by the matcher.
 */
object HardTerms {
    /**
     * Whole-word matches. Two reasons a term lands here rather than in STRONG: it is a substring
     * of something innocent (jav/java, nude/denude, milf/milfoil, anal/analysis, sex/Sussex), or
     * it was caught blocking real English when substring-matched (smut/bismuth, erotica/
     * sclerotica, orgy/porgy, ecchi/zecchino).
     *
     * Performer, show and keyword names are not listed here; they live in [Names] and are merged
     * into this tier by the matcher.
     */
    val terms = listOf(
        "pron", "prn", "nsfl", "prnstar", "pstar",  "cornstar", "corn star",
        "milf", "slut", "sluts", "whore", "whores", "nudes", "sexting",
        "booru", "ecchi", "nubiles", "dorcel", "sexart",
        "sex video", "sex videos", "sex pictures",
        "sex photos","nude pictures", "nude photos","naked pictures", "naked photos","explicit video", "explicit videos",
        "explicit images", "explicit pictures","sexual videos", "sexual images","erotic videos", "erotic images",
        "erotic stories", "sex stories","sex show", "sex shows", "pussy", "pussies", "cunt", "twat", "asshole",
        "blow job", "blow jobs", "hand job", "hand jobs", "gang bang", "gang bangs",
        "cum shot", "cum shots", "jerk off", "jerk offs", "oral sex", "vaginal sex", "group sex",
        "cam girl", "cam girls", "cam whore", "cam whores", "nude selfie", "nude selfies",
        "naked selfie", "naked selfies", "sex toy", "sex toys", "shemale", "shemales",
        "call girl", "call girls", "escort service", "escort services",
        "wank", "wanking", "fap","cumslut", "cumguzzler", "domsub",
        "rimjob", "hotwife"
    )
}
