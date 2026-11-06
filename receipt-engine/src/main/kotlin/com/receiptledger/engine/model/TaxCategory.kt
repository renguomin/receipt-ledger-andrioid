package com.receiptledger.engine.model

enum class Purpose {
    PERSONAL,
    BUSINESS,
    SPLIT,
}

enum class Country {
    CA,
    US,
    NONE,
}

data class TaxCategory(
    val id: String,
    val country: Country,
    val formLine: String,
    val label: String,
    val mealsFiftyPercent: Boolean = false,
    val personal: Boolean = false,
)
