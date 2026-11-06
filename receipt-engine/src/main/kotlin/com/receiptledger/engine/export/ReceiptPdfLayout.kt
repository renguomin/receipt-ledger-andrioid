package com.receiptledger.engine.export

import com.receiptledger.engine.model.LineItem
import com.receiptledger.engine.model.ReceiptRecordLogic
import kotlinx.serialization.json.Json
import java.util.Locale

data class ReceiptPdfRowInput(
    val date: String?,
    val merchant: String?,
    val total: Double?,
    val subtotal: Double?,
    val tax: Double?,
    val currency: String?,
    val purpose: String,
    val category: String?,
    val gstHstNumber: String?,
    val paymentMethod: String?,
    val receiptNumber: String? = null,
    val lineItemsJson: String = "[]",
    val lineItems: List<LineItem> = emptyList(),
    val imagePath: String,
    val dateAdded: String? = null,
)

data class ReceiptPdfField(
    val label: String,
    val value: String,
)

data class ReceiptPdfLine(
    val name: String,
    val quantity: String,
    val amount: String,
)

data class ReceiptPdfItem(
    val number: Int,
    val appendixRef: String,
    val heading: String,
    val fields: List<ReceiptPdfField>,
    val lineItems: List<ReceiptPdfLine>,
    val imagePath: String,
    val caption: String,
)

data class ReceiptPdfSpec(
    val title: String,
    val disclaimer: String,
    val appendixTitle: String,
    val items: List<ReceiptPdfItem>,
)

object ReceiptPdfLayout {
    const val MISSING = "—"
    const val DISCLAIMER = "Not tax advice. Keep the original photos with your records."
    const val APPENDIX_TITLE = "Appendix A — Receipt images"
    const val LINE_ITEMS_HEADING = "Line items"

    private val json = Json { ignoreUnknownKeys = true }

    fun appendixRef(number: Int): String = "A-$number"

    fun display(value: String?): String =
        value?.trim()?.takeIf { it.isNotEmpty() } ?: MISSING

    fun isMissing(value: String?): Boolean {
        val trimmed = value?.trim().orEmpty()
        return trimmed.isEmpty() || trimmed == MISSING || trimmed == "?" || trimmed == "-"
    }

    fun formatAmount(value: Double?): String {
        if (value == null) return MISSING
        return String.format(Locale.US, "%.2f", value)
    }

    fun formatQuantity(value: Double?): String {
        if (value == null) return MISSING
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%s", value)
        }
    }

    fun formatMoney(amount: Double?, currency: String?): String {
        if (amount == null) return MISSING
        val number = formatAmount(amount)
        val cur = currency?.trim().orEmpty()
        return if (cur.isEmpty()) number else "$number $cur"
    }

    fun comparableLabel(value: String): String =
        value.trim()
            .replace(DASHES, "-")
            .replace(Regex("\\s+"), " ")

    fun labelsMatch(a: String, b: String): Boolean =
        comparableLabel(a).equals(comparableLabel(b), ignoreCase = true)

    fun decodeLineItems(raw: String): List<LineItem> {
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<LineItem>>(raw) }
            .getOrDefault(emptyList())
            .filter { it.name.isNotBlank() }
    }

    fun caption(appendixRef: String, merchant: String, date: String): String =
        "$appendixRef  $merchant  ·  $date"

    fun build(title: String, rows: List<ReceiptPdfRowInput>): ReceiptPdfSpec {
        val items = rows.mapIndexed { index, row ->
            val number = index + 1
            val ref = appendixRef(number)
            val merchant = display(row.merchant)
            val date = display(row.date)
            val lines = row.lineItems.ifEmpty { decodeLineItems(row.lineItemsJson) }
                .filter { it.name.isNotBlank() }
                .map { line ->
                    ReceiptPdfLine(
                        name = line.name.trim(),
                        quantity = formatQuantity(line.quantity),
                        amount = formatAmount(line.amount),
                    )
                }
            ReceiptPdfItem(
                number = number,
                appendixRef = ref,
                heading = "Receipt $number",
                fields = listOf(
                    ReceiptPdfField(ReceiptRecordLogic.PURCHASED_DATE_LABEL, date),
                    ReceiptPdfField(ReceiptRecordLogic.ADDED_DATE_LABEL, display(row.dateAdded)),
                    ReceiptPdfField("Merchant", merchant),
                    ReceiptPdfField("Receipt number", display(row.receiptNumber)),
                    ReceiptPdfField("Total", formatMoney(row.total, row.currency)),
                    ReceiptPdfField("Subtotal", formatAmount(row.subtotal)),
                    ReceiptPdfField("Tax", formatAmount(row.tax)),
                    ReceiptPdfField("Purpose", display(row.purpose.lowercase(Locale.US))),
                    ReceiptPdfField("Category", display(row.category)),
                    ReceiptPdfField("GST/HST number", display(row.gstHstNumber)),
                    ReceiptPdfField("Payment", display(row.paymentMethod)),
                    ReceiptPdfField("Photo", "Appendix $ref"),
                ),
                lineItems = lines,
                imagePath = row.imagePath,
                caption = caption(ref, merchant, date),
            )
        }
        return ReceiptPdfSpec(
            title = title,
            disclaimer = DISCLAIMER,
            appendixTitle = APPENDIX_TITLE,
            items = items,
        )
    }

    private val DASHES = Regex("[\\u2010-\\u2015\\u2212-]")
}
