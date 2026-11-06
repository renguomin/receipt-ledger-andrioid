package com.receiptledger.app.ui

import com.receiptledger.app.data.ReceiptEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HomeListLogicTest {
    private val canadianTire = receipt(
        id = "ct",
        merchant = "Canadian Tire",
        dateIso = "2026-03-12",
        total = 22.59,
        year = 2026,
        purpose = "BUSINESS",
        categoryId = "ca_t2125_8810",
    )
    private val shoppers = receipt(
        id = "sd",
        merchant = "Shoppers Drug Mart",
        dateIso = "2025-11-02",
        total = 8.40,
        year = 2025,
        purpose = "PERSONAL",
    )
    private val unnamed = receipt(
        id = "xx",
        merchant = null,
        dateIso = null,
        total = 100.0,
        year = 2026,
        purpose = "PERSONAL",
    )
    private val all = listOf(canadianTire, shoppers, unnamed)

    @Test
    fun searchMatchesMerchantNameOnly() {
        val query = HomeFilters(query = "tire")
        assertEquals(listOf("ct"), HomeListLogic.apply(all, query).map { it.id })

        val dateLookalike = HomeFilters(query = "2026")
        assertTrue(HomeListLogic.apply(all, dateLookalike).isEmpty())

        val amountLookalike = HomeFilters(query = "22.59")
        assertTrue(HomeListLogic.apply(all, amountLookalike).isEmpty())

        val purposeLookalike = HomeFilters(query = "BUSINESS")
        assertTrue(HomeListLogic.apply(all, purposeLookalike).isEmpty())
    }

    @Test
    fun filtersYearPurposeCategoryAndAmountRange() {
        assertEquals(
            listOf("sd"),
            HomeListLogic.apply(all, HomeFilters(years = setOf(2025))).map { it.id },
        )
        assertEquals(
            listOf("ct"),
            HomeListLogic.apply(all, HomeFilters(purposes = setOf("business"))).map { it.id },
        )
        assertEquals(
            listOf("ct"),
            HomeListLogic.apply(all, HomeFilters(categoryIds = setOf("ca_t2125_8810"))).map { it.id },
        )
        assertEquals(
            listOf("sd"),
            HomeListLogic.apply(all, HomeFilters(minTotalText = "5", maxTotalText = "10")).map { it.id },
        )
        assertTrue(
            HomeListLogic.apply(all, HomeFilters(minTotalText = "200")).isEmpty(),
        )
    }

    @Test
    fun multiSelectWithinACategoryKeepsAnyMatch() {
        val years = HomeListLogic.apply(all, HomeFilters(years = setOf(2025, 2026)))
        assertEquals(listOf("ct", "sd", "xx"), years.map { it.id })
        val types = HomeListLogic.apply(all, HomeFilters(purposes = setOf("PERSONAL", "BUSINESS")))
        assertEquals(listOf("ct", "sd", "xx"), types.map { it.id })
    }

    @Test
    fun searchIsNotAnActiveFilterChip() {
        assertFalse(HomeFilters(query = "Tire").isActive)
        assertEquals(0, HomeFilters(query = "Tire").selectedCount)
        assertTrue(HomeFilters(years = setOf(2026)).isActive)
        assertTrue(HomeFilters(minTotalText = "10").isActive)
    }

    @Test
    fun selectedCountCountsEachTagAndAmountRangeOnce() {
        assertEquals(2, HomeFilters(years = setOf(2025, 2026)).selectedCount)
        assertEquals(1, HomeFilters(minTotalText = "10", maxTotalText = "80").selectedCount)
        assertEquals(
            4,
            HomeFilters(
                years = setOf(2026),
                purposes = setOf("PERSONAL"),
                categoryIds = setOf("ca_t2125_8810"),
                minTotalText = "5",
            ).selectedCount,
        )
    }

    @Test
    fun clearSheetFiltersKeepsSearchAndSort() {
        val kept = HomeFilters(
            query = "Tire",
            sort = ReceiptSort.MERCHANT,
        )
        assertEquals("Tire", kept.query)
        assertEquals(ReceiptSort.MERCHANT, kept.sort)
        assertFalse(kept.isActive)
        assertEquals(0, kept.selectedCount)
    }

    @Test
    fun sortByDateNewestFirstAndNoDateLast() {
        val sorted = HomeListLogic.apply(all, HomeFilters(sort = ReceiptSort.DATE))
        assertEquals(listOf("ct", "sd", "xx"), sorted.map { it.id })
    }

    @Test
    fun sortByMerchantPutsHashLast() {
        val sorted = HomeListLogic.apply(all, HomeFilters(sort = ReceiptSort.MERCHANT))
        assertEquals(listOf("ct", "sd", "xx"), sorted.map { it.id })
    }

    @Test
    fun sortByTotalHighestFirst() {
        val sorted = HomeListLogic.apply(all, HomeFilters(sort = ReceiptSort.TOTAL))
        assertEquals(listOf("xx", "ct", "sd"), sorted.map { it.id })
    }

    @Test
    fun dateSectionsSkipEmptyMonthsAndPutNoDateLast() {
        val items = HomeListLogic.sectioned(
            HomeListLogic.apply(all, HomeFilters(sort = ReceiptSort.DATE)),
            ReceiptSort.DATE,
        )
        assertEquals(
            listOf("2026-03", "ct", "2025-11", "sd", "No date", "xx"),
            items.labels(),
        )
    }

    @Test
    fun merchantSectionsSkipEmptyLetters() {
        val items = HomeListLogic.sectioned(
            HomeListLogic.apply(all, HomeFilters(sort = ReceiptSort.MERCHANT)),
            ReceiptSort.MERCHANT,
        )
        assertEquals(listOf("C", "ct", "S", "sd", "#", "xx"), items.labels())
    }

    @Test
    fun totalSortHasNoSectionHeaders() {
        val items = HomeListLogic.sectioned(
            HomeListLogic.apply(all, HomeFilters(sort = ReceiptSort.TOTAL)),
            ReceiptSort.TOTAL,
        )
        assertTrue(items.none { it is HomeListItem.Header })
        assertEquals(listOf("xx", "ct", "sd"), items.labels())
    }

    @Test
    fun sweepSelectsEveryCoveredReceiptAndSkipsHeaders() {
        val items = HomeListLogic.sectioned(
            HomeListLogic.apply(all, HomeFilters(sort = ReceiptSort.DATE)),
            ReceiptSort.DATE,
        )
        // Header, ct, header, sd — dragging from first receipt through the next header.
        assertEquals(setOf("ct", "sd"), HomeListLogic.idsInRange(items, 1, 3))
        // Reverse drag covers the same receipts.
        assertEquals(setOf("ct", "sd"), HomeListLogic.idsInRange(items, 3, 1))
        // Full list, including the no-date row after its header.
        assertEquals(setOf("ct", "sd", "xx"), HomeListLogic.idsInRange(items, 0, items.lastIndex))
    }

    @Test
    fun menuActionsUseSelectedReceiptsWhenAnyAreChosen() {
        assertEquals(
            listOf("ct", "sd", "xx"),
            HomeListLogic.actionReceipts(all, emptySet()).map { it.id },
        )
        assertEquals(
            listOf("ct", "xx"),
            HomeListLogic.actionReceipts(all, setOf("ct", "xx")).map { it.id },
        )
    }

    @Test
    fun overflowMenuIsHiddenWhenIdleAndListsExportWhenSelecting() {
        assertEquals(emptyList<HomeMenuAction>(), HomeListLogic.overflowActions(selecting = false))
        assertFalse(HomeListLogic.showOverflowMenu(selecting = false))
        assertEquals(
            listOf(
                HomeMenuAction.EXPORT_CSV,
                HomeMenuAction.EXPORT_PDF,
                HomeMenuAction.DELETE,
            ),
            HomeListLogic.overflowActions(selecting = true),
        )
        assertTrue(HomeListLogic.showOverflowMenu(selecting = true))
        assertEquals(emptyList<HomeMenuAction>(), HomeListLogic.selectedItemActions(selecting = false))
    }

    @Test
    fun settingsExportUsesEveryReceiptWhenNothingIsSelected() {
        assertEquals(all.map { it.id }, HomeListLogic.actionReceipts(all, emptySet()).map { it.id })
    }

    @Test
    fun addNewHintShowsOnlyOnFirstOpenUntilTheCameraIsUsed() {
        assertEquals("add new", HomeListLogic.ADD_NEW_HINT_LABEL)
        assertFalse(HomeListLogic.showAddNewHint(loaded = false, hasSeenHint = false, selecting = false))
        assertTrue(HomeListLogic.showAddNewHint(loaded = true, hasSeenHint = false, selecting = false))
        assertFalse(HomeListLogic.showAddNewHint(loaded = true, hasSeenHint = true, selecting = false))
        assertFalse(HomeListLogic.showAddNewHint(loaded = true, hasSeenHint = false, selecting = true))
    }

    @Test
    fun addNewHintStaysOpaqueForEightSecondsThenFadesOut() {
        assertEquals(8_000L, HomeListLogic.ADD_NEW_HINT_HOLD_MS)
        assertEquals(400L, HomeListLogic.ADD_NEW_HINT_FADE_MS)
        assertEquals(1f, HomeListLogic.addNewHintAlpha(0L))
        assertEquals(1f, HomeListLogic.addNewHintAlpha(8_000L))
        assertEquals(0.5f, HomeListLogic.addNewHintAlpha(8_200L), 0.001f)
        assertEquals(0f, HomeListLogic.addNewHintAlpha(8_400L))
        assertEquals(0f, HomeListLogic.addNewHintAlpha(9_000L))
        assertTrue(HomeListLogic.addNewHintVisible(1f))
        assertTrue(HomeListLogic.addNewHintVisible(0.5f))
        assertFalse(HomeListLogic.addNewHintVisible(0f))
    }

    @Test
    fun tapTogglesOneReceipt() {
        assertEquals(setOf("ct"), HomeListLogic.toggle(emptySet(), "ct"))
        assertEquals(emptySet<String>(), HomeListLogic.toggle(setOf("ct"), "ct"))
        assertEquals(setOf("ct", "sd"), HomeListLogic.toggle(setOf("ct"), "sd"))
    }

    @Test
    fun laterSweepAddsASkippedBlockWithoutClearingTheFirst() {
        val first = HomeListLogic.applySweep(emptySet(), setOf("ct"))
        val both = HomeListLogic.applySweep(first, setOf("xx"))
        assertEquals(setOf("ct", "xx"), both)
    }

    @Test
    fun laterSweepDeselectsCoveredItemsAndKeepsTheRest() {
        val selected = setOf("ct", "sd", "xx")
        assertEquals(setOf("ct", "xx"), HomeListLogic.applySweep(selected, setOf("sd")))
        assertEquals(setOf("xx"), HomeListLogic.applySweep(selected, setOf("ct", "sd")))
    }

    @Test
    fun sweepAgainstASnapshotDoesNotFlipItemsWhenTheFingerReverses() {
        val base = setOf("ct")
        val draggedFar = HomeListLogic.applySweep(base, setOf("sd", "xx"))
        assertEquals(setOf("ct", "sd", "xx"), draggedFar)
        val draggedBack = HomeListLogic.applySweep(base, setOf("sd"))
        assertEquals(setOf("ct", "sd"), draggedBack)
    }

    @Test
    fun indexAtUsesTheRowUnderTheFingerIncludingGaps() {
        val visible = listOf(
            VisibleRow(index = 0, offset = 0, size = 40),
            VisibleRow(index = 1, offset = 44, size = 40),
            VisibleRow(index = 2, offset = 88, size = 40),
        )
        assertEquals(0, HomeListLogic.indexAt(visible, 10f))
        assertEquals(0, HomeListLogic.indexAt(visible, 42f))
        assertEquals(1, HomeListLogic.indexAt(visible, 44f))
        assertEquals(2, HomeListLogic.indexAt(visible, 200f))
        assertEquals(0, HomeListLogic.indexAt(visible, -8f))
        assertNull(HomeListLogic.indexAt(emptyList(), 10f))
    }

    @Test
    fun scrollbarProgressTracksScrollOffsetNotFirstIndex() {
        fun rows(startIndex: Int) = List(8) { i ->
            VisibleRow(index = startIndex + i, offset = i * 54, size = 50)
        }
        val top = HomeListLogic.scrollbarMetrics(
            visible = rows(0),
            totalItems = 20,
            viewportStart = 0,
            viewportEnd = 400,
            beforePadding = 0,
            afterPadding = 0,
            spacingPx = 4f,
        )!!
        assertEquals(0f, top.progress, 0.001f)
        assertTrue(top.thumbFraction < 1f)

        val mid = HomeListLogic.scrollbarMetrics(
            visible = rows(8),
            totalItems = 20,
            viewportStart = 0,
            viewportEnd = 400,
            beforePadding = 0,
            afterPadding = 0,
            spacingPx = 4f,
        )!!
        assertTrue(mid.progress > 0.4f)
        assertTrue(mid.progress < 0.8f)

        assertNull(
            HomeListLogic.scrollbarMetrics(
                visible = emptyList(),
                totalItems = 20,
                viewportStart = 0,
                viewportEnd = 400,
                beforePadding = 0,
                afterPadding = 0,
                spacingPx = 4f,
            ),
        )
    }

    @Test
    fun dateScrollbarTagsKeepYearMonthNotASingleLetter() {
        assertEquals("2026-03", HomeListLogic.sectionMarkLabel("2026-03"))
        assertEquals("2025-11", HomeListLogic.sectionMarkLabel("2025-11"))
        assertEquals("No date", HomeListLogic.sectionMarkLabel("No date"))
        assertEquals("C", HomeListLogic.sectionMarkLabel("C"))
        assertEquals("#", HomeListLogic.sectionMarkLabel("#"))
    }

    @Test
    fun scrollbarSectionMarksSitAtHeaderPositionsAlongTheTrack() {
        val items = HomeListLogic.sectioned(
            HomeListLogic.apply(all, HomeFilters(sort = ReceiptSort.DATE)),
            ReceiptSort.DATE,
        )
        val marks = HomeListLogic.scrollbarSectionMarks(
            items = items,
            maxScroll = 500f,
            avgItemSize = 50f,
            spacingPx = 4f,
        )
        assertEquals(listOf("2026-03", "2025-11", "No date"), marks.map { it.label })
        assertEquals(0f, marks[0].progress, 0.001f)
        assertEquals(2 * 54f / 500f, marks[1].progress, 0.001f)
        assertEquals(4 * 54f / 500f, marks[2].progress, 0.001f)
        assertTrue(HomeListLogic.scrollbarSectionMarks(items, maxScroll = 500f, avgItemSize = 50f, spacingPx = 4f)
            .none { it.rawLabel == "ct" })
        assertTrue(
            HomeListLogic.scrollbarSectionMarks(
                HomeListLogic.sectioned(
                    HomeListLogic.apply(all, HomeFilters(sort = ReceiptSort.TOTAL)),
                    ReceiptSort.TOTAL,
                ),
                maxScroll = 500f,
                avgItemSize = 50f,
                spacingPx = 4f,
            ).isEmpty(),
        )
    }

    @Test
    fun nearbyScrollbarMarksCollapseWhenTooClose() {
        val crowded = listOf(
            ScrollbarSectionMark("2026-01", "2026-01", 0f, 0),
            ScrollbarSectionMark("2026-02", "2026-02", 0.02f, 2),
            ScrollbarSectionMark("2026-03", "2026-03", 0.5f, 10),
            ScrollbarSectionMark("No date", "No date", 0.51f, 20),
        )
        val kept = HomeListLogic.collapseSectionMarks(crowded, minProgressGap = 0.06f)
        assertEquals(listOf("2026-01", "2026-03"), kept.map { it.label })
    }

    @Test
    fun currentSectionFollowsTheHeaderAtOrAboveTheRow() {
        val items = HomeListLogic.sectioned(
            HomeListLogic.apply(all, HomeFilters(sort = ReceiptSort.DATE)),
            ReceiptSort.DATE,
        )
        assertEquals("2026-03", HomeListLogic.currentSectionHeader(items, 0))
        assertEquals("2026-03", HomeListLogic.currentSectionHeader(items, 1))
        assertEquals("2025-11", HomeListLogic.currentSectionHeader(items, 2))
        assertEquals("No date", HomeListLogic.currentSectionHeader(items, 5))
        assertNull(HomeListLogic.currentSectionHeader(emptyList(), 0))
        val byTotal = HomeListLogic.sectioned(
            HomeListLogic.apply(all, HomeFilters(sort = ReceiptSort.TOTAL)),
            ReceiptSort.TOTAL,
        )
        assertNull(HomeListLogic.currentSectionHeader(byTotal, 0))
    }

    @Test
    fun activeScrollbarMarkIsTheClosestToTheThumb() {
        val marks = listOf(
            ScrollbarSectionMark("2026-03", "2026-03", 0f, 0),
            ScrollbarSectionMark("2025-11", "2025-11", 0.4f, 4),
            ScrollbarSectionMark("No date", "No date", 1f, 8),
        )
        assertEquals("2026-03", HomeListLogic.activeSectionMark(marks, 0.05f)?.label)
        assertEquals("2025-11", HomeListLogic.activeSectionMark(marks, 0.45f)?.label)
        assertEquals("No date", HomeListLogic.activeSectionMark(marks, 0.9f)?.label)
        assertNull(HomeListLogic.activeSectionMark(emptyList(), 0.5f))
    }

    private fun List<HomeListItem>.labels(): List<String> = map { item ->
        when (item) {
            is HomeListItem.Header -> item.label
            is HomeListItem.Receipt -> item.row.id
        }
    }

    @Test
    fun eachSortSaysWhichDateItOrdersBy() {
        assertEquals("Purchased date", HomeListLogic.sortLabel(ReceiptSort.DATE))
        assertEquals("Added date", HomeListLogic.sortLabel(ReceiptSort.DATE_ADDED))
        assertEquals("Merchant", HomeListLogic.sortLabel(ReceiptSort.MERCHANT))
        assertEquals("Total cost", HomeListLogic.sortLabel(ReceiptSort.TOTAL))
    }

    @Test
    fun sortingByDateAddedPutsTheNewestArrivalFirstWhateverThePurchaseDates() {
        val sorted = HomeListLogic.apply(
            shoeboxRows,
            HomeFilters(sort = ReceiptSort.DATE_ADDED),
            now,
            zone,
        )
        assertEquals(listOf("newest", "middle", "oldest"), sorted.map { it.id })
        // The same rows by receipt date come out in a different order, so the two sorts differ.
        assertEquals(
            listOf("middle", "oldest", "newest"),
            HomeListLogic.apply(
                shoeboxRows,
                HomeFilters(sort = ReceiptSort.DATE),
                now,
                zone,
            ).map { it.id },
        )
    }

    @Test
    fun rowsNameTheDateTheListIsSortedBy() {
        val purchasedLongAgoAddedToday = shoeboxRows.first { it.id == "newest" }
        assertEquals(
            "Purchased on 2024-01-04 · business",
            HomeListLogic.rowSubtitle(purchasedLongAgoAddedToday, ReceiptSort.DATE, zone),
        )
        assertEquals(
            "Added on 2026-09-04 · business",
            HomeListLogic.rowSubtitle(purchasedLongAgoAddedToday, ReceiptSort.DATE_ADDED, zone),
        )
        // Sorting by something other than a date leaves the receipt's own date on show.
        assertEquals(
            "Purchased on 2024-01-04 · business",
            HomeListLogic.rowSubtitle(purchasedLongAgoAddedToday, ReceiptSort.MERCHANT, zone),
        )
    }

    @Test
    fun aRowWithNoReceiptDateStillReportsWhenItWasAdded() {
        val undated = receipt(
            id = "undated",
            merchant = null,
            dateIso = null,
            total = 5.0,
            year = 2026,
            purpose = "PERSONAL",
            createdAt = addedAt("2026-09-04T09:00"),
        )
        assertEquals("No date · personal", HomeListLogic.rowSubtitle(undated, ReceiptSort.DATE, zone))
        assertEquals(
            "Added on 2026-09-04 · personal",
            HomeListLogic.rowSubtitle(undated, ReceiptSort.DATE_ADDED, zone),
        )
    }

    @Test
    fun dateAddedSectionsGroupByTheMonthOfArrivalAndNeverNeedANoDateHeader() {
        val items = HomeListLogic.sectioned(
            HomeListLogic.apply(shoeboxRows, HomeFilters(sort = ReceiptSort.DATE_ADDED), now, zone),
            ReceiptSort.DATE_ADDED,
            zone,
        )
        assertEquals(
            listOf("2026-09", "newest", "2026-02", "middle", "2025-07", "oldest"),
            items.labels(),
        )
        assertFalse(items.labels().contains(HomeListLogic.NO_DATE))
    }

    @Test
    fun dateAddedWindowsCountWholeDaysBackFromToday() {
        fun addedOn(iso: String, window: DateAddedWindow) =
            HomeListLogic.addedWithin(addedAt(iso), window, now, zone)

        assertTrue(addedOn("2026-09-04T00:05", DateAddedWindow.TODAY))
        assertFalse(addedOn("2026-09-03T23:55", DateAddedWindow.TODAY))
        assertTrue(addedOn("2026-09-03T23:55", DateAddedWindow.LAST_7_DAYS))

        assertTrue(addedOn("2026-08-29T12:00", DateAddedWindow.LAST_7_DAYS))
        assertFalse(addedOn("2026-08-28T12:00", DateAddedWindow.LAST_7_DAYS))
        assertTrue(addedOn("2026-08-28T12:00", DateAddedWindow.LAST_30_DAYS))

        assertTrue(addedOn("2026-08-06T00:00", DateAddedWindow.LAST_30_DAYS))
        assertFalse(addedOn("2026-08-05T23:59", DateAddedWindow.LAST_30_DAYS))
        assertTrue(addedOn("2026-08-05T23:59", DateAddedWindow.THIS_YEAR))

        assertTrue(addedOn("2026-01-01T00:00", DateAddedWindow.THIS_YEAR))
        assertFalse(addedOn("2025-12-31T23:59", DateAddedWindow.THIS_YEAR))
        assertTrue(addedOn("2025-12-31T23:59", DateAddedWindow.ANY))
    }

    @Test
    fun aStampFromTheFutureStillCountsAsJustAdded() {
        assertTrue(
            HomeListLogic.addedWithin(addedAt("2026-09-05T10:00"), DateAddedWindow.TODAY, now, zone),
        )
    }

    @Test
    fun dateAddedFilterIgnoresTheDatePrintedOnTheReceipt() {
        val oldPurchaseAddedToday = receipt(
            id = "shoebox",
            merchant = "Canadian Tire",
            dateIso = "2024-01-04",
            total = 22.59,
            year = 2024,
            purpose = "BUSINESS",
            createdAt = addedAt("2026-09-04T09:00"),
        )
        val todaysPurchaseAddedLongAgo = receipt(
            id = "stale",
            merchant = "Shoppers Drug Mart",
            dateIso = "2026-09-04",
            total = 8.40,
            year = 2026,
            purpose = "PERSONAL",
            createdAt = addedAt("2026-02-01T09:00"),
        )
        val rows = listOf(oldPurchaseAddedToday, todaysPurchaseAddedLongAgo)

        assertEquals(
            listOf("shoebox"),
            HomeListLogic.apply(
                rows,
                HomeFilters(dateAdded = DateAddedWindow.TODAY),
                now,
                zone,
            ).map { it.id },
        )
        assertEquals(
            listOf("stale", "shoebox"),
            HomeListLogic.apply(rows, HomeFilters(), now, zone).map { it.id },
        )
    }

    @Test
    fun dateAddedNarrowsAlongsideTheOtherFiltersAndCountsAsOne() {
        val rows = listOf(
            receipt(
                id = "keep",
                merchant = "Canadian Tire",
                dateIso = "2026-03-12",
                total = 22.59,
                year = 2026,
                purpose = "BUSINESS",
                createdAt = addedAt("2026-09-04T09:00"),
            ),
            receipt(
                id = "wrongType",
                merchant = "Canadian Tire",
                dateIso = "2026-03-12",
                total = 22.59,
                year = 2026,
                purpose = "PERSONAL",
                createdAt = addedAt("2026-09-04T09:00"),
            ),
        )
        val filters = HomeFilters(
            purposes = setOf("BUSINESS"),
            dateAdded = DateAddedWindow.TODAY,
        )
        assertEquals(listOf("keep"), HomeListLogic.apply(rows, filters, now, zone).map { it.id })
        assertEquals(2, filters.selectedCount)
        assertTrue(filters.dateAddedSelected)
        assertFalse(HomeFilters().dateAddedSelected)
        assertEquals(0, HomeFilters().selectedCount)
    }

    private val zone: ZoneId = ZoneId.of("America/Vancouver")

    private val now: Long = addedAt("2026-09-04T15:59")

    /** An old purchase filed today, and newer ones filed earlier, so the two sorts disagree. */
    private val shoeboxRows = listOf(
        receipt(
            id = "newest",
            merchant = "Canadian Tire",
            dateIso = "2024-01-04",
            total = 22.59,
            year = 2024,
            purpose = "BUSINESS",
            createdAt = addedAt("2026-09-04T09:00"),
        ),
        receipt(
            id = "middle",
            merchant = "Shoppers Drug Mart",
            dateIso = "2026-02-01",
            total = 8.40,
            year = 2026,
            purpose = "PERSONAL",
            createdAt = addedAt("2026-02-01T09:00"),
        ),
        receipt(
            id = "oldest",
            merchant = "Costco",
            dateIso = "2025-07-15",
            total = 61.00,
            year = 2025,
            purpose = "PERSONAL",
            createdAt = addedAt("2025-07-15T09:00"),
        ),
    )

    private fun addedAt(localIso: String): Long =
        LocalDateTime.parse(localIso).atZone(zone).toInstant().toEpochMilli()

    private fun receipt(
        id: String,
        merchant: String?,
        dateIso: String?,
        total: Double?,
        year: Int,
        purpose: String,
        categoryId: String? = null,
        createdAt: Long = 1L,
    ) = ReceiptEntity(
        id = id,
        createdAt = createdAt,
        imagePath = "/tmp/$id.jpg",
        merchant = merchant,
        merchantConfidence = 1f,
        merchantSource = "test",
        dateIso = dateIso,
        dateConfidence = 1f,
        currency = "CAD",
        subtotal = total,
        taxTotal = 0.0,
        taxJson = "[]",
        total = total,
        totalConfidence = 1f,
        paymentMethod = null,
        gstHstNumber = null,
        lineItemsJson = "[]",
        purpose = purpose,
        categoryId = categoryId,
        rawText = "",
        year = year,
    )
}
