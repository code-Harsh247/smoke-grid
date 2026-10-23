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
    private const val KEY_START = "start_date"
    private const val DAY_PREFIX = "d_"

    /** A cigarette at 1:30am still counts for the previous day. */
    const val DAY_START_HOUR = 4L

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun today(): LocalDate = LocalDateTime.now().minusHours(DAY_START_HOUR).toLocalDate()

    /** First day of tracking; days before it are drawn dimmed. */
    fun startDate(context: Context): LocalDate {
        val p = prefs(context)
        p.getString(KEY_START, null)?.let { return LocalDate.parse(it) }
        val t = today()
        p.edit().putString(KEY_START, t.toString()).apply()
        return t
    }

    fun count(context: Context, date: LocalDate): Int =
        prefs(context).getInt(DAY_PREFIX + date, 0)

    fun setCount(context: Context, date: LocalDate, count: Int) {
        val start = startDate(context)
        val editor = prefs(context).edit()
        if (count <= 0) editor.remove(DAY_PREFIX + date) else editor.putInt(DAY_PREFIX + date, count)
        if (date.isBefore(start)) editor.putString(KEY_START, date.toString())
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
