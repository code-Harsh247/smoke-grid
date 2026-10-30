package com.smokegrid.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Totals for a stretch of days. Weeks run Mon–Sun (like the grid's columns), months are calendar months.
 * Only days from the start of tracking up to today count, so the current period is "so far".
 */
class Period(
    val from: LocalDate,
    val total: Int,
    /** Days in the period that were tracked (on or after the start, not after today). */
    val trackedDays: Int,
    val current: Boolean,
) {
    val average: Float get() = if (trackedDays == 0) 0f else total.toFloat() / trackedDays
}

object Stats {
    fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun period(counts: Map<LocalDate, Int>, from: LocalDate, to: LocalDate, start: LocalDate, today: LocalDate): Period {
        var total = 0
        var tracked = 0
        var day = maxOf(from, start)
        val end = minOf(to, today)
        while (!day.isAfter(end)) {
            total += counts[day] ?: 0
            tracked++
            day = day.plusDays(1)
        }
        return Period(from, total, tracked, current = !today.isBefore(from) && !today.isAfter(to))
    }

    /** The last [n] Monday-start weeks, oldest first, ending with this week. */
    fun weeks(counts: Map<LocalDate, Int>, start: LocalDate, today: LocalDate, n: Int): List<Period> {
        val thisMonday = mondayOf(today)
        return (n - 1 downTo 0).map { k ->
            val monday = thisMonday.minusWeeks(k.toLong())
            period(counts, monday, monday.plusDays(6), start, today)
        }
    }

    /** Calendar months from the month tracking began to this month, oldest first, at most the last [max]. */
    fun months(counts: Map<LocalDate, Int>, start: LocalDate, today: LocalDate, max: Int): List<Period> {
        val first = YearMonth.from(start)
        val last = YearMonth.from(today)
        val all = generateSequence(first) { if (it < last) it.plusMonths(1) else null }.toList()
        return all.takeLast(max).map { month ->
            period(counts, month.atDay(1), month.atEndOfMonth(), start, today)
        }
    }

    fun thisWeek(counts: Map<LocalDate, Int>, start: LocalDate, today: LocalDate) = weeks(counts, start, today, 1).single()

    fun lastWeek(counts: Map<LocalDate, Int>, start: LocalDate, today: LocalDate): Period {
        val monday = mondayOf(today).minusWeeks(1)
        return period(counts, monday, monday.plusDays(6), start, today)
    }

    fun thisMonth(counts: Map<LocalDate, Int>, start: LocalDate, today: LocalDate): Period {
        val month = YearMonth.from(today)
        return period(counts, month.atDay(1), month.atEndOfMonth(), start, today)
    }

    fun lastMonth(counts: Map<LocalDate, Int>, start: LocalDate, today: LocalDate): Period {
        val month = YearMonth.from(today).minusMonths(1)
        return period(counts, month.atDay(1), month.atEndOfMonth(), start, today)
    }

    /** Bar colour from the average per day: 0 clean, then under 1.5 · 1.5–2.5 · 2.5–3.5 · 3.5 and up. */
    fun shadeFor(period: Period, palette: GridPalette): Int {
        val avg = period.average
        return when {
            period.trackedDays == 0 -> palette.beforeTracking
            avg == 0f -> palette.clean
            avg < 1.5f -> palette.shades[0]
            avg < 2.5f -> palette.shades[1]
            avg < 3.5f -> palette.shades[2]
            else -> palette.shades[3]
        }
    }
}
