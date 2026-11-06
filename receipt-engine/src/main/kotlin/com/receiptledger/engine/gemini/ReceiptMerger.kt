package com.receiptledger.engine.gemini

import com.receiptledger.engine.alias.MerchantAliasBook
import com.receiptledger.engine.model.Field
import com.receiptledger.engine.model.FieldSource
import com.receiptledger.engine.model.LineItem
import com.receiptledger.engine.model.LineItemQuantity
import com.receiptledger.engine.model.ParsedReceipt
import com.receiptledger.engine.model.ReceiptRecordLogic
import com.receiptledger.engine.model.TaxAmount
import com.receiptledger.engine.model.TaxKind
import com.receiptledger.engine.parse.PaymentMethodParser
import kotlin.math.abs

object ReceiptMerger {
    /**
     * [preferGemini] makes the photo the source of truth: every field Gemini read from the image
     * replaces what is on screen. Use it when the shown fields did not come from the shown photo,
     * such as text lifted out of a PDF the app did not write, so that a rescan cannot leave the
     * details describing a different receipt.
     */
    fun merge(
        geometry: ParsedReceipt,
        gemini: GeminiReceipt?,
        aliases: MerchantAliasBook = MerchantAliasBook(),
        preferGemini: Boolean = false,
    ): ParsedReceipt {
        if (gemini == null) return geometry
        return geometry.copy(
            merchant = pickMerchant(
                geometry.merchant,
                gemini.merchant,
                aliases,
                preferGemini,
            ),
            date = pickString(geometry.date, gemini.date, 0.8f, preferGemini),
            currency = pickString(geometry.currency, gemini.currency, 0.75f, preferGemini),
            subtotal = pickMoney(geometry.subtotal, gemini.subtotal, preferGemini),
            taxes = mergeTaxes(geometry.taxes, gemini.taxes, preferGemini),
            total = pickMoney(geometry.total, gemini.total, preferGemini),
            paymentMethod = pickPayment(geometry.paymentMethod, gemini.paymentMethod, preferGemini),
            gstHstNumber = pickString(geometry.gstHstNumber, gemini.gstHstNumber, 0.8f, preferGemini),
            receiptNumber = pickString(geometry.receiptNumber, gemini.receiptNumber, 0.82f, preferGemini),
            lineItems = if (gemini.lineItems.isNotEmpty()) {
                LineItemQuantity.resolveAll(
                    ReceiptRecordLogic.compactLineItems(
                        gemini.lineItems.map { item ->
                            LineItem(
                                name = item.name.orEmpty(),
                                quantity = item.quantity,
                                amount = item.amount,
                            )
                        },
                    ),
                )
            } else {
                geometry.lineItems
            },
            suggestedCategoryId = pickString(
                geometry.suggestedCategoryId,
                gemini.suggestedCategoryId,
                0.7f,
                preferGemini,
            ),
        )
    }

    private fun pickMerchant(
        geometry: Field<String>,
        geminiRaw: String?,
        aliases: MerchantAliasBook,
        preferGemini: Boolean,
    ): Field<String> {
        val geminiAlias = if (preferGemini) {
            aliases.seedCanonicalOrNull(geminiRaw) ?: geminiRaw?.trim()
        } else {
            aliases.canonicalOrNull(geminiRaw) ?: geminiRaw?.trim()
        }
        val geoAlias = aliases.canonicalOrNull(geometry.value)
        return when {
            !geminiAlias.isNullOrBlank() && preferGemini ->
                Field(geminiAlias, 0.86f, FieldSource.GEMINI)
            geoAlias != null && geometry.source == FieldSource.ALIAS -> geometry
            !geminiAlias.isNullOrBlank() && (geometry.isUncertain || geometry.value.isNullOrBlank()) ->
                Field(geminiAlias, 0.82f, FieldSource.GEMINI)
            !geminiAlias.isNullOrBlank() && geometry.value.equals(geminiAlias, ignoreCase = true) ->
                geometry.copy(confidence = maxOf(geometry.confidence, 0.9f))
            !geminiAlias.isNullOrBlank() && geometry.confidence < 0.75f ->
                Field(geminiAlias, 0.78f, FieldSource.GEMINI)
            else -> geometry
        }
    }

    private fun pickPayment(
        geometry: Field<String>,
        gemini: String?,
        preferGemini: Boolean,
    ): Field<String> {
        if (preferGemini && !gemini.isNullOrBlank()) {
            val fromPhoto = PaymentMethodParser.normalize(gemini) ?: gemini.trim()
            return Field(fromPhoto, 0.86f, FieldSource.GEMINI)
        }
        val geoNorm = geometry.value?.let { PaymentMethodParser.normalize(it) }
        val chosen = PaymentMethodParser.prefer(geometry.value, gemini) ?: return geometry
        return if (chosen == geoNorm || chosen == geometry.value) {
            geometry.copy(value = chosen)
        } else {
            Field(chosen, 0.78f, FieldSource.GEMINI)
        }
    }

    private fun pickString(
        geometry: Field<String>,
        gemini: String?,
        geminiConfidence: Float,
        preferGemini: Boolean,
    ): Field<String> {
        val cleaned = gemini?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() != "null" }
        return when {
            preferGemini && cleaned != null -> Field(cleaned, geminiConfidence, FieldSource.GEMINI)
            !geometry.value.isNullOrBlank() && !geometry.isUncertain -> geometry
            cleaned != null -> Field(cleaned, geminiConfidence, FieldSource.GEMINI)
            else -> geometry
        }
    }

    private fun pickMoney(
        geometry: Field<Double>,
        gemini: Double?,
        preferGemini: Boolean,
    ): Field<Double> {
        if (gemini == null) return geometry
        if (preferGemini) return Field(gemini, 0.86f, FieldSource.GEMINI)
        val geo = geometry.value
        return when {
            geo != null && abs(geo - gemini) < 0.02 ->
                geometry.copy(confidence = maxOf(geometry.confidence, 0.93f))
            geo != null && !geometry.isUncertain -> geometry
            else -> Field(gemini, 0.8f, FieldSource.GEMINI)
        }
    }

    private fun mergeTaxes(
        geometry: List<TaxAmount>,
        gemini: List<GeminiTax>,
        preferGemini: Boolean,
    ): List<TaxAmount> {
        if (geometry.isNotEmpty() && !(preferGemini && gemini.isNotEmpty())) return geometry
        return gemini.mapNotNull { tax ->
            val amount = tax.amount ?: return@mapNotNull null
            val kind = runCatching {
                TaxKind.valueOf(tax.kind?.uppercase()?.replace(" ", "_") ?: "UNKNOWN")
            }.getOrDefault(TaxKind.UNKNOWN)
            TaxAmount(kind, amount, tax.rate, 0.75f, FieldSource.GEMINI)
        }
    }
}
