package com.receiptledger.engine.export

object PdfFonts {
    fun width(text: String, size: Float): Float {
        var total = 0f
        for (ch in text) {
            total += glyphWidth(ch) * size / 1000f
        }
        return total
    }

    fun wrap(text: String, size: Float, maxWidth: Float): List<String> {
        val source = text.ifEmpty { ReceiptPdfLayout.MISSING }
        if (width(source, size) <= maxWidth) return listOf(source)
        val lines = ArrayList<String>()
        var remaining = source
        while (remaining.isNotEmpty()) {
            var take = remaining.length
            while (take > 1 && width(remaining.take(take), size) > maxWidth) {
                take -= 1
            }
            if (take < remaining.length) {
                val space = remaining.lastIndexOf(' ', take - 1)
                if (space >= 1) take = space
            }
            lines += remaining.take(take).trimEnd()
            remaining = remaining.drop(take).trimStart()
        }
        return lines.ifEmpty { listOf(source) }
    }

    private fun glyphWidth(ch: Char): Int {
        val code = ch.code
        if (code in 32..126) return HELVETICA[code - 32]
        return 500
    }

    private val HELVETICA = intArrayOf(
        278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
        556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
        1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
        667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
        222, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
        556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584,
    )
}
