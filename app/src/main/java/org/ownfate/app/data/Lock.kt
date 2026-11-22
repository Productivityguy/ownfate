package org.ownfate.app.data


import android.content.Context
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The change lock.
 *
 * It answers one question: may this setting change now? The rules are these:
 *
 *   - a stricter change applies at once and cancels a pending weaker change for that feature;
 *   - a weaker change becomes pending, and the current restriction stays active until it lands;
 *   - a pending change can always be cancelled;
 *   - shortening the delay, turning the delay off or turning the lock off is itself weaker, so it
 *     waits under the delay that is already configured;
 *   - a correct password applies the change immediately, even with a delay configured;
 *   - there is no recovery inside the app. Clearing app data or uninstalling resets everything,
 *     and both are still covered by the tamper guards.
 *
 * A pending change is stored as one JSON object, keyed by feature, with the value to write and the
 * moment it becomes due. One pending change per feature; a second request replaces the first.
 */
object Lock {

    enum class Mode {
        OFF, DELAY, PASSWORD;

        val hasPassword: Boolean get() = this == PASSWORD
        val hasDelay: Boolean get() = this == DELAY
    }

    /**
     * Which of the other two mode-owned settings a mode clears.
     *
     * The screen only offers Off, Delay or Password, never a combination, so choosing one has to
     * take the others' state with it. OFF is handled by [resetToFresh], which clears pending
     * changes as well; this table is what keeps Delay and Password from inheriting each other.
     */
    internal data class ModeEffect(val wipePassword: Boolean, val resetDelay: Boolean)

    internal fun effectFor(mode: Mode): ModeEffect = when (mode) {
        Mode.OFF -> ModeEffect(wipePassword = true, resetDelay = true)
        Mode.DELAY -> ModeEffect(wipePassword = true, resetDelay = false)
        Mode.PASSWORD -> ModeEffect(wipePassword = false, resetDelay = true)
    }

    enum class Outcome { APPLIED, PENDING, NEEDS_PASSWORD, WRONG_PASSWORD, UNCHANGED }

    private enum class Kind { BOOL, SET, SET_INVERTED, TEXT, NUMBER, MODE }

    /**
     * Every setting the lock protects. The key is the SharedPreferences key, so a pending change
     * can be applied by the service without the UI being open.
     */
    enum class Feature(val key: String, val label: String, private val kind: Kind) {
        ADULT_SITES(Store.ADULT_SITES, "Adult websites", Kind.BOOL),
        ADULT_WORDS(Store.ADULT_WORDS, "Adult words & names", Kind.BOOL),
        IMAGE_VIDEO_SEARCH(Store.ADULT_IMAGE_VIDEO_SEARCH, "Image & video search", Kind.BOOL),
        INSTAGRAM_SEARCH(Store.BLOCK_INSTAGRAM_SEARCH, "Block Instagram search", Kind.BOOL),
        X_SEARCH(Store.BLOCK_X_SEARCH, "Block X search", Kind.BOOL),
        REDDIT_SEARCH(Store.BLOCK_REDDIT_SEARCH, "Block Reddit search", Kind.BOOL),
        TELEGRAM_SEARCH(Store.BLOCK_TELEGRAM_SEARCH, "Block Telegram search", Kind.BOOL),
        GAMBLING_SITES(Store.GAMBLING_SITES, "Gambling websites", Kind.BOOL),
        GAMBLING_APPS(Store.GAMBLING_APPS, "Gambling apps", Kind.BOOL),
        PREDICTION_MARKETS(Store.PREDICTION_MARKETS, "Prediction markets", Kind.BOOL),
        PROTECT_APP(Store.PROTECT_APP, "Protect OwnFate", Kind.BOOL),
        BLOCK_POWER(Store.BLOCK_POWER, "Block power menu & safe mode", Kind.BOOL),
        CUSTOM_WORDS("custom", "Custom words", Kind.TEXT),
        CUSTOM_APPS(Store.CUSTOM_APPS, "Blocked apps", Kind.SET),
        REMOVED_BUNDLED(Store.REMOVED_BUNDLED, "Gambling app exceptions", Kind.SET_INVERTED),
        ALLOW_WORDS(Store.ALLOW_WORDS, "Allowed words", Kind.SET_INVERTED),
        ALLOW_APPS(Store.ALLOW_APPS, "Allowed apps", Kind.SET_INVERTED),
        LOCK_MODE(Store.LOCK_MODE, "Lock changes", Kind.MODE),
        LOCK_DELAY(Store.LOCK_DELAY, "Change delay", Kind.NUMBER);

