package org.ownfate.app.data.words

/**
 * Unambiguous adult-intent phrases.
 *
 * HARD tier: blocks on sight. Single ambiguous words stay in [SoftTerms], where a second
 * signal is required. "adult content" is deliberately absent: it is the card's own copy, and
 * the app must not match its own labels.
 */
object AdultKeywords {
    val terms = listOf(
        "adult entertainment", "adult film", "adult films", "adult industry",
        "adult movie", "adult movies", "adult performer", "adult performers", "adult star",
        "adult stars", "adult video", "adult videos", "adult webcam", "amateur porn", "cam model",
        "cam models", "homemade porn", "leaked nudes", "leaked sex", "nude leak", "nude leaks",
        "onlyfans creator", "onlyfans model", "porn star", "porn stars", "sex tape", "sex tapes",
        "webcam model", "webcam models",
        // Search phrasing that used to slip through: the intent is explicit even though no STRONG
        // stem is present ("leaked clip", for example, has no unambiguous word in it).
        "xxx video", "xxx videos", "xxx pic", "xxx pics", "xxx photo", "xxx photos",
        // "leaked video" and "amateur video" were HARD and blocked company meetings and school
        // plays. Only combinations that name the explicit material stay on this tier.
        "leaked nude video", "leaked nude videos", "leaked sex video", "leaked sex videos",
        "leaked nude clip", "leaked nude clips", "leaked sex clip", "leaked sex clips",
        "amateur sex video", "amateur sex videos", "amateur sex clip", "amateur sex clips",
        "webcam sex show", "webcam sex shows",
        "deepfake nude", "deepfake nudes", "ai nude", "ai nudes",
        "nude celebrity", "nude celebrities", "celebrity nude", "celebrity nudes",
        // AI nudify and current search phrasing. Generic words such as "undress", "deepfake"
        // and "generator" stay out of STRONG; only the explicit combinations belong here.
        "undress ai", "ai undress", "nudify ai", "clothes remover ai",
        "ai clothes remover", "nude generator", "nude ai generator",
        "draw nudes", "deepfake porn video", "deepfake porn videos",
        "deepfake sex video", "deepfake sex videos", "celebrity deepfake nude",
        "celebrity deepfake nudes", "sex comic", "sex comics",
        "nude model", "nude models", "nude influencer", "nude influencers",
        "leaked influencer", "influencer leak",
        // Dotted brand forms are kept as phrases rather than short STRONG prefixes so that
        // ordinary lookalikes (XFree86, "adult workforce") are not swept up.
        "iwara.tv", "xfree.com", "adultwork.com", "fpo.xxx", "undress.app",
        // These two studio names were STRONG until "vixen"/"babes" proved too ordinary as stems.
        // The dotted brand form keeps the real sites on HARD without touching ordinary prose.
        "babes.com", "vixen.com",
    )
}
