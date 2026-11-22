package org.ownfate.app.ui

import org.ownfate.app.R
import org.ownfate.app.data.Store

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView

/**
 * OwnFate design system: a soft Material 3 look on a true-black AMOLED ground.
 *
 * Soft gray on a true-black AMOLED ground, rounded surfaces, one filled action per screen and
 * tonal cards instead of outlines. The interface is still
 * built entirely in code: no AppCompat, no Material components, no new dependencies.
 *
 * The background art is a braille drawing, tinted at runtime; it is deliberately
 * low-contrast so the text keeps its contrast at night and in bright rooms.
 */
object Brand {

    // ---- colour (set at runtime by [applyTheme]) --------------------------
    //
    // These used to be compile-time constants. They are vars now because the palette is a
    // preference: Dark keeps the original AMOLED grey, Light flips the page and draws dark ink
    // on it. Nothing stores a colour captured at class load, so screens must be rebuilt after a
    // theme or text-colour change (MainActivity does that).
    var PAGE = 0xFF000000.toInt(); private set
    var SURFACE = 0xCC0E0E0E.toInt(); private set   // 80% near-black, lets the art through
    var CARD_SOLID = 0xFF161616.toInt(); private set
    var SURFACE_HI = 0xFF3A3A3A.toInt(); private set
    var FIELD = 0xFF1F1F1F.toInt(); private set

    var PRIMARY = 0xFFD6D6D6.toInt(); private set
    var ON_PRIMARY = 0xFF1A1A1A.toInt(); private set
    var PRIMARY_CONTAINER = 0xFF3A3A3A.toInt(); private set
    var ON_PRIMARY_CONTAINER = 0xFFEDEDED.toInt(); private set
    var ERROR = 0xFFFFB4AB.toInt(); private set

    var INK = 0xFFE6E6E6.toInt(); private set
    var INK_80 = 0xCCE6E6E6.toInt(); private set
    var INK_60 = 0xB3E6E6E6.toInt(); private set
    var INK_45 = 0x99E6E6E6.toInt(); private set
    var INK_30 = 0x80E6E6E6.toInt(); private set

    var HAIRLINE = 0x33D6D6D6; private set
    var RIPPLE = 0x33D6D6D6; private set

    var POPUP = 0xE6121212.toInt(); private set
    var POPUP_STROKE = 0x1AFFFFFF; private set
    var SCRIM = 0x7A000000; private set

    /** True while the Dark palette is active. */
    var isDark = true; private set

    /**
     * Read the theme and rebuild every palette value. Call once per activity creation, and again
     * after the theme preference changes, before any view is built.
     */
    fun applyTheme(ctx: Context) {
        val dark = Store.str(ctx, Store.THEME, Store.THEME_DARK) != Store.THEME_LIGHT
        isDark = dark

        PAGE = if (dark) 0xFF000000.toInt() else 0xFFF7F7F5.toInt()
        SURFACE = if (dark) 0xCC0E0E0E.toInt() else 0xCCFFFFFF.toInt()
        CARD_SOLID = if (dark) 0xFF161616.toInt() else 0xFFFFFFFF.toInt()
        SURFACE_HI = if (dark) 0xFF3A3A3A.toInt() else 0xFFDADAD6.toInt()
        FIELD = if (dark) 0xFF1F1F1F.toInt() else 0xFFECECE8.toInt()
        POPUP = if (dark) 0xE6121212.toInt() else 0xF2FFFFFF.toInt()
        POPUP_STROKE = if (dark) 0x1AFFFFFF else 0x1A000000
        SCRIM = if (dark) 0x7A000000 else 0x66000000
        ERROR = if (dark) 0xFFFFB4AB.toInt() else 0xFFB3261E.toInt()

        val primary = if (dark) 0xFFD6D6D6.toInt() else 0xFF1F1F1F.toInt()
        val ink = if (dark) 0xFFE6E6E6.toInt() else 0xFF1A1A1A.toInt()

        PRIMARY = primary
        INK = ink
        INK_80 = withAlpha(ink, 0xCC)
        INK_60 = withAlpha(ink, 0xB3)
        INK_45 = withAlpha(ink, 0x99)
        INK_30 = withAlpha(ink, 0x80)

        ON_PRIMARY = if (luma(primary) > 140) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
        PRIMARY_CONTAINER = mix(primary, PAGE, if (dark) 0.80f else 0.88f)
        ON_PRIMARY_CONTAINER = ink
        HAIRLINE = withAlpha(primary, 0x33)
        RIPPLE = withAlpha(primary, 0x33)
    }

    /** A surface for sheets and menus, matching the current theme. */
    fun popupSurface(ctx: Context): GradientDrawable =
        surface(ctx, R_HERO, POPUP, POPUP_STROKE)

