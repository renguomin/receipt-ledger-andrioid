package com.receiptledger.engine.ocr

import com.receiptledger.engine.model.OcrLine
import com.receiptledger.engine.model.OcrPage
import com.receiptledger.engine.model.BoundingBox
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReceiptOcrFallbackTest {
    @Test
    fun emptyOrFailedPrimaryUsesFallback() {
        assertTrue(ReceiptOcrFallback.shouldUseFallback(null, null))
        assertTrue(ReceiptOcrFallback.shouldUseFallback(OcrPage(emptyList()), null))
        assertTrue(ReceiptOcrFallback.shouldUseFallback(null, IllegalStateException("onnx")))
        assertFalse(
            ReceiptOcrFallback.shouldUseFallback(
                OcrPage(listOf(OcrLine("BEST", BoundingBox(0f, 0f, 10f, 10f)))),
                null,
            ),
        )
    }
}
