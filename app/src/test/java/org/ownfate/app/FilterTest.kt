package org.ownfate.app

import org.ownfate.app.filter.Filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The word list is the whole product, so it gets the tests.
 *
 * Two failure modes matter and they pull in opposite directions. A miss lets the thing through; a
 * false positive teaches the user to distrust the guard, and a guard you do not trust is one you
 * switch off. Every false-positive case below is a real phrase that this matcher blocked at some
 * point while it was being written.
 */
class FilterTest {

    private fun hit(s: String) = Filter.match(s) != null
    private fun tier(s: String) = Filter.match(s)?.tier

    // ---- must catch -------------------------------------------------------

    @Test fun plainTerm() = assertNotNull(Filter.match("porn"))

    @Test fun case() = assertTrue(hit("PORN"))

    @Test fun leetspeak() = assertTrue(hit("p0rn"))

    @Test fun dotted() = assertTrue(hit("p.o.r.n"))

    @Test fun spacedOut() = assertTrue(hit("p o r n"))

    @Test fun stretched() = assertTrue(hit("poooorn"))

    @Test fun urlBar() =
        assertTrue(hit("https://www.pornhub.com/view_video.php?viewkey=123456"))

    @Test fun namedSite() = assertTrue(hit("xhamster.com"))

    @Test fun compactBrand() = assertTrue(hit("cam4"))

    @Test fun newSiteBrandIsStrong() =
        assertEquals(Filter.Tier.STRONG, tier("streamate"))

    @Test fun newDigitBrandUsesLiteralTokenPath() =
        assertEquals(Filter.Tier.STRONG, tier("n8ked"))

    @Test fun aiNudifyPhraseIsHard() =
        assertEquals(Filter.Tier.HARD, tier("undress ai"))

    @Test fun dottedBrandPhraseIsHard() =
        assertEquals(Filter.Tier.HARD, tier("iwara.tv"))

    @Test fun dedupedBrandStillStrong() =
        assertEquals(Filter.Tier.STRONG, tier("erome"))

    @Test fun dedupedAdultVideoStillHard() =
        assertEquals(Filter.Tier.HARD, tier("adult video"))

    @Test fun xFreeLookalikeIsNotAdult() =
        assertNull(Filter.match("XFree86 driver"))

    @Test fun adultWorkforceIsNotAdultSite() =
        assertNull(Filter.match("adult workforce shortage"))

    @Test fun compoundFromProse() = assertTrue(hit("live sex cams"))

    @Test fun softPlusContext() = assertTrue(hit("nude pics"))

    @Test fun softPlusContextTwo() = assertTrue(hit("horny teens"))

    @Test fun softPlusContextThree() = assertTrue(hit("naked video"))

    @Test fun strongStemInSentence() =
        assertNotNull(Filter.match("watch free hentai online"))

    @Test fun strongStemPrefixInCompound() =
        assertEquals(Filter.Tier.STRONG, tier("pornvideos"))

    @Test fun strongStemInflection() =
        assertEquals(Filter.Tier.STRONG, tier("pornography"))

    @Test fun masturbationInflection() =
        assertEquals(Filter.Tier.STRONG, tier("masturbation"))

    @Test fun spacedBrandIsStillCaught() =
        assertEquals(Filter.Tier.STRONG, tier("cam 4"))

    @Test fun spacedLetterBrandIsStillCaught() =
        assertEquals(Filter.Tier.STRONG, tier("x video"))

    @Test fun onlyFansWithoutSpaceIsStillCaught() =
        assertEquals(Filter.Tier.STRONG, tier("onlyfans"))

    @Test fun domainStemInTheMiddleIsStillCaught() =
        assertEquals(Filter.Tier.STRONG, tier("https://myfappsite.example.com"))

    @Test fun proseDomainStemInTheMiddleIsStillCaught() =
        assertEquals(Filter.Tier.STRONG, tier("visit myfappsite.example today"))

    @Test fun spacedObfuscationAfterAWordIsStillCaught() =
        assertEquals(Filter.Tier.STRONG, tier("watch p o r n"))

