package com.receiptledger.app.export

import com.receiptledger.app.data.ReceiptEntity
import com.receiptledger.engine.export.ReceiptPdfLayout
import com.receiptledger.engine.model.ReceiptRecordLogic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class ReceiptPdfMappingTest {
    @Test
    fun mapsEachReceiptToATableRowAndAppendixImage() {
        val rows = listOf(
            receipt(
                id = "ct",
                merchant = "Canadian Tire",
                dateIso = "2026-03-12",
                total = 22.59,
                categoryId = "custom_1",
                lineItemsJson = """[{"name":"Hammer","quantity":1.0,"amount":19.99}]""",
            ),
            receipt(
                id = "sd",
                merchant = "Shoppers",
                dateIso = "2025-11-02",
                total = 8.40,
                categoryId = "ca_t2125_8521",
            ),
        )
        val inputs = ReceiptPdf.inputsFrom(rows) { id ->
            if (id == "custom_1") "Client lunch" else "Meals"
        }
        assertEquals(listOf("/tmp/ct.jpg", "/tmp/sd.jpg"), inputs.map { it.imagePath })
        assertEquals("Client lunch", inputs[0].category)
        assertEquals("Meals", inputs[1].category)

        val spec = ReceiptPdfLayout.build("Receipts 2026", inputs)
        assertEquals(2, spec.items.size)
        assertEquals("Receipt 1", spec.items[0].heading)
        assertEquals("Appendix A-1", spec.items[0].fields.first { it.label == "Photo" }.value)
        assertEquals("Client lunch", spec.items[0].fields.first { it.label == "Category" }.value)
        assertEquals("Hammer", spec.items[0].lineItems.single().name)
        assertEquals(
            spec.items.map { it.appendixRef },
            spec.items.map { it.caption.substringBefore(" ") },
        )
        assertEquals(rows.map { it.imagePath }, spec.items.map { it.imagePath })
    }

    @Test
    fun theExportedRowCarriesWhenTheReceiptJoinedTheLedger() {
        val addedAt = Instant.parse("2026-09-04T22:59:00Z").toEpochMilli()
        val row = receipt(
            id = "ct",
            merchant = "Canadian Tire",
            dateIso = "2024-01-04",
            total = 22.59,
            categoryId = null,
            createdAt = addedAt,
        )
        val input = ReceiptPdf.inputsFrom(listOf(row)) { "Office expenses" }.single()
        assertEquals("2024-01-04", input.date)
        assertEquals(
            ReceiptRecordLogic.formatDateAdded(addedAt),
            input.dateAdded,
        )

        val item = ReceiptPdfLayout.build("Receipts", listOf(input)).items.single()
        assertEquals("2024-01-04", item.fields.first { it.label == "Purchased date" }.value)
        assertEquals(
            ReceiptRecordLogic.formatDateAdded(addedAt),
            item.fields.first { it.label == "Added date" }.value,
        )
    }

    private fun receipt(
        id: String,
        merchant: String,
        dateIso: String,
        total: Double,
        categoryId: String?,
        lineItemsJson: String = "[]",
        createdAt: Long = 1L,
    ) = ReceiptEntity(
        id = id,
        createdAt = createdAt,
        imagePath = "/tmp/$id.jpg",
        merchant = merchant,
        merchantConfidence = 1f,
        merchantSource = "test",
        dateIso = dateIso,
        dateConfidence = 1f,
        currency = "CAD",
        subtotal = total,
        taxTotal = 0.0,
        taxJson = "[]",
        total = total,
        totalConfidence = 1f,
        paymentMethod = "VISA",
        gstHstNumber = null,
        lineItemsJson = lineItemsJson,
        purpose = "BUSINESS",
        categoryId = categoryId,
        rawText = "",
        year = 2026,
    )
}