    internal fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)

    internal fun mix(a: Int, b: Int, bWeight: Float): Int {
        val t = bWeight.coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val av = (a ushr shift) and 0xFF
            val bv = (b ushr shift) and 0xFF
            return (av + (bv - av) * t).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun luma(color: Int): Int {
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    // ---- spacing scale (dp) -----------------------------------------------
    const val S1 = 4f
    const val S2 = 8f
    const val S3 = 12f
    const val S4 = 16f
    const val S5 = 20f
    const val S6 = 24f
    const val S8 = 32f

    // ---- radius -----------------------------------------------------------
    const val R_HERO = 28f
    const val R_CARD = 20f
    const val R_TILE = 14f
    const val R_PILL = 999f

    // ---- type scale (sp) --------------------------------------------------
    const val T_EYEBROW = 11.5f
    const val T_CAPTION = 12.5f
    const val T_BODY = 14.5f
    const val T_TITLE = 16.5f
    const val T_DISPLAY = 32f
    const val T_HERO = 64f

    fun dp(ctx: Context, v: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics
    ).toInt()

    // ---- surfaces ---------------------------------------------------------

    fun surface(ctx: Context, radius: Float = R_CARD, fill: Int = SURFACE,
                stroke: Int = Color.TRANSPARENT): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(ctx, radius).toFloat()
            setColor(fill)
            if (stroke != Color.TRANSPARENT) setStroke(dp(ctx, 1f), stroke)
        }

    /** Pressed feedback without shifting layout bounds. */
    fun tappable(ctx: Context, radius: Float = R_CARD, fill: Int = SURFACE,
                 stroke: Int = Color.TRANSPARENT): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(RIPPLE), surface(ctx, radius, fill, stroke), null)

    fun pill(ctx: Context, fill: Int, stroke: Int = Color.TRANSPARENT) =
        surface(ctx, R_PILL, fill, stroke)

    // ---- type -------------------------------------------------------------

    private val fonts = HashMap<Int, Typeface>()

    /** Preload every app typeface on a background thread so the first UI frame never parses a font. */
    fun preloadFonts(ctx: Context) = Thread {
        for (res in listOf(R.font.inter_regular, R.font.inter_medium, R.font.inter_semibold, R.font.inter_black)) {
            val tf = runCatching { ctx.resources.getFont(res) }
                .getOrDefault(Typeface.create("sans-serif", Typeface.NORMAL))
            synchronized(fonts) { fonts[res] = tf }
        }
    }.apply { isDaemon = true; start() }

    private fun font(ctx: Context, res: Int): Typeface = synchronized(fonts) {
        fonts.getOrPut(res) {
            runCatching { ctx.resources.getFont(res) }
                .getOrDefault(Typeface.create("sans-serif", Typeface.NORMAL))
        }
    }

    fun regular(ctx: Context) = font(ctx, R.font.inter_regular)
    fun medium(ctx: Context) = font(ctx, R.font.inter_medium)
    fun semibold(ctx: Context) = font(ctx, R.font.inter_semibold)
    fun black(ctx: Context) = font(ctx, R.font.inter_black)

    private fun text(ctx: Context, s: String, size: Float, color: Int,
                     face: Typeface, tracking: Float): TextView = TextView(ctx).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
        typeface = face
        letterSpacing = tracking
        includeFontPadding = false
        // Tabular figures everywhere: counters and table columns must not reflow as digits change.
        fontFeatureSettings = "tnum"
    }

    fun hero(ctx: Context, s: String, size: Float = T_HERO, color: Int = INK) =
        text(ctx, s, size, color, black(ctx), -0.045f)

    fun display(ctx: Context, s: String, size: Float = T_DISPLAY, color: Int = INK) =
        text(ctx, s, size, color, black(ctx), -0.03f)

    fun title(ctx: Context, s: String, size: Float = T_TITLE, color: Int = INK) =
        text(ctx, s, size, color, medium(ctx), -0.008f)

    fun body(ctx: Context, s: String, size: Float = T_BODY, color: Int = INK_60) =
        text(ctx, s, size, color, regular(ctx), 0f)
            .apply { setLineSpacing(dp(ctx, 4f).toFloat(), 1f) }

    /** Micro label. Sentence case, teal, quiet tracking: Material 3 label, not an all-caps stamp. */
    fun eyebrow(ctx: Context, s: String, color: Int = PRIMARY) =
        text(ctx, s, T_EYEBROW, color, medium(ctx), 0.06f)

    // ---- layout -----------------------------------------------------------

    fun col(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
    }

    fun row(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun gap(ctx: Context, h: Float): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(ctx, h))
    }

    fun rule(ctx: Context): View = View(ctx).apply {
        setBackgroundColor(HAIRLINE)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(ctx, 1f))
    }

    fun grow() = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
    fun fill() = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)

    const val MIN_CARD = 72f
    const val MIN_ROW = 56f
    const val MIN_TAP = 48f

    /** Card with the standard internal padding. Every card uses this. */
    fun card(ctx: Context, fill: Int = SURFACE, radius: Float = R_CARD): LinearLayout =
        col(ctx).apply {
            background = surface(ctx, radius, fill)
            setPadding(dp(ctx, S5), dp(ctx, S5), dp(ctx, S5), dp(ctx, S5))
            minimumHeight = dp(ctx, MIN_CARD)
        }
}
