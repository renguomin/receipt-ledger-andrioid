package com.receiptledger.engine.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReceiptImageEnhanceTest {
    @Test
    fun luminanceWeightsGreenHighest() {
        assertEquals(0, ReceiptImageEnhance.luminance(0xFF000000.toInt()))
        assertEquals(255, ReceiptImageEnhance.luminance(0xFFFFFFFF.toInt()))
        val green = ReceiptImageEnhance.luminance(0xFF00FF00.toInt())
        val red = ReceiptImageEnhance.luminance(0xFFFF0000.toInt())
        val blue = ReceiptImageEnhance.luminance(0xFF0000FF.toInt())
        assertTrue(green > red)
        assertTrue(red > blue)
    }

    @Test
    fun otsuSplitsDarkInkFromLightPaper() {
        val histogram = IntArray(256)
        histogram[20] = 40
        histogram[240] = 60
        val threshold = ReceiptImageEnhance.otsuThreshold(histogram, 100)
        assertTrue(threshold in 20..239)
    }

    @Test
    fun toBlackAndWhiteTurnsInkBlackAndPaperWhite() {
        val pixels = intArrayOf(
            0xFF111111.toInt(),
            0xFFF5F5F5.toInt(),
            0xFF202020.toInt(),
            0xFFFFFFFF.toInt(),
        )
        ReceiptImageEnhance.toBlackAndWhiteInPlace(pixels)
        assertEquals(ReceiptImageEnhance.BLACK, pixels[0])
        assertEquals(ReceiptImageEnhance.WHITE, pixels[1])
        assertEquals(ReceiptImageEnhance.BLACK, pixels[2])
        assertEquals(ReceiptImageEnhance.WHITE, pixels[3])
    }

    @Test
    fun yellowHighlightBecomesABlackBlobOverInk() {
        val paper = 0xFFFFFFFF.toInt()
        val yellow = 0xFFDAA520.toInt()
        val ink = 0xFF111111.toInt()
        val pixels = IntArray(200) { index ->
            when {
                index < 150 -> paper
                index < 190 -> yellow
                else -> ink
            }
        }
        ReceiptImageEnhance.toBlackAndWhiteInPlace(pixels)
        assertEquals(ReceiptImageEnhance.WHITE, pixels[0])
        assertEquals(ReceiptImageEnhance.BLACK, pixels[160])
        assertEquals(ReceiptImageEnhance.BLACK, pixels[195])
    }
}
