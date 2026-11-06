package com.receiptledger.app.export

import android.graphics.BitmapFactory
import com.receiptledger.app.data.ReceiptEntity
import com.receiptledger.engine.export.PackedPdfImage
import com.receiptledger.engine.export.PdfExportLogic
import com.receiptledger.engine.export.PdfImageEncoding
import com.receiptledger.engine.export.ReceiptPdfLayout
import com.receiptledger.engine.export.ReceiptPdfRowInput
import com.receiptledger.engine.export.ReceiptPdfSpec
import com.receiptledger.engine.export.ReceiptPdfWriter
import com.receiptledger.engine.model.ReceiptRecordLogic
import com.receiptledger.engine.tax.TaxCategories
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import java.io.File
import kotlin.coroutines.coroutineContext

object ReceiptPdf {
    suspend fun write(
        rows: List<ReceiptEntity>,
        title: String,
        output: File,
        categoryLabel: (String?) -> String = { id ->
            TaxCategories.find(id.orEmpty())?.label.orEmpty()
        },
        onProgress: (completed: Int, total: Int, message: String) -> Unit = { _, _, _ -> },
    ) {
        val spec = ReceiptPdfLayout.build(title, inputsFrom(rows, categoryLabel))
        render(spec, output, onProgress)
    }

    fun inputsFrom(
        rows: List<ReceiptEntity>,
        categoryLabel: (String?) -> String,
    ): List<ReceiptPdfRowInput> = rows.map { row ->
        ReceiptPdfRowInput(
            date = row.dateIso,
            merchant = row.merchant,
            total = row.total,
            subtotal = row.subtotal,
            tax = row.taxTotal,
            currency = row.currency,
            purpose = row.purpose,
            category = categoryLabel(row.categoryId),
            gstHstNumber = row.gstHstNumber,
            paymentMethod = row.paymentMethod,
            receiptNumber = row.receiptNumber,
            lineItemsJson = row.lineItemsJson,
            imagePath = row.imagePath,
            dateAdded = ReceiptRecordLogic.formatDateAdded(row.createdAt),
        )
    }

    private suspend fun render(
        spec: ReceiptPdfSpec,
        output: File,
        onProgress: (completed: Int, total: Int, message: String) -> Unit,
    ) {
        val total = PdfExportLogic.workUnits(spec.items.size)
        var completed = 0
        suspend fun step(message: String) {
            coroutineContext.ensureActive()
            completed += 1
            onProgress(completed, total, message)
            yield()
        }

        val images = spec.items.map { compressedImage(it.imagePath) }
        val writer = ReceiptPdfWriter()
        writer.drawText(spec.title, ReceiptPdfWriter.HEADING_SIZE, bold = true)
        writer.gap(8f)
        writer.drawText(spec.disclaimer, ReceiptPdfWriter.BODY_SIZE)
        writer.gap(18f)
        spec.items.forEach { item ->
            writer.drawReceiptTable(item)
            writer.gap(18f)
            step(PdfExportLogic.tableMessage(item.number, spec.items.size))
        }
        spec.items.forEachIndexed { index, item ->
            writer.newPage()
            if (index == 0) {
                writer.drawText(spec.appendixTitle, ReceiptPdfWriter.HEADING_SIZE, bold = true)
                writer.gap(16f)
            }
            writer.drawAppendixImage(item, images.getOrNull(index))
            step(PdfExportLogic.imageMessage(item.number, spec.items.size))
        }
        onProgress(completed, total, PdfExportLogic.writingMessage())
        output.parentFile?.mkdirs()
        output.writeBytes(writer.toByteArray())
        step(PdfExportLogic.writingMessage())
    }
}

private fun compressedImage(path: String): PackedPdfImage? {
    val file = File(path)
    if (!file.isFile) return null
    val bytes = file.readBytes()
    if (!PdfExportLogic.isJpeg(bytes) || !PdfExportLogic.pdfEmbedsStoredJpeg()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    return PackedPdfImage(
        width = bounds.outWidth,
        height = bounds.outHeight,
        bytes = bytes,
        encoding = PdfImageEncoding.JPEG,
    )
}
