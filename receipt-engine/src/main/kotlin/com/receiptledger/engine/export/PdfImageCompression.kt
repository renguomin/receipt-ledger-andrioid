package com.receiptledger.engine.export

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

object PdfImageCompression {
    fun rowBytes(width: Int): Int = (width + 7) / 8

    fun packWhiteAsOne(pixels: IntArray, width: Int, height: Int): ByteArray {
        require(pixels.size >= width * height)
        val stride = rowBytes(width)
        val packed = ByteArray(stride * height)
        var index = 0
        for (y in 0 until height) {
            val row = y * stride
            for (x in 0 until width) {
                if (pixels[index++] != ReceiptImageEnhance.BLACK) {
                    packed[row + x / 8] =
                        (packed[row + x / 8].toInt() or (0x80 shr (x % 8))).toByte()
                }
            }
        }
        return packed
    }

    fun flate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream(data.size.coerceAtMost(64 * 1024))
        val buffer = ByteArray(8192)
        while (!deflater.finished()) {
            val n = deflater.deflate(buffer)
            if (n > 0) out.write(buffer, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }
}
