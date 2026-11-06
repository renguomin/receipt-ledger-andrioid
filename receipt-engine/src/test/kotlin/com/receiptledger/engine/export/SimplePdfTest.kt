package com.receiptledger.engine.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimplePdfTest {
    @Test
    fun embedsTheStoredColorJpegInsteadOfRawPixels() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val pdf = SimplePdf()
        pdf.text(40f, 56f, 16f, "Receipts", bold = true)
        pdf.image(40f, 80f, 200f, 200f, PackedPdfImage(8, 8, jpeg, PdfImageEncoding.JPEG))
        val asText = pdf.toByteArray().toString(Charsets.ISO_8859_1)
        assertTrue(asText.startsWith("%PDF-1.4"))
        assertTrue(asText.contains("/Filter /DCTDecode"))
        assertTrue(asText.contains("/DeviceRGB"))
        assertTrue(asText.contains("(Receipts)"))
    }

    @Test
    fun wrapKeepsWordsInsideTheColumn() {
        val lines = PdfFonts.wrap("Canadian Tire store", 10f, 50f)
        assertTrue(lines.size >= 2)
        lines.forEach { line ->
            assertTrue(PdfFonts.width(line, 10f) <= 50f + 0.5f)
        }
        assertEquals(listOf("Hi"), PdfFonts.wrap("Hi", 10f, 400f))
    }

    @Test
    fun emDashUsedForMissingValuesIsStoredAsAscii() {
        val pdf = SimplePdf()
        pdf.text(40f, 56f, 10f, ReceiptPdfLayout.MISSING)
        val asText = pdf.toByteArray().toString(Charsets.ISO_8859_1)
        assertTrue(asText.contains("(-)"))
    }
}
