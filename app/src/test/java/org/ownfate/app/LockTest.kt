package org.ownfate.app

import org.ownfate.app.data.Lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lock's rules without an Android context: severity, delay labels and the password hash.
 * The Context-bound request/apply paths are exercised by the guard on a device.
 */
class LockTest {

    // ---- severity ---------------------------------------------------------

    @Test fun turningASwitchOnIsStricter() =
        assertEquals(1, Lock.Feature.ADULT_SITES.severity("false", "true"))

    @Test fun turningASwitchOffIsWeaker() =
        assertEquals(-1, Lock.Feature.ADULT_SITES.severity("true", "false"))

    @Test fun theSameSwitchIsUnchanged() =
        assertEquals(0, Lock.Feature.ADULT_SITES.severity("true", "true"))

    @Test fun addingToASetIsStricter() =
        assertEquals(1, Lock.Feature.CUSTOM_WORDS.severity("a", "a\nb"))

    @Test fun removingFromASetIsWeaker() =
        assertEquals(-1, Lock.Feature.CUSTOM_WORDS.severity("a\nb", "a"))

    @Test fun aMixedSetChangeIsTreatedAsWeaker() =
        assertEquals(-1, Lock.Feature.CUSTOM_WORDS.severity("a\nb", "b\nc"))

    // Removing a bundled-app exception puts the app back on the list, so it is stricter; adding
    // one unblocks an app and is weaker.
    @Test fun removingABundledAppExceptionIsStricter() =
        assertEquals(1, Lock.Feature.REMOVED_BUNDLED.severity("a", ""))

    @Test fun addingABundledAppExceptionIsWeaker() =
        assertEquals(-1, Lock.Feature.REMOVED_BUNDLED.severity("", "a"))

    // A mixed edit to an allow list can add a new exception, so it must wait out the lock even
    // though it also removes one. Negating setSeverity() used to classify it as stricter.
    @Test fun aMixedBundledAppExceptionEditIsWeaker() =
        assertEquals(-1, Lock.Feature.REMOVED_BUNDLED.severity("a", "b"))

    @Test fun aMixedAllowedWordEditIsWeaker() =
        assertEquals(-1, Lock.Feature.ALLOW_WORDS.severity("a", "b"))

    @Test fun aMixedAllowedAppEditIsWeaker() =
        assertEquals(-1, Lock.Feature.ALLOW_APPS.severity("a", "b"))

    @Test fun anUnchangedAllowedWordEditIsUnchanged() =
        assertEquals(0, Lock.Feature.ALLOW_WORDS.severity("a\nb", "b\na"))

    @Test fun aLongerDelayIsStricter() =
        assertEquals(1, Lock.Feature.LOCK_DELAY.severity("600000", "86400000"))

    @Test fun aShorterDelayIsWeaker() =
        assertEquals(-1, Lock.Feature.LOCK_DELAY.severity("86400000", "600000"))

    @Test fun lockModesAreRanked() {
        assertEquals(-1, Lock.Feature.LOCK_MODE.severity(Lock.Mode.PASSWORD.name, Lock.Mode.DELAY.name))
        assertEquals(2, Lock.Feature.LOCK_MODE.severity(Lock.Mode.OFF.name, Lock.Mode.PASSWORD.name))
        assertEquals(0, Lock.Feature.LOCK_MODE.severity(Lock.Mode.PASSWORD.name,
            Lock.Mode.PASSWORD.name))
    }

    @Test fun modeRequirements() {
        assertTrue(Lock.Mode.PASSWORD.hasPassword)
        assertTrue(Lock.Mode.DELAY.hasDelay)
        assertFalse(Lock.Mode.OFF.hasPassword)
        assertFalse(Lock.Mode.OFF.hasDelay)
        assertFalse(Lock.Mode.PASSWORD.hasDelay)
    }

    // ---- mode exclusivity -------------------------------------------------
    //
    // The screen offers one mode at a time. Picking Delay must not leave the old password
    // guarding it, and picking Password must not leave a delay that reappears when the password
    // is switched off.

    @Test fun choosingDelayClearsThePasswordAndKeepsNoDelay() {
        val effect = Lock.effectFor(Lock.Mode.DELAY)
        assertTrue(effect.wipePassword)
        assertFalse(effect.resetDelay)
    }

    @Test fun choosingPasswordClearsTheDelayAndKeepsNoPassword() {
        val effect = Lock.effectFor(Lock.Mode.PASSWORD)
        assertFalse(effect.wipePassword)
        assertTrue(effect.resetDelay)
    }

    @Test fun choosingOffClearsBoth() {
        val effect = Lock.effectFor(Lock.Mode.OFF)
        assertTrue(effect.wipePassword)
        assertTrue(effect.resetDelay)
    }