        fun current(ctx: Context): String = when (kind) {
            Kind.BOOL -> Store.bool(ctx, key, false).toString()
            Kind.SET, Kind.SET_INVERTED -> Store.set(ctx, key).sorted().joinToString("\n")
            // Custom words are one editable block of text, not a set: editing the field must not
            // lose comments, blank lines or the user's order.
            Kind.TEXT -> Store.str(ctx, key)
            Kind.NUMBER -> Store.long(ctx, key, DEFAULT_DELAY).toString()
            Kind.MODE -> Store.str(ctx, key, Mode.OFF.name)
        }

        fun write(ctx: Context, value: String) {
            when (kind) {
                Kind.BOOL -> Store.putBool(ctx, key, value.toBoolean())
                Kind.SET, Kind.SET_INVERTED ->
                    Store.putSet(ctx, key, lines(value).toSet())
                Kind.TEXT -> Store.putStr(ctx, key, value)
                Kind.NUMBER -> Store.putLong(ctx, key, value.toLongOrNull() ?: DEFAULT_DELAY)
                Kind.MODE -> {
                    Store.putStr(ctx, key, value)
                    val mode = runCatching { Mode.valueOf(value) }.getOrDefault(Mode.OFF)
                    if (mode == Mode.OFF) {
                        resetToFresh(ctx)
                    } else {
                        // The modes are exclusive. A password left over from Password mode would
                        // otherwise keep guarding a Delay, and a delay left over from Delay mode
                        // would come back when a Password is turned off.
                        val effect = effectFor(mode)
                        if (effect.wipePassword) wipePassword(ctx)
                        if (effect.resetDelay) Store.putLong(ctx, Store.LOCK_DELAY, DEFAULT_DELAY)
                    }
                }
            }
        }

        /** Positive is stricter, negative is weaker, zero is the same setting. */
        fun severity(old: String, new: String): Int = when (kind) {
            Kind.BOOL -> new.toBoolean().compareTo(old.toBoolean())
            Kind.SET, Kind.TEXT -> setSeverity(old, new)
            // Removing an exception is stricter. Adding one, or mixing a removal with an
            // addition, can unblock something, so every other edit is weaker. Negating
            // setSeverity() would turn a mixed edit (-1) into a stricter change and skip the lock.
            Kind.SET_INVERTED -> invertedSetSeverity(old, new)
            Kind.NUMBER -> (new.toLongOrNull() ?: 0L).compareTo(old.toLongOrNull() ?: 0L)
            Kind.MODE -> modeRank(new) - modeRank(old)
        }
    }

    /** Shared time units: the delay picker, the Log's relative times and the delay labels agree. */
    const val MINUTE = 60_000L
    const val HOUR = 3_600_000L
    const val DAY = 86_400_000L

    const val DEFAULT_DELAY = HOUR
    const val MIN_DELAY = 600_000L
    const val MAX_DELAY = 2 * DAY
    const val MIN_PASSWORD_LENGTH = 4
    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16

    val delays: LongArray = longArrayOf(
        600_000L,      // 10 minutes
        1_800_000L,    // 30 minutes
        HOUR,          // 1 hour
        21_600_000L,   // 6 hours
        43_200_000L,   // 12 hours
        DAY,           // 1 day
        MAX_DELAY      // 2 days
    )

    // ---- reading ----------------------------------------------------------

    fun mode(ctx: Context): Mode = runCatching {
        Mode.valueOf(Store.str(ctx, Store.LOCK_MODE, Mode.OFF.name))
    }.getOrDefault(Mode.OFF)

    fun delayMs(ctx: Context): Long =
        Store.long(ctx, Store.LOCK_DELAY, DEFAULT_DELAY).coerceIn(MIN_DELAY, MAX_DELAY)

    fun hasPassword(ctx: Context): Boolean = Store.str(ctx, "passwordHash").isNotEmpty()

    fun delayLabel(ms: Long): String {
        // Round a remaining time up to the next whole minute, so a second left is not "0 min".
        // Exact delays are multiples of a minute already and are unaffected.
        val totalMin = (ms.coerceAtLeast(0L) + MINUTE - 1) / MINUTE
        if (totalMin == 0L) return "0 min"
        val days = totalMin / 1_440L
        val hours = (totalMin % 1_440L) / 60L
        val minutes = totalMin % 60L
        val parts = ArrayList<String>(3)
        if (days > 0L) parts.add("$days day" + if (days == 1L) "" else "s")
        if (hours > 0L) parts.add("$hours h")
        if (minutes > 0L) parts.add("$minutes min")
        return parts.joinToString(" ")
    }

