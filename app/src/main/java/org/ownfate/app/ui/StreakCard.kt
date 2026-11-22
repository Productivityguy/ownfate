package org.ownfate.app.ui

import org.ownfate.app.MainActivity
import org.ownfate.app.R
import org.ownfate.app.data.Streak

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.DatePicker
import android.widget.ImageView
import android.widget.LinearLayout
import org.ownfate.app.ui.Brand.S1
import org.ownfate.app.ui.Brand.S2
import org.ownfate.app.ui.Brand.S3
import org.ownfate.app.ui.Brand.S4
import org.ownfate.app.ui.Brand.body
import org.ownfate.app.ui.Brand.card
import org.ownfate.app.ui.Brand.col
import org.ownfate.app.ui.Brand.dp
import org.ownfate.app.ui.Brand.eyebrow
import org.ownfate.app.ui.Brand.gap
import org.ownfate.app.ui.Brand.grow
import org.ownfate.app.ui.Brand.hero
import org.ownfate.app.ui.Brand.row
import org.ownfate.app.ui.Brand.title
import java.time.LocalDate

/**
 * The clean-day streak cards and their "pick a date" menu.
 *
 * Visual only: the counting rules live in [Streak]. Kept out of [GuardScreen] so the Home layout
 * stays readable and the date flow has one obvious home.
 */
internal class StreakCard(
    private val host: MainActivity,
    private val onChanged: () -> Unit
) {
    private val ctx: Context = host

    fun card(category: Streak.Category, trophy: Boolean): View {

        val days = Streak.days(ctx, category)
        val start = Streak.start(ctx, category)
        val unit = if (days == 1) "day" else "days"

        // Slightly translucent so the background art shows through, while the opaque text on
        // top keeps its contrast. 0xD9 is ~85%: a hint of depth, not a glass panel.
        return card(
            ctx,
            Brand.withAlpha(Brand.PRIMARY_CONTAINER, 0xD9),
            Brand.R_HERO
        ).apply {

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

            isClickable = true

            setOnClickListener {
                menu(category)
            }

            addView(row(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL

                addView(col(ctx).apply {
                    layoutParams = grow()

                    addView(eyebrow(
                        ctx,
                        category.label,
                        Brand.INK_80
                    ))

                    addView(gap(ctx, S2))

                    addView(row(ctx).apply {
                        gravity = Gravity.BOTTOM

                        addView(hero(
                            ctx,
                            days.toString(),
                            46f,
                            Brand.PRIMARY
                        ))

                        addView(View(ctx).apply {
                            layoutParams = LinearLayout.LayoutParams(dp(ctx, S2), 1)
                        })

                        addView(body(
                            ctx,
                            unit,
                            Brand.T_BODY,
                            Brand.INK_60
                        ).apply {
                            setPadding(0, 0, 0, dp(ctx, 7f))
                        })
                    })

                    addView(gap(ctx, S1))

                    addView(body(
                        ctx,
                        "Since ${Streak.format(start)}",
                        Brand.T_CAPTION,
                        Brand.INK_45
                    ))

                    addView(gap(ctx, S1))

                    addView(body(
                        ctx,
                        "Tap to change",
                        Brand.T_CAPTION,
                        Brand.INK_30
                    ))
                })

                if (trophy) {
                    addView(ImageView(ctx).apply {
                        setImageResource(R.drawable.ic_trophy)
                        contentDescription = null
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        imageTintList = ColorStateList.valueOf(Brand.PRIMARY)
                        background = Brand.surface(ctx, Brand.R_PILL, Brand.FIELD)
                        setPadding(
                            dp(ctx, 16f),
                            dp(ctx, 16f),
                            dp(ctx, 16f),
                            dp(ctx, 16f)
                        )
                        layoutParams = LinearLayout.LayoutParams(
                            dp(ctx, 68f),
                            dp(ctx, 68f)
                        ).apply {
                            marginStart = dp(ctx, S3)
                        }
                    })
                }
            })
        }
    }


    private fun menu(category: Streak.Category) {
        host.showPopup { dismiss ->
            Brand.col(ctx).apply {
                background = Brand.popupSurface(ctx)
                setPadding(dp(ctx, 20f), dp(ctx, 20f), dp(ctx, 20f), dp(ctx, 16f))
                isClickable = true
                addView(title(ctx, category.label, 20f))
                addView(gap(ctx, S2))
                addView(body(ctx, "Day ${Streak.days(ctx, category)} · since ${Streak.format(Streak.start(ctx, category))}",
                    Brand.T_BODY, Brand.INK_45))
                addView(gap(ctx, S4))
                addView(ctx.primaryButton("Pick a date") {
                    dismiss()
                    startPicker(category)
                })
                addView(gap(ctx, S2))
                addView(ctx.quietButton("Reset") {
                    Streak.reset(ctx, category)
                    dismiss()
                    host.toast("${category.label} reset · day 1")
                    onChanged()
                })
                addView(gap(ctx, S2))
                addView(ctx.quietButton("Cancel") { dismiss() })
            }
        }
    }

    private fun startPicker(category: Streak.Category) {
        val start = Streak.start(ctx, category)
        host.showPopup { dismiss ->
            Brand.col(ctx).apply {
                background = Brand.popupSurface(ctx)
                setPadding(dp(ctx, 20f), dp(ctx, 20f), dp(ctx, 20f), dp(ctx, 16f))
                isClickable = true
                addView(title(ctx, category.label, 20f))
                addView(gap(ctx, S3))
                val picker = DatePicker(ctx).apply {
                    maxDate = System.currentTimeMillis()
                    init(start.year, start.monthValue - 1, start.dayOfMonth, null)
                }
                addView(picker)
                addView(gap(ctx, S4))
                addView(ctx.primaryButton("Save") {
                    val date = LocalDate.of(picker.year, picker.month + 1, picker.dayOfMonth)
                    Streak.setStart(ctx, category, date)
                    dismiss()
                    host.toast("${category.label} · day ${Streak.days(ctx, category)}")
                    onChanged()
                })
                addView(gap(ctx, S2))
                addView(ctx.quietButton("Cancel") { dismiss() })
            }
        }
    }
}
