package com.receiptledger.engine.export

import com.receiptledger.engine.model.Purpose
import com.receiptledger.engine.tax.TaxCategories

data class ReceiptCsvRow(
    val date: String,
    val merchant: String,
    val total: String,
    val subtotal: String,
    val tax: String,
    val taxKinds: String,
    val currency: String,
    val purpose: Purpose,
    val categoryId: String,
    val categoryLabel: String = "",
    val gstHstNumber: String,
    val paymentMethod: String,
    val receiptNumber: String = "",
    val lineItems: String = "",
    val imagePath: String,
    val dateAdded: String = "",
)

object ReceiptCsv {
    val header = listOf(
        "date",
        "merchant",
        "total",
        "subtotal",
        "tax",
        "tax_kinds",
        "currency",
        "purpose",
        "category",
        "form_line",
        "meals_50_percent",
        "gst_hst_number",
        "payment_method",
        "receipt_number",
        "line_items",
        "image_path",
        "date_added",
    ).joinToString(",")

    fun row(item: ReceiptCsvRow): String {
        val category = TaxCategories.find(item.categoryId)
        return listOf(
            item.date,
            item.merchant,
            item.total,
            item.subtotal,
            item.tax,
            item.taxKinds,
            item.currency,
            item.purpose.name,
            category?.label ?: item.categoryLabel.ifBlank { item.categoryId },
            category?.formLine.orEmpty(),
            if (category?.mealsFiftyPercent == true) "yes" else "no",
            item.gstHstNumber,
            item.paymentMethod,
            item.receiptNumber,
            item.lineItems,
            item.imagePath,
            item.dateAdded,
        ).joinToString(",") { csvEscape(it) }
    }

    fun document(rows: List<ReceiptCsvRow>): String =
        buildString {
            appendLine(header)
            rows.forEach { appendLine(row(it)) }
        }

    private fun csvEscape(value: String): String {
        val v = value.replace("\"", "\"\"")
        return if (v.contains(',') || v.contains('"') || v.contains('\n')) "\"$v\"" else v
    }
}
