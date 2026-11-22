package org.ownfate.app.guard

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import org.ownfate.app.R
import org.ownfate.app.data.apps.AppBlocklist
import org.ownfate.app.filter.Filter
import org.ownfate.app.ui.Brand
import org.ownfate.app.ui.ghostButton
import org.ownfate.app.ui.lineField
import org.ownfate.app.ui.primaryButton

/** Which switch produced a match, so the card and the log can name it precisely. */
internal enum class Kind {
    ADULT_SITE, GAMBLING_SITE, PREDICTION_MARKET, ADULT_WORD, CUSTOM_WORD, IMAGE_VIDEO_SEARCH,
    APP_SEARCH
}

/**
 * The two intervention cards: one for a blocked app, one for a blocked page.
 *
 * Presentation only. The card decides what to say and which buttons to offer, but every action
 * (closing, granting an appeal, re-arming the watcher) goes back through the callbacks, so the
 * service keeps the state and the window lifecycle. That split is why this file can be read on
 * its own when the wording changes.
 *
 * No AndroidX: the same plain views the rest of the AMOLED UI uses.
 */
internal class BlockCard(
    private val ctx: Context,
    private val icon: Drawable?,
    private val onCloseSoft: () -> Unit,
    private val onCloseHard: () -> Unit,
    private val onGate: (open: Boolean) -> Unit,
    private val onAppeal: (pkg: String, passPlace: String?, typed: String) -> Unit
) {

    /**
     * Hold the card's actions inert for [BUTTON_LOCK_MS] after it appears.
     *
     * The card is the forced pause for every block, so a rapid tap must not be able to dismiss it
     * before the block's BACK has landed. The scrim and the card body stay clickable as touch sinks:
     * they consume every tap but do nothing. Only these action views come alive, and only after the
     * wait. There is no timer that hides the card — it goes away only when an action is pressed.
     */
    private fun lockActions(root: View, vararg actions: View) {
        actions.forEach {
            it.isEnabled = false
            it.alpha = 0.45f
        }
        root.postDelayed({
            actions.forEach {
                it.isEnabled = true
                it.alpha = 1f
            }
        }, BUTTON_LOCK_MS)
    }

    /** The blocked-app card. [onClose] goes HOME, leaving the app rather than lifting the card. */
    fun app(reason: AppBlocklist.Reason, label: String, onClose: () -> Unit): View {
        val scrim = FrameLayout(ctx).apply {
            setBackgroundColor(0xFA000000.toInt())
            isClickable = true // swallow touches; the blocked app must not be usable behind it
        }
        val box = Brand.card(ctx, Brand.CARD_SOLID, Brand.R_HERO).apply {
            setPadding(
                Brand.dp(ctx, Brand.S5), Brand.dp(ctx, Brand.S5),
                Brand.dp(ctx, Brand.S5), Brand.dp(ctx, Brand.S5)
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                marginStart = Brand.dp(ctx, Brand.S5)
                marginEnd = Brand.dp(ctx, Brand.S5)
            }
            isClickable = true
            setOnClickListener { }
        }

        box.addView(Brand.row(ctx).apply {
            addView(ImageView(ctx).apply {
                setImageDrawable(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(
                    Brand.dp(ctx, 30f), Brand.dp(ctx, 30f)
                ).apply { rightMargin = Brand.dp(ctx, Brand.S2) }
            })
            addView(Brand.eyebrow(ctx, "OwnFate · ${reason.kicker}"))
        })
        box.addView(Brand.gap(ctx, Brand.S3))
        box.addView(Brand.display(ctx, "Not now."))
        box.addView(Brand.gap(ctx, Brand.S2))
        box.addView(
            Brand.body(
                ctx,
                "“${label.take(60)}” is on your ${reason.kicker} list. " +
                    "Close returns Home.",
                Brand.T_BODY
            )
        )
        box.addView(Brand.gap(ctx, Brand.S5))
        val close = ctx.primaryButton("Close", onClose)
        box.addView(close)

        scrim.addView(box)
        lockActions(scrim, close)
        return scrim
    }

    /**
     * The page card. SOFT keeps the page underneath and offers the retype gate; every other tier
     * was already stepped back from, so Close only dismisses the card.
     */
    fun page(
        pkg: String,
        match: Filter.Match,
        kind: Kind,
        text: String,
        passPlace: String?
    ): View {
        val soft = match.tier == Filter.Tier.SOFT

        val scrim = FrameLayout(ctx).apply {
            setBackgroundColor(0xE6000000.toInt())
            isClickable = true // swallow touches; nothing underneath is reachable
            // No click listener: the scrim is a touch sink only. Tapping outside the card must not
            // dismiss it, or the forced pause would be skippable.
        }

        val box = Brand.card(ctx, Brand.CARD_SOLID, Brand.R_HERO).apply {
            setPadding(
                Brand.dp(ctx, Brand.S5), Brand.dp(ctx, Brand.S5),
                Brand.dp(ctx, Brand.S5), Brand.dp(ctx, Brand.S5)
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                marginStart = Brand.dp(ctx, Brand.S5)
                marginEnd = Brand.dp(ctx, Brand.S5)
            }
            // Taps on the card itself must not count as taps on the scrim, or placing the cursor
            // in the gate's field would close the card.
            isClickable = true
            setOnClickListener { }
        }

        val label = when {
            kind == Kind.ADULT_SITE -> "OwnFate · adult site"
            kind == Kind.GAMBLING_SITE -> "OwnFate · gambling site"
            kind == Kind.PREDICTION_MARKET -> "OwnFate · prediction market"
            kind == Kind.IMAGE_VIDEO_SEARCH -> "OwnFate · image & video search"
            kind == Kind.APP_SEARCH -> "OwnFate · app search"
            kind == Kind.CUSTOM_WORD -> "OwnFate · custom word"
            soft -> "OwnFate · soft match"
            else -> "OwnFate · blocked"
        }
        val headline = when {
            kind == Kind.ADULT_SITE || kind == Kind.GAMBLING_SITE ||
                kind == Kind.PREDICTION_MARKET || kind == Kind.IMAGE_VIDEO_SEARCH -> "Not this site."
            kind == Kind.APP_SEARCH -> "Not this search."
            soft -> "Not sure."
            else -> "Not now."
        }
        val explanation = when {
            kind == Kind.ADULT_SITE ->
                "The address bar matched “${match.term.take(48)}”, which is on the bundled " +
                    "adult-site list. Stepped back."
            kind == Kind.GAMBLING_SITE ->
                "The address bar matched “${match.term.take(48)}”, which is on the bundled " +
                    "gambling-site list. Stepped back."
            kind == Kind.PREDICTION_MARKET ->
                "The address bar matched “${match.term.take(48)}”, which is on the bundled " +
                    "prediction-market list. Stepped back."
            kind == Kind.IMAGE_VIDEO_SEARCH ->
                "The address bar shows an image or video search page. “${match.term.take(48)}”. " +
                    "Stepped back."
            kind == Kind.APP_SEARCH ->
                "The ${match.term} search screen is on your add-on list. Stepped back."
            kind == Kind.CUSTOM_WORD ->
                "The window matched “${match.term.take(48)}”, one of your own blocked words. " +
                    "Stepped back."
            soft ->
                "The window matched “${match.term.take(48)}”. Close if it is what it looks like. " +
                    "If it is not, appeal."
            else ->
                "The window matched “${match.term.take(48)}”. Stepped back."
        }

        box.addView(Brand.row(ctx).apply {
            addView(ImageView(ctx).apply {
                setImageDrawable(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(
                    Brand.dp(ctx, 30f), Brand.dp(ctx, 30f)
                ).apply { rightMargin = Brand.dp(ctx, Brand.S2) }
            })
            addView(Brand.eyebrow(ctx, label))
        })
        box.addView(Brand.gap(ctx, Brand.S3))
        box.addView(Brand.display(ctx, headline))
        box.addView(Brand.gap(ctx, Brand.S2))
        box.addView(Brand.body(ctx, explanation, Brand.T_BODY))
        // The page text only helps when the match is ambiguous. A hard match is already named
        // above; repeating the surrounding explicit text is unnecessary exposure.
        if (soft) {
            box.addView(Brand.gap(ctx, Brand.S1))
            box.addView(Brand.body(ctx, text.trim().take(140), Brand.T_CAPTION, Brand.INK_30))
        }
        box.addView(Brand.gap(ctx, Brand.S5))

        if (!soft) {
            val close = ctx.primaryButton("Close") { onCloseHard() }
            box.addView(close)
            scrim.addView(box)
            lockActions(scrim, close)
            return scrim
        }

        // SOFT: the page is still underneath. Close is the "yes, it is" answer and performs the
        // BACK that the other tiers already got; Allow opens the retype gate instead.
        val boxLp = box.layoutParams as FrameLayout.LayoutParams
        val close = ctx.primaryButton("Close — it's adult content") { onCloseSoft() }
        val actions = Brand.col(ctx).apply { addView(close) }
        box.addView(actions)

        val gate = Brand.col(ctx).apply { visibility = View.GONE }
        val error = Brand.body(ctx, "", Brand.T_CAPTION, Brand.ERROR)
        val field = ctx.lineField("Type it here")

        gate.addView(Brand.body(ctx, "Type this exactly:", Brand.T_CAPTION, Brand.INK_45))
        gate.addView(Brand.gap(ctx, Brand.S2))
        gate.addView(Brand.body(ctx, Appeal.SENTENCE, Brand.T_BODY, Brand.ON_PRIMARY_CONTAINER).apply {
            typeface = Brand.medium(ctx)
            background = Brand.surface(ctx, Brand.R_TILE, Brand.SURFACE_HI)
            setPadding(
                Brand.dp(ctx, Brand.S4), Brand.dp(ctx, Brand.S3),
                Brand.dp(ctx, Brand.S4), Brand.dp(ctx, Brand.S3)
            )
            layoutParams = Brand.fill()
            setTextIsSelectable(true)
        })
        gate.addView(Brand.gap(ctx, Brand.S2))
        gate.addView(field)
        gate.addView(Brand.gap(ctx, Brand.S1))
        gate.addView(error)
        gate.addView(Brand.gap(ctx, Brand.S2))
        gate.addView(ctx.primaryButton("Unlock") {
            val typed = field.text?.toString().orEmpty()
            if (!Appeal.correct(typed)) {
                error.text = ctx.getString(R.string.appeal_retry)
                return@primaryButton
            }
            onAppeal(pkg, passPlace, typed)
        })
        gate.addView(Brand.gap(ctx, Brand.S2))
        gate.addView(ctx.ghostButton("Back") {
            gate.visibility = View.GONE
            actions.visibility = View.VISIBLE
            error.text = ""
            onGate(false)
            boxLp.gravity = Gravity.CENTER
            boxLp.topMargin = 0
            box.layoutParams = boxLp
        })

        val appeal = ctx.ghostButton("This isn't adult content — appeal") {
            actions.visibility = View.GONE
            gate.visibility = View.VISIBLE
            onGate(true)
            // Sit at the top while the keyboard is up, so the field is not behind it.
            boxLp.gravity = Gravity.TOP
            boxLp.topMargin = Brand.dp(ctx, Brand.S5)
            box.layoutParams = boxLp
            field.post {
                field.requestFocus()
                val imm = ctx.getSystemService(InputMethodManager::class.java)
                imm?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        actions.addView(Brand.gap(ctx, Brand.S2))
        actions.addView(appeal)
        box.addView(gate)

        scrim.addView(box)
        lockActions(scrim, close, appeal)
        return scrim
    }

    companion object {
        /** The card is inert this long, so the block's BACK lands before any button can be used. */
        private const val BUTTON_LOCK_MS = 2_000L
    }
}
