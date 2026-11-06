package com.receiptledger.app.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FilterSheetLogicTest {
    @Test
    fun categoryStartsCollapsedBecauseItHasManyTags() {
        val expanded = FilterSheetLogic.initiallyExpanded()
        assertTrue(FilterSheetLogic.isExpanded(expanded, FilterSheetSection.YEAR))
        assertTrue(FilterSheetLogic.isExpanded(expanded, FilterSheetSection.TYPE))
        assertFalse(FilterSheetLogic.isExpanded(expanded, FilterSheetSection.CATEGORY))
        assertTrue(FilterSheetLogic.isExpanded(expanded, FilterSheetSection.AMOUNT))
        assertTrue(FilterSheetLogic.isExpanded(expanded, FilterSheetSection.DATE_ADDED))
        assertEquals("Type", FilterSheetLogic.title(FilterSheetSection.TYPE))
        assertEquals("Category", FilterSheetLogic.title(FilterSheetSection.CATEGORY))
        assertEquals("Total amount", FilterSheetLogic.title(FilterSheetSection.AMOUNT))
    }

    @Test
    fun theTwoDateSectionsSayWhichDateTheyMean() {
        assertEquals("Purchased year", FilterSheetLogic.title(FilterSheetSection.YEAR))
        assertEquals("Added date", FilterSheetLogic.title(FilterSheetSection.DATE_ADDED))
    }

    @Test
    fun dateAddedOffersRecentWindowsAndNoAnyTimeTag() {
        val offered = DateAddedWindow.entries.filter { it != DateAddedWindow.ANY }.map { it.label }
        assertEquals(listOf("Today", "Last 7 days", "Last 30 days", "This year"), offered)
        assertEquals("Any time", DateAddedWindow.ANY.label)
    }

    @Test
    fun headingToggleExpandsAndCollapsesASection() {
        val start = FilterSheetLogic.initiallyExpanded()
        val opened = FilterSheetLogic.toggle(start, FilterSheetSection.CATEGORY)
        assertTrue(FilterSheetLogic.isExpanded(opened, FilterSheetSection.CATEGORY))
        val closed = FilterSheetLogic.toggle(opened, FilterSheetSection.CATEGORY)
        assertFalse(FilterSheetLogic.isExpanded(closed, FilterSheetSection.CATEGORY))
        val yearClosed = FilterSheetLogic.toggle(start, FilterSheetSection.YEAR)
        assertFalse(FilterSheetLogic.isExpanded(yearClosed, FilterSheetSection.YEAR))
        assertTrue(FilterSheetLogic.isExpanded(yearClosed, FilterSheetSection.TYPE))
    }

    @Test
    fun expandActionNamesTheSection() {
        assertEquals("Collapse Category", FilterSheetLogic.expandActionLabel("Category", expanded = true))
        assertEquals("Expand Category", FilterSheetLogic.expandActionLabel("Category", expanded = false))
        assertEquals("Collapse Added date", FilterSheetLogic.expandActionLabel("Added date", expanded = true))
    }
}
