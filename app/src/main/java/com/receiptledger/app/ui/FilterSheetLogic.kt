package com.receiptledger.app.ui

enum class FilterSheetSection {
    YEAR,
    DATE_ADDED,
    TYPE,
    CATEGORY,
    AMOUNT,
}

object FilterSheetLogic {
    const val YEAR_TITLE = "Purchased year"
    const val DATE_ADDED_TITLE = "Added date"
    const val TYPE_TITLE = "Type"
    const val CATEGORY_TITLE = "Category"
    const val AMOUNT_TITLE = "Total amount"

    fun title(section: FilterSheetSection): String = when (section) {
        FilterSheetSection.YEAR -> YEAR_TITLE
        FilterSheetSection.DATE_ADDED -> DATE_ADDED_TITLE
        FilterSheetSection.TYPE -> TYPE_TITLE
        FilterSheetSection.CATEGORY -> CATEGORY_TITLE
        FilterSheetSection.AMOUNT -> AMOUNT_TITLE
    }

    fun initiallyExpanded(): Set<FilterSheetSection> =
        setOf(
            FilterSheetSection.YEAR,
            FilterSheetSection.DATE_ADDED,
            FilterSheetSection.TYPE,
            FilterSheetSection.AMOUNT,
        )

    fun toggle(expanded: Set<FilterSheetSection>, section: FilterSheetSection): Set<FilterSheetSection> =
        if (section in expanded) expanded - section else expanded + section

    fun isExpanded(expanded: Set<FilterSheetSection>, section: FilterSheetSection): Boolean =
        section in expanded

    fun expandActionLabel(title: String, expanded: Boolean): String =
        if (expanded) "Collapse $title" else "Expand $title"
}
