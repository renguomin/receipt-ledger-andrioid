package com.receiptledger.engine.tax

import com.receiptledger.engine.model.Country
import com.receiptledger.engine.model.Field
import com.receiptledger.engine.model.FieldSource

object CategorySuggester {
    fun suggest(
        country: Country,
        merchant: String?,
        rawText: String,
    ): Field<String> {
        val blob = listOfNotNull(merchant, rawText).joinToString(" ").uppercase()
        if (country == Country.NONE) return Field.missing()
        val id = when {
            matches(blob, "TIM HORTONS", "STARBUCKS", "MCDONALD", "RESTAURANT", "CAFE", "COFFEE") ->
                meals(country)
            matches(blob, "ESSO", "PETRO", "SHELL", "PIONEER", "GAS", "FUEL") ->
                vehicle(country)
            matches(blob, "STAPLES", "OFFICE", "PAPER", "INK") ->
                office(country)
            matches(blob, "LOBLAWS", "METRO", "SOBEYS", "WALMART", "COSTCO", "GROCERY") ->
                grocery(country)
            matches(blob, "CANADIAN TIRE", "HOME DEPOT", "RONA", "IKEA") ->
                supplies(country)
            else -> null
        } ?: return Field.missing()
        return Field(id, 0.55f, FieldSource.GEOMETRY)
    }

    private fun matches(blob: String, vararg needles: String): Boolean =
        needles.any { blob.contains(it) }

    private fun meals(country: Country) = when (country) {
        Country.CA -> "ca_t2125_8523"
        Country.US -> "us_schc_meals"
        Country.NONE -> null
    }

    private fun vehicle(country: Country) = when (country) {
        Country.CA -> "ca_t2125_9281"
        Country.US -> "us_schc_car"
        Country.NONE -> null
    }

    private fun office(country: Country) = when (country) {
        Country.CA -> "ca_t2125_8811"
        Country.US -> "us_schc_office"
        Country.NONE -> null
    }

    private fun grocery(country: Country) = when (country) {
        Country.CA -> "ca_personal_grocery"
        Country.US -> "us_personal_grocery"
        Country.NONE -> null
    }

    private fun supplies(country: Country) = when (country) {
        Country.CA -> "ca_t2125_8810"
        Country.US -> "us_schc_supplies"
        Country.NONE -> null
    }
}
