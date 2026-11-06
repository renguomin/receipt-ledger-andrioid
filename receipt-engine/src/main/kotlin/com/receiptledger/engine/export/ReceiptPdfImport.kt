package com.receiptledger.engine.export

import com.receiptledger.engine.model.BoundingBox
import com.receiptledger.engine.model.Country
import com.receiptledger.engine.model.Field
import com.receiptledger.engine.model.FieldSource
import com.receiptledger.engine.model.LineItem
import com.receiptledger.engine.model.LineItemQuantity
import com.receiptledger.engine.model.OcrLine
import com.receiptledger.engine.model.OcrPage
import com.receiptledger.engine.model.ParsedReceipt
import com.receiptledger.engine.model.Purpose
import com.receiptledger.engine.model.TaxAmount
import com.receiptledger.engine.model.TaxKind
import com.receiptledger.engine.parse.GeometryReceiptParser

data class ImportedCategory(
    val id: String? = null,
    val newLabel: String? = null,
)

object ReceiptPdfImport {
    fun usesPdfFields(fromLedgerExport: Boolean): Boolean = fromLedgerExport

    fun scansEachPhoto(fromLedgerExport: Boolean, photoCount: Int): Boolean =
        !fromLedgerExport && photoCount > 0

    fun hasStructuredFields(receipt: ImportedPdfReceipt): Boolean =
        listOf(
            receipt.date,
            receipt.merchant,
            receipt.total,
            receipt.subtotal,
            receipt.tax,
            receipt.payment,
            receipt.gstHstNumber,
            receipt.receiptNumber,
            receipt.category,
        ).any { !ReceiptPdfLayout.isMissing(it) } ||
            receipt.lineItems.isNotEmpty()

    fun hasUsefulParsedFields(parsed: ParsedReceipt): Boolean =
        !parsed.merchant.value.isNullOrBlank() ||
            !parsed.date.value.isNullOrBlank() ||
            parsed.total.value != null

    fun photoBytes(receipt: ImportedPdfReceipt): ByteArray? =
        receipt.jpegBytes?.takeIf { it.isNotEmpty() && PdfExportLogic.isImage(it) }

    fun toParsed(receipt: ImportedPdfReceipt, country: Country = Country.CA): ParsedReceipt {
        if (!hasStructuredFields(receipt) && receipt.rawText.isNotBlank()) {
            return fromRawText(receipt.rawText, country)
        }
        val (total, currency) = parseMoney(receipt.total)
        val tax = parseAmount(receipt.tax)
        return ParsedReceipt(
            merchant = field(missingToNull(receipt.merchant)),
            date = field(missingToNull(receipt.date)),
            currency = field(currency),
            subtotal = field(parseAmount(receipt.subtotal)),
            taxes = if (tax != null) {
                listOf(TaxAmount(TaxKind.UNKNOWN, tax, confidence = 1f, source = FieldSource.USER))
            } else {
                emptyList()
            },
            total = field(total),
            paymentMethod = field(missingToNull(receipt.payment)),
            gstHstNumber = field(missingToNull(receipt.gstHstNumber)),
            receiptNumber = field(missingToNull(receipt.receiptNumber)),
            lineItems = LineItemQuantity.resolveAll(
                receipt.lineItems.mapNotNull { line ->
                    val name = missingToNull(line.name) ?: return@mapNotNull null
                    LineItem(
                        name = name,
                        quantity = parseAmount(line.quantity),
                        amount = parseAmount(line.amount),
                    )
                },
            ),
            suggestedCategoryId = Field.missing(),
            rawText = receipt.rawText,
        )
    }

    fun fromRawText(text: String, country: Country = Country.CA): ParsedReceipt {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ParsedReceipt(rawText = text)
        val page = OcrPage(
            lines = lines.mapIndexed { index, line ->
                OcrLine(
                    text = line,
                    box = BoundingBox(0f, index * 22f, 600f, index * 22f + 18f),
                )
            },
            width = 600f,
            height = (lines.size * 22f).coerceAtLeast(1f),
        )
        return GeometryReceiptParser(country = country).parse(page)
    }

    fun purposeOf(raw: String?): Purpose =
        Purpose.entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
            ?: Purpose.PERSONAL

    fun resolveCategory(
        label: String?,
        catalog: List<Pair<String, String>>,
    ): ImportedCategory {
        val name = missingToNull(label) ?: return ImportedCategory()
        val id = catalog.firstOrNull { ReceiptPdfLayout.labelsMatch(it.second, name) }?.first
        return if (id != null) ImportedCategory(id = id) else ImportedCategory(newLabel = name)
    }

    fun parseMoney(raw: String?): Pair<Double?, String?> {
        val text = missingToNull(raw) ?: return null to null
        val parts = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val amount = parts.firstOrNull()?.toDoubleOrNull()
        val currency = parts.getOrNull(1)?.takeIf { part -> part.any { it.isLetter() } }
        return amount to currency
    }

    fun parseAmount(raw: String?): Double? = missingToNull(raw)?.toDoubleOrNull()

    private fun missingToNull(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        return trimmed.takeIf { it.isNotEmpty() && !ReceiptPdfLayout.isMissing(it) }
    }

    private fun <T> field(value: T?): Field<T> =
        if (value == null) Field.missing() else Field(value, 1f, FieldSource.USER)
}
