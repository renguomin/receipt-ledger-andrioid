package com.receiptledger.engine.parse

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PaymentMethodParserTest {
    @Test
    fun visaUsesOneStarAndLastFour() {
        val text = """
            Visa
            ************7403P
            Thank You
        """.trimIndent()
        assertEquals("visa *7403", PaymentMethodParser.find(text))
    }

    @Test
    fun mastercardFromMaskedPan() {
        assertEquals(
            "mastercard *2322",
            PaymentMethodParser.find("Account: MASTERCARD\n************2322 P"),
        )
    }

    @Test
    fun xMaskAndOcrVisaStillWork() {
        assertEquals("visa *1866", PaymentMethodParser.find("Uisa\nXXXXXX1866"))
        assertEquals("visa *8892", PaymentMethodParser.find("******8892 C VISA 515.19"))
    }

    @Test
    fun walletsAndCashHaveNoStar() {
        assertEquals("paypal", PaymentMethodParser.find("Paid with PayPal"))
        assertEquals("wechat", PaymentMethodParser.find("WECHAT PAY"))
        assertEquals("apple pay", PaymentMethodParser.find("APPLE PAY"))
        assertEquals("cash", PaymentMethodParser.find("CASH"))
    }

    @Test
    fun brandOnlyWhenLastFourMissing() {
        assertEquals("visa", PaymentMethodParser.find("VISA"))
        assertEquals("debit", PaymentMethodParser.find("DEBIT"))
        assertNull(PaymentMethodParser.find("Item count:5 Terminal:050108013"))
    }

    @Test
    fun alreadyFormattedStaysOneStar() {
        assertEquals("visa *1234", PaymentMethodParser.normalize("VISA ****1234"))
        assertEquals("visa *1234", PaymentMethodParser.normalize("visa *1234"))
        assertEquals("amex *1009", PaymentMethodParser.normalize("American Express ending in 1009"))
    }

    @Test
    fun preferTheSideThatHasLastFour() {
        assertEquals("visa *7403", PaymentMethodParser.prefer("visa", "visa *7403"))
        assertEquals("visa *7403", PaymentMethodParser.prefer("visa *7403", "visa"))
        assertEquals("visa *7403", PaymentMethodParser.prefer("visa *7403", "paypal"))
    }

    @Test
    fun geminiPromptAsksForOneStarLastFour() {
        assertTrue(
            com.receiptledger.engine.gemini.GeminiReceipt.JSON_SCHEMA_HINT.contains(
                PaymentMethodParser.GEMINI_RULE,
            ),
        )
    }
}
