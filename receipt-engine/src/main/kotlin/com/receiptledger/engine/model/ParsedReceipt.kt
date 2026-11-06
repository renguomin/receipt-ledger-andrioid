package com.receiptledger.engine.model

data class ParsedReceipt(
    val merchant: Field<String> = Field.missing(),
    val date: Field<String> = Field.missing(),
    val currency: Field<String> = Field.missing(),
    val subtotal: Field<Double> = Field.missing(),
    val taxes: List<TaxAmount> = emptyList(),
    val total: Field<Double> = Field.missing(),
    val paymentMethod: Field<String> = Field.missing(),
    val gstHstNumber: Field<String> = Field.missing(),
    val receiptNumber: Field<String> = Field.missing(),
    val lineItems: List<LineItem> = emptyList(),
    val suggestedCategoryId: Field<String> = Field.missing(),
    val rawText: String = "",
) {
    val taxTotal: Double get() = taxes.sumOf { it.amount }
}