    // "rule 34 of the regulations" is ordinary legal text, so the numbered term is SOFT now. It
    // still blocks beside adult context instead of disappearing from the lists.
    @Test fun digitTermIsDemotedToSoft() {
        assertNull(Filter.match("rule 34 of the regulations"))
        assertEquals(Filter.Tier.SOFT, tier("rule 34 video clip"))
    }

    @Test fun motherlessWithTld() = assertTrue(hit("motherless.com"))

    @Test fun queryString() = assertTrue(hit("google.com/search?q=porn"))

    @Test fun fullwidthLookalikes() = assertTrue(hit("ｐｏｒｎ"))

    @Test fun cyrillicLookalike() =
        assertTrue(hit("p\u043Ern")) // Cyrillic о, not Latin o

    @Test fun accentedLookalike() = assertTrue(hit("pórn"))

    @Test fun zeroWidthObfuscation() = assertTrue(hit("po\u200Brn"))

    @Test fun doubledLetterObfuscation() =
        assertEquals(Filter.Tier.STRONG, tier("poorn"))

    @Test fun spacedActPhrases() {
        assertTrue(hit("blow job"))
        assertTrue(hit("jerk off"))
        assertTrue(hit("cum shot"))
        assertTrue(hit("oral sex"))
        assertTrue(hit("cam girl"))
        assertTrue(hit("nude selfie"))
        assertTrue(hit("call girl"))
    }

    @Test fun newerBrands() {
        assertEquals(Filter.Tier.STRONG, tier("redgifs"))
        assertEquals(Filter.Tier.STRONG, tier("ahegao"))
        assertEquals(Filter.Tier.STRONG, tier("f95zone"))
    }

    // ---- must not catch ---------------------------------------------------

    @Test fun placeName() = assertNull(Filter.match("Sussex"))

    @Test fun ordinaryEnglish() = assertNull(Filter.match("analysis"))

    @Test fun latinHonours() = assertNull(Filter.match("cum laude"))

    @Test fun idiom() = assertNull(Filter.match("naked eye"))

    @Test fun schoolSubject() = assertNull(Filter.match("sex education"))

    @Test fun sexuality() = assertNull(Filter.match("sexual health"))

    @Test fun lookingForHelp() = assertNull(Filter.match("porn addiction recovery"))

    @Test fun lookingForHelpAgain() = assertNull(Filter.match("how to stop watching porn"))

    @Test fun bareQuitIsNotHelp() = assertNull(Filter.match("quit"))

    @Test fun quitButtonOnAPageDoesNotRescueThePage() {
        // Bare "Quit" used to be a rescue word, which meant any game menu disabled the screen.
        assertTrue(hit("quit the game porn video"))
    }

    @Test fun namedHelpStillRescues() = assertNull(Filter.match("quit porn"))

    @Test fun ordinaryWordWithStem() = assertNull(Filter.match("motherless"))

    @Test fun programming() = assertNull(Filter.match("javascript tutorial"))

    @Test fun plant() = assertNull(Filter.match("milfoil"))

    @Test fun verb() = assertNull(Filter.match("denude"))

    @Test fun name() = assertNull(Filter.match("Dick Van Dyke"))

    @Test fun rooster() = assertNull(Filter.match("cock crow at dawn"))

    @Test fun cat() = assertNull(Filter.match("pussycat"))

    @Test fun harassment() = assertNull(Filter.match("harassment policy"))

    @Test fun abbreviations() = assertNull(Filter.match("GNU/Linux"))

    @Test fun beeGeesIsNotTheBrand() = assertNull(Filter.match("Bee Gees greatest hits"))

    @Test fun oralSurgeryIsNotSex() = assertNull(Filter.match("oral surgery"))

    @Test fun sexWorkerRightsIsHelpAndNews() =
        assertNull(Filter.match("sex worker rights and support"))

    @Test fun bondageOfDebtIsNotEnoughAlone() =
        assertNull(Filter.match("the bondage of debt"))

    // "xxx" no longer blocks on its own: film titles, Roman numerals and placeholders pass
    // through. It still blocks as a context signal (see xxxWithContextStillBlocks).
    @Test fun romanNumeralIsNotBlocked() = assertNull(Filter.match("Super Bowl XXX"))

