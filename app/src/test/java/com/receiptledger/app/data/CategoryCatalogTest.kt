package com.receiptledger.app.data

import com.receiptledger.engine.model.Country
import com.receiptledger.engine.model.Purpose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CategoryCatalogTest {
    private val lunch = CustomCategoryEntity("custom_1", "Client lunch", 1L)

    @Test
    fun noneHidesPredefinedAndKeepsCustom() {
        val visible = CategoryCatalog.visible(Country.NONE, listOf(lunch))
        assertEquals(listOf("custom_1"), visible.map { it.id })
        assertTrue(visible.all { it.custom })
    }

    @Test
    fun canadaKeepsTaxLinesAndAppendsCustom() {
        val visible = CategoryCatalog.visible(Country.CA, listOf(lunch))
        assertTrue(visible.any { it.id == "ca_t2125_8810" })
        assertEquals("Client lunch", visible.last().label)
    }

    @Test
    fun labelFallsBackToCustomThenRawId() {
        assertEquals("Client lunch", CategoryCatalog.label("custom_1", listOf(lunch)))
        assertEquals("Office expenses", CategoryCatalog.label("ca_t2125_8810", emptyList()))
        assertEquals("unknown", CategoryCatalog.label("unknown", emptyList()))
    }

    @Test
    fun pendingOptionUsesWillBeCreatedLabelUntilItExists() {
        val pending = CategoryCatalog.visible(Country.CA, emptyList(), "Client lunch")
        assertEquals("Client lunch (will be created)", pending.first().label)
        assertEquals(CategoryCatalog.PENDING_ID, pending.first().id)
        assertTrue(pending.first().pending)
        assertEquals(
            "Client lunch (will be created)",
            CategoryCatalog.pendingMenuLabel("Client lunch"),
        )
        assertTrue(CategoryCatalog.isPending(CategoryCatalog.PENDING_ID))
    }

    @Test
    fun pendingOptionIsOmittedWhenTheLabelAlreadyExists() {
        val withCustom = CategoryCatalog.visible(Country.NONE, listOf(lunch), "client lunch")
        assertEquals(listOf("custom_1"), withCustom.map { it.id })
        val withTax = CategoryCatalog.visible(Country.CA, emptyList(), "Office expenses")
        assertTrue(withTax.none { it.pending })
        val hyphenatedPersonal = CategoryCatalog.visible(Country.CA, emptyList(), "Personal - groceries")
        assertTrue(hyphenatedPersonal.none { it.pending })
        assertEquals("custom_1", CategoryCatalog.existingIdForPending("Client lunch", listOf(lunch)))
        assertEquals(null, CategoryCatalog.existingIdForPending("Client lunch", emptyList()))
        assertEquals(null, CategoryCatalog.normalizedPendingLabel("  "))
    }

    @Test
    fun defaultIdFollowsCountryAndPurpose() {
        assertEquals("custom_1", CategoryCatalog.defaultId(Country.NONE, Purpose.BUSINESS, listOf(lunch)))
        assertEquals("ca_personal_other", CategoryCatalog.defaultId(Country.CA, Purpose.PERSONAL, emptyList()))
        assertEquals("ca_t2125_8521", CategoryCatalog.defaultId(Country.CA, Purpose.BUSINESS, emptyList()))
    }
}
