package com.receiptledger.app.ocr

import android.content.Context
import android.net.Uri
import android.util.Log
import com.receiptledger.engine.model.OcrPage
import com.receiptledger.engine.ocr.ReceiptOcrFallback

class FallbackReceiptOcr(
    private val primary: ReceiptOcr,
    private val fallback: ReceiptOcr,
) : ReceiptOcr {
    override suspend fun read(context: Context, imageUri: Uri): OcrPage {
        return try {
            val page = primary.read(context, imageUri)
            if (ReceiptOcrFallback.shouldUseFallback(page, null)) {
                Log.w(TAG, "Primary OCR returned no lines; using fallback")
                fallback.read(context, imageUri)
            } else {
                page
            }
        } catch (error: Exception) {
            Log.w(TAG, "Primary OCR failed; using fallback", error)
            fallback.read(context, imageUri)
        }
    }

    companion object {
        private const val TAG = "ReceiptOcr"
    }
}
