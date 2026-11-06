package com.receiptledger.engine.ocr

import com.receiptledger.engine.model.BoundingBox
import com.receiptledger.engine.model.OcrLine
import com.receiptledger.engine.model.OcrPage

data class DetectedRegion(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float = 1f,
)

object OcrPageAssembler {
    fun assemble(
        width: Float,
        height: Float,
        regions: List<DetectedRegion>,
    ): OcrPage {
        val lines = regions
            .filter { it.text.isNotBlank() }
            .map { region ->
                OcrLine(
                    text = region.text.trim(),
                    box = BoundingBox(
                        left = minOf(region.left, region.right),
                        top = minOf(region.top, region.bottom),
                        right = maxOf(region.left, region.right),
                        bottom = maxOf(region.top, region.bottom),
                    ),
                    confidence = region.confidence.coerceIn(0f, 1f),
                )
            }
            .sortedWith(compareBy({ it.box.top }, { it.box.left }))
        return OcrPage(
            lines = lines,
            width = width.coerceAtLeast(1f),
            height = height.coerceAtLeast(1f),
        )
    }

    fun boxFromPoints(points: List<Pair<Float, Float>>): BoundingBox? {
        if (points.isEmpty()) return null
        return BoundingBox(
            left = points.minOf { it.first },
            top = points.minOf { it.second },
            right = points.maxOf { it.first },
            bottom = points.maxOf { it.second },
        )
    }
}
