package com.receiptledger.engine.export

import kotlin.math.min

class ReceiptPdfWriter {
    private val pdf = SimplePdf()
    private var pageNumber = 1
    private var y = TOP

    fun newPage() {
        drawFooter()
        pdf.newPage()
        pageNumber += 1
        y = TOP
    }

    fun gap(size: Float) {
        y += size
    }

    fun drawText(
        text: String,
        size: Float,
        bold: Boolean = false,
        gray: Float = 0f,
    ) {
        val lines = PdfFonts.wrap(text, size, CONTENT_WIDTH)
        ensure(lines.size * (size + 4f) + 2f)
        lines.forEach { line ->
            y += size + 4f
            pdf.text(LEFT, y, size, line, bold = bold, gray = gray)
        }
    }

    fun drawReceiptTable(item: ReceiptPdfItem) {
        val fieldHeight = item.fields.sumOf { field ->
            rowHeight(field.label, field.value).toDouble()
        }.toFloat()
        val lineHeader = if (item.lineItems.isEmpty()) 0f else 22f + 18f
        val lineBody = item.lineItems.sumOf { line ->
            lineItemRowHeight(line).toDouble()
        }.toFloat()
        ensure(28f + fieldHeight + lineHeader + lineBody)
        y += 14f
        pdf.text(LEFT, y, SUBHEADING_SIZE, item.heading, bold = true)
        y += 8f
        item.fields.forEach { field ->
            drawFieldRow(field)
        }
        if (item.lineItems.isNotEmpty()) {
            y += 10f
            pdf.text(LEFT, y, BODY_SIZE, ReceiptPdfLayout.LINE_ITEMS_HEADING, bold = true)
            y += 8f
            drawLineItemHeader()
            item.lineItems.forEach { drawLineItemRow(it) }
        }
    }

    fun drawAppendixImage(item: ReceiptPdfItem, image: PackedPdfImage?) {
        drawText(item.caption, SUBHEADING_SIZE, bold = true)
        gap(10f)
        val maxWidth = CONTENT_WIDTH
        val maxHeight = (BOTTOM - y).coerceAtLeast(120f)
        if (image == null) {
            drawText("Photo not found", BODY_SIZE, gray = 0.4f)
            return
        }
        val fitted = fit(image.width, image.height, maxWidth, maxHeight)
        if (y + fitted.second > BOTTOM) {
            newPage()
            pdf.text(LEFT, y + SUBHEADING_SIZE + 4f, SUBHEADING_SIZE, item.caption, bold = true)
            y += SUBHEADING_SIZE + 14f
        }
        pdf.image(LEFT, y, fitted.first, fitted.second, image)
        y += fitted.second + 8f
    }

    fun toByteArray(): ByteArray {
        drawFooter()
        return pdf.toByteArray()
    }

    private fun ensure(needed: Float) {
        if (y + needed > BOTTOM) newPage()
    }

    private fun drawFieldRow(field: ReceiptPdfField) {
        val labelWidth = 132f
        val valueWidth = CONTENT_WIDTH - labelWidth
        val labelLines = PdfFonts.wrap(field.label, BODY_SIZE, labelWidth - 12f)
        val valueLines = PdfFonts.wrap(field.value, BODY_SIZE, valueWidth - 12f)
        val height = (maxOf(labelLines.size, valueLines.size) * LINE_STEP + 10f).coerceAtLeast(22f)
        ensure(height)
        val topY = y
        val bottomY = y + height
        pdf.strokeRect(LEFT, topY, RIGHT, bottomY)
        pdf.verticalLine(LEFT + labelWidth, topY, bottomY)
        var textY = topY + 15f
        labelLines.forEach { line ->
            pdf.text(LEFT + 6f, textY, BODY_SIZE, line, bold = true)
            textY += LINE_STEP
        }
        textY = topY + 15f
        valueLines.forEach { line ->
            pdf.text(LEFT + labelWidth + 6f, textY, BODY_SIZE, line)
            textY += LINE_STEP
        }
        y = bottomY
    }

