package org.ownfate.app

import org.ownfate.app.data.apps.GamblingApps
import org.ownfate.app.data.apps.PredictionApps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bundled prediction-market apps. These are a starter set matched by exact package name, so
 * the tests check shape and anchors rather than claiming to list every platform.
 */
class PredictionAppsTest {

    @Test fun bundledListIsSortedAndDistinct() {
        val bundled = PredictionApps.bundled
        assertEquals(bundled.distinct(), bundled)
        assertEquals(bundled.sorted(), bundled)
        assertTrue("expected at least twelve prediction-market apps", bundled.size >= 12)
    }

    @Test fun packageNamesLookLikePackageNames() {
        for (pkg in PredictionApps.bundled) {
            assertTrue("$pkg is blank", pkg.isNotBlank())
            assertTrue("$pkg has whitespace", pkg.none { it.isWhitespace() })
            assertTrue("$pkg has no package separator", pkg.contains('.'))
            assertFalse("$pkg is the app itself", pkg == "org.ownfate.app")
            assertFalse("$pkg is a launcher", pkg.startsWith("com.android."))
        }
    }

    @Test fun theLargerMarketsAreCovered() {
        val bundled = PredictionApps.bundled
        assertTrue(bundled.contains("com.kalshi.mobile"))
        assertTrue(bundled.contains("com.polymarket.android"))
        assertTrue(bundled.contains("com.markets.manifold"))
        assertTrue(bundled.contains("com.prophetxsweepstake"))
        assertTrue(bundled.contains("us.novig.app"))
        assertTrue(bundled.contains("com.fanatics.markets"))
        assertTrue(bundled.contains("com.draftkings.predict"))
        assertTrue(bundled.contains("com.fanduel.flywheelnativecontainer.futures"))
    }

    @Test fun clonesAndDelistedAppsAreAbsent() {
        val bundled = PredictionApps.bundled
        assertFalse(bundled.contains("com.kalshi.predictionmarket"))
        assertFalse(bundled.contains("com.predictit.cryptopredictionmarket"))
        assertFalse(bundled.contains("predictIt.PredictIt"))
        assertFalse(bundled.contains("com.metaculus.predictiondata"))
        // A licensed betting exchange is not an event-contract app; it stays under gambling.
        assertFalse(bundled.contains("com.smarkets"))
    }

    @Test fun matchingIsExactNotPrefix() {
        val bundled = PredictionApps.bundled
        assertTrue(bundled.contains("com.polymarket.android"))
        assertFalse(bundled.contains("com.polymarket.android.extra"))
        assertFalse(bundled.contains("com.polymarket"))
    }

    @Test fun noPackageIsBothGamblingAndPrediction() {
        // Two switches own two lists. An overlap would make the reason depend on check order.
        val overlap = PredictionApps.bundled.toSet() intersect GamblingApps.bundled.toSet()
        assertTrue("overlapping bundled apps: $overlap", overlap.isEmpty())
    }
}
