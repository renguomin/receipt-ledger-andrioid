package com.receiptledger.engine.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class LabeledValue(
    val label: String,
    val value: String,
)

object ReceiptRecordLogic {
    const val MISSING = "—"

    val promptFields = listOf(
        "merchant",
        "date",
        "currency",
        "subtotal",
        "taxes",
        "total",
        "paymentMethod",
        "gstHstNumber",
        "receiptNumber",
        "lineItems",
        "suggestedCategoryId",
    )

    fun compactLineItems(items: List<LineItem>): List<LineItem> =
        items.map { it.copy(name = it.name.trim()) }.filter { it.name.isNotEmpty() }

    fun addLineItem(items: List<LineItem>): List<LineItem> =
        items + LineItem(name = "", quantity = LineItemQuantity.DEFAULT, amount = null)

    fun removeLineItem(items: List<LineItem>, index: Int): List<LineItem> =
        items.filterIndexed { i, _ -> i != index }

    fun updateLineItem(
        items: List<LineItem>,
        index: Int,
        name: String? = null,
        quantityRaw: String? = null,
        amountRaw: String? = null,
    ): List<LineItem> {
        if (index !in items.indices) return items
        val current = items[index]
        return items.toMutableList().also { list ->
            list[index] = current.copy(
                name = name ?: current.name,
                quantity = if (quantityRaw != null) parseOptionalNumber(quantityRaw) else current.quantity,
                amount = if (amountRaw != null) parseOptionalNumber(amountRaw) else current.amount,
            )
        }
    }

    fun parseOptionalNumber(raw: String): Double? =
        raw.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    fun formatAmount(value: Double): String = String.format(Locale.US, "%.2f", value)

    fun formatQuantity(value: Double): String =
        if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%s", value)
        }

    fun formatLineItem(item: LineItem): String {
        val name = item.name.trim()
        val qty = item.quantity?.let { " x${formatQuantity(it)}" }.orEmpty()
        val amount = item.amount?.let { " @ ${formatAmount(it)}" }.orEmpty()
        return "$name$qty$amount"
    }

    fun formatLineItems(items: List<LineItem>, separator: String = "; "): String =
        compactLineItems(items).joinToString(separator) { formatLineItem(it) }

    fun formatTax(tax: TaxAmount): String {
        val rate = tax.rate?.let { " (${formatPercent(it)})" }.orEmpty()
        return "${tax.kind.name} ${formatAmount(tax.amount)}$rate"
    }

    fun formatPercent(rate: Double): String {
        val pct = rate * 100.0
        return if (pct == pct.toLong().toDouble()) {
            "${pct.toLong()}%"
        } else {
            String.format(Locale.US, "%s%%", pct)
        }
    }

    fun formatMoney(amount: Double?, currency: String?): String {
        if (amount == null) return MISSING
        val number = formatAmount(amount)
        val cur = currency?.trim().orEmpty()
        return if (cur.isEmpty()) number else "$number $cur"
    }

    fun display(value: String?): String =
        value?.trim()?.takeIf { it.isNotEmpty() } ?: MISSING

    fun taxDisplay(taxes: List<TaxAmount>, taxTotal: Double): String = when {
        taxes.isNotEmpty() -> taxes.joinToString(" · ") { formatTax(it) }
        taxTotal > 0 -> formatAmount(taxTotal)
        else -> MISSING
    }

    fun itemsDisplay(items: List<LineItem>): String =
        formatLineItems(items, separator = "\n").ifBlank { MISSING }

    /** The date printed on the receipt, as against the day it joined the ledger. */
    const val PURCHASED_DATE_LABEL = "Purchased date"

    /**
     * When the receipt joined the ledger. The app stamps it once and nobody can edit it, which is
     * what makes it worth keeping: the receipt's own date can be wrong or missing, this cannot.
     */
    const val ADDED_DATE_LABEL = "Added date"

    /**
     * The same two names as a phrase, for lines with no room for a label beside a value. Every
     * date the customer sees carries one form or the other, so none of them stands unexplained.
     */
    const val PURCHASED_ON_PREFIX = "Purchased on"
    const val ADDED_ON_PREFIX = "Added on"

    fun purchasedNote(value: String): String = "$PURCHASED_ON_PREFIX $value"

    fun addedNote(value: String): String = "$ADDED_ON_PREFIX $value"

    /** Reads as a moment rather than a bare date, since two receipts can join on the same day. */
    fun formatDateAdded(
        addedAtMs: Long,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        val format = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", locale)
        format.timeZone = timeZone
        return format.format(Date(addedAtMs))
    }

    /** Sortable and unambiguous, for a column a spreadsheet will read. */
    fun dateAddedForExport(
        addedAtMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        format.timeZone = timeZone
        return format.format(Date(addedAtMs))
    }

    fun detailFields(
        parsed: ParsedReceipt,
        purpose: String,
        category: String?,
        dateAdded: String? = null,
    ): List<LabeledValue> = listOfNotNull(
        LabeledValue(PURCHASED_DATE_LABEL, display(parsed.date.value)),
        LabeledValue("Receipt number", display(parsed.receiptNumber.value)),
        LabeledValue("Total", formatMoney(parsed.total.value, parsed.currency.value)),
        LabeledValue("Subtotal", parsed.subtotal.value?.let { formatAmount(it) } ?: MISSING),
        LabeledValue("Tax", taxDisplay(parsed.taxes, parsed.taxTotal)),
        LabeledValue("Currency", display(parsed.currency.value)),
        LabeledValue("GST/HST number", display(parsed.gstHstNumber.value)),
        LabeledValue("Payment", display(parsed.paymentMethod.value)),
        LabeledValue("Items", itemsDisplay(parsed.lineItems)),
        LabeledValue("Purpose", display(purpose.lowercase(Locale.US))),
        LabeledValue("Category", display(category)),
        dateAdded?.let { LabeledValue(ADDED_DATE_LABEL, display(it)) },
    )
}
