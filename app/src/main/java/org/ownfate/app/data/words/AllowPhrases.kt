package org.ownfate.app.data.words

/**
 * Ordinary life that happens to use the ambiguous words.
 *
 * Overrides HARD and SOFT, not STRONG: sex education, medical and biology pages survive while
 * an explicit term elsewhere on the page still counts. The user's own allow list is separate
 * and is checked before the engine runs at all.
 *
 * Bare "gender", "harassment", "abuse", "period", "std" and "sti" are deliberately absent. A
 * single sidebar word must not erase an explicit match; a real exception names the context, as
 * in "gender studies", "sexual abuse support" or "std test results".
 */
object AllowPhrases {
    /** Ordinary life that happens to use the ambiguous words. Overrides HARD and SOFT, not STRONG. */
    val terms = listOf(
        "sex education", "sex ed", "sexual health", "sexuality", "safe sex",
        "sexually transmitted", "reproduction", "reproductive", "puberty",
        "sexual consent", "consent education", "consent training", "consent workshop",
        "gender studies", "gender identity", "gender equality", "gender roles",
        "sexual harassment", "workplace harassment", "sexual assault", "domestic assault",
        "sexual abuse", "domestic abuse", "child abuse", "sex trafficking", "human trafficking",
        "sexism",
        // Bare "medical", "doctor", "biology", "anatomy" and "consent" used to excuse
        // "sex video medical advice", "milf doctor" and "nude pics consent form". Only a named
        // care or study context counts now.
        "medical condition", "medical conditions", "medical examination", "medical exam",
        "medical records", "medical treatment", "medical history", "doctor appointment",
        "doctor appointments", "doctor visit", "clinic visit", "hospital visit",
        "biology class", "anatomy class", "human anatomy", "biology textbook",
        "pregnancy test", "pregnancy tests", "prenatal care", "postnatal care",
        "contraception advice", "birth control", "iud", "std test", "std tests", "std testing",
        "sti test", "sti tests", "sti testing", "hiv", "aids", "herpes", "hpv",
        "chlamydia", "gonorrhea", "syphilis",
        "breast cancer", "cervical cancer", "pap smear", "mammogram", "childbirth",
        "anal fissure", "menstruation", "period pain", "period cramps", "ovulation", "condom"
    )
}
