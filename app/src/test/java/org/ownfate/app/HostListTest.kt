package org.ownfate.app

import org.ownfate.app.data.HostList
import org.ownfate.app.data.HostLists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The bundled host lists are matched on the address bar only. These tests cover the two things
 * that make that safe: turning whatever the bar shows into a host, and matching it on label
 * boundaries so a lookalike domain never inherits a block.
 */
class HostListTest {

    // ---- address-bar text -> host ----------------------------------------

    @Test fun plainHost() = assertEquals("pornhub.com", HostList.hostOf("pornhub.com"))

    @Test fun schemeIsStripped() =
        assertEquals("pornhub.com", HostList.hostOf("https://pornhub.com"))

    @Test fun pathQueryAndFragmentAreStripped() =
        assertEquals("pornhub.com", HostList.hostOf("https://pornhub.com/watch?v=1#top"))

    @Test fun portIsStripped() =
        assertEquals("pornhub.com", HostList.hostOf("pornhub.com:443"))

    @Test fun userInfoIsStripped() =
        assertEquals("pornhub.com", HostList.hostOf("https://user:pass@pornhub.com/x"))

    @Test fun caseIsFolded() =
        assertEquals("www.pornhub.com", HostList.hostOf("HTTPS://WWW.PornHub.COM"))

    @Test fun trailingDotIsStripped() =
        assertEquals("pornhub.com", HostList.hostOf("pornhub.com."))

    @Test fun unicodeDotIsTreatedAsADot() {
        assertEquals("pornhub.com", HostList.hostOf("https://pornhub。com/watch"))
        assertEquals("pornhub.com", HostList.hostOf("pornhub．com"))
        assertEquals("pornhub.com", HostList.hostOf("pornhub｡com"))
    }

    @Test fun unicodeHostIsConvertedToPunycode() =
        assertEquals("xn--mnchen-3ya.de", HostList.hostOf("münchen.de"))

    @Test fun punycodeHostIsLeftAlone() =
        assertEquals("xn--mnchen-3ya.de", HostList.hostOf("xn--mnchen-3ya.de"))

    @Test fun hintTextIsNotAHost() = assertNull(HostList.hostOf("Search or type URL"))

    @Test fun sentenceWithSpacesIsNotAHost() = assertNull(HostList.hostOf("go to pornhub.com"))

    @Test fun singleLabelIsNotAHost() = assertNull(HostList.hostOf("localhost"))

    @Test fun ipv6LiteralIsNotAHost() = assertNull(HostList.hostOf("[::1]"))

    @Test fun blankIsNotAHost() {
        assertNull(HostList.hostOf(null))
        assertNull(HostList.hostOf(""))
        assertNull(HostList.hostOf("   "))
    }

    // ---- address-bar description -> host ---------------------------------

    @Test fun firefoxDescriptionTitleAddressAndHint() = assertEquals(
        "pornhub.com",
        HostList.hostFromDescription(
            "Pornhub - The Best Free HD Porn pornhub.com. Search or enter address"
        )
    )

    @Test fun descriptionWithAFullUrl() = assertEquals(
        "www.pornhub.com",
        HostList.hostFromDescription(
            "Watch https://www.pornhub.com/view_video.php?viewkey=1. Search or enter address"
        )
    )

    @Test fun descriptionThatIsOnlyAnAddress() =
        assertEquals("pornhub.com", HostList.hostFromDescription("pornhub.com"))

    @Test fun descriptionWithoutAHint() =
        assertEquals("pornhub.com", HostList.hostFromDescription("Pornhub pornhub.com"))

    @Test fun descriptionHintOnlyIsNotAHost() {
        assertNull(HostList.hostFromDescription("Search or enter address"))
        assertNull(HostList.hostFromDescription("Search or enter address. Search or enter address"))
    }

    @Test fun descriptionTrailingPunctuationIsIgnored() = assertEquals(
        "pornhub.com",
        HostList.hostFromDescription("Pornhub (pornhub.com). Search or enter address")
    )

    @Test fun descriptionSkipsADomainInTheTitle() = assertEquals(
        "real.example.com",
        HostList.hostFromDescription(
            "About fake.example.org - real.example.com. Search or enter address"
        )
    )

    @Test fun descriptionTitleMentioningASiteIsNotAnAddress() = assertNull(
        HostList.hostFromDescription("Pornhub.com is shutting down. Search or enter address")
    )

    // ---- address-bar description -> the address word ----------------------

    @Test fun descriptionAddressWordKeepsThePath() = assertEquals(
        "https://www.example.com/watch?v=1",
        HostList.addressFromDescription(
            "Watch https://www.example.com/watch?v=1. Search or enter address"
        )
    )

    @Test fun descriptionAddressWordDropsTheTitle() = assertEquals(
        "example.com",
        HostList.addressFromDescription(
            "Some other title - example.com. Search or enter address"
        )
    )

    @Test fun descriptionWithAHintAndNoAddressHasNoAddressWord() = assertNull(
        HostList.addressFromDescription(
            "Pornhub.com is shutting down. Search or enter address"
        )
    )

    // ---- list entries ------------------------------------------------------

    @Test fun commentIsNotADomain() = assertNull(HostList.normalizeDomain("# Title: hosts"))

    @Test fun domainIsFoldedAndTrimmed() =
        assertEquals("www.foo.com", HostList.normalizeDomain("  WWW.Foo.COM. "))

    @Test fun singleLabelEntryIsIgnored() = assertNull(HostList.normalizeDomain("localhost"))

    @Test fun entryWithPathIsIgnored() =
        assertNull(HostList.normalizeDomain("foo.com/ads"))

    @Test fun underscoreEntryIsKept() =
        assertEquals("forgotten_dark_angel.tripod.com",
            HostList.normalizeDomain("forgotten_dark_angel.tripod.com"))

