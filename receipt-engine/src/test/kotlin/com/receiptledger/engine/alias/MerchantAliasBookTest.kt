package com.receiptledger.engine.alias

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MerchantAliasBookTest {
    @Test
    fun aLearnedNameDoesNotRenameAnUnrelatedStore() {
        val book = MerchantAliasBook(mapOf("IKEA" to "IKEA"))
        assertEquals("Walmart", book.canonicalOrNull("WALMART SUPERCENTRE 3120 ARGENTIA RD"))
        assertEquals("IKEA", book.canonicalOrNull("IKEA NORTH YORK 15 PROVOST DR"))
    }

    @Test
    fun aShortLearnedCodeHasToStandAsItsOwnWord() {
        val book = MerchantAliasBook(mapOf("MART" to "Office Mart"))
        assertEquals("Office Mart", book.learnedCanonicalOrNull("MART 1842"))
        assertNull(book.learnedCanonicalOrNull("WALMART SUPERCENTRE"))
        assertEquals("Tim Hortons", MerchantAliasBook().canonicalOrNull("TDL 1842"))
    }

    @Test
    fun theLongestNameWinsRatherThanTheFirstOneAdded() {
        val book = MerchantAliasBook()
        assertEquals("Canadian Tire", book.canonicalOrNull("CANADIAN TIRE 288"))
        val learned = MerchantAliasBook(
            mapOf(
                "NORTH" to "North Star",
                "NORTH STAR MARKETS" to "North Star Markets",
            ),
        )
        assertEquals("North Star Markets", learned.canonicalOrNull("NORTH STAR MARKETS 12"))
    }

    @Test
    fun aLongNameStillMatchesWhenOcrRunsTheWordsTogether() {
        val book = MerchantAliasBook()
        assertEquals("Walmart", book.canonicalOrNull("WALMARTSUPERCENTRE"))
        assertEquals("Best Buy", book.canonicalOrNull("BEST BUY 478"))
    }

    @Test
    fun twoLetterKeysAreTooShortToRewriteAStore() {
        val book = MerchantAliasBook(mapOf("BP" to "BP"))
        assertNull(book.learnedCanonicalOrNull("SUPERSTORE BP AISLE"))
        assertEquals("BP", book.learnedCanonicalOrNull("BP"))
    }
}
