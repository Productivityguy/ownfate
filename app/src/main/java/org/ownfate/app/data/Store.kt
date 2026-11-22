package org.ownfate.app.data


import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Catch(
    val term: String,
    val pkg: String,
    val text: String,
    val at: Long
)

/**
 * Everything lives in one SharedPreferences file. No database, no network, no backup
 * (android:allowBackup is false) and no file the user can accidentally sync somewhere.
 *
 * The typed getters are deliberately small: [Lock] owns the change rules, [GamblingApps] owns the
 * app list, and the screens only ask for the current value.
 */
object Store {

    const val FILE = "ownfate"
    private const val MAX_LOG = 300

    // Feature keys shared with Lock.
    const val ADULT_SITES = "adultSites"
    const val ADULT_WORDS = "adultWords"
    const val GAMBLING_SITES = "gamblingSites"
    const val GAMBLING_APPS = "gamblingApps"
    const val PREDICTION_MARKETS = "predictionMarkets"
    const val ADULT_IMAGE_VIDEO_SEARCH = "adultImageVideoSearch"
    const val BLOCK_INSTAGRAM_SEARCH = "blockInstagramSearch"
    const val BLOCK_X_SEARCH = "blockXSearch"
    const val BLOCK_REDDIT_SEARCH = "blockRedditSearch"
    const val BLOCK_TELEGRAM_SEARCH = "blockTelegramSearch"
    const val PROTECT_APP = "protectApp"
    const val BLOCK_POWER = "blockPower"
    const val CUSTOM_APPS = "customApps"
    const val REMOVED_BUNDLED = "removedBundledApps"
    const val ALLOW_WORDS = "allowWords"
    const val ALLOW_APPS = "allowApps"
    const val SHOW_PORN_STREAK = "showPornStreak"
    const val SHOW_GAMBLING_STREAK = "showGamblingStreak"
    const val THEME = "theme"
    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"
    const val BACKGROUND = "background"
    const val BG_GRID = "grid"
    const val ART_COLOR = "artColor"
    /** The art was low-contrast grey on black; keep that as the default tint. */
    const val ART_COLOR_DEFAULT = 0xFFB0B0B0.toInt()
    const val LOCK_MODE = "lockMode"
    const val LOCK_DELAY = "lockDelay"
    const val PENDING = "pendingChanges"

    private fun p(c: Context): SharedPreferences = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- generic access ---------------------------------------------------

    fun str(c: Context, key: String, def: String = ""): String =
        p(c).getString(key, def) ?: def

    fun putStr(c: Context, key: String, value: String) =
        p(c).edit().putString(key, value).apply()

    fun bool(c: Context, key: String, def: Boolean): Boolean = p(c).getBoolean(key, def)

    fun putBool(c: Context, key: String, value: Boolean) =
        p(c).edit().putBoolean(key, value).apply()

    fun long(c: Context, key: String, def: Long): Long = p(c).getLong(key, def)

    fun putLong(c: Context, key: String, value: Long) =
        p(c).edit().putLong(key, value).apply()

    fun int(c: Context, key: String, def: Int): Int = p(c).getInt(key, def)

    fun putInt(c: Context, key: String, value: Int) =
        p(c).edit().putInt(key, value).apply()

    /**
     * A stored set, copied so callers cannot mutate the preference's own set.
     *
     * Older builds stored the allow lists as one newline-separated string under the same key.
     * Reading such a value with getStringSet would throw, so the type is checked and a legacy
     * string is migrated in place.
     */
    fun set(c: Context, key: String): Set<String> {
        val prefs = p(c)
        val typed = runCatching { prefs.getStringSet(key, null) }.getOrNull()
        if (typed != null) return typed.toSet()
        val legacy = prefs.all[key] as? String ?: return emptySet()
        val migrated = lines(legacy).toSet()
        prefs.edit().putStringSet(key, migrated).apply()
        return migrated
    }

    fun putSet(c: Context, key: String, value: Set<String>) =
        p(c).edit().putStringSet(key, value.toSet()).apply()

    // ---- words ------------------------------------------------------------

    fun custom(c: Context): List<String> = lines(customRaw(c))

    fun customRaw(c: Context): String = str(c, "custom")

    fun allowWords(c: Context): List<String> = set(c, ALLOW_WORDS).sorted()

    private fun lines(s: String) = WordLines.of(s)

    // ---- hit log ----------------------------------------------------------

    fun log(c: Context): List<Catch> = runCatching {
        val arr = JSONArray(str(c, "log", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Catch(
                term = o.optString("term"),
                pkg = o.optString("pkg"),
                text = o.optString("text"),
                at = o.optLong("at")
            )
        }
    }.getOrDefault(emptyList())

    fun addCatch(c: Context, catch: Catch) {
        val arr = JSONArray()
        arr.put(
            JSONObject()
                .put("term", catch.term)
                .put("pkg", catch.pkg)
                .put("text", catch.text.take(160))
                .put("at", catch.at)
        )
        // prepend, cap
        log(c).take(MAX_LOG - 1).forEach { old ->
            arr.put(
                JSONObject()
                    .put("term", old.term)
                    .put("pkg", old.pkg)
                    .put("text", old.text)
                    .put("at", old.at)
            )
        }
        putStr(c, "log", arr.toString())
    }

    fun clearLog(c: Context) = putStr(c, "log", "[]")
}
