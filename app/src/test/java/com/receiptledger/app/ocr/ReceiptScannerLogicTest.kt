package com.receiptledger.app.ocr

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReceiptScannerLogicTest {
    @Test
    fun autoCaptureStaysOn() {
        assertTrue(ReceiptScannerLogic.AUTO_CAPTURE)
    }
}
