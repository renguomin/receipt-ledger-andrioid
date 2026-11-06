package com.receiptledger.engine.parse

import com.receiptledger.engine.ocrPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReceiptNumberParserTest {
    @Test
    fun findsTheDocumentIdFromNearbyKeyValueText() {
        assertEquals("004821", ReceiptNumberParser.find("SEQ NO 004821"))
        assertEquals("004821", ReceiptNumberParser.find("Seq # 004821"))
        assertEquals("A-91", ReceiptNumberParser.find("Ref #: A-91"))
        assertEquals("8821", ReceiptNumberParser.find("INV#8821"))
        assertEquals("10482", ReceiptNumberParser.find("Receipt number\n10482"))
        assertEquals("55", ReceiptNumberParser.find("No de reçu 55"))
        assertEquals("8841", ReceiptNumberParser.find("n° 8841"))
    }

    @Test
    fun ignoresStoreTaxItemAndCardIdentifiers() {
        assertNull(ReceiptNumberParser.find(ocrPage("CDN TIRE #123", "TOTAL 22.59").fullText))
        assertNull(ReceiptNumberParser.find("GST 123456789RT0001"))
        assertNull(ReceiptNumberParser.find("Item #4  DRILL BIT 19.99"))
        assertNull(ReceiptNumberParser.find("Store #12"))
        assertNull(ReceiptNumberParser.find("Auth code 019283"))
        assertNull(ReceiptNumberParser.find("VISA"))
    }

    @Test
    fun geometryParserReadsTheIdAndLeavesStoreNumbersAlone() {
        val withId = GeometryReceiptParser().parse(
            ocrPage(
                "TIM HORTONS",
                "Seq # 004821",
                "TOTAL 2.47",
            ),
        )
        assertEquals("004821", withId.receiptNumber.value)
        val storeOnly = GeometryReceiptParser().parse(
            ocrPage("CDN TIRE #123", "TOTAL 22.59", "GST 123456789RT0001"),
        )
        assertNull(storeOnly.receiptNumber.value)
    }

    @Test
    fun dedupIgnoresSeparatorsAndCase() {
        assertTrue(ReceiptNumberParser.sameDocument("A-91", "a91"))
        assertTrue(ReceiptNumberParser.sameDocument("004821", "004821"))
        assertFalse(ReceiptNumberParser.sameDocument("004821", "004822"))
        assertFalse(ReceiptNumberParser.sameDocument(null, "004821"))
        assertEquals("A91", ReceiptNumberParser.normalizeForDedup("A-91"))
    }

    @Test
    fun geminiPromptAsksForTheDocumentIdNotALabelList() {
        assertTrue(
            com.receiptledger.engine.gemini.GeminiReceipt.JSON_SCHEMA_HINT.contains(
                ReceiptNumberParser.GEMINI_RULE,
            ),
        )
        assertFalse(
            com.receiptledger.engine.gemini.GeminiReceipt.JSON_SCHEMA_HINT.contains(
                "seq no",
                ignoreCase = true,
            ),
        )
    }
}
