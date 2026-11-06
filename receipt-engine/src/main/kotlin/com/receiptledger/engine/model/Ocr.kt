package com.receiptledger.engine.model

data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerY: Float get() = (top + bottom) / 2f
    val centerX: Float get() = (left + right) / 2f
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

data class OcrLine(
    val text: String,
    val box: BoundingBox,
    val confidence: Float = 1f,
)

data class OcrPage(
    val lines: List<OcrLine>,
    val width: Float = 1f,
    val height: Float = 1f,
) {
    val fullText: String get() = lines.joinToString("\n") { it.text }
}