    // Google result URLs carry base64 in "&sxsrf=APpeQ...". Welding across "=" produced
    // "sxsrfappeq", which contains "fapp" and blocked this search. It must stay clean.
    @Test fun googleSearchUrlIsNotAWeld() =
        assertNull(Filter.match("https://www.google.com/search?q=3.5+mm+jack+earphone&sxsrf=APpeQabc123"))

    // A query's plus signs are word gaps, not glue. These ordinary searches used to become
    // STRONG brands only because URL compaction welded across "+".
    @Test fun urlQueryPlusDoesNotWeldOrdinaryWords() {
        for (url in listOf(
            "https://www.google.com/search?q=watch+anime",
            "https://www.google.com/search?q=jerk+mate",
            "https://www.google.com/search?q=free+use+doctrine",
            "https://www.google.com/search?q=benefits+of+app"
        )) assertNull(url, Filter.match(url))
    }

    @Test fun urlQueryEscapedSpaceDoesNotWeldOrdinaryWords() {
        assertNull(Filter.match("https://www.google.com/search?q=watch%20anime"))
        assertNull(Filter.match("https://www.google.com/search?q=benefits%20of%20app"))
    }

    @Test fun urlQueryStillCatchesRealAdultTerms() {
        assertEquals(Filter.Tier.STRONG, tier("https://www.google.com/search?q=porn+videos"))
        assertEquals(Filter.Tier.STRONG, tier("https://www.example.com/watch?v=pornvideos"))
    }

    // Short words weld together in the folded text. That is useful for "p o r n", but it used
    // to make ordinary phrases contain "porn" and "fapp". A term now has to start a word or be
    // the whole welded run.
    @Test fun stopOrNotIsNotPorn() = assertNull(Filter.match("stop or not"))

    @Test fun topOrNotIsNotPorn() = assertNull(Filter.match("top or not"))

    @Test fun benefitsOfAppIsNotFapp() = assertNull(Filter.match("benefits of app"))

    @Test fun ofAppIsNotFapp() = assertNull(Filter.match("of app"))

    @Test fun ofAppStoreIsNotFapp() = assertNull(Filter.match("the benefits of app stores"))

    // Two ordinary long words are not welded into a brand. "cream pie" is a dessert.
    @Test fun creamPieIsNotCreampie() = assertNull(Filter.match("cream pie recipe"))

    // A stem buried in the middle of a plain word is not a hit; a domain with punctuation still
    // is, because URL-like tokens keep the broad match.
    @Test fun stemInTheMiddleOfAPlainWordIsNotAStrongHit() =
        assertNull(Filter.match("myfappsite"))

    @Test fun motherlessChildIsNotTheSite() =
        assertNull(Filter.match("sometimes i feel like a motherless child lyrics"))

    // "xxx" is no longer a standalone block: it needs a second signal. Placeholders, film titles
    // and Roman numerals pass through; "nude xxx pics" still blocks (soft + context).
    @Test fun xxxPlaceholderIsNotBlockedAlone() = assertNull(Filter.match("set the value to xxx"))

    @Test fun xxxWithContextStillBlocks() = assertNotNull(Filter.match("nude xxx pics"))

    // ---- overrides --------------------------------------------------------

    @Test fun customPhrase() =
        assertEquals("example-site", Filter.match("visit example-site today",
            custom = listOf("example-site"))?.term)

    @Test fun customStem() =
        assertEquals("foobar", Filter.match("foobar", custom = listOf("foobar"))?.term)

    @Test fun customIsHard() =
        assertEquals(Filter.Tier.CUSTOM, Filter.match("foobar", custom = listOf("foobar"))?.tier)

    @Test fun customDoesNotAffectOthers() =
        assertNull(Filter.match("visit example today", custom = listOf("example-site")))

    @Test fun customRunsWithAdultWordsOff() =
        assertEquals(Filter.Tier.CUSTOM,
            Filter.match("foobar", custom = listOf("foobar"), includeWords = false)?.tier)

    @Test fun builtInWordsStopWhenTheSwitchIsOff() {
        assertNull(Filter.match("milf", includeWords = false))
        assertNull(Filter.match("nude pics", includeWords = false))
        assertNotNull(Filter.match("milf", includeWords = true))
    }

