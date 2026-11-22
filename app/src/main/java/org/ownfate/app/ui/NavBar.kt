package org.ownfate.app.ui

import org.ownfate.app.R

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.ownfate.app.ui.Brand.col
import org.ownfate.app.ui.Brand.dp

/**
 * The floating bottom navigation: a translucent capsule with a sliding pill.
 *
 * A capsule inset from the screen edges, its four tabs dividing the width evenly, with a soft
 * pill sliding behind the selected tab. The surface is a translucent near-black with a hairline
 * edge, so the background still reads through it on AMOLED screens. The selected glyph lifts
 * slightly and the pill travels on a light overshoot, mirroring the reference bar's spring.
 */
@SuppressLint("ViewConstructor")
class BottomNavBar(
    private val ctx: Context,
    initial: String,
    private val onSelect: (String) -> Unit
) : FrameLayout(ctx) {

    private data class Tab(val id: String, val label: String, val icon: Int)

    private val tabs = listOf(
        Tab("home", "Home", R.drawable.ic_nav_home),
        Tab("custom", "Custom", R.drawable.ic_nav_words),
        Tab("protection", "Protection", R.drawable.ic_nav_shield),
        Tab("log", "Log", R.drawable.ic_nav_log)
    )

    private val cells = ArrayList<FrameLayout>()
    private val icons = ArrayList<ImageView>()
    private val labels = ArrayList<TextView>()

    private val indicator = View(ctx)
    private val row = LinearLayout(ctx)
    private val capsule = FrameLayout(ctx)

    private var selected = initial

    init {
        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
            marginStart = dp(ctx, 19f)
            marginEnd = dp(ctx, 19f)
            bottomMargin = dp(ctx, 6f)
        }

        // The capsule itself: fully rounded, translucent, hairline rim.
        capsule.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(ctx, Brand.R_PILL).toFloat()
            setColor(Brand.SURFACE)
            setStroke(maxOf(1, dp(ctx, 0.5f)), Brand.HAIRLINE)
        }
        capsule.elevation = dp(ctx, 12f).toFloat()
        capsule.setPadding(dp(ctx, 4f), dp(ctx, 4f), dp(ctx, 4f), dp(ctx, 4f))
        addView(capsule, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // Selection pill, drawn under the tab row.
        indicator.background = Brand.pill(ctx, Brand.withAlpha(Brand.PRIMARY, 0x24))
        indicator.visibility = View.INVISIBLE
        capsule.addView(indicator, FrameLayout.LayoutParams(0, 0))

        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        // Six dp between tabs, none at the outer edges.
        row.dividerDrawable = object : ColorDrawable(Color.TRANSPARENT) {
            override fun getIntrinsicWidth(): Int = dp(ctx, 6f)
        }
        row.showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        // The pill can only be placed once the cells have a width.
        row.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            row.post { positionIndicator(animate = false) }
        }
        capsule.addView(row, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        tabs.forEach { addTab(it) }

        updateSelection(animate = false)
        row.post { positionIndicator(animate = false) }
    }

    /** Move the highlight to [tab]. Reusing the bar lets the pill travel between tabs. */
    fun select(tab: String, animate: Boolean = true) {
        if (selected == tab) return
        selected = tab
        updateSelection(animate)
        positionIndicator(animate)
    }

    private fun addTab(tab: Tab) {
        val cell = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            minimumHeight = dp(ctx, Brand.MIN_TAP)
            contentDescription = "${tab.label} tab"
            isFocusable = true
            isClickable = true
            setOnClickListener { if (selected != tab.id) onSelect(tab.id) }
        }

        val stack = col(ctx).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(ctx, 6.5f), 0, dp(ctx, 6.5f))
        }

        val icon = ImageView(ctx).apply {
            setImageResource(tab.icon)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 28f), dp(ctx, 28f))
        }
        stack.addView(icon)

        stack.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(ctx, 1f))
        })

        val label = TextView(ctx).apply {
            text = tab.label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            letterSpacing = 0.05f
            typeface = Brand.medium(ctx)
            includeFontPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        stack.addView(label)

        cell.addView(stack, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        row.addView(cell)
        cells.add(cell)
        icons.add(icon)
        labels.add(label)
    }

    /** Tint and scale the active tab, as the reference bar does. */
    private fun updateSelection(animate: Boolean) {
        tabs.forEachIndexed { index, tab ->
            val on = tab.id == selected
            val color = if (on) Brand.INK else Brand.INK_45
            icons[index].imageTintList = ColorStateList.valueOf(color)
            labels[index].setTextColor(color)

            val scale = if (on) 1.08f else 1f
            val icon = icons[index]
            if (animate) {
                icon.animate().scaleX(scale).scaleY(scale)
                    .setDuration(200L)
                    .start()
            } else {
                icon.scaleX = scale
                icon.scaleY = scale
            }
        }
    }

    /** Align the pill with the selected tab, travelling there with a soft overshoot. */
    private fun positionIndicator(animate: Boolean) {
        val index = tabs.indexOfFirst { it.id == selected }
        val cell = cells.getOrNull(index) ?: return
        if (cell.width == 0 || row.height == 0) return

        val lp = indicator.layoutParams as FrameLayout.LayoutParams
        if (lp.width != cell.width || lp.height != row.height) {
            lp.width = cell.width
            lp.height = row.height
            indicator.layoutParams = lp
        }
        indicator.visibility = View.VISIBLE

        val target = cell.left.toFloat()
        if (!animate || !indicator.isLaidOut) {
            indicator.animate().cancel()
            indicator.translationX = target
        } else {
            indicator.animate()
                .translationX(target)
                .setDuration(320L)
                .setInterpolator(OvershootInterpolator(0.75f))
                .start()
        }
    }
}
