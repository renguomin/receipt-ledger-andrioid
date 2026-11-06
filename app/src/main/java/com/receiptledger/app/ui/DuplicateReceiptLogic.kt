package com.receiptledger.app.ui

import com.receiptledger.app.data.ReceiptEntity
import com.receiptledger.engine.model.LineItem
import com.receiptledger.engine.model.ReceiptRecordLogic
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

data class DuplicateWarning(
    val body: String,
)

object DuplicateReceiptLogic {
    const val TITLE = "This might already be saved"
    const val SKIP = "Skip"
    const val SAVE_AGAIN = "Save anyway"
    const val NOT_LISTED = "Not listed"
    const val NONE_LISTED = "None listed"

    /** Totals are stored as doubles, so they are compared to the cent rather than exactly. */
    private const val CENT = 0.005

    /**
     * Matching is on store name, date and total, not receipt number: numbers are often unreadable
     * or absent, which left most receipts unchecked. These three are almost always parsed, at the
     * cost of flagging genuinely separate purchases that agree on all three — two $50 fills at the
     * same station on the same day. That is why this is a warning the customer can save through.
     */
    fun shouldCheck(isNewReceipt: Boolean, dateIso: String?, total: Double?): Boolean =
        isNewReceipt && hasComparableKey(dateIso, total)

    fun pickMatch(
        merchant: String?,
        dateIso: String?,
        total: Double?,
        existing: List<ReceiptEntity>,
        excludingId: String? = null,
    ): ReceiptEntity? {
        if (!hasComparableKey(dateIso, total)) return null
        val date = dateIso.orEmpty().trim()
        val store = normalizeMerchant(merchant)
        return existing
            .filter { it.id != excludingId }
            .filter {
                it.dateIso?.trim() == date &&
                    sameTotal(it.total, total) &&
                    normalizeMerchant(it.merchant) == store
            }
            .maxByOrNull { it.createdAt }
    }

    fun sameTotal(left: Double?, right: Double?): Boolean {
        if (left == null || right == null) return false
        return abs(left - right) < CENT
    }

    /**
     * The same store reads back with different casing and spacing from one scan to the next, so
     * names are compared loosely. A receipt with no store name still matches other nameless ones,
     * which keeps re-imports of hard-to-read receipts checked.
     */
    fun normalizeMerchant(value: String?): String =
        value?.trim()?.lowercase()?.replace(whitespaceRun, " ").orEmpty()

    private val whitespaceRun = Regex("""\s+""")

    private fun hasComparableKey(dateIso: String?, total: Double?): Boolean =
        !dateIso.isNullOrBlank() && total != null && total > 0.0

    fun warning(
        merchant: String?,
        dateIso: String?,
        total: Double?,
        currency: String?,
        lineItems: List<LineItem>,
        createdAtMs: Long,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): DuplicateWarning = DuplicateWarning(
        body = body(
            merchant = merchant,
            dateIso = dateIso,
            total = total?.let { ReceiptRecordLogic.formatMoney(it, currency) },
            items = ReceiptRecordLogic.formatLineItems(ReceiptRecordLogic.compactLineItems(lineItems)),
            addedAt = formatAddedAt(createdAtMs, locale, timeZone),
        ),
    )

    fun body(
        merchant: String?,
        dateIso: String?,
        total: String?,
        items: String?,
        addedAt: String,
    ): String = buildString {
        appendLine("A receipt with the same store, date and total is already in your list.")
        appendLine()
        appendLine("Store: ${listed(merchant)}")
        appendLine("${ReceiptRecordLogic.PURCHASED_DATE_LABEL}: ${listed(dateIso)}")
        appendLine("Total: ${listed(total)}")
        appendLine("Items: ${items?.trim()?.takeIf { it.isNotEmpty() } ?: NONE_LISTED}")
        appendLine("${ReceiptRecordLogic.ADDED_DATE_LABEL}: $addedAt")
        appendLine()
        append("Skip this copy, or save it anyway if it is a separate purchase.")
    }

    fun formatAddedAt(
        createdAtMs: Long,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String = ReceiptRecordLogic.formatDateAdded(createdAtMs, locale, timeZone)

    private fun listed(value: String?): String =
        value?.trim()?.takeIf { it.isNotEmpty() } ?: NOT_LISTED

    fun afterReview(
        savedId: String?,
        hasNextImport: Boolean,
        importBatchSize: Int,
    ): AfterReviewAction = when {
        hasNextImport -> AfterReviewAction.CONTINUE_IMPORT
        importBatchSize > 0 -> AfterReviewAction.FINISH_IMPORT
        savedId != null -> AfterReviewAction.OPEN_SAVED
        else -> AfterReviewAction.RETURN_HOME
    }
}

enum class AfterReviewAction {
    CONTINUE_IMPORT,
    FINISH_IMPORT,
    OPEN_SAVED,
    RETURN_HOME,
}