    @Test fun unicodeDomainEntryIsConvertedToPunycode() =
        assertEquals("xn--mnchen-3ya.de", HostList.normalizeDomain("münchen.de"))

    // ---- host matching -----------------------------------------------------

    private val list = setOf("pornhub.com", "a.b.foo.com", "bar.net")

    @Test fun exactHostMatches() =
        assertEquals("pornhub.com", HostList.matchHost("pornhub.com", list))

    @Test fun parentDomainMatches() {
        assertEquals("pornhub.com", HostList.matchHost("www.pornhub.com", list))
        assertEquals("pornhub.com", HostList.matchHost("m.asia.pornhub.com", list))
    }

    @Test fun listEntryForASubdomainStaysScoped() {
        assertEquals("a.b.foo.com", HostList.matchHost("a.b.foo.com", list))
        assertEquals("a.b.foo.com", HostList.matchHost("x.a.b.foo.com", list))
        assertNull(HostList.matchHost("b.foo.com", list))
        assertNull(HostList.matchHost("foo.com", list))
    }

    @Test fun lookalikeDomainDoesNotMatch() {
        assertNull(HostList.matchHost("notpornhub.com", list))
        assertNull(HostList.matchHost("pornhub.com.evil.example", list))
        assertNull(HostList.matchHost("pornhub.co", list))
    }

    @Test fun bareTldIsNeverConsulted() {
        assertNull(HostList.matchHost("foo.com", setOf("com")))
    }

    @Test fun unloadedListMatchesNothing() =
        assertNull(HostList.matchHost("pornhub.com", null))

    // ---- the bundled assets ------------------------------------------------

    private fun asset(name: String): File {
        val candidates = listOf(
            File("src/main/assets/blocklist/$name"),
            File("app/src/main/assets/blocklist/$name")
        )
        return candidates.firstOrNull { it.exists() } ?: candidates.first()
    }

    private fun domains(name: String): Set<String> {
        val file = asset(name)
        assumeTrue("asset not found from the test working directory", file.exists())
        val out = HashSet<String>(90_000)
        file.useLines { lines ->
            for (line in lines) HostList.normalizeDomain(line)?.let { out.add(it) }
        }
        return out
    }

    @Test fun adultAssetParsesAndCoversTheKnownSites() {
        val domains = domains("adult-hosts.txt")

        assertTrue("expected a large bundled list, got ${domains.size}",
            domains.size > 70_000)
        assertTrue(domains.contains("pornhub.com"))
        assertTrue(domains.contains("xvideos.com"))
        assertTrue(domains.contains("xnxx.com"))
        assertTrue(HostList.matchHost("www.pornhub.com", domains) != null)
        assertFalse(domains.contains("com"))
    }

    @Test fun gamblingAssetParsesAndCoversTheKnownSites() {
        val domains = domains("gambling-hosts.txt")

        assertTrue("expected a bundled gambling list, got ${domains.size}",
            domains.size > 6_000)
        assertTrue(domains.contains("bet365.com"))
        assertTrue(domains.contains("draftkings.com"))
        assertTrue(HostList.matchHost("www.bet365.com", domains) != null)
        assertFalse(domains.contains("com"))
    }

    @Test fun bundledListsMatchTheDisplayedCounts() {
        // The Home screen prints these constants. If an asset is regenerated without updating
        // them, the app quietly lies; this test makes that impossible.
        val adultFile = asset("adult-hosts.txt")
        val gamblingFile = asset("gambling-hosts.txt")
        val predictionFile = asset("prediction-market-hosts.txt")
        assumeTrue("assets not found from the test working directory",
            adultFile.exists() && gamblingFile.exists() && predictionFile.exists())

        val adultLines = adultFile.useLines { lines ->
            lines.count { it.isNotBlank() && !it.startsWith("#") }
        }
        val gamblingLines = gamblingFile.useLines { lines ->
            lines.count { it.isNotBlank() && !it.startsWith("#") }
        }
        val predictionLines = predictionFile.useLines { lines ->
            lines.count { it.isNotBlank() && !it.startsWith("#") }
        }

        assertEquals(HostLists.adult.expectedCount, adultLines)
        assertEquals(HostLists.gambling.expectedCount, gamblingLines)
        assertEquals(HostLists.predictionMarkets.expectedCount, predictionLines)
    }

    @Test fun predictionAssetCoversTheMarketsAndRejectsFalsePositives() {
        val domains = domains("prediction-market-hosts.txt")

        assertTrue("expected a curated prediction-market list, got ${domains.size}",
            domains.size >= 24)
        for (host in listOf(
            "polymarket.com", "kalshi.com", "predictit.org", "manifold.markets",
            "metaculus.com", "augur.net", "zeitgeist.pm", "futuur.com",
            "prophetx.co", "limitless.exchange", "myriad.markets", "opinion.trade",
            "predict.fun", "sx.bet", "totalis.trade", "novig.com", "outpoll.com",
            "tradebanta.com", "fanaticsmarkets.com", "predictions.draftkings.com"
        )) {
            assertTrue("missing $host", domains.contains(host))
            assertTrue("www form does not match $host",
                HostList.matchHost("www.$host", domains) != null)
        }

        // A broker and a weather site used to sit in this list. Neither is a prediction market.
        assertFalse(domains.contains("tradeking.com"))
        assertFalse(domains.contains("forecast.com"))
        // Betting exchanges are already in the gambling list; keeping them here would make the
        // prediction switch quietly widen into a second gambling switch.
        assertFalse(domains.contains("betfair.com"))
        assertFalse(domains.contains("smarkets.com"))
        assertFalse(domains.contains("matchbook.com"))
        assertFalse(domains.contains("com"))
    }
}
