package com.receiptledger.app.ui

data class ImportPlanItem(
    val photoIndex: Int?,
    val receiptIndex: Int?,
    val ocrPhoto: Boolean,
)

enum class ImportFinishMode {
    REVIEW_EACH,
    SAVE_ALL,
}

object ImportScreenLogic {
    const val TITLE = "Import receipts"
    const val SETTINGS_HEADING = "Import"
    const val BODY =
        "Import receipts from a PDF — an export from this app, a store email, or a scan."
    const val TOGGLE_LABEL = "Quick import"
    const val DEFAULT_QUICK_IMPORT = false
    const val TOGGLE_HELP =
        "Saves every receipt in the PDF without opening Check the fields. Not recommended when the PDF was created outside this app."
    const val CHOOSE_PDF = "Choose PDF"
    const val CANCEL = "Cancel"
    const val OUTSIDE_WARNING_TITLE = "Review this PDF?"
    const val OUTSIDE_WARNING_BODY =
        "This file wasn’t created in Receipt Ledger, so the layout may not match. Names, dates, and amounts can be incomplete. Checking each receipt before it’s saved is the safer option."
    const val OUTSIDE_WARNING_REVIEW = "Review each receipt"
    const val OUTSIDE_WARNING_SAVE_ALL = "Save all without reviewing"

    fun toggleHelp(quickImport: Boolean): String = TOGGLE_HELP

    fun needsOutsideWarning(quickImport: Boolean, fromLedgerExport: Boolean): Boolean =
        quickImport && !fromLedgerExport

    fun finishMode(
        quickImport: Boolean,
        fromLedgerExport: Boolean,
        saveOutsideWithoutReview: Boolean = false,
    ): ImportFinishMode {
        if (!quickImport) return ImportFinishMode.REVIEW_EACH
        if (fromLedgerExport) return ImportFinishMode.SAVE_ALL
        return if (saveOutsideWithoutReview) {
            ImportFinishMode.SAVE_ALL
        } else {
            ImportFinishMode.REVIEW_EACH
        }
    }

    fun usesPdfFields(fromLedgerExport: Boolean): Boolean = fromLedgerExport

    /**
     * Every PDF that came from somewhere else arranges itself differently: a table then photos at
     * the back, one receipt to a page, captions above or below. Rather than guess at a layout, the
     * app reads the photos and lets the AI lift the fields off the pixels, which works whatever the
     * page looks like. A report that states a receipt in both text and picture is caught on saving.
     */
    fun aiScansPhoto(fromLedgerExport: Boolean, ocrPhoto: Boolean): Boolean =
        !fromLedgerExport && ocrPhoto

    fun scansEachPhoto(fromLedgerExport: Boolean, photoCount: Int): Boolean =
        !fromLedgerExport && photoCount > 0

    fun successMessage(count: Int): String =
        if (count == 1) "1 receipt imported successfully."
        else "$count receipts imported successfully."

    fun reviewBatchMessage(saved: Int, skipped: Int): String {
        if (skipped <= 0) return successMessage(saved)
        val skippedPart = if (skipped == 1) "1 receipt skipped." else "$skipped receipts skipped."
        if (saved <= 0) return "No receipts imported. $skippedPart"
        return "${successMessage(saved)} $skippedPart"
    }

    fun planImport(
        fromLedgerExport: Boolean,
        receiptCount: Int,
        photoCount: Int,
    ): List<ImportPlanItem> {
        if (fromLedgerExport) {
            if (receiptCount <= 0) return emptyList()
            return List(receiptCount) { receiptIndex ->
                ImportPlanItem(
                    photoIndex = receiptIndex,
                    receiptIndex = receiptIndex,
                    ocrPhoto = false,
                )
            }
        }
        if (photoCount > 0) {
            return List(photoCount) { index ->
                ImportPlanItem(
                    photoIndex = index,
                    receiptIndex = null,
                    ocrPhoto = true,
                )
            }
        }
        if (receiptCount <= 0) return emptyList()
        return List(receiptCount) { receiptIndex ->
            ImportPlanItem(
                photoIndex = null,
                receiptIndex = receiptIndex,
                ocrPhoto = false,
            )
        }
    }

    fun photoForLedgerReceipt(
        receiptIndex: Int,
        embeddedByReceipt: List<ByteArray?>,
        renderedPages: List<ByteArray>,
    ): ByteArray? {
        embeddedByReceipt.getOrNull(receiptIndex)?.let { return it }
        if (embeddedByReceipt.any { it != null }) return null
        if (renderedPages.isEmpty()) return null
        val receiptCount = embeddedByReceipt.size
        if (receiptCount > 0 && renderedPages.size >= receiptCount) {
            return renderedPages[renderedPages.size - receiptCount + receiptIndex]
        }
        return renderedPages.getOrNull(receiptIndex)
    }
}
