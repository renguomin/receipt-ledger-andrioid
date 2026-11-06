package com.receiptledger.engine.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

class ReceiptRecordLogicTest {
    @Test
    fun promptFieldsCoverTheGeminiJsonShape() {
        assertEquals(
            listOf(
                "merchant",
                "date",
                "currency",
                "subtotal",
                "taxes",
                "total",
                "paymentMethod",
                "gstHstNumber",
                "receiptNumber",
                "lineItems",
                "suggestedCategoryId",
            ),
            ReceiptRecordLogic.promptFields,
        )
    }

    @Test
    fun emptyLineItemsStayEmptyAndFormatAsMissing() {
        assertEquals(emptyList<LineItem>(), ReceiptRecordLogic.compactLineItems(emptyList()))
        assertEquals("", ReceiptRecordLogic.formatLineItems(emptyList()))
        assertEquals(ReceiptRecordLogic.MISSING, ReceiptRecordLogic.itemsDisplay(emptyList()))
        // A hand-added row starts at one unit, the same default a scanned line gets.
        assertEquals(
            listOf(LineItem("", LineItemQuantity.DEFAULT, null)),
            ReceiptRecordLogic.addLineItem(emptyList()),
        )
    }

    @Test
    fun compactDropsBlankNamesAndFormatsDetectedItems() {
        val items = listOf(
            LineItem(" Coffee ", 2.0, 7.5),
            LineItem("  ", 1.0, 1.0),
            LineItem("Muffin", null, 3.0),
        )
        assertEquals(
            listOf(LineItem("Coffee", 2.0, 7.5), LineItem("Muffin", null, 3.0)),
            ReceiptRecordLogic.compactLineItems(items),
        )
        assertEquals("Coffee x2 @ 7.50", ReceiptRecordLogic.formatLineItem(items[0]))
        assertEquals(
            "Coffee x2 @ 7.50; Muffin @ 3.00",
            ReceiptRecordLogic.formatLineItems(items),
        )
    }

    @Test
    fun lineItemEditsUpdateOnlyTheChosenRow() {
        val start = listOf(LineItem("Coffee", 1.0, 4.0))
        val renamed = ReceiptRecordLogic.updateLineItem(start, 0, name = "Tea")
        val qty = ReceiptRecordLogic.updateLineItem(renamed, 0, quantityRaw = "2")
        val cleared = ReceiptRecordLogic.updateLineItem(qty, 0, amountRaw = "")
        assertEquals(LineItem("Tea", 2.0, null), cleared.single())
        assertEquals(emptyList<LineItem>(), ReceiptRecordLogic.removeLineItem(cleared, 0))
        assertEquals(start, ReceiptRecordLogic.updateLineItem(start, 4, name = "Nope"))
    }

    @Test
    fun detailIncludesEveryPromptFieldAndEmptyItems() {
        val parsed = ParsedReceipt(
            date = Field("2026-03-12", 1f, FieldSource.USER),
            currency = Field("CAD", 1f, FieldSource.USER),
            subtotal = Field(19.99, 1f, FieldSource.USER),
            taxes = listOf(TaxAmount(TaxKind.HST, 2.60, 0.13)),
            total = Field(22.59, 1f, FieldSource.USER),
            paymentMethod = Field("VISA", 1f, FieldSource.USER),
            gstHstNumber = Field("123456789RT0001", 1f, FieldSource.USER),
            receiptNumber = Field("004821", 1f, FieldSource.USER),
            lineItems = emptyList(),
        )
        val rows = ReceiptRecordLogic.detailFields(parsed, "BUSINESS", "Office expenses")
        assertEquals(
            listOf(
                "Purchased date" to "2026-03-12",
                "Receipt number" to "004821",
                "Total" to "22.59 CAD",
                "Subtotal" to "19.99",
                "Tax" to "HST 2.60 (13%)",
                "Currency" to "CAD",
                "GST/HST number" to "123456789RT0001",
                "Payment" to "VISA",
                "Items" to ReceiptRecordLogic.MISSING,
                "Purpose" to "business",
                "Category" to "Office expenses",
            ),
            rows.map { it.label to it.value },
        )
    }

    @Test
    fun detailNamesTheTwoDatesApartAndOmitsDateAddedWhenNotSupplied() {
        val parsed = ParsedReceipt(date = Field("2026-03-12", 1f, FieldSource.USER))
        val withoutAdded = ReceiptRecordLogic.detailFields(parsed, "PERSONAL", null)
        assertTrue(withoutAdded.none { it.label == ReceiptRecordLogic.ADDED_DATE_LABEL })

        val rows = ReceiptRecordLogic.detailFields(
            parsed = parsed,
            purpose = "PERSONAL",
            category = null,
            dateAdded = "Sep 4, 2026 at 3:59 PM",
        )
        assertEquals("Purchased date", ReceiptRecordLogic.PURCHASED_DATE_LABEL)
        assertEquals("Added date", ReceiptRecordLogic.ADDED_DATE_LABEL)
        assertEquals("2026-03-12", rows.first { it.label == "Purchased date" }.value)
        assertEquals("Sep 4, 2026 at 3:59 PM", rows.first { it.label == "Added date" }.value)
    }

    @Test
    fun theLabelAndPhraseFormsOfEachDateUseTheSameVerb() {
        assertEquals("Purchased on 2026-03-12", ReceiptRecordLogic.purchasedNote("2026-03-12"))
        assertEquals(
            "Added on Sep 4, 2026 at 3:59 PM",
            ReceiptRecordLogic.addedNote("Sep 4, 2026 at 3:59 PM"),
        )
        assertTrue(
            ReceiptRecordLogic.PURCHASED_DATE_LABEL
                .startsWith(ReceiptRecordLogic.PURCHASED_ON_PREFIX.substringBefore(' ')),
        )
        assertTrue(
            ReceiptRecordLogic.ADDED_DATE_LABEL
                .startsWith(ReceiptRecordLogic.ADDED_ON_PREFIX.substringBefore(' ')),
        )
    }

    @Test
    fun dateAddedReadsAsAMomentForPeopleAndSortsForSpreadsheets() {
        val utc = TimeZone.getTimeZone("UTC")
        val addedAt = Instant.parse("2026-02-25T18:13:20Z").toEpochMilli()
        assertEquals(
            "Feb 25, 2026 at 6:13 PM",
            ReceiptRecordLogic.formatDateAdded(addedAt, Locale.US, utc),
        )
        assertEquals(
            "2026-02-25 18:13",
            ReceiptRecordLogic.dateAddedForExport(addedAt, utc),
        )
    }

    @Test
    fun detailListsLineItemsWhenDetected() {
        val parsed = ParsedReceipt(
            lineItems = listOf(LineItem("Hammer", 1.0, 19.99)),
        )
        val items = ReceiptRecordLogic.detailFields(parsed, "PERSONAL", null)
            .first { it.label == "Items" }
        assertEquals("Hammer x1 @ 19.99", items.value)
        assertTrue(
            ReceiptRecordLogic.detailFields(parsed, "PERSONAL", null)
                .any { it.label == "Items" },
        )
    }
}
