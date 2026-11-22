package org.ownfate.app.ui

/**
 * Copy for the small info icons on section headings.
 *
 * Kept next to the UI rather than in resources because every line describes a switch that lives
 * in a different screen: one place to keep the explanations consistent when a switch changes.
 */
internal object SectionHelp {

    data class Text(val heading: String, val lines: List<String>)

    fun of(section: String): Text? = when (section) {
        "Porn" -> Text(
            "Porn options",
            listOf(
                "Adult websites blocks list of websites when it appears in a browser address bar.",
                "Adult content blocks adult words and names in page text and typed searches.",
                "Image & video search blocks image and video search screens in the browser.",
            )
        )

        "Gambling" -> Text(
            "Gambling options",
            listOf(
                "Gambling websites blocks a list of Gambling websites.",
                "Gambling apps blocks a list of famous Gambling apps.",
                "Prediction markets blocks Prediction websites and apps."
            )
        )

        "Blocklist" -> Text(
            "Blocklist",
            listOf(
                "Words / sites are your own words and hosts. They always block, even when other options are off.",
                "Apps blocks apps you select from your own installed apps.",
                "This is for your own custom words and apps that we didn't include in the app."
            )
        )

        "Exclude" -> Text(
            "Exclude",
            listOf(
                "Words / sites here are never used to block. An excluded word wins over every word list, including your own.",
                "Exclude apps are never checked or blocked.",
                "Use this for words and apps that would otherwise cause false positives."
            )
        )

        "Search add-ons" -> Text(
            "Search add-ons",
            listOf(
                "Each switch blocks that app's own search screen before its results load.",
                "You can select which app to block.",
                "All four are off by default and stay locked while a password is set."
            )
        )

        "Status" -> Text(
            "Status",
            listOf(
                "Accessibility service is required for every block. With it off, nothing runs.",
                "Device admin is optional. it does not block anything itself, It is just used as friction."
            )
        )

        "Guards" -> Text(
            "Guards",
            listOf(
                "Protect OwnFate guards this app's accessibility and device-admin screens, so a switch cannot be turned off in passing.",
                "Block power menu guards the power menu's actions against a quick bypass."
            )
        )

        "Lock changes" -> Text(
            "Lock changes",
            listOf(
                "Delay makes a weaker change wait for the time you choose.",
                "Password makes a weaker change ask for the password.",
                "A stronger change always applies at once, whatever the mode."
            )
        )

        "Waiting" -> Text(
            "Waiting",
            listOf(
                "Changes held back by a delay are listed here with the time left.",
                "Tap one to enter the password and apply it now, or let the timer finish on its own."
            )
        )

        else -> null
    }
}
