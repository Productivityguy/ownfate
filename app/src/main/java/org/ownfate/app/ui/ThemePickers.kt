package org.ownfate.app.ui

import org.ownfate.app.MainActivity
import org.ownfate.app.R
import org.ownfate.app.data.Store

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * The background picker, kept out of the activity shell.
 *
 * It is a popup over Home. It writes the art and tint preferences, then asks the activity to
 * rebuild the background; nothing here owns the activity's view tree beyond the popup it is
 * handed.
 */

internal fun MainActivity.showBackgroundPicker() {
        dismissHomeMenu {
            showPopup { dismiss ->
                val box = Brand.col(this).apply {
                    background = Brand.popupSurface(this@showBackgroundPicker)
                    setPadding(
                        Brand.dp(this@showBackgroundPicker, Brand.S5),
                        Brand.dp(this@showBackgroundPicker, Brand.S5),
                        Brand.dp(this@showBackgroundPicker, Brand.S5),
                        Brand.dp(this@showBackgroundPicker, Brand.S4)
                    )
                    isClickable = true
                }

                fun render() {
                    box.removeAllViews()
                    box.addView(Brand.title(this, "Background", 20f))
                    box.addView(Brand.gap(this, Brand.S2))
                    box.addView(Brand.body(
                        this,
                        "A faint pattern or braille art sits behind every screen. Grid is the default.",
                        Brand.T_CAPTION,
                        Brand.INK_45
                    ))
                    box.addView(Brand.gap(this, Brand.S3))

                    val list = Brand.col(this)
                    val stored = Store.str(this, Store.BACKGROUND, Store.BG_GRID)
                    val selected = if (BACKGROUND_ART.any { it.id == stored }) stored else Store.BG_GRID
                    val entries = BACKGROUND_ART
                    entries.forEachIndexed { i, art ->
                        list.addView(Brand.row(this).apply {
                            setPadding(0, Brand.dp(this@showBackgroundPicker, Brand.S2), 0, Brand.dp(this@showBackgroundPicker, Brand.S2))
                            minimumHeight = Brand.dp(this@showBackgroundPicker, Brand.MIN_TAP)
                            isClickable = true
                            isFocusable = true
                            setOnClickListener {
                                Store.putStr(this@showBackgroundPicker, Store.BACKGROUND, art.id)
                                applyBackground()
                                render()
                            }
                            addView(Brand.title(this@showBackgroundPicker, art.label, Brand.T_BODY)
                                .apply { layoutParams = Brand.grow() })
                            addView(this@showBackgroundPicker.stateDot(art.id == selected, 13f))
                        })
                        if (i < entries.lastIndex) list.addView(Brand.rule(this))
                    }
                    val scroll = ScrollView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            Brand.dp(this@showBackgroundPicker, 286f)
                        )
                        isVerticalScrollBarEnabled = true
                        addView(list)
                    }
                    box.addView(scroll)

                    box.addView(Brand.gap(this, Brand.S3))
                    box.addView(Brand.rule(this))
                    box.addView(Brand.gap(this, Brand.S3))
                    box.addView(Brand.title(this, "Art colour", Brand.T_BODY))
                    box.addView(Brand.gap(this, Brand.S2))
                    box.addView(colourGrid(
                        selected = Store.int(this, Store.ART_COLOR, Store.ART_COLOR_DEFAULT),
                        colours = ART_COLOURS
                    ) { colour ->
                        Store.putInt(this, Store.ART_COLOR, colour)
                        applyBackground()
                        render()
                    })

                    box.addView(Brand.gap(this, Brand.S4))
                    box.addView(quietButton("Done") { dismiss() })
                }

                render()
                box
            }
        }
    }

    /** A grid of round swatches used by the art-colour picker. */
    private fun MainActivity.colourGrid(
        selected: Int,
        colours: List<ColourSwatch>,
        onPick: (Int) -> Unit
    ): View {
        val grid = Brand.col(this)
        colours.chunked(4).forEachIndexed { rowIndex, chunk ->
            if (rowIndex > 0) grid.addView(Brand.gap(this, Brand.S2))
            grid.addView(Brand.row(this).apply {
                chunk.forEach { swatch ->
                    addView(FrameLayout(this@colourGrid).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            Brand.dp(this@colourGrid, 52f),
                            Brand.dp(this@colourGrid, 48f)
                        )
                        isClickable = true
                        isFocusable = true
                        contentDescription = swatch.label
                        setOnClickListener { onPick(swatch.value) }
                        addView(View(this@colourGrid).apply {
                            background = android.graphics.drawable.GradientDrawable().apply {
                                shape = android.graphics.drawable.GradientDrawable.OVAL
                                setColor(swatch.display)
                                setStroke(
                                    Brand.dp(this@colourGrid, if (swatch.value == selected) 2.5f else 1f),
                                    if (swatch.value == selected) Brand.INK else Brand.HAIRLINE
                                )
                            }
                            layoutParams = FrameLayout.LayoutParams(
                                Brand.dp(this@colourGrid, 32f),
                                Brand.dp(this@colourGrid, 32f)
                            ).apply { gravity = Gravity.CENTER }
                        })
                    })
                }
            })
        }
        return grid
    }

