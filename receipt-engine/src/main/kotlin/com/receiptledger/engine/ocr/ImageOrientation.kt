package com.receiptledger.engine.ocr

import com.receiptledger.engine.model.BoundingBox
import com.receiptledger.engine.model.OcrLine
import com.receiptledger.engine.model.OcrPage
import com.receiptledger.engine.parse.MoneyParser

/**
 * Google's document scanner sometimes saves a receipt rotated 180°.
 * EXIF is applied first; leftover upside-down pages are caught from OCR layout.
 */
object ImageOrientation {
    const val EXIF_NORMAL = 1
    const val EXIF_FLIP_HORIZONTAL = 2
    const val EXIF_ROTATE_180 = 3
    const val EXIF_FLIP_VERTICAL = 4
    const val EXIF_TRANSPOSE = 5
    const val EXIF_ROTATE_90 = 6
    const val EXIF_TRANSVERSE = 7
    const val EXIF_ROTATE_270 = 8

    const val TOP_BAND = 0.40f
    const val BOTTOM_BAND = 0.55f

    enum class ExifAction {
        IDENTITY,
        FLIP_HORIZONTAL,
        ROTATE_180,
        FLIP_VERTICAL,
        TRANSPOSE,
        ROTATE_90_CW,
        TRANSVERSE,
        ROTATE_270_CW,
    }

    data class CorrectedPage(
        val page: OcrPage,
        val rotated180: Boolean,
    )

    fun exifAction(orientation: Int): ExifAction = when (orientation) {
        EXIF_FLIP_HORIZONTAL -> ExifAction.FLIP_HORIZONTAL
        EXIF_ROTATE_180 -> ExifAction.ROTATE_180
        EXIF_FLIP_VERTICAL -> ExifAction.FLIP_VERTICAL
        EXIF_TRANSPOSE -> ExifAction.TRANSPOSE
        EXIF_ROTATE_90 -> ExifAction.ROTATE_90_CW
        EXIF_TRANSVERSE -> ExifAction.TRANSVERSE
        EXIF_ROTATE_270 -> ExifAction.ROTATE_270_CW
        else -> ExifAction.IDENTITY
    }

    fun correctUpsideDown(page: OcrPage): CorrectedPage {
        if (!shouldRotate180(page)) return CorrectedPage(page, rotated180 = false)
        return CorrectedPage(rotatePage180(page), rotated180 = true)
    }

    fun shouldRotate180(page: OcrPage): Boolean {
        val height = page.height.coerceAtLeast(1f)
        val markers = page.lines.filter { isUprightnessMarker(it.text) }
        if (markers.isEmpty()) return false
        val inTop = markers.any { it.box.centerY < height * TOP_BAND }
        val inBottom = markers.any { it.box.centerY > height * BOTTOM_BAND }
        return inTop && !inBottom
    }

    fun rotatePage180(page: OcrPage): OcrPage {
        val width = page.width.coerceAtLeast(1f)
        val height = page.height.coerceAtLeast(1f)
        val lines = page.lines
            .map { line -> line.copy(box = rotateBox180(line.box, width, height)) }
            .sortedWith(compareBy({ it.box.top }, { it.box.left }))
        return page.copy(lines = lines)
    }

    fun rotateBox180(box: BoundingBox, pageWidth: Float, pageHeight: Float): BoundingBox =
        BoundingBox(
            left = pageWidth - box.right,
            top = pageHeight - box.bottom,
            right = pageWidth - box.left,
            bottom = pageHeight - box.top,
        )

    fun isUprightnessMarker(text: String): Boolean {
        val line = text.trim()
        if (line.isEmpty()) return false
        if (NOT_FOOTER.containsMatchIn(line)) return false
        if (CLOSING.containsMatchIn(line)) return true
        if (AMOUNT_DUE.containsMatchIn(line)) return true
        if (!TOTAL.containsMatchIn(line)) return false
        return MoneyParser.lastAmount(line) != null || TOTAL_ONLY.matches(line)
    }

    private val NOT_FOOTER = Regex(
        """\b(sub[\s-]*total|total\s*(sale|fuel|savings|items?|qty|quantity)|item\s*total)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val TOTAL = Regex("""\b((grand\s*)?total)\b""", RegexOption.IGNORE_CASE)
    private val TOTAL_ONLY = Regex("""^((grand\s*)?total)[.\s]*$""", RegexOption.IGNORE_CASE)
    private val AMOUNT_DUE = Regex(
        """\b(amount\s*due|balance\s*due|change\s*due)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val CLOSING = Regex(
        """\bthank\s*you\b|\bplease\s+come\s+again\b|\bcustomer\s+copy\b""",
        RegexOption.IGNORE_CASE,
    )
}
