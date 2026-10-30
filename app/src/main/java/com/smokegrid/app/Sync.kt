package com.smokegrid.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.glance.appwidget.updateAll
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** One day as the sync server sees it. [updatedAt] is when it last changed (ms); the newest wins. */
data class SyncDay(val date: String, val count: Int, val updatedAt: Long)

/**
 * Keeps the log in sync with the Cloudflare Worker in /worker.
 * Signing in stores only a token (never the password). Changed days are sent by a WorkManager job
 * as soon as there's a connection; other phones' changes are pulled on app open and every few hours.
 */
object Sync {
    private const val PREFS = "sync"
    private const val KEY_TOKEN = "token"
    private const val KEY_EMAIL = "email"
    /** Server time of the last pull; -1 until the first sync after signing in. */
    private const val KEY_CURSOR = "cursor"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_ERROR = "error"

    private const val WORK_NOW = "sync-now"
    private const val WORK_PERIODIC = "sync-periodic"

    val isConfigured: Boolean get() = BuildConfig.SYNC_URL.isNotBlank()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSignedIn(context: Context) = prefs(context).getString(KEY_TOKEN, null) != null
    fun email(context: Context): String? = prefs(context).getString(KEY_EMAIL, null)
    /** When the last successful sync finished (ms), or 0. */
    fun lastSyncAt(context: Context) = prefs(context).getLong(KEY_LAST_SYNC, 0)
    fun lastError(context: Context): String? = prefs(context).getString(KEY_ERROR, null)

    /** Returns null when signed in, otherwise a message to show. */
    suspend fun signIn(context: Context, email: String, password: String): String? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext "Sync isn't set up in this build."
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        val (code, json) = try {
            post("/login", body, token = null)
        } catch (e: IOException) {
            return@withContext "Couldn't reach the sync server. Check your connection."
        }
        val token = json?.optString("token").orEmpty()
        if (code != 200 || token.isEmpty()) {
            return@withContext json?.optString("error")?.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() }
                ?: "Sign-in failed ($code)."
        }
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_EMAIL, email.trim())
            .putLong(KEY_CURSOR, -1)
            .remove(KEY_ERROR)
            .commit()
        schedulePeriodic(context)
        requestSync(context, delaySeconds = 0)
        null
    }

    /** Signs this phone out (its token is revoked on the server too). The log on the phone stays. */
    suspend fun signOut(context: Context) {
        withContext(Dispatchers.IO) {
            val token = prefs(context).getString(KEY_TOKEN, null)
            prefs(context).edit().clear().commit()
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NOW)
            WorkManager.getInstance(context).cancelUniqueWork(WORK_PERIODIC)
            token?.let { runCatching { post("/logout", JSONObject(), it) } }
        }
    }

    /** Sends changes soon. Repeated calls within a few seconds collapse into one sync. */
    fun requestSync(context: Context, delaySeconds: Long = 3) {
        if (!isConfigured || !isSignedIn(context)) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(online)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    /** Pulls other phones' changes every few hours, even when the app isn't opened. */
    fun schedulePeriodic(context: Context) {
        if (!isConfigured || !isSignedIn(context)) return
        val request = PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS).setConstraints(online).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    enum class Outcome { Done, Retry, Stop }

    /** One round trip: send changed days, get everything that changed on the server since the last pull. */
    suspend fun syncNow(context: Context): Outcome = withContext(Dispatchers.IO) {
        val p = prefs(context)
        val token = p.getString(KEY_TOKEN, null) ?: return@withContext Outcome.Stop
        if (!isConfigured) return@withContext Outcome.Stop
        try {
            var cursor = p.getLong(KEY_CURSOR, -1)
            if (cursor < 0) {
                // First sync on this phone: merge with what's already on the server.
                val (code, json) = post("/sync", payload(since = 0, days = emptyList(), context), token)
                when {
                    code == 401 -> return@withContext signedOutByServer(context)
                    code != 200 || json == null -> return@withContext failed(context, "Server error ($code)")
                }
                SmokeLog.mergeFirstSync(context, parseDays(json!!))
                cursor = 0
            }

            val sending = SmokeLog.dirtyDays(context)
            val (code, json) = post("/sync", payload(cursor, sending, context), token)
            when {
                code == 401 -> return@withContext signedOutByServer(context)
                code != 200 || json == null -> return@withContext failed(context, "Server error ($code)")
            }
            SmokeLog.markSent(context, sending)
            SmokeLog.applyRemote(
                context,
                parseDays(json!!),
                json.optString("firstDay").takeIf { it.isNotEmpty() && it != "null" }?.let(LocalDate::parse),
            )
            p.edit()
                .putLong(KEY_CURSOR, json.getLong("now"))
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .remove(KEY_ERROR)
                .commit()
            Outcome.Done
        } catch (e: IOException) {
            failed(context, "Offline, will retry")
        }
    }

    private fun failed(context: Context, message: String): Outcome {
        prefs(context).edit().putString(KEY_ERROR, message).commit()
        return Outcome.Retry
    }

    private fun signedOutByServer(context: Context): Outcome {
        prefs(context).edit().remove(KEY_TOKEN).putString(KEY_ERROR, "Signed out. Sign in again to keep syncing.").commit()
        return Outcome.Stop
    }

    private fun payload(since: Long, days: List<SyncDay>, context: Context) = JSONObject()
        .put("since", since)
        .put("firstDay", SmokeLog.firstDayForSync(context).toString())
        .put("days", JSONArray().apply {
            days.forEach { put(JSONObject().put("date", it.date).put("count", it.count).put("updatedAt", it.updatedAt)) }
        })

    private fun parseDays(json: JSONObject): List<SyncDay> {
        val array = json.optJSONArray("days") ?: return emptyList()
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            SyncDay(o.getString("date"), o.getInt("count"), o.getLong("updatedAt"))
        }
    }

    private fun post(path: String, body: JSONObject, token: String?): Pair<Int, JSONObject?> {
        val connection = URL(BuildConfig.SYNC_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }
            return code to text?.let { runCatching { JSONObject(it) }.getOrNull() }
        } finally {
            connection.disconnect()
        }
    }

    /** Emits whenever sign-in or sync status changes. */
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

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (Sync.syncNow(applicationContext)) {
        Sync.Outcome.Done -> {
            SmokeWidget().updateAll(applicationContext)
            Result.success()
        }
        Sync.Outcome.Retry -> Result.retry()
        Sync.Outcome.Stop -> Result.success()
    }
}