internal fun backgroundRes(id: String): Int =
        BACKGROUND_ART.firstOrNull { it.id == id }?.res
            ?: BACKGROUND_ART.first { it.id == Store.BG_GRID }.res

internal fun backgroundLabel(id: String): String =
        BACKGROUND_ART.firstOrNull { it.id == id }?.label ?: "Grid"

private data class BackgroundArt(val id: String, val label: String, val res: Int)
private data class ColourSwatch(val label: String, val value: Int, val display: Int)

/**
 * The braille art bundled in the APK. The source artwork is kept outside the source release; these
 * PNGs are pre-rendered so the app does not lay out a 74-line text block behind every screen.
 */
private val BACKGROUND_ART = listOf(
    BackgroundArt(Store.BG_GRID, "Grid", R.drawable.bg_grid),
    BackgroundArt("dots", "Dots", R.drawable.bg_dots),
    BackgroundArt("samurai", "Samurai", R.drawable.bg_samurai),
    BackgroundArt("warrior", "Warrior", R.drawable.bg_warrior),
    BackgroundArt("miku", "Miku", R.drawable.bg_miku),
    BackgroundArt("flower", "Flower", R.drawable.bg_flower),
    BackgroundArt("spiderweb", "Spiderweb", R.drawable.bg_spiderweb),
    BackgroundArt("sword", "Sword", R.drawable.bg_sword)
)

private val ART_COLOURS = listOf(
    ColourSwatch("Grey", Store.ART_COLOR_DEFAULT, Store.ART_COLOR_DEFAULT),
    ColourSwatch("Blossom", 0xFFF2A7C3.toInt(), 0xFFF2A7C3.toInt()),
    ColourSwatch("Rose", 0xFFE57373.toInt(), 0xFFE57373.toInt()),
    ColourSwatch("Black", 0xFF000000.toInt(), 0xFF000000.toInt()),
    ColourSwatch("Mint", 0xFF7FD8A8.toInt(), 0xFF7FD8A8.toInt()),
    ColourSwatch("Cyan", 0xFF6ED6E8.toInt(), 0xFF6ED6E8.toInt()),
    ColourSwatch("Sky", 0xFF7FB2F0.toInt(), 0xFF7FB2F0.toInt()),
    ColourSwatch("Violet", 0xFFB39DFF.toInt(), 0xFFB39DFF.toInt()),
    ColourSwatch("Lavender", 0xFFD0A8F0.toInt(), 0xFFD0A8F0.toInt()),
    ColourSwatch("Sand", 0xFFD8C4A0.toInt(), 0xFFD8C4A0.toInt()),
    ColourSwatch("Coral", 0xFFFF8A65.toInt(), 0xFFFF8A65.toInt()),
    ColourSwatch("White", 0xFFE6E6E6.toInt(), 0xFFE6E6E6.toInt())
)
