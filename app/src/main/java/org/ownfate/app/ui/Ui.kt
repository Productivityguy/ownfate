package org.ownfate.app.ui

import org.ownfate.app.MainActivity
import org.ownfate.app.R
import org.ownfate.app.data.Catch
import org.ownfate.app.data.Lock

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.ownfate.app.ui.Brand.S2
import org.ownfate.app.ui.Brand.S3
import org.ownfate.app.ui.Brand.S4
import org.ownfate.app.ui.Brand.S5
import org.ownfate.app.ui.Brand.S6
import org.ownfate.app.ui.Brand.body
import org.ownfate.app.ui.Brand.col
import org.ownfate.app.ui.Brand.dp
import org.ownfate.app.ui.Brand.gap
import org.ownfate.app.ui.Brand.grow
import org.ownfate.app.ui.Brand.row
import org.ownfate.app.ui.Brand.title

// ---------------------------------------------------------------- bits

/** Filled is on, outlined is off. Teal instead of black now, but the same state language. */
fun Context.stateDot(on: Boolean, size: Float = 10f): View = View(this).apply {
    background = if (on) Brand.pill(this@stateDot, Brand.PRIMARY)
    else Brand.pill(this@stateDot, Color.TRANSPARENT, Brand.PRIMARY)
    layoutParams = LinearLayout.LayoutParams(dp(this@stateDot, size), dp(this@stateDot, size))
        .apply { rightMargin = dp(this@stateDot, S3) }
}

/** The single filled element on a screen. */
fun Context.primaryButton(label: String, onTap: () -> Unit): View =
    TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(Brand.ON_PRIMARY)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
        typeface = Brand.semibold(this@primaryButton)
        letterSpacing = 0.01f
        background = android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(0x33FFFFFF),
            Brand.pill(this@primaryButton, Brand.PRIMARY),
            null
        )
        setPadding(dp(this@primaryButton, S4), dp(this@primaryButton, S4),
            dp(this@primaryButton, S4), dp(this@primaryButton, S4))
        layoutParams = Brand.fill()
        minHeight = dp(this@primaryButton, 56f)
        isClickable = true
        setOnClickListener { onTap() }
    }

/** Outlined pill: the second action, never filled. */
fun Context.ghostButton(label: String, onTap: () -> Unit): View =
    title(this, label, Brand.T_BODY, Brand.PRIMARY).apply {
        gravity = Gravity.CENTER
        background = Brand.tappable(this@ghostButton, Brand.R_PILL,
            Color.TRANSPARENT, Brand.HAIRLINE)
        setPadding(dp(this@ghostButton, S4), dp(this@ghostButton, S3),
            dp(this@ghostButton, S4), dp(this@ghostButton, S3))
        layoutParams = Brand.fill()
        minHeight = dp(this@ghostButton, 52f)
        isClickable = true
        setOnClickListener { onTap() }
    }

/** A quiet full-width text action for the bottom of a sheet. */
fun Context.quietButton(label: String, onTap: () -> Unit): View =
    title(this, label, Brand.T_BODY, Brand.PRIMARY).apply {
        gravity = Gravity.CENTER
        minHeight = dp(this@quietButton, Brand.MIN_TAP)
        isClickable = true
        isFocusable = true
        setOnClickListener { onTap() }
    }

/** A segmented pill: options divide the width, the chosen one is filled. */
fun Context.segmented(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
): View {
    val ctx = this
    var current = selected
    val bar = row(ctx).apply {
        background = Brand.pill(ctx, Brand.FIELD)
        setPadding(dp(ctx, 4f), dp(ctx, 4f), dp(ctx, 4f), dp(ctx, 4f))
    }
    fun render() {
        bar.removeAllViews()
        options.forEach { (label, value) ->
            val on = value == current
            bar.addView(TextView(ctx).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Brand.T_CAPTION)
                setTextColor(if (on) Brand.INK else Brand.INK_45)
                typeface = Brand.medium(ctx)
                includeFontPadding = false
                background = if (on) Brand.pill(ctx, Brand.PRIMARY_CONTAINER) else null
                setPadding(dp(ctx, S3), dp(ctx, S2), dp(ctx, S3), dp(ctx, S2))
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                minHeight = dp(ctx, 40f)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (!on) {
                        current = value
                        render()
                        onSelect(value)
                    }
                }
            })
        }
    }
    render()
    return bar
}

