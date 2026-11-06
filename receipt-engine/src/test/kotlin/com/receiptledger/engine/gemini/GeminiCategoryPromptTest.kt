package com.receiptledger.engine.gemini

import com.receiptledger.engine.model.Country
import com.receiptledger.engine.tax.TaxCategories
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeminiCategoryPromptTest {
    @Test
    fun everyIdIsSentWithTheWordsItStandsFor() {
        val section = GeminiReceipt.categorySection(
            listOf(
                "ca_t2125_9281" to "Motor vehicle expenses",
                "ca_t2125_8960" to "Repairs and maintenance",
            ),
        )
        assertTrue(section.contains("ca_t2125_9281 = Motor vehicle expenses"))
        assertTrue(section.contains("ca_t2125_8960 = Repairs and maintenance"))
    }

    @Test
    fun theRealCanadianCatalogArrivesReadableRatherThanAsBareCodes() {
        val section = GeminiReceipt.categorySection(
            TaxCategories.forCountry(Country.CA).map { it.id to it.label },
        )
        TaxCategories.forCountry(Country.CA).forEach {
            assertTrue(section.contains("${it.id} = ${it.label}"), it.id)
        }
        assertTrue(section.contains("ca_t2125_9281 = Motor vehicle expenses"))
    }

    @Test
    fun theStoreDecidesWhenItOnlyDoesOneTrade() {
        assertTrue(GeminiReceipt.CATEGORY_RULE.contains("tire and auto centre"))
        assertTrue(GeminiReceipt.CATEGORY_RULE.contains("motor vehicle expense"))
        assertTrue(GeminiReceipt.CATEGORY_RULE.contains("only does one trade"))
    }

    @Test
    fun theItemsDecideWhenTheStoreSellsAcrossTrades() {
        val rule = GeminiReceipt.CATEGORY_RULE
        assertTrue(rule.contains("Canadian Tire"))
        assertTrue(rule.contains("paint"))
        assertTrue(rule.contains("repairs and maintenance"))
        assertTrue(rule.contains("oil filter"))
        assertTrue(rule.contains("the line items decide"))
    }

    @Test
    fun aReceiptSpanningTradesFollowsTheMoneyAndNeverInventsAnId() {
        assertTrue(GeminiReceipt.CATEGORY_RULE.contains("most of the money"))
        assertTrue(GeminiReceipt.CATEGORY_RULE.contains("never invent an id"))
        assertTrue(GeminiReceipt.CATEGORY_RULE.contains("Use null"))
    }

    @Test
    fun anEmptyCatalogAsksForNullInsteadOfAnEmptyList() {
        val section = GeminiReceipt.categorySection(emptyList())
        assertEquals(
            "No categories are available, so return null for suggestedCategoryId.",
            section,
        )
        assertFalse(section.contains("Allowed"))
    }
}
