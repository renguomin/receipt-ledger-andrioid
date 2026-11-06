package com.receiptledger.app.ui

import com.receiptledger.app.data.CategoryCatalog
import com.receiptledger.app.data.CategoryChoice
import com.receiptledger.engine.export.ReceiptPdfLayout
import com.receiptledger.engine.model.Purpose
import java.util.Locale

data class ReviewCategorySelection(
    val categoryId: String?,
    val pendingNewLabel: String? = null,
)

object ReviewScreenLogic {
    const val TITLE = "Check the fields"
    const val VERIFY_COPY =
        "Please verify the fields. Fix anything that's off, then save. This is a record for you, not tax advice."
    const val PHOTO_HINT = "Tap the photo to zoom"
    const val SAVE_OCR_DUMP_LABEL = "Save OCR + scan"
    const val SKIP_LABEL = "Skip this receipt"
    const val ITEMS_EMPTY = "None found"
    const val ADD_ITEM_LABEL = "Add item"
    const val ITEM_HEADER = "Item"
    const val QTY_HEADER = "Qty"
    const val AMOUNT_HEADER = "Amount"
    const val CATEGORY_LABEL = "Category"
    const val CATEGORY_PLACEHOLDER = "Choose a category"

    fun showImportProgress(total: Int): Boolean = total > 0

    /** The column headers stand in for the per-field labels a one-row item cannot fit. */
    fun showItemsHeader(itemCount: Int): Boolean = itemCount > 0

    fun itemNamePlaceholder(index: Int): String = "Item ${index + 1}"

    fun removeItemDescription(index: Int): String = "Remove item ${index + 1}"

    /** [options] are the id-to-label pairs the picker renders. */
    fun categoryLabel(selectedId: String?, options: List<Pair<String, String>>): String =
        options.firstOrNull { it.first == selectedId }?.second ?: CATEGORY_PLACEHOLDER

    /** Skipping only makes sense inside an import batch; a single scan has nothing to move on to. */
    fun showSkipButton(importTotal: Int): Boolean = importTotal > 0

    fun importDetectedLabel(total: Int): String =
        if (total == 1) "1 receipt detected" else "$total receipts detected"

    fun importProgressLabel(index: Int, total: Int): String = "Receipt $index of $total"

    fun importProgressFraction(index: Int, total: Int): Float {
        if (total <= 0) return 0f
        return (index.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    fun showOcrDumpAction(developerMode: Boolean): Boolean = developerMode

    fun purposeLabel(purpose: Purpose): String =
        purpose.name.lowercase().replaceFirstChar { it.titlecase(Locale.ENGLISH) }

    fun categorySelection(
        draftCategoryId: String?,
        draftNewLabel: String?,
        suggestedId: String?,
        defaultId: String?,
        options: List<CategoryChoice>,
    ): ReviewCategorySelection {
        draftCategoryId?.takeIf { id -> options.any { it.id == id } }?.let {
            return ReviewCategorySelection(it)
        }
        val pending = CategoryCatalog.normalizedPendingLabel(draftNewLabel)
        if (pending != null) {
            options.firstOrNull { ReceiptPdfLayout.labelsMatch(it.label, pending) }?.let {
                return ReviewCategorySelection(it.id)
            }
            return ReviewCategorySelection(CategoryCatalog.PENDING_ID, pending)
        }
        suggestedId?.takeIf { id -> options.any { it.id == id } }?.let {
            return ReviewCategorySelection(it)
        }
        return ReviewCategorySelection(defaultId)
    }

    fun labelToCreateOnSave(selectedId: String?, pendingNewLabel: String?): String? {
        if (!CategoryCatalog.isPending(selectedId)) return null
        return CategoryCatalog.normalizedPendingLabel(pendingNewLabel)
    }

    fun keepCategoryOnPurposeChange(categoryId: String?, countryNone: Boolean): Boolean =
        countryNone || CategoryCatalog.isCustom(categoryId) || CategoryCatalog.isPending(categoryId)

    fun catalogIdsForAi(options: List<CategoryChoice>): List<String> =
        options.filter { !it.pending }.map { it.id }

    /**
     * The AI is given each id with the words it stands for, since "ca_t2125_9281" on its own says
     * nothing about motor vehicles. A category still being typed is left out; it does not exist yet.
     */
    fun catalogForAi(options: List<CategoryChoice>): List<Pair<String, String>> =
        options.filter { !it.pending }.map { it.id to it.label }

    fun sessionKey(existingId: String?, imageUri: String?): String =
        existingId?.takeIf { it.isNotBlank() }?.let { "id:$it" }
            ?: "image:${imageUri.orEmpty()}"
}
