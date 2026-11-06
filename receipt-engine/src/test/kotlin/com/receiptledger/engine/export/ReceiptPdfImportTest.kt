package com.receiptledger.engine.export

import com.receiptledger.engine.model.FieldSource
import com.receiptledger.engine.model.Purpose
import com.receiptledger.engine.model.TaxKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReceiptPdfImportTest {
    @Test
    fun ledgerExportsUsePdfFieldsAndOutsidePdfsAreScanned() {
        assertTrue(ReceiptPdfImport.usesPdfFields(fromLedgerExport = true))
        assertFalse(ReceiptPdfImport.usesPdfFields(fromLedgerExport = false))
        assertFalse(ReceiptPdfImport.scansEachPhoto(fromLedgerExport = true, photoCount = 2))
        assertTrue(ReceiptPdfImport.scansEachPhoto(fromLedgerExport = false, photoCount = 2))
        assertFalse(ReceiptPdfImport.scansEachPhoto(fromLedgerExport = false, photoCount = 0))
    }

    @Test
    fun mapsMoneyPurposeItemsAndBlankDash() {
        val parsed = ReceiptPdfImport.toParsed(
            ImportedPdfReceipt(
                heading = "Receipt 1",
                date = "2026-03-12",
                merchant = "Canadian Tire",
                total = "22.59 CAD",
                subtotal = "20.00",
                tax = "2.59",
                purpose = "business",
                category = "Office expenses",
                gstHstNumber = "123",
                payment = "visa *7403",
                receiptNumber = "004821",
                photoRef = "Appendix A-1",
                lineItems = listOf(ImportedPdfLine("Hammer", "1", "19.99")),
                jpegBytes = null,
            ),
        )
        assertEquals("Canadian Tire", parsed.merchant.value)
        assertEquals("2026-03-12", parsed.date.value)
        assertEquals(22.59, parsed.total.value)
        assertEquals("CAD", parsed.currency.value)
        assertEquals(20.00, parsed.subtotal.value)
        assertEquals(2.59, parsed.taxTotal)
        assertEquals(TaxKind.UNKNOWN, parsed.taxes.single().kind)
        assertEquals(FieldSource.USER, parsed.total.source)
        assertEquals("visa *7403", parsed.paymentMethod.value)
        assertEquals("123", parsed.gstHstNumber.value)
        assertEquals("004821", parsed.receiptNumber.value)
        assertEquals("Hammer", parsed.lineItems.single().name)
        assertEquals(1.0, parsed.lineItems.single().quantity)
        assertEquals(19.99, parsed.lineItems.single().amount)
        assertEquals(Purpose.BUSINESS, ReceiptPdfImport.purposeOf("business"))
        assertEquals(Purpose.PERSONAL, ReceiptPdfImport.purposeOf(ReceiptPdfLayout.MISSING))
        assertNull(ReceiptPdfImport.toParsed(
            ImportedPdfReceipt(
                heading = "Receipt 1",
                date = "?",
                merchant = "-",
                total = null,
                subtotal = null,
                tax = null,
                purpose = "personal",
                category = "?",
                gstHstNumber = null,
                payment = null,
                photoRef = "Appendix A-1",
                lineItems = emptyList(),
                jpegBytes = null,
            ),
        ).date.value)
    }

    @Test
    fun missingDashBecomesEmptyFields() {
        val parsed = ReceiptPdfImport.toParsed(
            ImportedPdfReceipt(
                heading = "Receipt 1",
                date = null,
                merchant = null,
                total = ReceiptPdfLayout.MISSING,
                subtotal = ReceiptPdfLayout.MISSING,
                tax = null,
                purpose = "personal",
                category = ReceiptPdfLayout.MISSING,
                gstHstNumber = null,
                payment = null,
                photoRef = "Appendix A-1",
                lineItems = emptyList(),
                jpegBytes = null,
            ),
        )
        assertNull(parsed.merchant.value)
        assertNull(parsed.total.value)
        assertNull(parsed.currency.value)
        assertTrue(parsed.taxes.isEmpty())
        assertEquals(0.0, parsed.taxTotal)
    }

    @Test
    fun resolveCategoryMatchesLabelOrAsksForANewOne() {
        val catalog = listOf("ca_t2125_8810" to "Office expenses")
        assertEquals("ca_t2125_8810", ReceiptPdfImport.resolveCategory("Office expenses", catalog).id)
        assertEquals(
            "ca_personal_grocery",
            ReceiptPdfImport.resolveCategory(
                "Personal - groceries",
                listOf("ca_personal_grocery" to "Personal — groceries"),
            ).id,
        )
        assertEquals("Client lunch", ReceiptPdfImport.resolveCategory("Client lunch", catalog).newLabel)
        assertNull(ReceiptPdfImport.resolveCategory(ReceiptPdfLayout.MISSING, catalog).id)
        assertNull(ReceiptPdfImport.resolveCategory(null, catalog).newLabel)
    }

    @Test
    fun parseMoneySplitsAmountAndCurrency() {
        assertEquals(22.59 to "CAD", ReceiptPdfImport.parseMoney("22.59 CAD"))
        assertEquals(8.4 to null, ReceiptPdfImport.parseMoney("8.4"))
        assertEquals(null to null, ReceiptPdfImport.parseMoney(ReceiptPdfLayout.MISSING))
    }

    @Test
    fun genericRawTextIsParsedLikeAScan() {
        val parsed = ReceiptPdfImport.fromRawText(
            """
            CANADIAN TIRE
            2026-03-12
            TOTAL 22.59 CAD
            """.trimIndent(),
        )
        assertEquals("2026-03-12", parsed.date.value)
        assertEquals(22.59, parsed.total.value)
        assertTrue(parsed.merchant.value?.contains("Tire", ignoreCase = true) == true)
        assertTrue(ReceiptPdfImport.hasStructuredFields(
            ImportedPdfReceipt(heading = "Receipt 1", merchant = "Store", total = "1.00"),
        ))
        assertTrue(!ReceiptPdfImport.hasStructuredFields(
            ImportedPdfReceipt(heading = "Receipt 1", rawText = "hello"),
        ))
        assertTrue(ReceiptPdfImport.hasUsefulParsedFields(parsed))
        assertFalse(
            ReceiptPdfImport.hasUsefulParsedFields(
                ReceiptPdfImport.toParsed(ImportedPdfReceipt(heading = "Receipt 1")),
            ),
        )
    }
}
