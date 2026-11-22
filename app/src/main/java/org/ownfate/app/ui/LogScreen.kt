package org.ownfate.app.ui

import org.ownfate.app.MainActivity
import org.ownfate.app.data.Store

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import org.ownfate.app.ui.Brand.S3
import org.ownfate.app.ui.Brand.S4
import org.ownfate.app.ui.Brand.S6
import org.ownfate.app.ui.Brand.card
import org.ownfate.app.ui.Brand.dp
import org.ownfate.app.ui.Brand.gap
import org.ownfate.app.ui.Brand.title

/** The Log tab: the catch log, rebuilt on show so a new catch is there when the tab opens. */
class LogScreen(private val host: MainActivity) : Screen {

    private val ctx = host
    private val content: LinearLayout = page(host)
    private val scroll: ScrollView = host.appScroll(content)

    /** When the clear action was first tapped. It disarms on its own after [CLEAR_ARM_MS]. */
    private var clearArmedAt = 0L

    override val view: View get() = scroll

    init {
        refresh()
    }

    private fun clearArmed(): Boolean =
        clearArmedAt > 0L && System.currentTimeMillis() - clearArmedAt < CLEAR_ARM_MS

    override fun onHide() {
        // A switch away and back must not leave the second tap armed: the user is no longer
        // answering the "tap again" question that was on screen.
        clearArmedAt = 0L
        refresh()
    }

    override fun refresh() {
        val y = scroll.scrollY
        content.removeAllViews()
        val log = Store.log(ctx)
        val armed = clearArmed()
        if (!armed) clearArmedAt = 0L

        content.addView(gap(ctx, S6))

        // The heading stays first and visible whether or not anything has been caught yet.
        content.addView(title(ctx, "Recent", 22f))
        content.addView(gap(ctx, S3))

        if (log.isEmpty()) {
            content.addView(card(ctx).apply {
                addView(title(ctx, "Nothing caught yet", Brand.T_BODY))
            })
        } else {
            // Clear sits above the entries, so it is reachable without scrolling past the log.
            content.addView(clearButton(armed))
            content.addView(gap(ctx, S4))

            val box = card(ctx)
            log.forEachIndexed { i, c ->
                box.addView(ctx.catchRow(c, revealable = true))
                if (i < log.size - 1) box.addView(Brand.rule(ctx))
            }
            content.addView(box)
        }

        scroll.post { scroll.scrollTo(0, y) }
    }

    /** The two-tap clear control. The second tap has to come within [CLEAR_ARM_MS]. */
    private fun clearButton(armed: Boolean): View = title(
        ctx,
        if (armed) "Tap again to clear" else "Clear the log",
        Brand.T_BODY,
        if (armed) Brand.ERROR else Brand.INK_45
    ).apply {
        gravity = Gravity.CENTER
        background = Brand.tappable(ctx, Brand.R_PILL, Brand.FIELD)
        setPadding(0, dp(ctx, S4), 0, dp(ctx, S4))
        minHeight = dp(ctx, Brand.MIN_TAP)
        isClickable = true
        setOnClickListener {
            if (!clearArmed()) {
                clearArmedAt = System.currentTimeMillis()
                refresh()
            } else {
                Store.clearLog(ctx)
                clearArmedAt = 0L
                refresh()
            }
        }
    }

    private companion object {
        /** The second tap has to come within this window; leaving the tab disarms it. */
        const val CLEAR_ARM_MS = 5_000L
    }
}