    private fun drawLineItemHeader() {
        val height = 18f
        ensure(height)
        pdf.fillRect(LEFT, y, RIGHT, y + height)
        pdf.strokeRect(LEFT, y, RIGHT, y + height)
        val qtyX = RIGHT - 120f
        pdf.verticalLine(qtyX, y, y + height)
        pdf.verticalLine(RIGHT - 64f, y, y + height)
        pdf.text(LEFT + 6f, y + 13f, BODY_SIZE, "Item", bold = true)
        pdf.text(RIGHT - 72f, y + 13f, BODY_SIZE, "Qty", bold = true, align = SimplePdf.Align.RIGHT)
        pdf.text(RIGHT - 16f, y + 13f, BODY_SIZE, "Amount", bold = true, align = SimplePdf.Align.RIGHT)
        y += height
    }

    private fun drawLineItemRow(line: ReceiptPdfLine) {
        val height = lineItemRowHeight(line)
        ensure(height)
        val qtyX = RIGHT - 120f
        pdf.strokeRect(LEFT, y, RIGHT, y + height)
        pdf.verticalLine(qtyX, y, y + height)
        pdf.verticalLine(RIGHT - 64f, y, y + height)
        val nameLines = PdfFonts.wrap(line.name, BODY_SIZE, qtyX - LEFT - 12f)
        var textY = y + 13f
        nameLines.forEach { name ->
            pdf.text(LEFT + 6f, textY, BODY_SIZE, name)
            textY += LINE_STEP
        }
        pdf.text(RIGHT - 72f, y + 13f, BODY_SIZE, line.quantity, align = SimplePdf.Align.RIGHT)
        pdf.text(RIGHT - 16f, y + 13f, BODY_SIZE, line.amount, align = SimplePdf.Align.RIGHT)
        y += height
    }

    private fun rowHeight(label: String, value: String): Float {
        val labelLines = PdfFonts.wrap(label, BODY_SIZE, 120f).size
        val valueLines = PdfFonts.wrap(value, BODY_SIZE, CONTENT_WIDTH - 132f - 12f).size
        return (maxOf(labelLines, valueLines) * LINE_STEP + 10f).coerceAtLeast(22f)
    }

    private fun lineItemRowHeight(line: ReceiptPdfLine): Float {
        val nameLines = PdfFonts.wrap(line.name, BODY_SIZE, CONTENT_WIDTH - 120f - 12f).size
        return (nameLines * LINE_STEP + 6f).coerceAtLeast(18f)
    }

    private fun drawFooter() {
        pdf.text(306f, 776f, 9f, pageNumber.toString(), gray = 0.45f, align = SimplePdf.Align.CENTER)
    }

    companion object {
        const val HEADING_SIZE = 16f
        const val SUBHEADING_SIZE = 13f
        const val BODY_SIZE = 10f
        const val LINE_STEP = 14f
        const val LEFT = 40f
        const val RIGHT = 572f
        const val TOP = 52f
        const val BOTTOM = 752f
        const val CONTENT_WIDTH = RIGHT - LEFT
        const val PHOTO_MISSING = "Photo not found"
    }
}

object ReceiptPdfDocuments {
    fun write(spec: ReceiptPdfSpec, images: List<PackedPdfImage?>): ByteArray {
        val writer = ReceiptPdfWriter()
        writer.drawText(spec.title, ReceiptPdfWriter.HEADING_SIZE, bold = true)
        writer.gap(8f)
        writer.drawText(spec.disclaimer, ReceiptPdfWriter.BODY_SIZE)
        writer.gap(18f)
        spec.items.forEach { item ->
            writer.drawReceiptTable(item)
            writer.gap(18f)
        }
        spec.items.forEachIndexed { index, item ->
            writer.newPage()
            if (index == 0) {
                writer.drawText(spec.appendixTitle, ReceiptPdfWriter.HEADING_SIZE, bold = true)
                writer.gap(16f)
            }
            val image = images.getOrNull(index)
            writer.drawAppendixImage(item, image)
        }
        return writer.toByteArray()
    }
}

private fun fit(width: Int, height: Int, maxWidth: Float, maxHeight: Float): Pair<Float, Float> {
    val scale = min(maxWidth / width.toFloat(), maxHeight / height.toFloat())
    return width * scale to height * scale
}
