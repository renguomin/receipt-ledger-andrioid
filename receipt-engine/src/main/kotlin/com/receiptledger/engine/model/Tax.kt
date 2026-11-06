package com.receiptledger.engine.model

import kotlinx.serialization.Serializable

enum class TaxKind {
    GST,
    HST,
    PST,
    QST,
    TPS,
    TVQ,
    SALES_TAX,
    UNKNOWN,
}

@Serializable
data class TaxAmount(
    val kind: TaxKind,
    val amount: Double,
    val rate: Double? = null,
    val confidence: Float = 0.8f,
    val source: FieldSource = FieldSource.GEOMETRY,
)

@Serializable
data class LineItem(
    val name: String,
    val quantity: Double? = null,
    val amount: Double? = null,
)
