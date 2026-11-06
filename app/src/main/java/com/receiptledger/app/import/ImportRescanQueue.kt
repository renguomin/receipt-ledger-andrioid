package com.receiptledger.app.import

import com.receiptledger.engine.model.ParsedReceipt
import com.receiptledger.engine.model.Purpose

data class ImportDraft(
    val imagePath: String,
    val ocrPhoto: Boolean,
    /** Read this photo with the AI as soon as it opens, rather than waiting to be asked. */
    val aiScan: Boolean = false,
    val parsed: ParsedReceipt? = null,
    val purpose: Purpose? = null,
    val categoryId: String? = null,
    val newCategoryLabel: String? = null,
)

data class ImportProgress(
    val index: Int,
    val total: Int,
)

data class ImportOutcome(
    val saved: Int,
    val skipped: Int,
) {
    val reviewed: Int get() = saved + skipped
}

class ImportRescanQueue {
    private val remaining = ArrayDeque<ImportDraft>()
    private var total = 0
    private var index = 0
    private var active = false
    private var saved = 0
    private var skipped = 0

    @Synchronized
    fun start(items: List<ImportDraft>) {
        remaining.clear()
        remaining.addAll(items)
        total = items.size
        index = if (items.isEmpty()) 0 else 1
        active = items.isNotEmpty()
        saved = 0
        skipped = 0
    }

    @Synchronized
    fun current(): ImportDraft? = remaining.firstOrNull()

    /** Photos of drafts still waiting to be reviewed, so a cleanup sweep leaves them alone. */
    @Synchronized
    fun imagePaths(): List<String> = remaining.map { it.imagePath }

    @Synchronized
    fun progress(): ImportProgress? =
        if (!active || total <= 0) null else ImportProgress(index, total)

    /** Survives the end of the batch so the closing notice can report it; [start] and [clear] reset it. */
    @Synchronized
    fun outcome(): ImportOutcome = ImportOutcome(saved = saved, skipped = skipped)

    @Synchronized
    fun finishCurrent(saved: Boolean = true): ImportDraft? {
        remaining.removeFirstOrNull() ?: return null
        if (saved) this.saved += 1 else this.skipped += 1
        if (remaining.isEmpty()) {
            active = false
            index = 0
            total = 0
            return null
        }
        index += 1
        return remaining.first()
    }

    @Synchronized
    fun clear() {
        remaining.clear()
        total = 0
        index = 0
        active = false
        saved = 0
        skipped = 0
    }
}
