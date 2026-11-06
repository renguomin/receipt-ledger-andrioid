package com.receiptledger.engine.tax

import com.receiptledger.engine.model.Country
import com.receiptledger.engine.model.TaxCategory

object TaxCategories {
    val canada = listOf(
        TaxCategory("ca_personal_grocery", Country.CA, "", "Personal — groceries", personal = true),
        TaxCategory("ca_personal_other", Country.CA, "", "Personal — other", personal = true),
        TaxCategory("ca_t2125_8521", Country.CA, "8521", "Advertising"),
        TaxCategory(
            "ca_t2125_8523",
            Country.CA,
            "8523",
            "Meals and entertainment (50%)",
            mealsFiftyPercent = true,
        ),
        TaxCategory("ca_t2125_8690", Country.CA, "8690", "Insurance"),
        TaxCategory("ca_t2125_8710", Country.CA, "8710", "Interest and bank charges"),
        TaxCategory("ca_t2125_8760", Country.CA, "8760", "Business taxes, licences, memberships"),
        TaxCategory("ca_t2125_8810", Country.CA, "8810", "Office expenses"),
        TaxCategory("ca_t2125_8811", Country.CA, "8811", "Office stationery and supplies"),
        TaxCategory("ca_t2125_8860", Country.CA, "8860", "Professional fees"),
        TaxCategory("ca_t2125_8871", Country.CA, "8871", "Management and administration fees"),
        TaxCategory("ca_t2125_8910", Country.CA, "8910", "Rent"),
        TaxCategory("ca_t2125_8960", Country.CA, "8960", "Repairs and maintenance"),
        TaxCategory("ca_t2125_9200", Country.CA, "9200", "Travel"),
        TaxCategory("ca_t2125_9220", Country.CA, "9220", "Utilities"),
        TaxCategory("ca_t2125_9224", Country.CA, "9224", "Fuel (except motor vehicles)"),
        TaxCategory("ca_t2125_9275", Country.CA, "9275", "Delivery, freight and express"),
        TaxCategory("ca_t2125_9281", Country.CA, "9281", "Motor vehicle expenses"),
        TaxCategory("ca_t2125_9270", Country.CA, "9270", "Other expenses"),
    )

    val unitedStates = listOf(
        TaxCategory("us_personal_grocery", Country.US, "", "Personal — groceries", personal = true),
        TaxCategory("us_personal_other", Country.US, "", "Personal — other", personal = true),
        TaxCategory("us_schc_advertising", Country.US, "Sch C 8", "Advertising"),
        TaxCategory("us_schc_car", Country.US, "Sch C 9", "Car and truck"),
        TaxCategory("us_schc_commissions", Country.US, "Sch C 10", "Commissions and fees"),
        TaxCategory("us_schc_contract", Country.US, "Sch C 11", "Contract labor"),
        TaxCategory("us_schc_insurance", Country.US, "Sch C 15", "Insurance"),
        TaxCategory("us_schc_legal", Country.US, "Sch C 17", "Legal and professional"),
        TaxCategory("us_schc_office", Country.US, "Sch C 18", "Office expense"),
        TaxCategory("us_schc_rent", Country.US, "Sch C 20", "Rent or lease"),
        TaxCategory("us_schc_supplies", Country.US, "Sch C 22", "Supplies"),
        TaxCategory(
            "us_schc_meals",
            Country.US,
            "Sch C 24b",
            "Meals (50%)",
            mealsFiftyPercent = true,
        ),
        TaxCategory("us_schc_travel", Country.US, "Sch C 24a", "Travel"),
        TaxCategory("us_schc_utilities", Country.US, "Sch C 25", "Utilities"),
        TaxCategory("us_schc_other", Country.US, "Sch C 27a", "Other expenses"),
    )

    fun forCountry(country: Country): List<TaxCategory> = when (country) {
        Country.CA -> canada
        Country.US -> unitedStates
        Country.NONE -> emptyList()
    }

    fun find(id: String): TaxCategory? =
        (canada + unitedStates).firstOrNull { it.id == id }

    fun defaultPersonal(country: Country): TaxCategory? = when (country) {
        Country.CA -> canada.first { it.id == "ca_personal_other" }
        Country.US -> unitedStates.first { it.id == "us_personal_other" }
        Country.NONE -> null
    }
}
