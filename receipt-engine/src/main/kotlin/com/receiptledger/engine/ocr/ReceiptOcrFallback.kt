package com.receiptledger.engine.ocr

import com.receiptledger.engine.model.OcrPage

object ReceiptOcrFallback {
    fun shouldUseFallback(primary: OcrPage?, error: Throwable?): Boolean =
        error != null || primary == null || primary.lines.isEmpty()
}
