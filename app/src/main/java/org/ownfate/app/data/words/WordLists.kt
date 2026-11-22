package org.ownfate.app.data.words

/**
 * Every built-in word list, in one place.
 *
 * The lists live one-per-file next to this facade. The engine only ever reads them through
 * here, so moving or renaming a list is one edit. All lists are compiled into the APK; none is
 * downloaded, and none needs a Context to load.
 *
 * Adding a term:
 *  - adult performers, shows/channels and explicit phrases -> AdultPerformers / AdultShows /
 *    AdultKeywords (HARD, blocks on sight)
 *  - unambiguous stems and brands -> StrongTerms (STRONG, blocks on sight)
 *  - explicit words that are unsafe as substrings -> HardTerms (HARD, whole token only)
 *  - ambiguous words that need context -> SoftTerms (SOFT, needs a second signal)
 *  - explicit help-seeking phrases -> RescuePhrases.explicit (excuses a nearby match)
 *  - generic help context -> RescuePhrases.generic (excuses a nearby SOFT match only)
 *  - ordinary-life phrases -> AllowPhrases (excuses a nearby HARD/SOFT match)
 */
object WordLists {
    val strong = StrongTerms.terms
    val hard = HardTerms.terms
    val soft = SoftTerms.terms
    val context = ContextTerms.terms
    val rescue = RescuePhrases.explicit
    val genericRescue = RescuePhrases.generic
    val allow = AllowPhrases.terms

    val adultPerformers = AdultPerformers.terms
    val commonNames = CommonNames.terms
    val adultShows = AdultShows.terms
    val adultKeywords = AdultKeywords.terms
}