    // ---- shape of the result ---------------------------------------------

    @Test fun exactlyOneSoftWordIsNotEnough() = assertNull(Filter.match("nude"))

    @Test fun twoSoftWordsAreEnough() =
        assertNotNull(Filter.match("nude naked"))

    @Test fun matchReportsTheTerm() {
        val m = Filter.match("nude pics")
        assertNotNull(m)
        assertTrue(m!!.term.contains("nude"))
    }

    // ---- tiers ------------------------------------------------------------

    @Test fun strongTier() = assertEquals(Filter.Tier.STRONG, tier("porn"))

    @Test fun hardTier() = assertEquals(Filter.Tier.HARD, tier("milf"))

    @Test fun softTier() = assertEquals(Filter.Tier.SOFT, tier("nude pics"))

    // "ownfate" is an ordinary political word; it is not a reason to step back from a page.
    @Test fun ownfateIsNotABypassWord() = assertNull(Filter.match("remove ownfate of speech"))

    // ---- names ------------------------------------------------------------

    @Test fun famousNameBlocksOnSight() = assertEquals(Filter.Tier.HARD, tier("Mia Khalifa"))

    // Verified against names.txt and not a common-name collision, so it blocks without context.
    @Test fun verifiedStarBlocksOnSight() = assertEquals(Filter.Tier.HARD, tier("Adria Rae"))

    @Test fun retiredFamousNameStillBlocks() = assertEquals(Filter.Tier.HARD, tier("Stoya"))

    @Test fun commonNameAloneIsNotEnough() = assertNull(Filter.match("Alex Jones"))

    @Test fun commonNameWithContextIsSoft() {
        val m = Filter.match("Alex Jones nude pics")
        assertNotNull(m)
        assertEquals(Filter.Tier.SOFT, m!!.tier)
        assertTrue(m.term.contains("Alex Jones"))
    }

    @Test fun adultShowBlocksOnSight() = assertEquals(Filter.Tier.HARD, tier("plug talk"))

    @Test fun adultKeywordBlocksOnSight() = assertEquals(Filter.Tier.HARD, tier("adult star"))

    @Test fun lesserKnownNameAloneIsNotEnough() = assertNull(Filter.match("Alice Miller"))

    @Test fun lesserKnownNameWithContextIsSoft() {
        val m = Filter.match("Alice Miller nude pics")
        assertNotNull(m)
        assertEquals(Filter.Tier.SOFT, m!!.tier)
        assertTrue(m.term.contains("Alice Miller"))
    }

    @Test fun nameDoesNotMatchInsideAToken() = assertNull(Filter.match("Aliceville"))

    // ---- our own copy -----------------------------------------------------

    // The first version blocked itself when opened: the Controls blurb contained the literal
    // spellings the engine folds into a match, and the launcher's event pointed the scan at our
    // own window. The window guard is the real fix; this keeps the copy honest as well.
    @Test fun controlsCopyIsClean() = assertNull(Filter.match(
        "Lookalike digits, dotted-out letters and stretched spellings are folded before matching."))

    @Test fun wordsCopyIsClean() = assertNull(Filter.match(
        "Matching ignores case, lookalike digits and stretched spellings before any list is consulted."))

    @Test fun cardLabelsAreClean() {
        assertNull(Filter.match("This isn't adult content — appeal"))
        assertNull(Filter.match("Close — it's adult content"))
    }

    // ---- allow words ------------------------------------------------------

    @Test fun allowWordSuppressesAStrongWordMatch() =
        assertNull(Filter.match("porn documentary", allowWords = listOf("documentary")))

    @Test fun allowWordSuppressesACustomWordMatch() =
        assertNull(Filter.match("badword here", custom = listOf("badword"), allowWords = listOf("badword")))

    @Test fun allowPhraseSuppressesAMatch() =
        assertNull(Filter.match("sex education class", allowWords = listOf("education class")))

    @Test fun sexWorkIsNotABuiltInAllowPhrase() =
        assertEquals(Filter.Tier.SOFT, tier("nude sex worker photos"))

    @Test fun allowWordDoesNotInventMatches() =
        assertNull(Filter.match("ordinary text", allowWords = listOf("documentary")))

