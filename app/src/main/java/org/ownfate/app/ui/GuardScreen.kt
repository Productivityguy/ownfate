package org.ownfate.app.ui

import org.ownfate.app.MainActivity
import org.ownfate.app.R
import org.ownfate.app.data.Lock
import org.ownfate.app.data.Store
import org.ownfate.app.data.Streak
import org.ownfate.app.guard.PagePass
import org.ownfate.app.isServiceOn

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import org.ownfate.app.ui.Brand.S2
import org.ownfate.app.ui.Brand.S3
import org.ownfate.app.ui.Brand.S6
import org.ownfate.app.ui.Brand.body
import org.ownfate.app.ui.Brand.card
import org.ownfate.app.ui.Brand.col
import org.ownfate.app.ui.Brand.dp
import org.ownfate.app.ui.Brand.eyebrow
import org.ownfate.app.ui.Brand.gap
import org.ownfate.app.ui.Brand.grow
import org.ownfate.app.ui.Brand.row
import org.ownfate.app.ui.Brand.title

/**
 * Home screen.
 *
 * Minimal design:
 * Larger OwnFate heading.
 * No section descriptions.
 * No switch descriptions.
 * Existing protection functionality preserved.
 */
class GuardScreen(private val host: MainActivity) : Screen {

    private val ctx = host
    private val content: LinearLayout = page(host)
    private val scroll: ScrollView = host.appScroll(content)

    /** The streak cards and their date menu. */
    private val streak = StreakCard(host) { refresh() }

    override val view: View get() = scroll

    init {
        refresh()
    }

    override fun refresh() {
        val y = scroll.scrollY
        content.removeAllViews()

        val on = isServiceOn(ctx)

        // ---- header -------------------------------------------------------

        content.addView(row(ctx).apply {

            addView(row(ctx).apply {
                layoutParams = grow()
                gravity = Gravity.CENTER_VERTICAL
                addView(title(ctx, "OwnFate", 32f))
                addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(ctx, S2), 1)
                })
                addView(ctx.stateDot(on, 10f))
            })

            addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_settings)
                contentDescription = "Settings"
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                imageTintList = ColorStateList.valueOf(
                    if (on) Brand.PRIMARY else Brand.INK_45
                )
                background = Brand.tappable(ctx, Brand.R_PILL, Brand.SURFACE_HI, Brand.HAIRLINE)
                setPadding(dp(ctx, 9f), dp(ctx, 9f), dp(ctx, 9f), dp(ctx, 9f))
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 42f), dp(ctx, 42f))
                isClickable = true
                isFocusable = true
                setOnClickListener { host.showHomeMenu() }
            })
        })

        content.addView(gap(ctx, S6))

        // ---- streaks ------------------------------------------------------

        val showPorn = Store.bool(ctx, Store.SHOW_PORN_STREAK, true)
        val showGambling = Store.bool(ctx, Store.SHOW_GAMBLING_STREAK, true)

        if (showPorn || showGambling) {
            val solo = showPorn != showGambling
            content.addView(row(ctx).apply {
                if (showPorn) addView(streak.card(Streak.Category.PORN, trophy = solo))

                if (showPorn && showGambling) {
                    addView(View(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(ctx, S3), 1)
                    })
                }

                if (showGambling) addView(streak.card(Streak.Category.GAMBLING, trophy = solo))
            })

            content.addView(gap(ctx, S6))
        }

        // ---- porn ---------------------------------------------------------

        content.addView(host.infoTitle("Porn"))
        content.addView(gap(ctx, S3))
        content.addView(card(ctx).apply {

            addView(toggle(
                ctx,
                "Adult websites",
                "",
                Store.bool(ctx, Store.ADULT_SITES, false),
                Lock.Feature.ADULT_SITES
            ))

            addView(Brand.rule(ctx))

            addView(toggle(
                ctx,
                "Adult content",
                "",
                Store.bool(ctx, Store.ADULT_WORDS, false),
                Lock.Feature.ADULT_WORDS
            ))

            addView(Brand.rule(ctx))

            addView(toggle(
                ctx,
                "Image & video search",
                "",
                Store.bool(ctx, Store.ADULT_IMAGE_VIDEO_SEARCH, false),
                Lock.Feature.IMAGE_VIDEO_SEARCH
            ))
        })

        content.addView(gap(ctx, S6))

        // ---- gambling -----------------------------------------------------

        content.addView(host.infoTitle("Gambling"))
        content.addView(gap(ctx, S3))
        content.addView(card(ctx).apply {

            addView(toggle(
                ctx,
                "Gambling websites",
                "",
                Store.bool(ctx, Store.GAMBLING_SITES, false),
                Lock.Feature.GAMBLING_SITES
            ))

            addView(Brand.rule(ctx))

            addView(toggle(
                ctx,
                "Gambling apps",
                "",
                Store.bool(ctx, Store.GAMBLING_APPS, false),
                Lock.Feature.GAMBLING_APPS
            ))

            addView(Brand.rule(ctx))

            addView(toggle(
                ctx,
                "Prediction markets",
                "",
                Store.bool(ctx, Store.PREDICTION_MARKETS, false),
                Lock.Feature.PREDICTION_MARKETS
            ))
        })

        content.addView(gap(ctx, S6))

        // ---- accessibility state -----------------------------------------

        val status = card(ctx).apply {

            isClickable = !on

            if (!on) {
                setOnClickListener {
                    host.openAccessibility()
                }
            }

            addView(row(ctx).apply {

                addView(ctx.stateDot(on))

                addView(col(ctx).apply {
                    layoutParams = grow()

                    addView(title(ctx, "Accessibility"))

                    addView(gap(ctx, 2f))

                    addView(body(
                        ctx,
                        if (on) "Watching window text — always on"
                        else "Off — tap to enable",
                        Brand.T_CAPTION,
                        Brand.INK_45
                    ))
                })

                addView(eyebrow(
                    ctx,
                    if (on) "On" else "Off",
                    if (on) Brand.PRIMARY else Brand.INK_30
                ))
            })
        }

        content.addView(status)

        if (!on) {
            content.addView(gap(ctx, S3))

            content.addView(
                ctx.primaryButton("Turn on protection") {
                    host.openAccessibility()
                }
            )
        }

        content.addView(gap(ctx, S6))

        // ---- open pass ----------------------------------------------------

        val pass = PagePass.current()

        if (pass != null) {

            content.addView(card(ctx).apply {

                isClickable = true

                setOnClickListener {
                    PagePass.clear()
                    refresh()
                }

                addView(eyebrow(ctx, "Open pass"))

                addView(gap(ctx, S2))

                addView(title(ctx, appLabel(ctx.packageManager, pass.pkg)))

                addView(gap(ctx, 2f))

                addView(body(
                    ctx,
                    "This screen · ${PagePass.minutesLeft()} min left · tap to end",
                    Brand.T_CAPTION,
                    Brand.INK_45
                ))
            })

            content.addView(gap(ctx, S6))
        }
    }

    // ---- protection switches ---------------------------------------------

    private fun toggle(
        ctx: Context,
        label: String,
        sub: String,
        on: Boolean,
        feature: Lock.Feature,
        labelSize: Float = Brand.T_BODY,
        subSize: Float = Brand.T_CAPTION
    ): View {

        val pending = Lock.pendingFor(ctx, feature)

        // Descriptions stay hidden, but a change that is waiting still has to say so: otherwise
        // the switch looks stuck and the wait is invisible on Home.
        val caption = pending?.let {
            "Turns ${if (on) "off" else "on"} in ${it.remainingLabel()}"
        } ?: sub

        return ctx.toggleRow(
            label,
            caption,
            on,
            labelSize,
            subSize,
            {
                host.change(feature, (!on).toString())
            }
        )
    }
}
