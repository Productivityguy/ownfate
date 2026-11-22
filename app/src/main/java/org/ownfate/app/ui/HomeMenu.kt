package org.ownfate.app.ui

import org.ownfate.app.MainActivity
import org.ownfate.app.data.Store

import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout

/**
 * The Home settings sheet: a dimmed card over the current screen.
 *
 * It writes preferences only. Anything that changes the palette asks the activity to re-apply
 * the theme; anything that changes the art asks it to rebuild the background. Kept out of the
 * activity shell so Home's small sheet does not compete with navigation code for attention.
 */

    /** The circular settings button on Home: a dimmed sheet over the current screen. */
internal fun MainActivity.showHomeMenu() {
        if (homeMenu != null) return
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Brand.SCRIM)
            isClickable = true
            setOnClickListener { dismissHomeMenu() }
        }
        homeMenu = overlay
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(150L).start()
        renderHomeMenu(animate = true)
    }

internal fun MainActivity.renderHomeMenu(animate: Boolean) {
        val overlay = homeMenu ?: return
        overlay.removeAllViews()

        val card = buildHomeMenuCard()
        overlay.addView(card, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            gravity = Gravity.TOP
            topMargin = Brand.dp(this@renderHomeMenu, 64f)
            marginStart = Brand.dp(this@renderHomeMenu, 20f)
            marginEnd = Brand.dp(this@renderHomeMenu, 20f)
        })

        if (animate) {
            card.alpha = 0f
            card.translationY = -Brand.dp(this, 12f).toFloat()
            card.animate().alpha(1f).translationY(0f).setDuration(180L).start()
        } else {
            card.alpha = 1f
            card.translationY = 0f
        }
    }

internal fun MainActivity.buildHomeMenuCard(): View {
        val ctx = this
        val card = Brand.col(ctx).apply {
            background = Brand.popupSurface(ctx)
            setPadding(
                Brand.dp(ctx, Brand.S5), Brand.dp(ctx, Brand.S5),
                Brand.dp(ctx, Brand.S5), Brand.dp(ctx, Brand.S4)
            )
            isClickable = true
        }

        card.addView(Brand.row(ctx).apply {
            addView(Brand.title(ctx, "Settings", 20f).apply { layoutParams = Brand.grow() })
        })
        card.addView(Brand.gap(ctx, Brand.S3))

        card.addView(ctx.toggleRow(
            "Porn streak",
            "Track how many days clean from Porn",
            Store.bool(ctx, Store.SHOW_PORN_STREAK, true)
        ) {
            val on = !Store.bool(ctx, Store.SHOW_PORN_STREAK, true)
            Store.putBool(ctx, Store.SHOW_PORN_STREAK, on)
            // Hiding a streak is a view choice, not a reset. The start date stays where it was.
            refreshCurrentScreen()
            renderHomeMenu(animate = false)
        })

        card.addView(Brand.rule(ctx))

        card.addView(ctx.toggleRow(
            "Gambling streak",
            "Track how many days clean from Gambling",
            Store.bool(ctx, Store.SHOW_GAMBLING_STREAK, true)
        ) {
            val on = !Store.bool(ctx, Store.SHOW_GAMBLING_STREAK, true)
            Store.putBool(ctx, Store.SHOW_GAMBLING_STREAK, on)
            refreshCurrentScreen()
            renderHomeMenu(animate = false)
        })

        card.addView(Brand.rule(ctx))
        card.addView(Brand.gap(ctx, Brand.S3))

        card.addView(Brand.title(ctx, "Theme", Brand.T_BODY))
        card.addView(Brand.gap(ctx, Brand.S2))
        card.addView(ctx.segmented(
            listOf("Dark" to Store.THEME_DARK, "Light" to Store.THEME_LIGHT),
            Store.str(ctx, Store.THEME, Store.THEME_DARK)
        ) { value ->
            if (value != Store.str(ctx, Store.THEME, Store.THEME_DARK)) {
                Store.putStr(ctx, Store.THEME, value)
                applyThemeChange()
                renderHomeMenu(animate = false)
            }
        })
        card.addView(Brand.gap(ctx, Brand.S3))

        card.addView(Brand.rule(ctx))

        card.addView(settingRow(
            "Background",
            backgroundLabel(Store.str(ctx, Store.BACKGROUND, Store.BG_GRID))
        ) { showBackgroundPicker() })

        card.addView(Brand.gap(ctx, Brand.S4))
        card.addView(ctx.quietButton("Done") { dismissHomeMenu() })
        return card
    }

internal fun MainActivity.settingRow(label: String, value: String, onTap: () -> Unit): View =
        Brand.row(this).apply {
            setPadding(0, Brand.dp(this@settingRow, Brand.S3), 0, Brand.dp(this@settingRow, Brand.S3))
            minimumHeight = Brand.dp(this@settingRow, Brand.MIN_ROW)
            isClickable = true
            isFocusable = true
            addView(Brand.col(this@settingRow).apply {
                layoutParams = Brand.grow()
                addView(Brand.title(this@settingRow, label, Brand.T_TITLE))
                addView(Brand.gap(this@settingRow, 3f))
                addView(Brand.body(this@settingRow, value, Brand.T_CAPTION, Brand.INK_30))
            })
            setOnClickListener { onTap() }
        }

internal fun MainActivity.dismissHomeMenu(then: (() -> Unit)? = null) {
        val overlay = homeMenu
        if (overlay == null) {
            then?.invoke()
            return
        }
        homeMenu = null
        overlay.animate().alpha(0f).setDuration(140L)
            .withEndAction {
                root.removeView(overlay)
                then?.invoke()
            }
            .start()
    }
