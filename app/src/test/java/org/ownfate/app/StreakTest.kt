package org.ownfate.app

import org.ownfate.app.data.Streak

import org.junit.Assert.assertEquals
import org.junit.Test

/** Streaks are inclusive and user-controlled: the start day is day one, never zero. */
class StreakTest {

    @Test fun theStartDayIsDayOne() = assertEquals(1, Streak.daysBetween(0L, 0L))

    @Test fun yesterdayIsDayTwo() = assertEquals(2, Streak.daysBetween(100L, 101L))

    @Test fun aFullWeekIsSevenDays() = assertEquals(7, Streak.daysBetween(100L, 106L))

    @Test fun aFutureStartIsStillDayOne() {
        assertEquals(1, Streak.daysBetween(200L, 100L))
        assertEquals(1, Streak.daysBetween(101L, 100L))
    }

    @Test fun leapDaysAreCounted() {
        // 2024-02-28 through 2024-03-01 inclusive is three days.
        val start = java.time.LocalDate.of(2024, 2, 28).toEpochDay()
        val today = java.time.LocalDate.of(2024, 3, 1).toEpochDay()
        assertEquals(3, Streak.daysBetween(start, today))
    }

    @Test fun dayLabelIsSingularOnlyOnDayOne() {
        assertEquals("1 day", Streak.dayLabel(1))
        assertEquals("2 days", Streak.dayLabel(2))
        assertEquals("365 days", Streak.dayLabel(365))
    }
}
