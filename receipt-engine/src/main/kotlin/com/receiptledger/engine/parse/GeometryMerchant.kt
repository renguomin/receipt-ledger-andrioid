package com.receiptledger.engine.parse

import com.receiptledger.engine.catalog.BrandCatalog
import com.receiptledger.engine.catalog.BrandTokens
import com.receiptledger.engine.model.BoundingBox
import com.receiptledger.engine.model.OcrLine
import kotlin.math.max
import kotlin.math.min

object GeometryMerchant {
    const val HEADER_HEIGHT_FRACTION = 0.50f
    const val LOGO_HEIGHT_FRACTION = 0.30f

    fun guess(
        lines: List<OcrLine>,
        pageHeight: Float,
        catalog: BrandCatalog = BrandCatalog.nsi,
    ): String? {
        val content = lines.filter { it.text.isNotBlank() }
        if (content.isEmpty()) return null
        val header = mergeStackedLogoLines(headerLines(content, pageHeight))
        val ocrTokens = content.flatMap { BrandTokens.tokenize(it.text) }.toSet()
        val logoTokens = logoZone(header, pageHeight)
            .flatMap { BrandTokens.tokenize(it.text) }
            .toSet()
        val blob = BrandTokens.compact(content.joinToString(" ") { it.text })
        catalog.match(ocrTokens, logoTokens, blob)?.let { return it }
        val originTop = header.minOf { it.box.top }
        val picked = header
            .filter { isCandidate(it.text) }
            .maxWithOrNull(
                compareBy<OcrLine> { sizeScore(it, originTop) }
                    .thenBy { it.box.width }
                    .thenBy { -it.box.top },
            ) ?: return null
        return cleanMerchantText(picked.text).ifBlank { null }
    }

    fun headerLines(lines: List<OcrLine>, pageHeight: Float): List<OcrLine> {
        val content = lines.filter { it.text.isNotBlank() }
        if (content.isEmpty()) return emptyList()
        if (pageHeight <= 0f) return content.take(4)
        val top = content.minOf { it.box.top }
        val bandBottom = top + pageHeight * HEADER_HEIGHT_FRACTION
        return content.filter { it.box.top <= bandBottom }.ifEmpty { content.take(4) }
    }

    fun mergeStackedLogoLines(lines: List<OcrLine>): List<OcrLine> {
        if (lines.size < 2) return lines
        val sorted = lines.sortedBy { it.box.top }
        val merged = mutableListOf<OcrLine>()
        var index = 0
        while (index < sorted.size) {
            var current = sorted[index]
            var nextIndex = index + 1
            while (nextIndex < sorted.size && shouldStack(current, sorted[nextIndex])) {
                current = joinStacked(current, sorted[nextIndex])
                nextIndex += 1
            }
            merged += current
            index = nextIndex
        }
        return merged
    }

    fun cleanMerchantText(text: String): String =
        text.trim()
            .replace(STORE_NUMBER, " ")
            .replace(TRAILING_HASH_NUMBER, "")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun isCandidate(text: String): Boolean {
        val line = cleanMerchantText(text)
        return line.length in 3..48 &&
            !URL.containsMatchIn(line) &&
            !PHONE.containsMatchIn(line) &&
            !STREET.containsMatchIn(line) &&
            MoneyParser.findAll(line).isEmpty() &&
            DateParser.findIsoDate(line) == null
    }

    fun sizeScore(line: OcrLine, originTop: Float): Float {
        val dist = (line.box.top - originTop).coerceAtLeast(0f)
        return line.box.height / (1f + dist / 40f)
    }

    private fun logoZone(header: List<OcrLine>, pageHeight: Float): List<OcrLine> {
        if (header.isEmpty()) return emptyList()
        val top = header.minOf { it.box.top }
        val cutoff = top + pageHeight.coerceAtLeast(1f) * LOGO_HEIGHT_FRACTION
        return header.filter { it.box.top <= cutoff }.ifEmpty { header.take(2) }
    }

    private fun shouldStack(upper: OcrLine, lower: OcrLine): Boolean {
        if (!isLogoWord(upper.text) || !isLogoWord(lower.text)) return false
        val taller = max(upper.box.height, lower.box.height)
        val shorter = min(upper.box.height, lower.box.height)
        if (taller <= 0f || shorter < taller * 0.65f) return false
        val gap = lower.box.top - upper.box.bottom
        if (gap < -taller * 0.15f || gap > taller * 0.6f) return false
        val overlapsX = upper.box.left < lower.box.right && lower.box.left < upper.box.right
        if (!overlapsX) return false
        val combined = cleanMerchantText("${upper.text} ${lower.text}")
        return combined.length in 3..48
    }

    private fun isLogoWord(text: String): Boolean {
        val word = cleanMerchantText(text)
        if (word.length !in 2..16) return false
        if (word.contains(' ')) return false
        return word.all { it.isLetter() || it == '&' || it == '\'' }
    }

    private fun joinStacked(upper: OcrLine, lower: OcrLine): OcrLine {
        val text = cleanMerchantText("${upper.text} ${lower.text}")
        val box = BoundingBox(
            left = min(upper.box.left, lower.box.left),
            top = min(upper.box.top, lower.box.top),
            right = max(upper.box.right, lower.box.right),
            bottom = max(upper.box.bottom, lower.box.bottom),
        )
        return OcrLine(text = text, box = box, confidence = min(upper.confidence, lower.confidence))
    }

    private val STORE_NUMBER = Regex("""\bstore\s*#?\s*\d+\b""", RegexOption.IGNORE_CASE)
    private val TRAILING_HASH_NUMBER = Regex("""\s*#\s*\d+\s*$""")
    private val URL = Regex("""www\.|https?://""", RegexOption.IGNORE_CASE)
    private val PHONE = Regex(
        """(\(?\d{3}\)?[\s.\-]\d{3}[\s.\-]\d{4})|(\b(tel|fax|phone)\b.*\d{3})""",
        RegexOption.IGNORE_CASE,
    )
    private val STREET = Regex(
        """^[\d][\d\-]*\s+.+\b(st|street|ave|avenue|rd|road|dr|drive|blvd|boulevard|way|ln|lane)\b""",
        RegexOption.IGNORE_CASE,
    )
}
