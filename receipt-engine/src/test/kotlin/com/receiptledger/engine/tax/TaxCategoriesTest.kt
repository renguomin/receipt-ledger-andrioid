package com.receiptledger.engine.tax

import com.receiptledger.engine.model.Country
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TaxCategoriesTest {
    @Test
    fun canadaIncludesMealsFiftyPercent() {
        val meals = TaxCategories.find("ca_t2125_8523")!!
        assertTrue(meals.mealsFiftyPercent)
        assertEquals("8523", meals.formLine)
    }

    @Test
    fun countrySwitchChangesList() {
        assertTrue(TaxCategories.forCountry(Country.CA).all { it.country == Country.CA })
        assertTrue(TaxCategories.forCountry(Country.US).all { it.country == Country.US })
        assertTrue(TaxCategories.forCountry(Country.NONE).isEmpty())
    }

    @Test
    fun noneDoesNotSuggestAPredefinedCategory() {
        val guess = CategorySuggester.suggest(
            country = Country.NONE,
            merchant = "Starbucks",
            rawText = "LATTE TOTAL 5.00",
        )
        assertEquals(null, guess.value)
    }
}
