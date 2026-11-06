package com.receiptledger.engine.export

object ReceiptImageEnhance {
    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    fun luminance(argb: Int): Int {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return ((r * 299 + g * 587 + b * 114) / 1000).coerceIn(0, 255)
    }

    fun otsuThreshold(histogram: IntArray, total: Int): Int {
        if (total <= 0) return 127
        var sum = 0L
        for (level in histogram.indices) {
            sum += level.toLong() * histogram[level]
        }
        var sumB = 0L
        var weightB = 0
        var bestVariance = -1.0
        var threshold = 127
        for (level in histogram.indices) {
            weightB += histogram[level]
            if (weightB == 0) continue
            val weightF = total - weightB
            if (weightF == 0) break
            sumB += level.toLong() * histogram[level]
            val meanB = sumB.toDouble() / weightB
            val meanF = (sum - sumB).toDouble() / weightF
            val diff = meanB - meanF
            val variance = weightB.toDouble() * weightF.toDouble() * diff * diff
            if (variance > bestVariance) {
                bestVariance = variance
                threshold = level
            }
        }
        return threshold
    }

    fun toBlackAndWhite(pixels: IntArray): IntArray {
        toBlackAndWhiteInPlace(pixels)
        return pixels
    }

    fun toBlackAndWhiteInPlace(pixels: IntArray) {
        if (pixels.isEmpty()) return
        val histogram = IntArray(256)
        for (pixel in pixels) {
            histogram[luminance(pixel)] += 1
        }
        val threshold = otsuThreshold(histogram, pixels.size)
        for (index in pixels.indices) {
            pixels[index] = if (luminance(pixels[index]) <= threshold) BLACK else WHITE
        }
    }
}
