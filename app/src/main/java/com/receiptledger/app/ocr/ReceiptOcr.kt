package com.receiptledger.app.ocr

import android.content.Context
import android.net.Uri
import com.receiptledger.engine.model.OcrPage

interface ReceiptOcr {
    suspend fun read(context: Context, imageUri: Uri): OcrPage
}
