package com.receiptledger.engine.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PdfExportLogicTest {
    @Test
    fun workUnitsCountTablesImagesAndTheFinalWrite() {
        assertEquals(1, PdfExportLogic.workUnits(0))
        assertEquals(3, PdfExportLogic.workUnits(1))
        assertEquals(7, PdfExportLogic.workUnits(3))
        val date = LocalDate.of(2026, 8, 28)
        assertEquals("receipts-all-2026-08-28.pdf", PdfExportLogic.fileName(allReceipts = true, date))
        assertEquals(
            "receipts-selected-2026-08-28.pdf",
            PdfExportLogic.fileName(allReceipts = false, date),
        )
        assertEquals(
            "receipts-all-2026-08-28.csv",
            PdfExportLogic.fileName(allReceipts = true, date, "csv"),
        )
        assertEquals("1 receipt", PdfExportLogic.readySummary(1))
        assertEquals("40 receipts", PdfExportLogic.readySummary(40))
    }

    @Test
    fun fractionAndMessagesTrackEachPhase() {
        assertEquals(0f, PdfExportLogic.fraction(0, 7), 0.001f)
        assertEquals(3f / 7f, PdfExportLogic.fraction(3, 7), 0.001f)
        assertEquals(1f, PdfExportLogic.fraction(7, 7), 0.001f)
        assertEquals(0f, PdfExportLogic.fraction(1, 0))
        assertEquals("Writing table 2 of 5", PdfExportLogic.tableMessage(2, 5))
        assertEquals("Adding photo 5 of 5", PdfExportLogic.imageMessage(5, 5))
        assertEquals("Saving PDF file…", PdfExportLogic.writingMessage())
    }

    @Test
    fun storedPhotosAreColorJpegAndThePdfReusesThem() {
        assertEquals(1, PdfExportLogic.imageSampleSize(4000, 3000))
        assertEquals(4000 to 3000, PdfExportLogic.exportImageSize(4000, 3000))
        assertEquals(80, PdfExportLogic.storedJpegQuality())
        assertTrue(!PdfExportLogic.storedPhotosAreEnhancedBlackAndWhite())
        assertTrue(PdfExportLogic.pdfEmbedsStoredJpeg())
        assertTrue(PdfExportLogic.isJpeg(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertTrue(!PdfExportLogic.isJpeg(byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())))
    }
}