    /**
     * True when this exact change would need the password or a wait. The UI asks for a password
     * before calling [request], so a stricter change is never interrupted by a dialog.
     */
    fun needsAuth(ctx: Context, feature: Feature, value: String): Boolean {
        if (mode(ctx) == Mode.OFF) return false
        return feature.severity(feature.current(ctx), value) < 0
    }

    // ---- requests ---------------------------------------------------------

    /**
     * Change one setting.
     *
     * [password] is only consulted when a weaker change is offered while a password is configured.
     * A null password means "no password offered": the change waits out the delay, or is refused
     * when the lock is password-only.
     */
    fun request(ctx: Context, feature: Feature, value: String, password: String? = null): Outcome {
        val old = feature.current(ctx)
        if (old == value) {
            clearPending(ctx, feature)
            return Outcome.UNCHANGED
        }

        // A lock needs a password before it can start guarding one.
        if (feature == Feature.LOCK_MODE) {
            val target = runCatching { Mode.valueOf(value) }.getOrDefault(Mode.OFF)
            if (target.hasPassword && !hasPassword(ctx)) return Outcome.NEEDS_PASSWORD
        }

        val stricter = feature.severity(old, value) >= 0
        if (mode(ctx) == Mode.OFF || stricter) {
            feature.write(ctx, value)
            clearPending(ctx, feature)
            return Outcome.APPLIED
        }

        // Weaker change while the lock is on.
        if (hasPassword(ctx) && password != null) {
            if (!verify(ctx, password)) return Outcome.WRONG_PASSWORD
            feature.write(ctx, value)
            clearPending(ctx, feature)
            return Outcome.APPLIED
        }

        if (mode(ctx).hasDelay) {
            val at = System.currentTimeMillis() + delayMs(ctx)
            putPending(ctx, feature, value, at)
            return Outcome.PENDING
        }

        return Outcome.NEEDS_PASSWORD
    }

    // ---- password ---------------------------------------------------------

    /** Set or replace the password. A replacement needs the existing one. */
    fun setPassword(
        ctx: Context,
        next: String,
        current: String? = null,
        iterations: Int = ITERATIONS
    ): Outcome {
        if (next.length < MIN_PASSWORD_LENGTH) return Outcome.WRONG_PASSWORD
        if (hasPassword(ctx)) {
            if (current == null) return Outcome.NEEDS_PASSWORD
            if (!verify(ctx, current)) return Outcome.WRONG_PASSWORD
        }
        val salt = newSalt()
        Store.putStr(ctx, "passwordSalt", salt)
        Store.putStr(ctx, "passwordHash", hash(next, salt, iterations))
        Store.putStr(ctx, "passwordIterations", iterations.toString())
        return Outcome.APPLIED
    }

    private fun wipePassword(ctx: Context) {
        Store.putStr(ctx, "passwordHash", "")
        Store.putStr(ctx, "passwordSalt", "")
        Store.putStr(ctx, "passwordIterations", "")
    }

    /** When lock is turned Off, reset to a fresh, unconfigured state. */
    internal fun resetToFresh(ctx: Context) {
        wipePassword(ctx)
        Store.putLong(ctx, Store.LOCK_DELAY, DEFAULT_DELAY)
        // A pending change belongs to a lock that is now off. Leaving any of them behind would
        // let a scheduled weaker change land after the user has already reset the lock.
        storePending(ctx, emptyList())
    }

    fun verify(ctx: Context, password: String): Boolean {
        if (!hasPassword(ctx)) return false
        val salt = Store.str(ctx, "passwordSalt")
        val expected = Store.str(ctx, "passwordHash")
        val iterations = Store.str(ctx, "passwordIterations", ITERATIONS.toString())
            .toIntOrNull() ?: ITERATIONS
        return constantTimeEquals(hash(password, salt, iterations), expected)
    }

    // ---- pending changes --------------------------------------------------

    data class Pending(
        val feature: Feature,
        val value: String,
        val at: Long
    ) {
        fun remainingLabel(now: Long = System.currentTimeMillis()): String =
            delayLabel((at - now).coerceAtLeast(0L))
    }

