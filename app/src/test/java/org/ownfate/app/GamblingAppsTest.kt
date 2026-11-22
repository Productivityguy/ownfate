package org.ownfate.app

import org.ownfate.app.data.apps.GamblingApps
import org.ownfate.app.guard.OffSwitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bundled gambling app list. It is a curated starter set, so these tests check its shape and
 * a few anchors rather than trying to be an exhaustive catalogue.
 */
class GamblingAppsTest {

    @Test fun bundledListIsSortedAndDistinct() {
        val bundled = GamblingApps.bundled
        assertEquals(bundled.distinct(), bundled)
        assertEquals(bundled.sorted(), bundled)
        assertTrue(bundled.size >= 40)
    }

    @Test fun everyEntryLooksLikeAPackage() {
        for (pkg in GamblingApps.bundled) {
            assertTrue("$pkg is not a package name", pkg.contains('.'))
            assertTrue("$pkg has whitespace", pkg.none { it.isWhitespace() })
            assertFalse("$pkg starts with a dot", pkg.startsWith('.'))
        }
    }

    @Test fun theLargeUSBooksArePresent() {
        val bundled = GamblingApps.bundled
        assertTrue(bundled.contains("com.draftkings.sportsbook"))
        assertTrue(bundled.contains("com.fanduel.sportsbook"))
        assertTrue(bundled.contains("com.betfanatics.sportsbook.android"))
        assertTrue(bundled.contains("com.playmgm.nj.sports"))
        assertTrue(bundled.contains("com.espn.bet"))
        assertTrue(bundled.contains("com.bet365SportsNJ.Bet365_ApplicationNJ"))
        assertTrue(bundled.contains("com.pyrsoftware.pokerstars.net"))
    }

    @Test fun theVerifiedGamblingGapsArePresent() {
        val bundled = GamblingApps.bundled
        assertTrue(bundled.contains("com.rush.ri"))
        assertTrue(bundled.contains("com.goldennugget.casino"))
        assertTrue(bundled.contains("com.pennentertainment.hc"))
        assertTrue(bundled.contains("com.parxcasino.pa.app.play.us"))
        assertTrue(bundled.contains("com.wsoppa.gp"))
        assertTrue(bundled.contains("com.myprizepicks.myprizepicks"))
        assertTrue(bundled.contains("app.instabet.betr"))
        assertTrue(bundled.contains("com.playstar.casino.prod"))
        assertTrue(bundled.contains("air.com.playtika.slotomania"))
        assertTrue(bundled.contains("com.williamsinteractive.jackpotparty"))
        assertTrue(bundled.contains("com.ddi"))
        assertTrue(bundled.contains("com.playstudios.myvegas"))
        assertTrue(bundled.contains("com.playstudios.popslots"))
        // A betting exchange stays with gambling; its host is in the gambling list too.
        assertTrue(bundled.contains("com.smarkets"))
        // Event-contract apps are owned by the prediction switch, not this one.
        assertFalse(bundled.contains("com.draftkings.predict"))
        assertFalse(bundled.contains("com.fanduel.flywheelnativecontainer.futures"))
    }

    @Test fun ownfateAndSettingsAreNeverBundled() {
        assertFalse(GamblingApps.bundled.contains("org.ownfate.app"))
        for (pkg in GamblingApps.bundled) {
            assertFalse("$pkg must not be a Settings shell",
                OffSwitch.looksLikeSettings(pkg))
        }
    }

    @Test fun membershipIsExact() {
        assertTrue(GamblingApps.bundled.contains("com.draftkings.sportsbook"))
        assertFalse(GamblingApps.bundled.contains("com.draftkings.sportsbook.extra"))
        assertFalse(GamblingApps.bundled.contains("com.example.notgambling"))
    }

    @Test fun allowedAppsOverrideBothBlockLists() {
        val allowed = setOf("com.draftkings.sportsbook", "com.example.userblocked")
        val blocked = GamblingApps.blockedSet(
            bundledOn = true,
            removedBundled = setOf("com.fanduel.sportsbook"),
            blockedApps = setOf("com.example.userblocked", "com.example.other"),
            allowedApps = allowed,
            selfPkg = "org.ownfate.app"
        )
        assertFalse(blocked.contains("com.draftkings.sportsbook"))
        assertFalse(blocked.contains("com.example.userblocked"))
        assertTrue(blocked.contains("com.example.other"))
        assertTrue(blocked.contains("com.pyrsoftware.pokerstars.net"))
        assertFalse(blocked.contains("com.fanduel.sportsbook"))
    }

    @Test fun ownfateIsNeverBlocked() {
        val blocked = GamblingApps.blockedSet(
            bundledOn = true,
            removedBundled = emptySet(),
            blockedApps = setOf("org.ownfate.app"),
            allowedApps = emptySet(),
            selfPkg = "org.ownfate.app"
        )
        assertFalse(blocked.contains("org.ownfate.app"))
    }
}
