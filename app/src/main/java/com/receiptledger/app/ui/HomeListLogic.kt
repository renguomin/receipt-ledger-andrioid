package com.receiptledger.app.ui

import com.receiptledger.app.data.ReceiptEntity
import com.receiptledger.engine.model.ReceiptRecordLogic
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

enum class HomeMenuAction {
    EXPORT_CSV,
    EXPORT_PDF,
    DELETE,
}

object HomeListLogic {
    const val NO_DATE = "No date"
    const val ADD_NEW_HINT_LABEL = "add new"
    const val ADD_NEW_HINT_HOLD_MS = 8_000L
    const val ADD_NEW_HINT_FADE_MS = 400L

    fun showAddNewHint(
        loaded: Boolean,
        hasSeenHint: Boolean,
        selecting: Boolean,
    ): Boolean = loaded && !hasSeenHint && !selecting

    fun addNewHintAlpha(
        elapsedMs: Long,
        holdMs: Long = ADD_NEW_HINT_HOLD_MS,
        fadeMs: Long = ADD_NEW_HINT_FADE_MS,
    ): Float {
        if (elapsedMs <= holdMs) return 1f
        if (fadeMs <= 0L) return 0f
        val faded = (elapsedMs - holdMs).toFloat() / fadeMs.toFloat()
        return (1f - faded).coerceIn(0f, 1f)
    }

    fun addNewHintVisible(alpha: Float): Boolean = alpha > 0f

    /**
     * Compares whole days in the reader's own time zone, so "today" means the day they are looking
     * at the list on and not the last 24 hours. A stamp from the future, which a clock change can
     * leave behind, counts as freshly added rather than dropping out of every window.
     */
    fun addedWithin(
        addedAtMs: Long,
        window: DateAddedWindow,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (window == DateAddedWindow.ANY) return true
        val added = Instant.ofEpochMilli(addedAtMs).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        if (added.isAfter(today)) return true
        return when (window) {
            DateAddedWindow.ANY -> true
            DateAddedWindow.TODAY -> added == today
            DateAddedWindow.LAST_7_DAYS -> !added.isBefore(today.minusDays(6))
            DateAddedWindow.LAST_30_DAYS -> !added.isBefore(today.minusDays(29))
            DateAddedWindow.THIS_YEAR -> added.year == today.year
        }
    }

    fun matches(
        row: ReceiptEntity,
        filters: HomeFilters,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (filters.years.isNotEmpty() && row.year !in filters.years) return false
        if (filters.purposes.isNotEmpty() &&
            filters.purposes.none { row.purpose.equals(it, ignoreCase = true) }
        ) {
            return false
        }
        if (filters.categoryIds.isNotEmpty() && row.categoryId !in filters.categoryIds) return false
        if (!addedWithin(row.createdAt, filters.dateAdded, nowMs, zone)) return false
        val total = row.total
        val min = filters.minTotal
        val max = filters.maxTotal
        if (min != null && (total == null || total < min)) return false
        if (max != null && (total == null || total > max)) return false
        val q = filters.query.trim()
        if (q.isNotEmpty() && !row.merchant.orEmpty().contains(q, ignoreCase = true)) return false
        return true
    }

    /** Names which date each sort orders by, so neither can be taken for the other. */
    fun sortLabel(sort: ReceiptSort): String = when (sort) {
        ReceiptSort.DATE -> ReceiptRecordLogic.PURCHASED_DATE_LABEL
        ReceiptSort.DATE_ADDED -> ReceiptRecordLogic.ADDED_DATE_LABEL
        ReceiptSort.MERCHANT -> "Merchant"
        ReceiptSort.TOTAL -> "Total cost"
    }

