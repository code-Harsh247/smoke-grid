package com.smokegrid.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * All data lives on the phone in SharedPreferences, one key per day: "d_2026-10-23" -> count.
 * v1 only uses 0 / 1, but storing a count means per-day cigarette counts (v2) need no migration.
 */
object SmokeLog {
    private const val PREFS = "smoke_log"
    /** The day the app was first opened. Never changes once set. */
    private const val KEY_FIRST_DAY = "first_day"
    /** Old key that edits used to move (the bug). Dropped on first run of this version. */
    private const val LEGACY_KEY_START = "start_date"
    private const val DAY_PREFIX = "d_"

    /** A cigarette at 1:30am still counts for the previous day. */
    const val DAY_START_HOUR = 4L

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun today(): LocalDate = LocalDateTime.now().minusHours(DAY_START_HOUR).toLocalDate()

    private fun firstDay(context: Context): LocalDate {
        val p = prefs(context)
        p.getString(KEY_FIRST_DAY, null)?.let { return LocalDate.parse(it) }
        val t = today()
        p.edit().putString(KEY_FIRST_DAY, t.toString()).remove(LEGACY_KEY_START).apply()
        return t
    }

    /**
     * Start of tracking; days before it are drawn dimmed.
     * Worked out fresh each time (first day, or an earlier logged day if there is one),
     * so un-marking a past day puts the start back where it was.
     */
    fun startDate(context: Context): LocalDate {
        val first = firstDay(context)
        val earliestLogged = allCounts(context).filterValues { it > 0 }.keys.minOrNull()
        return if (earliestLogged != null && earliestLogged.isBefore(first)) earliestLogged else first
    }

    fun count(context: Context, date: LocalDate): Int =
        prefs(context).getInt(DAY_PREFIX + date, 0)

    fun setCount(context: Context, date: LocalDate, count: Int) {
        firstDay(context) // make sure the first day is recorded before any edit
        val editor = prefs(context).edit()
        if (count <= 0) editor.remove(DAY_PREFIX + date) else editor.putInt(DAY_PREFIX + date, count)
        editor.apply()
    }

    fun toggle(context: Context, date: LocalDate) =
        setCount(context, date, if (count(context, date) > 0) 0 else 1)

    fun allCounts(context: Context): Map<LocalDate, Int> =
        prefs(context).all.mapNotNull { (key, value) ->
            if (key.startsWith(DAY_PREFIX) && value is Int) {
                LocalDate.parse(key.removePrefix(DAY_PREFIX)) to value
            } else null
        }.toMap()

    /** Days since the last smoking day. 0 if you smoked today. */
    fun cleanStreak(context: Context): Long {
        val today = today()
        val lastSmoked = allCounts(context)
            .filter { (date, count) -> count > 0 && !date.isAfter(today) }
            .keys.maxOrNull()
        val from = lastSmoked ?: startDate(context).minusDays(1)
        return ChronoUnit.DAYS.between(from, today)
    }

    /** Emits whenever the log changes, so the widget and app redraw immediately. */
    fun changes(context: Context): Flow<Int> = callbackFlow {
        val p = prefs(context)
        var version = 0
        trySend(version)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            version += 1
            trySend(version)
        }
        p.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { p.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