    @Test fun noModeClearsNothingItOwns() {
        for (mode in Lock.Mode.values()) {
            val effect = Lock.effectFor(mode)
            assertTrue("$mode clears nothing", effect.wipePassword || effect.resetDelay)
        }
    }

    @Test fun onlyPasswordModeKeepsAPassword() {
        for (mode in Lock.Mode.values()) {
            // A password may only survive if Password mode is the one being selected.
            assertEquals(mode == Lock.Mode.PASSWORD, !Lock.effectFor(mode).wipePassword)
        }
    }

    // ---- delays -----------------------------------------------------------

    @Test fun delayLabels() {
        assertEquals("10 min", Lock.delayLabel(600_000L))
        assertEquals("30 min", Lock.delayLabel(1_800_000L))
        assertEquals("1 h", Lock.delayLabel(3_600_000L))
        assertEquals("12 h", Lock.delayLabel(43_200_000L))
        assertEquals("1 day", Lock.delayLabel(86_400_000L))
        assertEquals("2 days", Lock.delayLabel(172_800_000L))
    }

    @Test fun delayLabelsKeepPartialHoursAndDays() {
        assertEquals("1 h 30 min", Lock.delayLabel(5_400_000L))
        assertEquals("1 day 2 h", Lock.delayLabel(93_600_000L))
        assertEquals("1 day 2 h 30 min", Lock.delayLabel(95_400_000L))
    }

    @Test fun delayLabelsRoundARemainingMinuteUp() {
        assertEquals("1 min", Lock.delayLabel(1L))
        assertEquals("1 min", Lock.delayLabel(59_999L))
        assertEquals("0 min", Lock.delayLabel(0L))
    }

    @Test fun delaysAreSortedAndBounded() {
        val delays = Lock.delays
        assertEquals(Lock.MIN_DELAY, delays.first())
        assertEquals(Lock.MAX_DELAY, delays.last())
        assertEquals(delays.sorted().toList(), delays.toList())
        assertTrue(delays.distinct().size == delays.size)
    }

    // ---- pending order ----------------------------------------------------

    @Test fun theLockModeIsAppliedLastSoOffResetsTheDelay() {
        val due = listOf(
            Lock.Pending(Lock.Feature.LOCK_MODE, Lock.Mode.OFF.name, 1L),
            Lock.Pending(Lock.Feature.LOCK_DELAY, "600000", 2L)
        )
        assertEquals(
            listOf(Lock.Feature.LOCK_DELAY, Lock.Feature.LOCK_MODE),
            Lock.applyOrder(due).map { it.feature }
        )
    }

    // ---- password ---------------------------------------------------------

    private val salt = "00112233445566778899aabbccddeeff"

    @Test fun hashIsDeterministic() {
        assertEquals(Lock.hash("hunter2", salt), Lock.hash("hunter2", salt))
    }

    @Test fun differentPasswordsHashDifferently() {
        assertNotEquals(Lock.hash("hunter2", salt), Lock.hash("hunter3", salt))
    }

    @Test fun differentSaltsHashDifferently() {
        assertNotEquals(Lock.hash("hunter2", salt), Lock.hash("hunter2", "ffeeddccbbaa99887766554433221100"))
    }

    @Test fun hashIsNotThePassword() {
        assertFalse(Lock.hash("hunter2", salt).contains("hunter2"))
    }

    @Test fun matchesOnlyTheRightPassword() {
        val expected = Lock.hash("hunter2", salt)
        assertTrue(Lock.matches("hunter2", salt, expected, 120_000))
        assertFalse(Lock.matches("hunter3", salt, expected, 120_000))
    }

    @Test fun aFourCharacterMinimumIsEnforced() {
        assertTrue(Lock.MIN_PASSWORD_LENGTH >= 4)
    }

    @Test fun saltIsHexAndFresh() {
        val a = Lock.newSalt()
        val b = Lock.newSalt()
        assertEquals(32, a.length)
        assertTrue(a.all { Character.digit(it, 16) >= 0 })
        assertNotEquals(a, b)
    }

    @Test fun addingAnAllowedWordIsWeaker() =
        assertEquals(-1, Lock.Feature.ALLOW_WORDS.severity("a", "a\nb"))

    @Test fun removingAnAllowedWordIsStricter() =
        assertEquals(1, Lock.Feature.ALLOW_WORDS.severity("a\nb", "a"))

    @Test fun addingAnAllowedAppIsWeaker() =
        assertEquals(-1, Lock.Feature.ALLOW_APPS.severity("", "com.example"))

    @Test fun removingAnAllowedAppIsStricter() =
        assertEquals(1, Lock.Feature.ALLOW_APPS.severity("com.example", ""))

}
