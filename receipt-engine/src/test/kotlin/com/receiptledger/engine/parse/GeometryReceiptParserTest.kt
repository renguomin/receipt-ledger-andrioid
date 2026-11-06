package com.receiptledger.engine.parse

import com.receiptledger.engine.ocrPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MoneyParserTest {
    @Test
    fun readsPlainDollars() {
        val found = MoneyParser.findAll("TOTAL 45.19")
        assertEquals(listOf(45.19), found.map { it.value })
    }

    @Test
    fun readsEuropeanDecimal() {
        val found = MoneyParser.findAll("TOTAL 45,19")
        assertEquals(listOf(45.19), found.map { it.value })
    }

    @Test
    fun readsThousands() {
        val found = MoneyParser.findAll("1,234.56")
        assertEquals(listOf(1234.56), found.map { it.value })
    }
}

class GeometryReceiptParserTest {
    private val parser = GeometryReceiptParser()

    @Test
    fun readsCanadianTireHstReceipt() {
        val page = ocrPage(
            "CDN TIRE #123",
            "123 MAIN ST",
            "2026-03-12",
            "DRILL BIT 19.99",
            "SUBTOTAL 19.99",
            "HST 13% 2.60",
            "TOTAL 22.59",
            "VISA",
            "GST 123456789RT0001",
        )
        val parsed = parser.parse(page)
        assertEquals("CDN TIRE", parsed.merchant.value)
        assertEquals("2026-03-12", parsed.date.value)
        assertEquals(19.99, parsed.subtotal.value)
        assertEquals(22.59, parsed.total.value)
        assertEquals("CAD", parsed.currency.value)
        assertEquals(1, parsed.taxes.size)
        assertEquals(2.60, parsed.taxes.first().amount)
        assertTrue(parsed.gstHstNumber.value!!.contains("123456789RT0001"))
        assertEquals("visa", parsed.paymentMethod.value)
        assertEquals(null, parsed.receiptNumber.value)
    }

    @Test
    fun readsTimHortons() {
        val page = ocrPage(
            "TIM HORTONS",
            "Mar 8, 2026",
            "LRG COFFEE 2.19",
            "SUBTOTAL 2.19",
            "HST 0.28",
            "TOTAL 2.47",
            "DEBIT",
        )
        val parsed = parser.parse(page)
        assertEquals("Tim Hortons", parsed.merchant.value)
        assertEquals(2.47, parsed.total.value)
        assertEquals("ca_t2125_8523", parsed.suggestedCategoryId.value)
    }

    @Test
    fun readsQuebecTpsTvq() {
        val page = ocrPage(
            "METRO",
            "2026-01-15",
            "SUBTOTAL 10.00",
            "TPS 5% 0.50",
            "TVQ 9.975% 1.00",
            "TOTAL 11.50",
        )
        val parsed = parser.parse(page)
        assertEquals("Metro", parsed.merchant.value)
        assertEquals(11.50, parsed.total.value)
        assertEquals(2, parsed.taxes.size)
        assertEquals("CAD", parsed.currency.value)
    }

    @Test
    fun prefersBottomTotalOverLinePrice() {
        val page = ocrPage(
            "STAPLES",
            "PAPER 12.99",
            "PENS 4.50",
            "SUBTOTAL 17.49",
            "HST 2.27",
            "TOTAL 19.76",
        )
        val parsed = parser.parse(page)
        assertEquals(19.76, parsed.total.value)
        assertEquals(17.49, parsed.subtotal.value)
        assertEquals("ca_t2125_8811", parsed.suggestedCategoryId.value)
    }

    @Test
    fun paymentIncludesCardLastFour() {
        val page = ocrPage(
            "T&T SUPERMARKET",
            "TOTAL 29.83",
            "Visa",
            "************7403P",
        )
        val parsed = parser.parse(page)
        assertEquals("visa *7403", parsed.paymentMethod.value)
    }
}