    fun sorter(sort: ReceiptSort): Comparator<ReceiptEntity> = when (sort) {
        ReceiptSort.DATE -> compareByDescending<ReceiptEntity> { it.dateIso.orEmpty() }
            .thenByDescending { it.createdAt }
        ReceiptSort.DATE_ADDED -> compareByDescending { it.createdAt }
        ReceiptSort.MERCHANT -> compareBy<ReceiptEntity> { letterKey(it) == "#" }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.merchant.orEmpty() }
        ReceiptSort.TOTAL -> compareByDescending { it.total ?: Double.NEGATIVE_INFINITY }
    }

    /**
     * The row shows whichever date the list is ordered by, named by its verb. Reading down a list
     * sorted by date added while each row showed a purchase date would look unsorted.
     */
    fun rowDate(
        row: ReceiptEntity,
        sort: ReceiptSort,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = when (sort) {
        ReceiptSort.DATE_ADDED ->
            ReceiptRecordLogic.addedNote(addedDateIso(row.createdAt, zone))
        else -> row.dateIso?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { ReceiptRecordLogic.purchasedNote(it) }
            ?: NO_DATE
    }

    fun rowSubtitle(
        row: ReceiptEntity,
        sort: ReceiptSort,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = "${rowDate(row, sort, zone)} · ${row.purpose.lowercase(Locale.US)}"

    fun apply(
        receipts: List<ReceiptEntity>,
        filters: HomeFilters,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ReceiptEntity> =
        receipts.filter { matches(it, filters, nowMs, zone) }.sortedWith(sorter(filters.sort))

    fun sectioned(
        receipts: List<ReceiptEntity>,
        sort: ReceiptSort,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<HomeListItem> {
        if (receipts.isEmpty()) return emptyList()
        return when (sort) {
            ReceiptSort.TOTAL -> receipts.map { HomeListItem.Receipt(it) }
            ReceiptSort.DATE -> grouped(receipts, ::monthKey)
            ReceiptSort.DATE_ADDED -> grouped(receipts) { addedMonthKey(it, zone) }
            ReceiptSort.MERCHANT -> grouped(receipts, ::letterKey)
        }
    }

    fun idsInRange(items: List<HomeListItem>, from: Int, to: Int): Set<String> {
        if (items.isEmpty()) return emptySet()
        val start = minOf(from, to).coerceIn(0, items.lastIndex)
        val end = maxOf(from, to).coerceIn(0, items.lastIndex)
        return items.subList(start, end + 1).mapNotNull { item ->
            (item as? HomeListItem.Receipt)?.row?.id
        }.toSet()
    }

    fun <T> toggle(selected: Set<T>, value: T): Set<T> =
        if (value in selected) selected - value else selected + value

    fun applySweep(base: Set<String>, covered: Set<String>): Set<String> =
        (base - covered) + (covered - base)

    fun actionReceipts(
        all: List<ReceiptEntity>,
        selectedIds: Set<String>,
    ): List<ReceiptEntity> =
        if (selectedIds.isEmpty()) all else all.filter { it.id in selectedIds }

    fun selectedItemActions(selecting: Boolean): List<HomeMenuAction> =
        if (selecting) {
            listOf(HomeMenuAction.EXPORT_CSV, HomeMenuAction.EXPORT_PDF, HomeMenuAction.DELETE)
        } else {
            emptyList()
        }

    fun overflowActions(selecting: Boolean): List<HomeMenuAction> =
        if (selecting) {
            selectedItemActions(selecting = true)
        } else {
            emptyList()
        }

    fun showOverflowMenu(selecting: Boolean): Boolean = overflowActions(selecting).isNotEmpty()

    fun indexAt(visible: List<VisibleRow>, y: Float): Int? {
        if (visible.isEmpty()) return null
        return visible.lastOrNull { y >= it.offset }?.index ?: visible.first().index
    }

    fun scrollbarMetrics(
        visible: List<VisibleRow>,
        totalItems: Int,
        viewportStart: Int,
        viewportEnd: Int,
        beforePadding: Int,
        afterPadding: Int,
        spacingPx: Float,
    ): ScrollbarMetrics? {
        if (visible.isEmpty()) return null
        val viewport = (viewportEnd - viewportStart).toFloat()
        val avg = visible.map { it.size }.average().toFloat()
        if (avg <= 0f || totalItems <= 0) return null
        val content = totalItems * avg + (totalItems - 1).coerceAtLeast(0) * spacingPx +
            beforePadding + afterPadding
        val maxScroll = content - viewport
        if (maxScroll < 8f) return null
        val first = visible.first()
        val stride = avg + spacingPx
        val scrollOffset = (first.index * stride - first.offset + beforePadding).coerceAtLeast(0f)
        val progress = (scrollOffset / maxScroll).coerceIn(0f, 1f)
        val thumbFraction = (viewport / content).coerceIn(0.12f, 1f)
        return ScrollbarMetrics(progress, thumbFraction, maxScroll, avg)
    }

    fun sectionMarkLabel(raw: String): String = raw

    fun currentSectionHeader(items: List<HomeListItem>, index: Int): String? {
        if (items.isEmpty() || index < 0) return null
        val start = index.coerceAtMost(items.lastIndex)
        for (i in start downTo 0) {
            val header = items[i] as? HomeListItem.Header ?: continue
            return header.label
        }
        return null
    }

    fun scrollbarSectionMarks(
        items: List<HomeListItem>,
        maxScroll: Float,
        avgItemSize: Float,
        spacingPx: Float,
        minProgressGap: Float = 0.06f,
    ): List<ScrollbarSectionMark> {
        val stride = avgItemSize + spacingPx
        if (items.isEmpty() || stride <= 0f || maxScroll <= 0f) return emptyList()
        val marks = items.mapIndexedNotNull { index, item ->
            val header = item as? HomeListItem.Header ?: return@mapIndexedNotNull null
            ScrollbarSectionMark(
                label = sectionMarkLabel(header.label),
                rawLabel = header.label,
                progress = (index * stride / maxScroll).coerceIn(0f, 1f),
                index = index,
            )
        }
        return collapseSectionMarks(marks, minProgressGap)
    }

    fun activeSectionMark(
        marks: List<ScrollbarSectionMark>,
        progress: Float,
    ): ScrollbarSectionMark? =
        marks.minByOrNull { abs(it.progress - progress) }

    internal fun collapseSectionMarks(
        marks: List<ScrollbarSectionMark>,
        minProgressGap: Float,
    ): List<ScrollbarSectionMark> {
        if (marks.size <= 1) return marks
        val kept = ArrayList<ScrollbarSectionMark>(marks.size)
        for (mark in marks) {
            val last = kept.lastOrNull()
            if (last == null || mark.progress - last.progress >= minProgressGap) {
                kept += mark
            }
        }
        return kept
    }

    internal fun monthKey(row: ReceiptEntity): String {
        val date = row.dateIso?.trim().orEmpty()
        return if (date.length >= 7) date.take(7) else NO_DATE
    }

    /** Never falls back to [NO_DATE]: every saved receipt carries the moment it was added. */
    internal fun addedMonthKey(row: ReceiptEntity, zone: ZoneId): String =
        addedDateIso(row.createdAt, zone).take(7)

    internal fun addedDateIso(addedAtMs: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(addedAtMs).atZone(zone).toLocalDate().toString()

    internal fun letterKey(row: ReceiptEntity): String {
        val letter = row.merchant?.trim()?.firstOrNull()
        return if (letter != null && letter.isLetter()) {
            letter.uppercaseChar().toString()
        } else {
            "#"
        }
    }

    private fun grouped(
        receipts: List<ReceiptEntity>,
        keyOf: (ReceiptEntity) -> String,
    ): List<HomeListItem> {
        val items = ArrayList<HomeListItem>(receipts.size + 8)
        var current: String? = null
        for (row in receipts) {
            val key = keyOf(row)
            if (key != current) {
                items += HomeListItem.Header(key)
                current = key
            }
            items += HomeListItem.Receipt(row)
        }
        return items
    }
}

data class VisibleRow(val index: Int, val offset: Int, val size: Int)

data class ScrollbarMetrics(
    val progress: Float,
    val thumbFraction: Float,
    val maxScroll: Float,
    val avgItemSize: Float,
)

data class ScrollbarSectionMark(
    val label: String,
    val rawLabel: String,
    val progress: Float,
    val index: Int,
)

