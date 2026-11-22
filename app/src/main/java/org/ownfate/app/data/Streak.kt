package org.ownfate.app.data


import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The user's own clean-day counter, one per category.
 *
 * A streak has a start date chosen by the user and counts inclusively: a streak that starts today
 * is day 1. The count never resets itself; the only ways it changes are a new start date or the
 * "reset from day one" action. A future date is clamped to today, so the counter cannot show a day
 * that has not happened yet.
 *
 * A streak with no stored start begins today, not on the day the app was installed. Installing
 * (or reinstalling) is not a clean day, and a counter that silently ran from install day made the
 * first number meaningless.
 */
object Streak {

    enum class Category(val key: String, val label: String) {
        PORN("streakPornStart", "Porn streak"),
        GAMBLING("streakGamblingStart", "Gambling streak")
    }

    /** Format with the locale in force when the label is drawn, not the locale at class load. */
    fun format(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

    /** The stored start date, never in the future. Missing means today, which is day 1. */
    fun start(ctx: Context, category: Category): LocalDate {
        val stored = Store.long(ctx, category.key, UNSET)
        val today = LocalDate.now()
        if (stored == UNSET) {
            // Persist it so toggling the card off and on again does not move the start forward.
            Store.putLong(ctx, category.key, today.toEpochDay())
            return today
        }
        val date = runCatching { LocalDate.ofEpochDay(stored) }.getOrDefault(today)
        return if (date.isAfter(today)) today else date
    }

    /** Inclusive day count: the start day is day one, and today counts. */
    fun days(ctx: Context, category: Category, today: LocalDate = LocalDate.now()): Int =
        daysBetween(start(ctx, category).toEpochDay(), today.toEpochDay())

    fun setStart(ctx: Context, category: Category, date: LocalDate) {
        val today = LocalDate.now()
        Store.putLong(ctx, category.key, (if (date.isAfter(today)) today else date).toEpochDay())
    }

    fun reset(ctx: Context, category: Category, today: LocalDate = LocalDate.now()) {
        Store.putLong(ctx, category.key, today.toEpochDay())
    }

    /** Inclusive: the start day is day one, and today is included. A future start is day 1. */
    fun daysBetween(startEpochDay: Long, todayEpochDay: Long): Int =
        (todayEpochDay - startEpochDay + 1L)
            .coerceIn(1L, Int.MAX_VALUE.toLong())
            .toInt()

    fun dayLabel(days: Int): String = if (days == 1) "1 day" else "$days days"

    private const val UNSET = Long.MIN_VALUE
}