    fun pending(ctx: Context): List<Pending> = runCatching {
        val o = JSONObject(Store.str(ctx, Store.PENDING, "{}"))
        Feature.values().mapNotNull { f ->
            val e = o.optJSONObject(f.key) ?: return@mapNotNull null
            Pending(f, e.optString("value"), e.optLong("at"))
        }.sortedBy { it.at }
    }.getOrDefault(emptyList())

    fun pendingFor(ctx: Context, feature: Feature): Pending? =
        pending(ctx).firstOrNull { it.feature == feature }

    fun cancelPending(ctx: Context, feature: Feature) = clearPending(ctx, feature)

    /** Write every pending change whose moment has arrived. Called by the guard on window events. */
    fun applyDue(ctx: Context, now: Long = System.currentTimeMillis()): List<Feature> {
        val due = pending(ctx).filter { it.at <= now }
        if (due.isEmpty()) return emptyList()
        // Apply the lock mode last. Its only possible pending value is Off, which resets the lock
        // configuration; writing a due delay change after that reset would leave the old delay
        // behind. Everything else can land in any order.
        for (p in applyOrder(due)) p.feature.write(ctx, p.value)
        val keep = pending(ctx).filter { it.at > now }
        storePending(ctx, keep)
        return due.map { it.feature }
    }

    /**
     * The order due changes are written in. Kept separate from [applyDue] so the reset ordering
     * can be tested without a Context.
     */
    internal fun applyOrder(due: List<Pending>): List<Pending> =
        due.filter { it.feature != Feature.LOCK_MODE } +
            due.filter { it.feature == Feature.LOCK_MODE }

    // ---- storage helpers --------------------------------------------------

    private fun putPending(ctx: Context, feature: Feature, value: String, at: Long) {
        val keep = pending(ctx).filter { it.feature != feature }
        storePending(ctx, keep + Pending(feature, value, at))
    }

    private fun clearPending(ctx: Context, feature: Feature) {
        val keep = pending(ctx).filter { it.feature != feature }
        storePending(ctx, keep)
    }

    private fun storePending(ctx: Context, list: List<Pending>) {
        if (list.isEmpty()) {
            Store.putStr(ctx, Store.PENDING, "{}")
            return
        }
        val o = JSONObject()
        for (p in list) {
            o.put(p.feature.key, JSONObject().put("value", p.value).put("at", p.at))
        }
        Store.putStr(ctx, Store.PENDING, o.toString())
    }

    // ---- pure helpers, kept testable without Android ----------------------

    private fun lines(s: String): List<String> = WordLines.of(s)

    private fun setSeverity(old: String, new: String): Int {
        val a = lines(old).toSet()
        val b = lines(new).toSet()
        if (a == b) return 0
        if (b.containsAll(a)) return 1
        // A mix of adding and removing is treated as a weakening: the safe direction.
        return -1
    }

    /** For an inverted set, only removals tighten the setting; additions and mixes loosen it. */
    private fun invertedSetSeverity(old: String, new: String): Int {
        val a = lines(old).toSet()
        val b = lines(new).toSet()
        if (a == b) return 0
        if (a.containsAll(b)) return 1
        return -1
    }

    private fun modeRank(name: String): Int = when (
        runCatching { Mode.valueOf(name) }.getOrDefault(Mode.OFF)
    ) {
        Mode.OFF -> 0
        Mode.DELAY -> 1
        Mode.PASSWORD -> 2
    }

    internal fun newSalt(): String {
        val b = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(b)
        return hex(b)
    }

    internal fun hash(password: String, saltHex: String, iterations: Int = ITERATIONS): String {
        val spec = PBEKeySpec(
            password.toCharArray(),
            unhex(saltHex),
            iterations,
            256
        )
        return try {
            hex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded)
        } finally {
            spec.clearPassword()
        }
    }

    internal fun matches(password: String, saltHex: String, expectedHex: String, iterations: Int): Boolean =
        constantTimeEquals(hash(password, saltHex, iterations), expectedHex)

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    private fun hex(b: ByteArray): String {
        val out = StringBuilder(b.size * 2)
        for (v in b) {
            val n = v.toInt() and 0xFF
            out.append(HEX[n ushr 4]).append(HEX[n and 0x0F])
        }
        return out.toString()
    }

    private fun unhex(s: String): ByteArray {
        if (s.length % 2 != 0) return ByteArray(0)
        val out = ByteArray(s.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(s[i * 2], 16)
            val lo = Character.digit(s[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return ByteArray(0)
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private const val HEX = "0123456789abcdef"
}
