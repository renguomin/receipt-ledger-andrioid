package com.receiptledger.engine.gemini

import com.receiptledger.engine.alias.MerchantAliasBook
import com.receiptledger.engine.model.Field
import com.receiptledger.engine.model.FieldSource
import com.receiptledger.engine.model.LineItem
import com.receiptledger.engine.model.ParsedReceipt
import com.receiptledger.engine.model.TaxAmount
import com.receiptledger.engine.model.TaxKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReceiptMergerTest {
    @Test
    fun geminiFillsWeakMerchant() {
        val geometry = ParsedReceipt(
            merchant = Field("CDN TIRE", 0.4f, FieldSource.GEOMETRY),
            total = Field(22.59, 0.88f, FieldSource.GEOMETRY),
        )
        val gemini = GeminiReceipt(merchant = "Canadian Tire", total = 22.59)
        val merged = ReceiptMerger.merge(geometry, gemini, MerchantAliasBook())
        assertEquals("Canadian Tire", merged.merchant.value)
        assertEquals(22.59, merged.total.value)
        assertEquals(FieldSource.GEOMETRY, merged.total.source)
    }

    @Test
    fun scanWithAiReplacesAConfidentStoreName() {
        val geometry = ParsedReceipt(
            merchant = Field("Canadian Tire", 1f, FieldSource.USER),
        )
        val merged = ReceiptMerger.merge(
            geometry,
            GeminiReceipt(merchant = "Shoppers Drug Mart"),
            preferGemini = true,
        )
        assertEquals("Shoppers Drug Mart", merged.merchant.value)
        assertEquals(FieldSource.GEMINI, merged.merchant.source)
    }

    @Test
    fun photoAsTruthReplacesEveryFieldTakenFromPdfText() {
        val fromPdfText = ParsedReceipt(
            merchant = Field("Costco", 1f, FieldSource.USER),
            date = Field("2026-08-01", 1f, FieldSource.USER),
            total = Field(212.44, 1f, FieldSource.USER),
            subtotal = Field(200.00, 1f, FieldSource.USER),
            receiptNumber = Field("A-8", 1f, FieldSource.USER),
            paymentMethod = Field("Visa", 1f, FieldSource.USER),
            gstHstNumber = Field("111111111RT0001", 1f, FieldSource.USER),
            taxes = listOf(TaxAmount(TaxKind.GST, 12.44, null, 1f, FieldSource.USER)),
        )
        val fromPhoto = GeminiReceipt(
            merchant = "T&T Supermarket",
            date = "2026-08-03",
            total = 41.18,
            subtotal = 39.22,
            receiptNumber = "0042",
            paymentMethod = "Mastercard",
            gstHstNumber = "222222222RT0001",
            taxes = listOf(GeminiTax(kind = "GST", amount = 1.96)),
        )
        val merged = ReceiptMerger.merge(fromPdfText, fromPhoto, preferGemini = true)
        assertEquals("T&T Supermarket", merged.merchant.value)
        assertEquals("2026-08-03", merged.date.value)
        assertEquals(41.18, merged.total.value)
        assertEquals(39.22, merged.subtotal.value)
        assertEquals("0042", merged.receiptNumber.value)
        assertEquals("222222222RT0001", merged.gstHstNumber.value)
        assertTrue(merged.paymentMethod.value?.contains("astercard", ignoreCase = true) == true)
        assertEquals(listOf(1.96), merged.taxes.map { it.amount })
    }

    @Test
    fun aRescanRenamesAStoreThatALearnedAliasHadRewritten() {
        val misread = ParsedReceipt(merchant = Field("IKEA", 1f, FieldSource.ALIAS))
        val merged = ReceiptMerger.merge(
            misread,
            GeminiReceipt(merchant = "Walmart"),
            MerchantAliasBook(mapOf("IKEA" to "IKEA")),
            preferGemini = true,
        )
        assertEquals("Walmart", merged.merchant.value)
        assertEquals(FieldSource.GEMINI, merged.merchant.source)
    }

    @Test
    fun aLearnedAliasCannotRenameTheStoreTheAiReadOffThePhoto() {
        val poisoned = MerchantAliasBook(mapOf("WALMART" to "IKEA"))
        val merged = ReceiptMerger.merge(
            ParsedReceipt(merchant = Field("IKEA", 1f, FieldSource.ALIAS)),
            GeminiReceipt(merchant = "Walmart"),
            poisoned,
            preferGemini = true,
        )
        assertEquals("Walmart", merged.merchant.value)
    }

    @Test
    fun theBuiltInListStillTidiesWhatTheAiReturns() {
        val merged = ReceiptMerger.merge(
            ParsedReceipt(),
            GeminiReceipt(merchant = "WMT"),
            MerchantAliasBook(),
            preferGemini = true,
        )
        assertEquals("Walmart", merged.merchant.value)
    }

    @Test
    fun aConfidentReadOfThePhotoIsKeptWhenTheAiDisagrees() {
        val fromPhoto = ParsedReceipt(
            total = Field(41.18, 0.95f, FieldSource.GEOMETRY),
            date = Field("2026-08-03", 0.95f, FieldSource.GEOMETRY),
        )
        val merged = ReceiptMerger.merge(
            fromPhoto,
            GeminiReceipt(total = 4.18, date = "2026-01-03"),
            preferGemini = false,
        )
        assertEquals(41.18, merged.total.value)
        assertEquals("2026-08-03", merged.date.value)
    }

    @Test
    fun aliasBeatsGeminiWhenAlreadyResolved() {
        val geometry = ParsedReceipt(
            merchant = Field("Canadian Tire", 0.92f, FieldSource.ALIAS),
        )
        val gemini = GeminiReceipt(merchant = "Some Other Store")
        val merged = ReceiptMerger.merge(geometry, gemini)
        assertEquals("Canadian Tire", merged.merchant.value)
        assertEquals(FieldSource.ALIAS, merged.merchant.source)
    }

    @Test
    fun geminiLineItemsFillTheRecordAndBlankNamesAreDropped() {
        val geometry = ParsedReceipt()
        val gemini = GeminiReceipt(
            lineItems = listOf(
                GeminiLineItem(name = "Coffee", quantity = 2.0, amount = 7.5),
                GeminiLineItem(name = "  ", quantity = 1.0, amount = 1.0),
                GeminiLineItem(name = null, amount = 3.0),
            ),
        )
        val merged = ReceiptMerger.merge(geometry, gemini)
        assertEquals(listOf(LineItem("Coffee", 2.0, 7.5)), merged.lineItems)
    }

    @Test
    fun emptyGeminiLineItemsLeaveTheListEmpty() {
        val geometry = ParsedReceipt()
        val merged = ReceiptMerger.merge(geometry, GeminiReceipt(lineItems = emptyList()))
        assertTrue(merged.lineItems.isEmpty())
    }

    @Test
    fun geminiAddsCardLastFourToABrandOnlyPayment() {
        val geometry = ParsedReceipt(
            paymentMethod = Field("visa", 0.75f, FieldSource.GEOMETRY),
        )
        val merged = ReceiptMerger.merge(geometry, GeminiReceipt(paymentMethod = "Visa ****7403"))
        assertEquals("visa *7403", merged.paymentMethod.value)
        assertEquals(FieldSource.GEMINI, merged.paymentMethod.source)
    }

    @Test
    fun geminiFillsAMissingReceiptNumber() {
        val geometry = ParsedReceipt()
        val merged = ReceiptMerger.merge(
            geometry,
            GeminiReceipt(receiptNumber = "004821"),
        )
        assertEquals("004821", merged.receiptNumber.value)
        assertEquals(FieldSource.GEMINI, merged.receiptNumber.source)
    }
}