    @Test fun withoutAllowWordsTheMatchStillHappens() =
        assertEquals(Filter.Tier.STRONG, tier("porn documentary"))

    @Test fun allowWordExcusesItsOwnWholeToken() =
        assertNull(Filter.match("sex", allowWords = listOf("sex")))

    @Test fun shortAllowWordDoesNotExcuseALongerOrdinaryWord() =
        assertNotNull(Filter.match("porn in Sussex", allowWords = listOf("sex")))

    @Test fun shortAllowWordDoesNotExcuseALongerWordInAUrl() =
        assertNotNull(Filter.match("https://sussex.example porn", allowWords = listOf("sex")))

    @Test fun allowWordStillCoversALongerInflectedForm() =
        assertNull(Filter.match("casinos", custom = listOf("casino"), allowWords = listOf("casino")))

    // ---- ordinary English that was once a hard block ----------------------

    // These words are real English, place names and sports chants. On their own they must not
    // eject an unrelated page; with a second signal the soft tier still blocks (see the next
    // tests), so the protection is not lost.
    @Test fun selectiveBreedingIsNotAdult() = assertNull(Filter.match("selective breeding of cattle"))

    @Test fun currencyPeggingIsNotAdult() = assertNull(Filter.match("currency pegging explained"))

    @Test fun placeCalledCummingIsNotAdult() = assertNull(Filter.match("Cumming GA homes for sale"))

    @Test fun gardenEdgingIsNotAdult() = assertNull(Filter.match("garden edging ideas"))

    @Test fun footballGoonersIsNotAdult() = assertNull(Filter.match("Arsenal Gooners chant"))

    @Test fun orbitIsNotAdult() = assertNull(Filter.match("the orgy of colours in the sunset"))

    @Test fun demotedWordWithContextStillBlocks() =
        assertEquals(Filter.Tier.SOFT, tier("nude edging videos"))

    // ---- scope of a rescue or allow phrase --------------------------------

    private fun filler(words: Int) = " ordinary prose about gardening and weather.".repeat(words)

    @Test fun helpNearOneTermDoesNotExcuseAnotherTermFarAway() {
        val page = "porn addiction recovery" + filler(20) + " pornhub"
        assertEquals(Filter.Tier.STRONG, tier(page))
    }

    @Test fun builtInAllowNearOneTermDoesNotExcuseAnotherTermFarAway() {
        val page = "medical advice about sex" + filler(20) + " pornhub"
        assertEquals(Filter.Tier.STRONG, tier(page))
    }

    @Test fun allowWordStillExcusesANearbyMatch() =
        assertNull(Filter.match("a documentary about porn", allowWords = listOf("documentary")))

    // ---- tuned rescue and allow -------------------------------------------

    // Generic help context may soften an ambiguous word, but it must not erase an explicit stem.
    @Test fun genericRescueDoesNotExcuseStrongTerms() {
        for (text in listOf(
            "help me find porn videos",
            "help me with a porn star presentation",
            "porn mental health article",
            "porn addiction stories"
        )) assertEquals(text, Filter.Tier.STRONG, tier(text))
    }

    @Test fun explicitRescueStillExcusesStrongTerms() {
        for (text in listOf(
            "porn addiction recovery", "porn addiction help", "quit porn",
            "how to stop watching porn", "help me quit porn", "porn blocker comparison"
        )) assertNull(text, Filter.match(text))
    }

    @Test fun bareBlockerDoesNotExcuseStrongTerms() =
        assertEquals(Filter.Tier.STRONG, tier("porn site blocker review"))

    @Test fun broadMedicalAndConsentWordsNoLongerExcuseAdultTerms() {
        assertNotNull(Filter.match("nude photos doctor"))
        assertNotNull(Filter.match("milf doctor"))
        assertNotNull(Filter.match("sex video medical advice"))
        assertNotNull(Filter.match("nude pics consent form"))
    }

    @Test fun rescuedNameDoesNotHideALaterName() {
        val page = "Mia Khalifa porn addiction recovery" + filler(20) + " Mia Khalifa"
        assertEquals(Filter.Tier.HARD, tier(page))
    }

    // ---- short custom words ------------------------------------------------

