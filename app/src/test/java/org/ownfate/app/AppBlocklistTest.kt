package org.ownfate.app

import org.ownfate.app.data.apps.AppBlocklist
import org.ownfate.app.data.apps.GamblingApps
import org.ownfate.app.data.apps.PredictionApps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which list wins when an app is on more than one.
 *
 * The exclude list is the user saying "not monitored" about one package, so it beats every
 * block. A package on the user's own list is blocked even when both category switches are off.
 * The category switches only decide the bundled starter sets, and guarding the same app twice
 * must still produce one reason.
 */
class AppBlocklistTest {

    private val self = "org.ownfate.app"

    private fun reason(
        pkg: String,
        custom: Set<String> = emptySet(),
        allowed: Set<String> = emptySet(),
        removed: Set<String> = emptySet(),
        gambling: Boolean = false,
        prediction: Boolean = false
    ) = AppBlocklist.reasonFor(
        pkg = pkg,
        selfPkg = self,
        blockedApps = custom,
        allowedApps = allowed,
        removedBundled = removed,
        gamblingOn = gambling,
        predictionOn = prediction
    )

    @Test fun aCustomAppIsBlockedWithTheSwitchesOff() =
        assertEquals(AppBlocklist.Reason.CUSTOM, reason("com.example.app", custom = setOf("com.example.app")))

    @Test fun anUnknownAppIsLeftAlone() = assertNull(reason("com.example.app"))

    @Test fun theGuardNeverBlocksItself() = assertNull(reason(self, custom = setOf(self)))

    @Test fun excludeBeatsACustomBlock() = assertNull(
        reason("com.example.app", custom = setOf("com.example.app"), allowed = setOf("com.example.app"))
    )

    @Test fun excludeBeatsABundledBlock() {
        val kalshi = PredictionApps.bundled.first()
        assertNull(reason(kalshi, allowed = setOf(kalshi), prediction = true))
    }

    @Test fun aCustomBlockBeatsACategoryReason() {
        val kalshi = PredictionApps.bundled.first()
        assertEquals(
            AppBlocklist.Reason.CUSTOM,
            reason(kalshi, custom = setOf(kalshi), prediction = true)
        )
    }

    @Test fun predictionAppsAreBlockedOnlyWhileTheSwitchIsOn() {
        val pkg = PredictionApps.bundled.first()
        assertNull(reason(pkg))
        assertEquals(AppBlocklist.Reason.PREDICTION, reason(pkg, prediction = true))
    }

    @Test fun gamblingAppsAreBlockedOnlyWhileTheSwitchIsOn() {
        val pkg = GamblingApps.bundled.first()
        assertNull(reason(pkg))
        assertEquals(AppBlocklist.Reason.GAMBLING, reason(pkg, gambling = true))
    }

    @Test fun anExceptionSilencesABundledApp() {
        val pkg = PredictionApps.bundled.first()
        assertNull(reason(pkg, removed = setOf(pkg), prediction = true))
    }

    @Test fun bothSwitchesOnStillGivesOneReason() {
        val pkg = PredictionApps.bundled.first()
        assertEquals(AppBlocklist.Reason.PREDICTION, reason(pkg, gambling = true, prediction = true))
    }

    @Test fun aCategorySwitchDoesNotCatchAnUnrelatedApp() =
        assertNull(reason("com.example.app", gambling = true, prediction = true))

    @Test fun everyBundledPackageHasAReason() {
        for (pkg in PredictionApps.bundled) {
            assertEquals(AppBlocklist.Reason.PREDICTION, reason(pkg, prediction = true))
        }
        for (pkg in GamblingApps.bundled) {
            assertEquals(AppBlocklist.Reason.GAMBLING, reason(pkg, gambling = true))
        }
    }
}