/** Material-style pill switch: rounded track, circular thumb. */
fun Context.toggleRow(
    label: String, sub: String, on: Boolean,
    labelSize: Float = Brand.T_TITLE, subSize: Float = Brand.T_CAPTION,
    onToggle: () -> Unit
): View {
    val r = row(this).apply {
        setPadding(0, dp(this@toggleRow, S3), 0, dp(this@toggleRow, S3))
        minimumHeight = dp(this@toggleRow, Brand.MIN_ROW)
        isClickable = true
        setOnClickListener { onToggle() }
    }
    r.addView(col(this).apply {
        layoutParams = grow()
        addView(title(this@toggleRow, label, labelSize))
        if (sub.isNotEmpty()) {
            addView(gap(this@toggleRow, 3f))
            addView(body(this@toggleRow, sub, subSize, Brand.INK_30))
        }
    })
    val track = FrameLayout(this).apply {
        background = Brand.pill(
            this@toggleRow,
            if (on) Brand.PRIMARY else Brand.SURFACE_HI
        )
        layoutParams = LinearLayout.LayoutParams(dp(this@toggleRow, 50f), dp(this@toggleRow, 30f))
    }
    track.addView(View(this).apply {
        background = Brand.pill(
            this@toggleRow,
            if (on) Brand.ON_PRIMARY else Brand.INK
        )
        elevation = dp(this@toggleRow, 1f).toFloat()
        layoutParams = FrameLayout.LayoutParams(
            dp(this@toggleRow, 24f), dp(this@toggleRow, 24f)).apply {
            gravity = if (on) Gravity.END or Gravity.CENTER_VERTICAL
                      else Gravity.START or Gravity.CENTER_VERTICAL
            marginStart = dp(this@toggleRow, 3f); marginEnd = dp(this@toggleRow, 3f)
        }
    })
    r.addView(track)
    return r
}

/**
 * A caught attempt: the term on the left, when and where underneath. Page text is not shown by
 * default; [revealable] adds a tap-to-show control for the Log screen, where diagnosing a
 * false positive needs the context.
 */
fun Context.catchRow(c: Catch, revealable: Boolean = false): View {
    val ctx = this
    val snippet = body(ctx, c.text.replace('\n', ' ').trim().take(160),
        Brand.T_CAPTION, Brand.INK_30).apply {
        visibility = View.GONE
        setPadding(0, dp(ctx, 2f), 0, 0)
    }
    val toggle = body(ctx, "Show context", Brand.T_CAPTION, Brand.PRIMARY).apply {
        visibility = if (revealable && c.text.isNotBlank()) View.VISIBLE else View.GONE
        isClickable = true
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(ctx, Brand.MIN_TAP)
        setOnClickListener {
            val show = snippet.visibility != View.VISIBLE
            snippet.visibility = if (show) View.VISIBLE else View.GONE
            text = if (show) "Hide context" else "Show context"
        }
    }
    return col(ctx).apply {
        setPadding(0, dp(ctx, S3), 0, dp(ctx, S3))
        addView(row(ctx).apply {
            addView(title(ctx, c.term, Brand.T_BODY).apply { layoutParams = grow() })
            addView(body(ctx, timeAgo(c.at), Brand.T_CAPTION, Brand.INK_30))
        })
        addView(gap(ctx, 3f))
        addView(body(ctx, appLabel(ctx.packageManager, c.pkg), Brand.T_CAPTION, Brand.INK_30))
        if (revealable && c.text.isNotBlank()) {
            addView(toggle)
            addView(snippet)
        }
    }
}

fun timeAgo(at: Long): String {
    val d = System.currentTimeMillis() - at
    return when {
        d < Lock.MINUTE -> "now"
        d < Lock.HOUR -> "${d / Lock.MINUTE} min"
        d < Lock.DAY -> "${d / Lock.HOUR} h"
        else -> "${d / Lock.DAY} d"
    }
}

fun appLabel(pm: PackageManager, pkg: String): String = runCatching {
    @Suppress("DEPRECATION")
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg.substringAfterLast('.'))