    @Test fun shortCustomWordDoesNotMatchInsideAnotherWord() {
        assertNull(Filter.match("better than expected", custom = listOf("bet")))
        assertNull(Filter.match("analysis of the data", custom = listOf("anal")))
    }

    @Test fun shortCustomWordStillMatchesTheWord() {
        assertEquals(Filter.Tier.CUSTOM, Filter.match("place a bet", custom = listOf("bet"))?.tier)
        assertEquals(Filter.Tier.CUSTOM, Filter.match("anal", custom = listOf("anal"))?.tier)
    }

    @Test fun shortCustomWordMatchesItsPlural() =
        assertEquals(Filter.Tier.CUSTOM, Filter.match("two bets", custom = listOf("bet"))?.tier)

    @Test fun longerCustomWordKeepsItsPrefixMatch() =
        assertEquals(Filter.Tier.CUSTOM, Filter.match("many casinos", custom = listOf("casino"))?.tier)

    @Test fun customWordInALongTailUrlStillMatches() =
        assertEquals(Filter.Tier.CUSTOM,
            Filter.match("https://best-casino-deals.example", custom = listOf("casino"))?.tier)

    @Test fun shortCustomWordMatchesAUrlWordBoundary() =
        assertEquals(Filter.Tier.CUSTOM,
            Filter.match("https://place-a-bet.example", custom = listOf("bet"))?.tier)

    @Test fun shortCustomWordDoesNotMatchInsideAUrlWord() =
        assertNull(Filter.match("https://better.example", custom = listOf("bet")))

    // ---- word-list cleanup regressions ------------------------------------

    // Fold.spaced() lowercases input; the STRONG lookup keys must be lowercased too, or every
    // capitalized brand in StrongTerms is silently dead.
    @Test fun capitalizedStrongBrandsNowMatch() {
        assertEquals(Filter.Tier.STRONG, tier("javhub"))
        assertEquals(Filter.Tier.STRONG, tier("tiny4k"))
        assertEquals(Filter.Tier.STRONG, tier("JAVHub"))
        assertEquals(Filter.Tier.STRONG, tier("javhd"))
    }

    // Activating those keys must not activate ordinary words that were only listed as brands.
    @Test fun removedStrongWordsDoNotBlock() {
        assertNull(Filter.match("twerk"))
        assertNull(Filter.match("freezer"))
    }

    // These stems are real words or numbered rules, so they were demoted rather than deleted.
    @Test fun demotedStrongWordsNeedContext() {
        for (text in listOf("babes in arms", "vixen fox", "rule 34 of the regulations")) {
            assertNull(text, Filter.match(text))
        }
        assertEquals(Filter.Tier.SOFT, tier("babes video clip"))
        assertEquals(Filter.Tier.SOFT, tier("vixen video clip"))
    }

    @Test fun demotedStillsKeepTheirDottedSiteNames() {
        assertEquals(Filter.Tier.HARD, tier("babes.com"))
        assertEquals(Filter.Tier.HARD, tier("vixen.com"))
    }

    @Test fun removedContextWordsDoNotCreateASoftPair() {
        assertNull(Filter.match("hot bikini weather"))
        assertNull(Filter.match("big sex scandal"))
        assertNull(Filter.match("shaking hands"))
    }

    // These used to be unappealable HARD blocks. Now they need a second signal like any other
    // common name, while still blocking when the context is there.
    @Test fun movedOrdinaryNamesAreSoftNotHard() {
        val names = listOf(
            "The Pope", "morning star", "Red Fox", "The Red Fox", "Small Hands",
            "Ashley Adams", "Jessica Miller", "Sugar Baby", "Ice Cold", "Queen B", "Sunny Day"
        )
        for (name in names) {
            assertNull(name, Filter.match(name))
            val m = Filter.match("$name nude pics")
            assertNotNull(name, m)
            assertEquals(Filter.Tier.SOFT, m!!.tier)
        }
    }

    @Test fun softWordsFarApartDoNotPair() =
        assertNull(Filter.match("nude" + filler(20) + "naked"))

    @Test fun closeSoftPairStillBlocks() =
        assertEquals(Filter.Tier.SOFT, tier("nude and naked in the same gallery"))

