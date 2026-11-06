package com.receiptledger.engine.parse

import com.receiptledger.engine.ocrPage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GoldenReceiptParseTest {
    private val json = Json { prettyPrint = true }

    @Test
    fun canadianTireFixtureMatchesGoldenShape() {
        val parsed = GeometryReceiptParser().parse(
            ocrPage(
                "CDN TIRE #123",
                "123 MAIN ST",
                "2026-03-12",
                "DRILL BIT 19.99",
                "SUBTOTAL 19.99",
                "HST 13% 2.60",
                "TOTAL 22.59",
                "VISA",
                "GST 123456789RT0001",
            ),
        )
        val actual = json.encodeToString(
            Golden.serializer(),
            Golden(
                merchant = parsed.merchant.value,
                date = parsed.date.value,
                currency = parsed.currency.value,
                subtotal = parsed.subtotal.value,
                total = parsed.total.value,
                hst = parsed.taxes.first().amount,
                gstHstNumber = parsed.gstHstNumber.value,
            ),
        )
        val expected = json.encodeToString(
            Golden.serializer(),
            Golden(
                merchant = "CDN TIRE",
                date = "2026-03-12",
                currency = "CAD",
                subtotal = 19.99,
                total = 22.59,
                hst = 2.60,
                gstHstNumber = "123456789RT0001",
            ),
        )
        assertEquals(expected, actual)
    }

    @Serializable
    private data class Golden(
        val merchant: String?,
        val date: String?,
        val currency: String?,
        val subtotal: Double?,
        val total: Double?,
        val hst: Double?,
        val gstHstNumber: String?,
    )
}
