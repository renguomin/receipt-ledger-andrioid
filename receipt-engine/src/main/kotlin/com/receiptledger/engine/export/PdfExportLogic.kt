package com.receiptledger.engine.export

import java.time.LocalDate

object PdfExportLogic {
    fun workUnits(receiptCount: Int): Int =
        if (receiptCount <= 0) 1 else receiptCount * 2 + 1

    fun fraction(completed: Int, total: Int): Float =
        if (total <= 0) 0f else (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    fun tableMessage(number: Int, count: Int): String = "Writing table $number of $count"

    fun imageMessage(number: Int, count: Int): String = "Adding photo $number of $count"

    fun writingMessage(): String = "Saving PDF file…"

    fun startingMessage(): String = "Starting…"

    fun fileName(
        allReceipts: Boolean,
        date: LocalDate,
        extension: String = "pdf",
    ): String {
        val kind = if (allReceipts) "all" else "selected"
        return "receipts-$kind-$date.$extension"
    }

    fun readySummary(receiptCount: Int): String =
        if (receiptCount == 1) "1 receipt" else "$receiptCount receipts"

    fun imageSampleSize(width: Int, height: Int): Int = 1

    fun exportImageSize(width: Int, height: Int): Pair<Int, Int> = width to height

    fun storedJpegQuality(): Int = 80

    fun storedPhotosAreEnhancedBlackAndWhite(): Boolean = false

    fun pdfEmbedsStoredJpeg(): Boolean = true

    fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte()

    fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()

    fun isImage(bytes: ByteArray): Boolean = isJpeg(bytes) || isPng(bytes)
}
