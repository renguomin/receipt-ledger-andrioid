package com.receiptledger.engine.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PdfImageCompressionTest {
    @Test
    fun packsEightPixelsWithWhiteAsOne() {
        val black = ReceiptImageEnhance.BLACK
        val white = ReceiptImageEnhance.WHITE
        val packed = PdfImageCompression.packWhiteAsOne(
            intArrayOf(black, white, white, white, white, white, white, white),
            width = 8,
            height = 1,
        )
        assertEquals(1, packed.size)
        assertEquals(0x7F.toByte(), packed[0])
        assertEquals(
            0xFF.toByte(),
            PdfImageCompression.packWhiteAsOne(IntArray(8) { white }, 8, 1)[0],
        )
        assertEquals(
            0x00.toByte(),
            PdfImageCompression.packWhiteAsOne(IntArray(8) { black }, 8, 1)[0],
        )
    }

    @Test
    fun flateShrinksAMostlyWhiteReceipt() {
        val white = ReceiptImageEnhance.WHITE
        val packed = PdfImageCompression.packWhiteAsOne(IntArray(64 * 64) { white }, 64, 64)
        val compressed = PdfImageCompression.flate(packed)
        assertEquals(64 * 64 / 8, packed.size)
        assertTrue(compressed.size < packed.size / 4)
    }
}
