package com.receiptledger.app.ui

import com.receiptledger.engine.model.Field
import com.receiptledger.engine.model.FieldSource
import com.receiptledger.engine.model.LineItem
import com.receiptledger.engine.model.ParsedReceipt
import com.receiptledger.engine.model.ReceiptRecordLogic
import com.receiptledger.engine.model.TaxAmount
import com.receiptledger.engine.model.TaxKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DetailScreenLogicTest {
    @Test
    fun groupsFieldsIntoHomeStyleSectionsAndTitleCasesPurpose() {
        val parsed = ParsedReceipt(
            date = Field("2026-03-12", 1f, FieldSource.USER),
            currency = Field("CAD", 1f, FieldSource.USER),
            subtotal = Field(19.99, 1f, FieldSource.USER),
            taxes = listOf(TaxAmount(TaxKind.HST, 2.60, 0.13)),
            total = Field(22.59, 1f, FieldSource.USER),
            paymentMethod = Field("visa *1234", 1f, FieldSource.USER),
            gstHstNumber = Field("123456789RT0001", 1f, FieldSource.USER),
            receiptNumber = Field("004821", 1f, FieldSource.USER),
            lineItems = listOf(LineItem("Hammer", 1.0, 19.99)),
        )
        val presented = DetailScreenLogic.present(
            ReceiptRecordLogic.detailFields(parsed, "BUSINESS", "Office expenses"),
        )
        assertEquals("22.59 CAD", presented.total)
        assertEquals("Purchased on 2026-03-12", presented.purchasedOn)
        assertEquals(
            listOf("Details", "Amounts", "Items", "This purchase is"),
            presented.sections.map { it.title },
        )
        assertNull(presented.addedOn)
        assertEquals(
            listOf("Receipt number", "Payment", "GST/HST number"),
            presented.sections[0].rows.map { it.label },
        )
        assertEquals("Business", presented.sections.last().rows.first { it.label == "Purpose" }.value)
        assertEquals("Hammer x1 @ 19.99", presented.sections[2].rows.single().value)
        assertFalse(presented.sections[1].rows.any { it.label == "Total" })
    }

    @Test
    fun bothDatesUnderTheTotalSayWhichIsWhichAndNeitherIsASectionRow() {
        val presented = DetailScreenLogic.present(
            ReceiptRecordLogic.detailFields(
                parsed = ParsedReceipt(date = Field("2026-03-12", 1f, FieldSource.USER)),
                purpose = "BUSINESS",
                category = "Office expenses",
                dateAdded = "Sep 4, 2026 at 3:59 PM",
            ),
        )
        assertEquals("Purchased on 2026-03-12", presented.purchasedOn)
        assertEquals("Added on Sep 4, 2026 at 3:59 PM", presented.addedOn)
        assertFalse(presented.addedOn!!.contains('\n'))
        // No section carries either, so neither reads as one of the receipt's own fields.
        assertEquals(listOf("Items", "This purchase is"), presented.sections.map { it.title })
        assertFalse(
            presented.sections.any { section ->
                section.rows.any {
                    it.label == ReceiptRecordLogic.ADDED_DATE_LABEL ||
                        it.label == ReceiptRecordLogic.PURCHASED_DATE_LABEL
                }
            },
        )
    }

    @Test
    fun aReceiptWithNoPrintedDateShowsNoPurchasedLineAtAll() {
        val presented = DetailScreenLogic.present(
            ReceiptRecordLogic.detailFields(
                parsed = ParsedReceipt(),
                purpose = "PERSONAL",
                category = null,
                dateAdded = "Sep 4, 2026 at 3:59 PM",
            ),
        )
        assertNull(presented.purchasedOn)
        assertEquals("Added on Sep 4, 2026 at 3:59 PM", presented.addedOn)
    }

    @Test
    fun hidesBlankOptionalRowsAndShowsNoneFoundForItems() {
        val presented = DetailScreenLogic.present(
            ReceiptRecordLogic.detailFields(ParsedReceipt(), "PERSONAL", null),
        )
        val labels = presented.sections.flatMap { section -> section.rows.map { it.label } }
        assertFalse(labels.contains("Payment"))
        assertFalse(labels.contains("GST/HST number"))
        assertFalse(labels.contains("Receipt number"))
        assertEquals("None found", presented.sections.first { it.title == "Items" }.rows.single().value)
        assertEquals("Personal", presented.sections.last().rows.first { it.label == "Purpose" }.value)
        assertEquals(listOf("None found"), DetailScreenLogic.itemLines(DetailScreenLogic.EMPTY_ITEMS))
        assertTrue(DetailScreenLogic.MEALS_NOTE.contains("half"))
    }
}