    // The proximity rule must not lose protection: a lone soft word with its own nearby context
    // still blocks even when a different soft word appears far away on the same page.
    @Test fun farApartSoftWordStillBlocksWithItsOwnContext() =
        assertEquals(Filter.Tier.SOFT, tier("nude pics" + filler(20) + "naked"))

    @Test fun addedAdultIntentPhrasesAreHard() {
        val phrases = listOf(
            "xxx video", "xxx videos", "xxx pic", "xxx pics", "xxx photo", "xxx photos",
            "leaked nude video", "leaked nude videos", "leaked sex video", "leaked sex videos",
            "leaked nude clip", "leaked nude clips", "leaked sex clip", "leaked sex clips",
            "amateur sex video", "amateur sex videos", "amateur sex clip", "amateur sex clips",
            "webcam sex show", "webcam sex shows",
            "deepfake nude", "deepfake nudes", "ai nude", "ai nudes",
            "nude celebrity", "nude celebrities", "celebrity nude", "celebrity nudes"
        )
        for (phrase in phrases) assertEquals(phrase, Filter.Tier.HARD, tier(phrase))
    }

    @Test fun newPhrasesDoNotOverblockOrdinaryText() {
        assertNull(Filter.match("ai photo enhancer"))
        assertNull(Filter.match("leaked internal documents"))
        assertNull(Filter.match("leaked video of a company meeting"))
        assertNull(Filter.match("amateur video of a school play"))
        assertNull(Filter.match("webcam show schedule for students"))
    }

    // ---- added brands and creators ----------------------------------------

    @Test fun addedMajorBrandTermsAreStrong() {
        val brands = listOf(
            "erome", "jerkmate", "flirt4free", "pimpbunny", "luxuretv",
            "superchatlive", "qorno", "aznude", "sexkomix", "dlsite", "hanime"
        )
        for (brand in brands) assertEquals(brand, Filter.Tier.STRONG, tier(brand))
    }

    @Test fun addedOnlyFansCreatorsAreHard() {
        val names = listOf(
            "Sophie Rain", "Amouranth", "Corinna Kopf", "Belle Delphine", "Bhad Bhabie",
            "PeachJars", "Mrs Poindexter", "Siew Pui Yi", "Pandora Kaaki",
            "Lilianaheartsss", "Mistress Cardi"
        )
        for (name in names) assertEquals(name, Filter.Tier.HARD, tier(name))
    }

    // Gapped or welded ordinary phrases must not turn into one of the new brands.
    @Test fun newBrandsDoNotCatchOrdinaryPhrases() {
        assertNull(Filter.match("watch anime"))
        assertNull(Filter.match("super chat"))
        assertNull(Filter.match("jerk mate"))
        assertNull(Filter.match("x cams"))
    }

    // ---- narrowed excuses --------------------------------------------------

    // A bare "appeal" was a rescue word, so any court or moderation copy erased a nearby block.
    @Test fun bareAppealDoesNotExcuseAStrongTerm() =
        assertEquals(Filter.Tier.STRONG, tier("porn appeal court ruling"))

    // These single words used to be ALLOW entries. They are ordinary on their own, but they must
    // not switch off a real soft pair when they sit next to it.
    @Test fun broadExcuseWordsNoLongerEraseSoftMatches() {
        for (text in listOf(
            "nude pics gender", "nude pics abuse", "nude pics period", "nude pics std"
        )) assertEquals(text, Filter.Tier.SOFT, tier(text))
    }

    @Test fun specificExcusePhrasesStillExcuseMatches() {
        for (text in listOf(
            "sex education", "sexual abuse support", "gender studies about sex",
            "std test results with sex"
        )) assertNull(text, Filter.match(text))
    }

    @Test fun medicalExcuseIsStillLocalAndSpecific() {
        assertNull(Filter.match("Mia Khalifa medical condition"))
        // Bare "medical" used to excuse the name; it is too easy to find on an unrelated page.
        assertEquals(Filter.Tier.HARD, tier("Mia Khalifa medical"))
        assertEquals(Filter.Tier.HARD, tier("Mia Khalifa" + filler(2) + " medical condition"))
    }

}
