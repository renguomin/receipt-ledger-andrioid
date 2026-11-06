package com.receiptledger.engine.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BrandCatalogTest {
    private val catalog = BrandCatalog.fromPhrases(
        listOf(
            "Best Buy" to "Best Buy",
            "Best Buy Express" to "Best Buy Express",
            "Acme Hardware" to "Acme Hardware",
        ),
    )

    @Test
    fun bagOfWordsPicksTheCatalogRowWhoseTokensAllAppear() {
        assertEquals("Best Buy", catalog.matchText("WELCOME TO BEST BUY #478 SALE"))
        assertEquals("Best Buy", catalog.matchText("BEST BUY STORE 304"))
        assertEquals("Best Buy Express", catalog.matchText("BEST BUY EXPRESS"))
        assertEquals("Acme Hardware", catalog.matchText("ACME HARDWARE"))
    }

    @Test
    fun incompleteOrUnknownTokenBagsDoNotMatch() {
        assertNull(catalog.matchText("SALE"))
        assertNull(catalog.matchText("WELCOME TO OUR STORE"))
        assertNull(catalog.matchText("BEST PRICE"))
        assertNull(catalog.matchText("CDN TIRE"))
    }

    @Test
    fun nsiCatalogMapsCommonCanadianChains() {
        val nsi = BrandCatalog.nsi
        assertEquals("Best Buy", nsi.matchText("WELCOME TO BEST BUY #478 SALE"))
        assertEquals("Best Buy", nsi.matchText("BEST BUY"))
        assertEquals("Tim Hortons", nsi.matchText("TIM HORTONS"))
        assertEquals("Canadian Tire", nsi.matchText("CANADIAN TIRE"))
        assertEquals("Metro", nsi.matchText("METRO"))
        assertEquals("Shoppers Drug Mart", nsi.matchText("SHOPPERS DRUG MART"))
        assertEquals("The Home Depot", nsi.matchText("HOME DEPOT"))
        assertNull(nsi.matchText("CDN TIRE"))
        assertNull(nsi.matchText("SALE"))
        assertNull(nsi.matchText("OFFICE MART"))
        assertNull(nsi.matchText("NORTH STAR"))
        assertNull(nsi.matchText("1847 TECHNOLOGY DRIVE"))
    }

    @Test
    fun gluedAndDomainFormsMatchWhenLogoWordsAreMissing() {
        assertEquals("Best Buy", catalog.matchText("visit BestBuy.ca"))
        assertEquals("Best Buy", catalog.matchText("adyur or visit Bestßuy.ca."))
        assertEquals("Best Buy", catalog.matchText("available at bes tbuy.c8/elif"))
        assertEquals("Best Buy Express", catalog.matchText("bestbuyexpress.com"))
        assertNull(catalog.matchText("SALE TAX TOTAL VISA"))
    }

    @Test
    fun nsiCatalogMapsGluedBestBuyFromRealReceiptOcr() {
        val nsi = BrandCatalog.nsi
        assertEquals("Best Buy", nsi.matchText("visit BestBuy.ca"))
        assertEquals("Best Buy", nsi.matchText("Bestßuy.ca"))
        assertEquals("Best Buy", nsi.matchText("bes tbuy"))
        assertEquals(
            "Best Buy",
            nsi.matchText(
                """
                The tech you need it the prices you'11 Tove.
                SALE
                TAX
                TOTAL
                Visa
                adyur or visit Bestßuy.ca.
                Reed help? Visit: BestBuy.ca/Contact-us
                """.trimIndent(),
            ),
        )
        assertNull(nsi.matchText("The tech you need it the prices you'11 Tove.\nSALE\nTAX\nTOTAL"))
    }

    @Test
    fun tokenizerKeepsStoreWordsAndDropsStoreNumbers() {
        assertEquals(listOf("WELCOME", "TO", "BEST", "BUY"), BrandTokens.tokenize("WELCOME TO BEST BUY #478"))
        assertEquals(listOf("MCDONALDS"), BrandTokens.tokenize("McDonald's"))
        assertEquals(listOf("HOME", "DEPOT"), BrandTokens.tokenizeName("The Home Depot"))
        assertEquals("BESTBUYCA", BrandTokens.compact("Bestßuy.ca"))
        assertEquals("BESTBUY", BrandTokens.compact("bes tbuy"))
    }
}
