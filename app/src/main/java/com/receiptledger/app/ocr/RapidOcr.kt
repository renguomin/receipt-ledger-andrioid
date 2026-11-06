package com.receiptledger.app.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.receiptledger.engine.model.OcrPage
import com.receiptledger.engine.ocr.DetectedRegion
import com.receiptledger.engine.ocr.OcrPageAssembler
import io.github.hzkitty.RapidOCR
import io.github.hzkitty.entity.OcrConfig
import io.github.hzkitty.entity.RecResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.opencv.core.Point

class RapidOcr : ReceiptOcr {
    private val mutex = Mutex()
    private var engine: RapidOCR? = null

    override suspend fun read(context: Context, imageUri: Uri): OcrPage = withContext(Dispatchers.Default) {
        val app = context.applicationContext
        val bitmap = app.contentResolver.openInputStream(imageUri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: error("Could not decode image for OCR")
        try {
            val result = mutex.withLock {
                engine(app).run(bitmap)
            }
            val regions = result.recRes.orEmpty().mapNotNull { row ->
                val rec = row as? RecResult ?: return@mapNotNull null
                val box = OcrPageAssembler.boxFromPoints(pointsOf(rec.dtBoxes)) ?: return@mapNotNull null
                DetectedRegion(
                    text = rec.text.orEmpty(),
                    left = box.left,
                    top = box.top,
                    right = box.right,
                    bottom = box.bottom,
                    confidence = rec.confidence,
                )
            }
            OcrPageAssembler.assemble(
                width = bitmap.width.toFloat(),
                height = bitmap.height.toFloat(),
                regions = regions,
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun engine(context: Context): RapidOCR {
        engine?.let { return it }
        val created = RapidOCR.create(context, OcrConfig().also { config ->
            config.global.useCls = ReceiptOcrLogic.USE_ANGLE_CLASSIFIER
        })
        engine = created
        return created
    }

    private fun pointsOf(dtBoxes: Array<Point>?): List<Pair<Float, Float>> =
        dtBoxes.orEmpty().map { point -> point.x.toFloat() to point.y.toFloat() }
}
