package com.receiptledger.engine.export

import com.receiptledger.engine.model.LineItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReceiptPdfLayoutTest {
    @Test
    fun eachReceiptGetsATableAndMatchingAppendixImage() {
        val spec = ReceiptPdfLayout.build(
            title = "Receipts 2026",
            rows = listOf(
                row(
                    date = "2026-03-12",
                    merchant = "Canadian Tire",
                    total = 22.59,
                    imagePath = "/tmp/ct.jpg",
                ),
                row(
                    date = "2025-11-02",
                    merchant = "Shoppers",
                    total = 8.4,
                    imagePath = "/tmp/sd.jpg",
                ),
            ),
        )

        assertEquals("Receipts 2026", spec.title)
        assertEquals(ReceiptPdfLayout.DISCLAIMER, spec.disclaimer)
        assertEquals(ReceiptPdfLayout.APPENDIX_TITLE, spec.appendixTitle)
        assertEquals(2, spec.items.size)

        val first = spec.items[0]
        assertEquals(1, first.number)
        assertEquals("Receipt 1", first.heading)
        assertEquals("A-1", first.appendixRef)
        assertEquals("/tmp/ct.jpg", first.imagePath)
        assertEquals("A-1  Canadian Tire  ·  2026-03-12", first.caption)
        assertEquals("22.59 CAD", first.field("Total"))
        assertEquals("Appendix A-1", first.field("Photo"))

        val second = spec.items[1]
        assertEquals("A-2", second.appendixRef)
        assertEquals("/tmp/sd.jpg", second.imagePath)
        assertEquals(listOf("A-1", "A-2"), spec.items.map { it.appendixRef })
        assertEquals(listOf("/tmp/ct.jpg", "/tmp/sd.jpg"), spec.items.map { it.imagePath })
    }

    @Test
    fun missingValuesAndCustomCategoryStayReadable() {
        val spec = ReceiptPdfLayout.build(
            title = "Receipts",
            rows = listOf(
                row(
                    date = null,
                    merchant = "  ",
                    total = null,
                    subtotal = null,
                    tax = null,
                    currency = null,
                    purpose = "PERSONAL",
                    category = "Client lunch",
                    gstHstNumber = "",
                    paymentMethod = null,
                    imagePath = "/tmp/x.jpg",
                ),
            ),
        )
        val item = spec.items.single()
        assertEquals("—", item.field("Purchased date"))
        assertEquals("—", item.field("Added date"))
        assertEquals("—", item.field("Merchant"))
        assertEquals("—", item.field("Total"))
        assertEquals("personal", item.field("Purpose"))
        assertEquals("Client lunch", item.field("Category"))
        assertEquals("—", item.field("GST/HST number"))
        assertEquals("—", item.field("Receipt number"))
        assertEquals("A-1  —  ·  —", item.caption)
    }

    @Test
    fun theTableNamesBothDatesAndPrintsThemAsSeparateRows() {
        val spec = ReceiptPdfLayout.build(
            title = "Receipts",
            rows = listOf(
                row(
                    date = "2024-01-04",
                    merchant = "Canadian Tire",
                    dateAdded = "Sep 4, 2026 at 3:59 PM",
                    imagePath = "/tmp/ct.jpg",
                ),
            ),
        )
        val item = spec.items.single()
        assertEquals("2024-01-04", item.field("Purchased date"))
        assertEquals("Sep 4, 2026 at 3:59 PM", item.field("Added date"))
        assertFalse(item.fields.any { it.label == "Date" })
        // The caption identifies the purchase, so it keeps the receipt's own date.
        assertEquals("A-1  Canadian Tire  ·  2024-01-04", item.caption)
    }

    @Test
    fun lineItemsFromJsonAppearOnThatReceiptTableOnly() {
        val spec = ReceiptPdfLayout.build(
            title = "Receipts",
            rows = listOf(
                row(
                    merchant = "Cafe",
                    lineItemsJson = """[{"name":"Coffee","quantity":2.0,"amount":7.5},{"name":"  ","amount":1.0}]""",
                    imagePath = "/tmp/cafe.jpg",
                ),
                row(merchant = "No items", imagePath = "/tmp/empty.jpg"),
            ),
        )
        assertEquals(
            listOf(ReceiptPdfLine("Coffee", "2", "7.50")),
            spec.items[0].lineItems,
        )
        assertTrue(spec.items[1].lineItems.isEmpty())
    }

    @Test
    fun suppliedLineItemsAreKeptAndBlankNamesDropped() {
        val spec = ReceiptPdfLayout.build(
            title = "Receipts",
            rows = listOf(
                row(
                    merchant = "Hardware",
                    lineItems = listOf(
                        LineItem("Hammer", 1.0, 19.99),
                        LineItem(" ", null, 4.0),
                    ),
                    imagePath = "/tmp/hw.jpg",
                ),
            ),
        )
        assertEquals(listOf(ReceiptPdfLine("Hammer", "1", "19.99")), spec.items[0].lineItems)
    }

    @Test
    fun emptyExportHasNoAppendixRows() {
        val spec = ReceiptPdfLayout.build("Receipts", emptyList())
        assertTrue(spec.items.isEmpty())
        assertEquals(ReceiptPdfLayout.APPENDIX_TITLE, spec.appendixTitle)
    }

    @Test
    fun labelsMatchTreatsDashVariantsAsTheSame() {
        assertTrue(ReceiptPdfLayout.labelsMatch("Personal — groceries", "Personal - groceries"))
        assertTrue(ReceiptPdfLayout.labelsMatch("Appendix A — Receipt images", "Appendix A - Receipt images"))
        assertFalse(ReceiptPdfLayout.labelsMatch("Office expenses", "Meals"))
    }

    @Test
    fun invalidLineItemJsonIsIgnored() {
        assertTrue(ReceiptPdfLayout.decodeLineItems("not-json").isEmpty())
        assertTrue(ReceiptPdfLayout.decodeLineItems("").isEmpty())
    }

    private fun ReceiptPdfItem.field(label: String): String =
        fields.first { it.label == label }.value

    private fun row(
        date: String? = "2026-03-12",
        merchant: String? = "Store",
        total: Double? = 10.0,
        subtotal: Double? = 9.0,
        tax: Double? = 1.0,
        currency: String? = "CAD",
        purpose: String = "BUSINESS",
        category: String? = "Office expenses",
        gstHstNumber: String? = "123",
        paymentMethod: String? = "VISA",
        lineItemsJson: String = "[]",
        lineItems: List<LineItem> = emptyList(),
        imagePath: String,
        dateAdded: String? = null,
    ) = ReceiptPdfRowInput(
        date = date,
        merchant = merchant,
        total = total,
        subtotal = subtotal,
        tax = tax,
        currency = currency,
        purpose = purpose,
        category = category,
        gstHstNumber = gstHstNumber,
        paymentMethod = paymentMethod,
        lineItemsJson = lineItemsJson,
        lineItems = lineItems,
        imagePath = imagePath,
        dateAdded = dateAdded,
    )
}