/** The standard page column. Bottom padding clears the fixed navigation tray. */
fun page(ctx: Context): LinearLayout = col(ctx).apply {
    setPadding(dp(ctx, S6), dp(ctx, S6), dp(ctx, S6), dp(ctx, 116f))
    minimumHeight = dp(ctx, 560f)
}

/**
 * A section heading with a trailing info icon. The icon opens the same centred popup used by the
 * word and password pickers, explaining what the switches under this heading actually do.
 * Sections without help copy keep the plain heading.
 */
fun MainActivity.infoTitle(section: String): View {
    val help = SectionHelp.of(section) ?: return title(this, section, 22f)
    return row(this).apply {
        addView(title(this@infoTitle, section, 22f).apply { layoutParams = grow() })
        addView(ImageView(this@infoTitle).apply {
            setImageResource(R.drawable.ic_info)
            imageTintList = ColorStateList.valueOf(Brand.withAlpha(Brand.INK, 0xCC))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "About $section"
            layoutParams = LinearLayout.LayoutParams(dp(this@infoTitle, 24f), dp(this@infoTitle, 24f))
            setPadding(
                dp(this@infoTitle, 2f), dp(this@infoTitle, 2f),
                dp(this@infoTitle, 2f), dp(this@infoTitle, 2f)
            )
            // A faint translucent chip, not a solid badge: the art behind it stays visible.
            background = Brand.tappable(
                this@infoTitle,
                Brand.R_PILL,
                Brand.withAlpha(Brand.SURFACE_HI, 0x33),
                Brand.withAlpha(Brand.INK, 0x33)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { showInfo(help) }
        })
    }
}

/** A lighter, see-through version of [popupCard] for the small explainer sheets. */
private fun glassInfoCard(ctx: Context, heading: String): LinearLayout = col(ctx).apply {
    // Same surface as the settings popups, so the transparency matches exactly.
    background = Brand.popupSurface(ctx)
    setPadding(dp(ctx, 22f), dp(ctx, 20f), dp(ctx, 22f), dp(ctx, 12f))
    isClickable = true
    addView(title(ctx, heading, 20f))
    addView(gap(ctx, S4))
}

/** The explainer behind a section's info icon. */
private fun MainActivity.showInfo(help: SectionHelp.Text) {
    showPopup { dismiss ->
        glassInfoCard(this, help.heading).apply {
            help.lines.forEachIndexed { i, line ->
                addView(body(this@showInfo, line, Brand.T_BODY, Brand.INK_60))
                if (i < help.lines.lastIndex) addView(gap(this@showInfo, S3))
            }
            addView(gap(this@showInfo, S5))
            addView(ghostButton("Got it") { dismiss() })
        }
    }
}

/** The shared card wrapper for in-app popups: delay, password and word/app pickers. */
fun popupCard(ctx: Context, titleText: String): LinearLayout = col(ctx).apply {
    background = Brand.popupSurface(ctx)
    setPadding(dp(ctx, 20f), dp(ctx, 20f), dp(ctx, 20f), dp(ctx, 16f))
    isClickable = true
    addView(title(ctx, titleText, 20f))
    addView(gap(ctx, S3))
}

/**
 * Single-line field for the retype gate. Suggestions and autocorrect are off on purpose: the
 * keyboard must not "fix" the sentence into one that fails the exact comparison.
 */
fun Context.lineField(hint: String, value: String = ""): EditText =
    EditText(this).apply {
        this.hint = hint
        setHintTextColor(Brand.INK_30)
        setTextColor(Brand.INK)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, Brand.T_BODY)
        typeface = Brand.regular(this@lineField)
        background = Brand.surface(this@lineField, Brand.R_TILE, Brand.FIELD)
        setPadding(dp(this@lineField, S4), dp(this@lineField, S3),
            dp(this@lineField, S4), dp(this@lineField, S3))
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        imeOptions = EditorInfo.IME_ACTION_DONE
        isSingleLine = true
        layoutParams = Brand.fill()
        minHeight = dp(this@lineField, Brand.MIN_TAP)
        setText(value)
        setSelection(text.length)
    }

/** Transparent scroll host; the background art lives behind it. */
fun Context.appScroll(content: View): ScrollView = ScrollView(this).apply {
    isFillViewport = true
    isVerticalScrollBarEnabled = false
    setBackgroundColor(Color.TRANSPARENT)
    addView(content, ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
}
