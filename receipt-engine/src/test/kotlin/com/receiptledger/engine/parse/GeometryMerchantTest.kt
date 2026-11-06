package com.receiptledger.engine.parse

import com.receiptledger.engine.alias.MerchantAliasBook
import com.receiptledger.engine.loadOcrFixture
import com.receiptledger.engine.model.BoundingBox
import com.receiptledger.engine.model.OcrLine
import com.receiptledger.engine.model.OcrPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeometryMerchantTest {
    @Test
    fun headerBandIsTheTopHalfOfTheReceipt() {
        assertEquals(0.50f, GeometryMerchant.HEADER_HEIGHT_FRACTION)
        val lines = listOf(
            line("SMALL PRINT", top = 0f, height = 10f),
            line("STORE NAME", top = 40f, height = 36f),
            line("MID PAGE", top = 150f, height = 14f),
            line("TOTAL 9.99", top = 300f, height = 40f),
        )
        val header = GeometryMerchant.headerLines(lines, pageHeight = 400f)
        assertEquals(listOf("SMALL PRINT", "STORE NAME", "MID PAGE"), header.map { it.text })
    }

    @Test
    fun catalogMatchBeatsLargestHeaderFont() {
        val page = OcrPage(
            lines = listOf(
                line("www.shop.example", top = 0f, height = 10f, width = 180f),
                line("TEL 555-0100", top = 14f, height = 10f, width = 120f),
                line("OFFICE MART", top = 30f, height = 34f, width = 240f),
                line("123 MAIN ST", top = 70f, height = 12f, width = 160f),
                line("TOTAL 22.59", top = 320f, height = 28f, width = 200f),
            ),
            width = 300f,
            height = 400f,
        )
        val parsed = GeometryReceiptParser().parse(page)
        assertEquals("OFFICE MART", parsed.merchant.value)
        assertEquals("OFFICE MART", GeometryMerchant.guess(page.lines, page.height))
    }

    @Test
    fun largeTypeBelowTheHeaderIsNotTheStore() {
        val guessed = GeometryMerchant.guess(
            lines = listOf(
                line("OFFICE MART", top = 0f, height = 22f, width = 160f),
                line("PAPER 12.99", top = 80f, height = 14f),
                line("TOTAL 19.76", top = 340f, height = 48f, width = 220f),
            ),
            pageHeight = 400f,
        )
        assertEquals("OFFICE MART", guessed)
    }

    @Test
    fun structuralNoiseIsNotAStoreNameFallback() {
        assertTrue(GeometryMerchant.isCandidate("OFFICE MART"))
        assertTrue(GeometryMerchant.isCandidate("North Star Store #304"))
        assertTrue(GeometryMerchant.isCandidate("WELCOME TO NORTH STAR #478"))
        assertTrue(!GeometryMerchant.isCandidate("www.example.com"))
        assertTrue(!GeometryMerchant.isCandidate("TEL 555-0100"))
        assertTrue(!GeometryMerchant.isCandidate("TOTAL 22.59"))
        assertTrue(!GeometryMerchant.isCandidate("1847 Technology Drive"))
        assertTrue(!GeometryMerchant.isCandidate("(408) 555-0174"))
        assertEquals("WELCOME TO NORTH STAR", GeometryMerchant.cleanMerchantText("WELCOME TO NORTH STAR #478"))
        assertEquals("North Star", GeometryMerchant.cleanMerchantText("North Star Store #304"))
        assertEquals("NORTH STAR", GeometryMerchant.cleanMerchantText("NORTH STAR #123"))
    }

    @Test
    fun catalogPicksBestBuyFromAWelcomeLineAndIgnoresSale() {
        val guessed = GeometryMerchant.guess(
            lines = listOf(
                line("WELCOME TO BEST BUY #478", top = 8f, height = 14f, width = 220f),
                line("50-01 NORTHERN BOULEVARD", top = 26f, height = 12f, width = 240f),
                line("SALE", top = 180f, height = 40f, width = 90f),
                line("WIDGET 16.99", top = 220f, height = 12f, width = 180f),
                line("TOTAL 29.21", top = 360f, height = 18f, width = 160f),
            ),
            pageHeight = 400f,
        )
        assertEquals("Best Buy", guessed)
    }

    @Test
    fun unknownStoreAtTheTopBeatsALargeSaleLabel() {
        val guessed = GeometryMerchant.guess(
            lines = listOf(
                line("WELCOME TO NORTH STAR #478", top = 8f, height = 14f, width = 220f),
                line("50-01 NORTHERN BOULEVARD", top = 26f, height = 12f, width = 240f),
                line("SALE", top = 180f, height = 40f, width = 90f),
                line("WIDGET 16.99", top = 220f, height = 12f, width = 180f),
                line("TOTAL 29.21", top = 360f, height = 18f, width = 160f),
            ),
            pageHeight = 400f,
        )
        assertEquals("WELCOME TO NORTH STAR", guessed)
    }

    @Test
    fun stackedLogoWordsMatchACatalogBrand() {
        val guessed = GeometryMerchant.guess(
            lines = listOf(
                line("BEST", top = 8f, height = 42f, width = 160f),
                line("BUY", top = 52f, height = 40f, width = 140f),
                line("Best Buy Store #304", top = 110f, height = 14f, width = 220f),
                line("1847 Technology Drive", top = 128f, height = 12f, width = 210f),
                line("TOTAL 48.69", top = 360f, height = 22f, width = 180f),
            ),
            pageHeight = 400f,
        )
        assertEquals("Best Buy", guessed)
    }

    @Test
    fun stackedLogoWordsAreJoinedWhenTheBrandIsUnknown() {
        val guessed = GeometryMerchant.guess(
            lines = listOf(
                line("NORTH", top = 8f, height = 42f, width = 160f),
                line("STAR", top = 52f, height = 40f, width = 140f),
                line("North Star Store #304", top = 110f, height = 14f, width = 220f),
                line("1847 Technology Drive", top = 128f, height = 12f, width = 210f),
                line("TOTAL 48.69", top = 360f, height = 22f, width = 180f),
            ),
            pageHeight = 400f,
        )
        assertEquals("NORTH STAR", guessed)
    }

    @Test
    fun readableStoreLineIsUsedWhenStackedLogoWordsAreMissing() {
        val guessed = GeometryMerchant.guess(
            lines = listOf(
                line("North Star Store #304", top = 20f, height = 16f, width = 220f),
                line("1847 Technology Drive", top = 40f, height = 12f, width = 210f),
                line("Cashier: David", top = 80f, height = 12f, width = 160f),
            ),
            pageHeight = 400f,
        )
        assertEquals("North Star", guessed)
    }

    @Test
    fun onlyLearnedAliasesRewriteOcrStoreNames() {
        val page = OcrPage(
            lines = listOf(line("NORTH STAR", top = 8f, height = 36f, width = 200f)),
            width = 300f,
            height = 400f,
        )
        assertEquals("NORTH STAR", GeometryReceiptParser().parse(page).merchant.value)
        val learned = GeometryReceiptParser(
            aliases = MerchantAliasBook(mapOf("NORTH STAR" to "North Star Markets")),
        )
        assertEquals("North Star Markets", learned.parse(page).merchant.value)
    }

    @Test
    fun gluedFooterUrlBeatsASaleLabelWhenLogoOcrMissedBestAndBuy() {
        val guessed = GeometryMerchant.guess(
            lines = listOf(
                line("The tech you need it the prices you'11 Tove.", top = 8f, height = 14f, width = 260f),
                line("SALE", top = 180f, height = 40f, width = 90f),
                line("TOTAL 515.19", top = 300f, height = 18f, width = 160f),
                line("adyur or visit Bestßuy.ca.", top = 340f, height = 12f, width = 240f),
                line("Reed help? Visit: BestBuy.ca/Contact-us", top = 360f, height = 12f, width = 240f),
            ),
            pageHeight = 400f,
        )
        assertEquals("Best Buy", guessed)
    }

    @Test
    fun savedBestBuyOcrDumpMatchesEvenWithoutLogoWords() {
        val page = loadOcrFixture("ocr-20260824-185108")
        assertEquals("Best Buy", GeometryMerchant.guess(page.lines, page.height))
    }

    private fun line(
        text: String,
        top: Float,
        height: Float,
        width: Float = 200f,
    ) = OcrLine(
        text = text,
        box = BoundingBox(left = 0f, top = top, right = width, bottom = top + height),
    )
}
