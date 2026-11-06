package com.receiptledger.app.ocr

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReceiptOcrLogicTest {
    @Test
    fun angleClassifierStaysOnSoUpsideDownTextCanBeRead() {
        assertTrue(ReceiptOcrLogic.USE_ANGLE_CLASSIFIER)
    }
}
