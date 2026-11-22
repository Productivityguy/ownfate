package org.ownfate.app

import org.ownfate.app.ui.Brand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The colour helpers the theme is built from. */
class BrandColourTest {

    @Test fun withAlphaKeepsTheColourAndReplacesAlpha() {
        val result = Brand.withAlpha(0xFF123456.toInt(), 0x80)
        assertEquals(0x80123456.toInt(), result)
    }

    @Test fun withAlphaMasksAWidthLargerThanOneByte() {
        assertEquals(0xFF123456.toInt(), Brand.withAlpha(0xFF123456.toInt(), 0x1FF))
    }

    @Test fun mixAtZeroIsTheFirstColour() {
        assertEquals(
            0xFF102030.toInt(),
            Brand.mix(0xFF102030.toInt(), 0xFFA0B0C0.toInt(), 0f)
        )
    }

    @Test fun mixAtOneIsTheSecondColour() {
        assertEquals(
            0xFFA0B0C0.toInt(),
            Brand.mix(0xFF102030.toInt(), 0xFFA0B0C0.toInt(), 1f)
        )
    }

    @Test fun mixBlendsEachChannel() {
        assertEquals(
            0xFF586878.toInt(),
            Brand.mix(0xFF102030.toInt(), 0xFFA0B0C0.toInt(), 0.5f)
        )
    }

    @Test fun mixClampsAWeightOutsideTheUnitRange() {
        assertEquals(
            Brand.mix(0xFF102030.toInt(), 0xFFA0B0C0.toInt(), 0f),
            Brand.mix(0xFF102030.toInt(), 0xFFA0B0C0.toInt(), -2f)
        )
        assertEquals(
            Brand.mix(0xFF102030.toInt(), 0xFFA0B0C0.toInt(), 1f),
            Brand.mix(0xFF102030.toInt(), 0xFFA0B0C0.toInt(), 2f)
        )
    }

    @Test fun mixedColoursAreOpaque() {
        val result = Brand.mix(0x00102030, 0x00A0B0C0, 0.25f)
        assertNotEquals(0, result ushr 24)
        assertEquals(0xFF, result ushr 24)
    }
}
