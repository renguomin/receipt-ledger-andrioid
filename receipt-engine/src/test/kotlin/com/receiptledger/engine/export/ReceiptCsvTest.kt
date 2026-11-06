package com.receiptledger.engine.export

import com.receiptledger.engine.model.Purpose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReceiptCsvTest {
    @Test
    fun writesHeaderAndEscapesCommas() {
        val csv = ReceiptCsv.document(
            listOf(
                ReceiptCsvRow(
                    date = "2026-03-12",
                    merchant = "Canadian Tire, Inc",
                    total = "22.59",
                    subtotal = "19.99",
                    tax = "2.60",
                    taxKinds = "HST",
                    currency = "CAD",
                    purpose = Purpose.BUSINESS,
                    categoryId = "ca_t2125_8810",
                    gstHstNumber = "123456789RT0001",
                    paymentMethod = "VISA",
                    lineItems = "Coffee x2 @ 7.50; Muffin @ 3.00",
                    imagePath = "/tmp/a.jpg",
                    dateAdded = "2026-09-04 15:59",
                ),
            ),
        )
        assertTrue(csv.startsWith("date,merchant,total"))
        assertTrue(csv.contains("\"Canadian Tire, Inc\""))
        assertTrue(csv.contains("Office expenses"))
        assertTrue(csv.contains("8521").not())
        assertTrue(csv.contains("8810"))
        assertTrue(csv.contains("line_items"))
        assertTrue(csv.contains("receipt_number"))
        assertTrue(csv.contains("Coffee x2 @ 7.50; Muffin @ 3.00"))
    }

    @Test
    fun bothDatesAreExportedUnderNamesThatSayWhichIsWhich() {
        val csv = ReceiptCsv.document(
            listOf(
                ReceiptCsvRow(
                    date = "2026-03-12",
                    merchant = "Canadian Tire",
                    total = "22.59",
                    subtotal = "19.99",
                    tax = "2.60",
                    taxKinds = "HST",
                    currency = "CAD",
                    purpose = Purpose.BUSINESS,
                    categoryId = "ca_t2125_8810",
                    gstHstNumber = "",
                    paymentMethod = "",
                    imagePath = "/tmp/a.jpg",
                    dateAdded = "2026-09-04 15:59",
                ),
            ),
        )
        val header = csv.lineSequence().first().split(",")
        val row = csv.lineSequence().drop(1).first().split(",")
        assertEquals("2026-03-12", row[header.indexOf("date")])
        assertEquals("2026-09-04 15:59", row[header.indexOf("date_added")])
    }

    @Test
    fun dateAddedIsBlankRatherThanMissingForRowsThatDoNotSupplyIt() {
        val csv = ReceiptCsv.document(
            listOf(
                ReceiptCsvRow(
                    date = "2026-03-12",
                    merchant = "Cafe",
                    total = "5.00",
                    subtotal = "5.00",
                    tax = "",
                    taxKinds = "",
                    currency = "CAD",
                    purpose = Purpose.PERSONAL,
                    categoryId = "",
                    gstHstNumber = "",
                    paymentMethod = "",
                    imagePath = "/tmp/a.jpg",
                ),
            ),
        )
        val header = csv.lineSequence().first().split(",")
        val row = csv.lineSequence().drop(1).first().split(",")
        assertEquals(header.size, row.size)
        assertEquals("", row[header.indexOf("date_added")])
    }

    @Test
    fun customCategoryUsesProvidedLabel() {
        val csv = ReceiptCsv.document(
            listOf(
                ReceiptCsvRow(
                    date = "2026-03-12",
                    merchant = "Cafe",
                    total = "5.00",
                    subtotal = "5.00",
                    tax = "",
                    taxKinds = "",
                    currency = "CAD",
                    purpose = Purpose.PERSONAL,
                    categoryId = "custom_1",
                    categoryLabel = "Client lunch",
                    gstHstNumber = "",
                    paymentMethod = "",
                    imagePath = "/tmp/a.jpg",
                ),
            ),
        )
        assertTrue(csv.contains("Client lunch"))
        assertTrue(csv.contains("custom_1").not())
    }
}
