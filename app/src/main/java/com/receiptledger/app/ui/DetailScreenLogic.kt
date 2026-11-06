package com.receiptledger.app.ui

import com.receiptledger.engine.model.LabeledValue
import com.receiptledger.engine.model.Purpose
import com.receiptledger.engine.model.ReceiptRecordLogic
import java.util.Locale

data class DetailSection(
    val title: String,
    val rows: List<LabeledValue>,
)

data class DetailPresentation(
    val total: String,
    /** Both dates carry their verb here, so the pair under the total cannot be read wrongly. */
    val purchasedOn: String?,
    /** One short line rather than a labelled row, since this records the ledger, not the receipt. */
    val addedOn: String?,
    val sections: List<DetailSection>,
)

object DetailScreenLogic {
    const val EMPTY_ITEMS = "None found"
    const val MEALS_NOTE =
        "Meals and entertainment: only half of this amount is usually deductible."

    private val alwaysShown = setOf("Purpose", "Category", "Items")

    fun present(fields: List<LabeledValue>): DetailPresentation {
        val rows = fields.map { polish(it) }.filter { shouldShow(it) }
        val byLabel = rows.associateBy { it.label }
        return DetailPresentation(
            total = byLabel["Total"]?.value ?: ReceiptRecordLogic.MISSING,
            purchasedOn = byLabel[ReceiptRecordLogic.PURCHASED_DATE_LABEL]?.value
                ?.let { ReceiptRecordLogic.purchasedNote(it) },
            addedOn = byLabel[ReceiptRecordLogic.ADDED_DATE_LABEL]?.value
                ?.let { ReceiptRecordLogic.addedNote(it) },
            sections = listOf(
                DetailSection("Details", take(byLabel, "Receipt number", "Payment", "GST/HST number")),
                DetailSection("Amounts", take(byLabel, "Subtotal", "Tax", "Currency")),
                DetailSection("Items", take(byLabel, "Items")),
                DetailSection("This purchase is", take(byLabel, "Purpose", "Category")),
            ).filter { it.rows.isNotEmpty() },
        )
    }

    fun itemLines(value: String): List<String> =
        value.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun purposeLabel(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == ReceiptRecordLogic.MISSING) return ReceiptRecordLogic.MISSING
        val key = trimmed.uppercase(Locale.US)
        return runCatching { Purpose.valueOf(key) }.getOrNull()
            ?.let { ReviewScreenLogic.purposeLabel(it) }
            ?: trimmed.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
    }

    private fun polish(row: LabeledValue): LabeledValue = when (row.label) {
        "Purpose" -> row.copy(value = purposeLabel(row.value))
        "Items" -> if (row.value == ReceiptRecordLogic.MISSING) {
            row.copy(value = EMPTY_ITEMS)
        } else {
            row
        }
        else -> row
    }

    private fun shouldShow(row: LabeledValue): Boolean =
        row.label in alwaysShown || row.value != ReceiptRecordLogic.MISSING

    private fun take(
        byLabel: Map<String, LabeledValue>,
        vararg labels: String,
    ): List<LabeledValue> = labels.mapNotNull { byLabel[it] }
}
