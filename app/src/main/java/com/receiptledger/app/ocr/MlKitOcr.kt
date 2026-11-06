package com.receiptledger.app.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.receiptledger.engine.model.BoundingBox
import com.receiptledger.engine.model.OcrLine
import com.receiptledger.engine.model.OcrPage
import kotlinx.coroutines.tasks.await

class MlKitOcr : ReceiptOcr {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun read(context: Context, imageUri: Uri): OcrPage {
        val image = InputImage.fromFilePath(context, imageUri)
        val result = recognizer.process(image).await()
        val width = (image.width.takeIf { it > 0 } ?: 1).toFloat()
        val height = (image.height.takeIf { it > 0 } ?: 1).toFloat()
        val lines = result.textBlocks.flatMap { block ->
            block.lines.map { line ->
                val box = line.boundingBox
                OcrLine(
                    text = line.text,
                    box = BoundingBox(
                        left = box?.left?.toFloat() ?: 0f,
                        top = box?.top?.toFloat() ?: 0f,
                        right = box?.right?.toFloat() ?: width,
                        bottom = box?.bottom?.toFloat() ?: 0f,
                    ),
                    confidence = 1f,
                )
            }
        }
        return OcrPage(lines = lines, width = width, height = height)
    }
}
