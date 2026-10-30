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
 * The log lives on the phone in SharedPreferences, one key per day: "d_2026-10-23" -> count.
 * The app and widget only ever read this; Sync.kt copies changes to and from the server.
 */
object SmokeLog {
    private const val PREFS = "smoke_log"
    /** The day the app was first opened. Never changes once set. */
    private const val KEY_FIRST_DAY = "first_day"
    /** Old key that edits used to move (the bug). Dropped on first run of this version. */
    private const val LEGACY_KEY_START = "start_date"
    private const val DAY_PREFIX = "d_"
    /** "u_2026-10-23" -> when that day was last changed (ms), for last-write-wins sync. */
    private const val UPDATED_PREFIX = "u_"
    /** Days changed on this phone that the sync server hasn't stored yet. */
    private const val KEY_DIRTY = "dirty_days"

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
        val p = prefs(context)
        val editor = p.edit()
        if (count <= 0) editor.remove(DAY_PREFIX + date) else editor.putInt(DAY_PREFIX + date, count)
        // Remember when and that this day changed, so it gets sent to the sync server.
        editor.putLong(UPDATED_PREFIX + date, System.currentTimeMillis())
        editor.putStringSet(KEY_DIRTY, dirtySet(p) + date.toString())
        editor.apply()
        Sync.requestSync(context)
    }

    /** Adds [delta] cigarettes to [date]; never goes below 0. */
    fun adjust(context: Context, date: LocalDate, delta: Int) =
        setCount(context, date, count(context, date) + delta)

    fun allCounts(context: Context): Map<LocalDate, Int> =
        prefs(context).all.mapNotNull { (key, value) ->
            if (key.startsWith(DAY_PREFIX) && value is Int) {
                LocalDate.parse(key.removePrefix(DAY_PREFIX)) to value
            } else null
        }.toMap()

    /** Clean days in a row ending on [through], that day included. 0 if you smoked that day. */
    fun cleanStreak(context: Context, through: LocalDate = today()): Long {
        val lastSmoked = allCounts(context)
            .filter { (date, count) -> count > 0 && !date.isAfter(through) }
            .keys.maxOrNull()
        val from = lastSmoked ?: startDate(context).minusDays(1)
        return ChronoUnit.DAYS.between(from, through).coerceAtLeast(0)
    }

    // --- Sync support (see Sync.kt). Remote changes are applied without marking days dirty. ---

    private fun dirtySet(p: SharedPreferences): Set<String> = p.getStringSet(KEY_DIRTY, null).orEmpty()

    internal fun firstDayForSync(context: Context): LocalDate = firstDay(context)

    /** Days changed here that still need sending. */
    internal fun dirtyDays(context: Context): List<SyncDay> {
        val p = prefs(context)
        return dirtySet(p).map { date ->
            SyncDay(date, p.getInt(DAY_PREFIX + date, 0), p.getLong(UPDATED_PREFIX + date, 0))
        }
    }

    /** Clears the dirty flag for days the server stored, unless they changed again meanwhile. */
    internal fun markSent(context: Context, sent: List<SyncDay>) {
        val p = prefs(context)
        val still = dirtySet(p).filterNot { date ->
            sent.any { it.date == date && it.updatedAt == p.getLong(UPDATED_PREFIX + date, 0) }
        }
        p.edit().putStringSet(KEY_DIRTY, still.toSet()).commit()
    }

    /** Applies days from the server that are newer than this phone's copy (last write wins). */
    internal fun applyRemote(context: Context, days: List<SyncDay>, remoteFirstDay: LocalDate?) {
        val p = prefs(context)
        val editor = p.edit()
        val dirty = dirtySet(p).toMutableSet()
        var changed = false
        for (day in days) {
            if (day.updatedAt <= p.getLong(UPDATED_PREFIX + day.date, 0)) continue
            if (day.count <= 0) editor.remove(DAY_PREFIX + day.date) else editor.putInt(DAY_PREFIX + day.date, day.count)
            editor.putLong(UPDATED_PREFIX + day.date, day.updatedAt)
            dirty -= day.date
            changed = true
        }
        if (remoteFirstDay != null && remoteFirstDay.isBefore(firstDay(context))) {
            editor.putString(KEY_FIRST_DAY, remoteFirstDay.toString())
            changed = true
        }
        if (changed) editor.putStringSet(KEY_DIRTY, dirty).commit()
    }

    /**
     * First sync after signing in: keep the higher count of this phone and the server for every day,
     * and mark the days where this phone is higher (or the server has nothing) for sending.
     */
    internal fun mergeFirstSync(context: Context, remote: List<SyncDay>) {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val remoteByDate = remote.associateBy { it.date }
        val editor = p.edit()
        val dirty = dirtySet(p).toMutableSet()
        for (day in remote) {
            val local = p.getInt(DAY_PREFIX + day.date, 0)
            if (day.count >= local) {
                if (day.count <= 0) editor.remove(DAY_PREFIX + day.date) else editor.putInt(DAY_PREFIX + day.date, day.count)
                editor.putLong(UPDATED_PREFIX + day.date, day.updatedAt)
                dirty -= day.date
            } else {
                editor.putLong(UPDATED_PREFIX + day.date, now)
                dirty += day.date
            }
        }
        for ((date, count) in allCounts(context)) {
            val key = date.toString()
            if (count > 0 && key !in remoteByDate) {
                if (p.getLong(UPDATED_PREFIX + key, 0) == 0L) editor.putLong(UPDATED_PREFIX + key, now)
                dirty += key
            }
        }
        editor.putStringSet(KEY_DIRTY, dirty).commit()
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
