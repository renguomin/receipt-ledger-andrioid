package com.receiptledger.engine.parse

import com.receiptledger.engine.listOcrFixtureNames
import com.receiptledger.engine.loadOcrExpected
import com.receiptledger.engine.loadOcrFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class OcrResourceFixtureTest {
    private val parser = GeometryReceiptParser()

    @Test
    fun fixturesFolderIsNotEmpty() {
        assertTrue(listOcrFixtureNames().isNotEmpty(), "Add at least one ocr/*.txt fixture")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtureNames")
    fun geometryParserMatchesExpectedWhenPresent(name: String) {
        val page = loadOcrFixture(name)
        val parsed = parser.parse(page)
        val expected = loadOcrExpected(name) ?: return
        assertEquals(expected.merchant, parsed.merchant.value, "merchant")
        assertEquals(expected.date, parsed.date.value, "date")
        assertEquals(expected.currency, parsed.currency.value, "currency")
        assertEquals(expected.subtotal, parsed.subtotal.value, "subtotal")
        assertEquals(expected.total, parsed.total.value, "total")
        if (expected.taxTotal != null) {
            assertEquals(expected.taxTotal, parsed.taxTotal, "taxTotal")
        }
        assertEquals(expected.gstHstNumber, parsed.gstHstNumber.value, "gstHstNumber")
        assertEquals(expected.paymentMethod, parsed.paymentMethod.value, "paymentMethod")
        assertEquals(expected.receiptNumber, parsed.receiptNumber.value, "receiptNumber")
    }

    companion object {
        @JvmStatic
        fun fixtureNames(): List<String> = listOcrFixtureNames()
    }
}
