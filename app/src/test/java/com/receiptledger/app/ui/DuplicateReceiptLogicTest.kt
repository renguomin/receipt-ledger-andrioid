package com.receiptledger.app.ui

import com.receiptledger.app.data.ReceiptEntity
import com.receiptledger.engine.model.LineItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.TimeZone

class DuplicateReceiptLogicTest {
    @Test
    fun newReceiptsWithADateAndTotalAreCheckedAndEditsAreNot() {
        assertTrue(DuplicateReceiptLogic.shouldCheck(true, dateIso = "2026-03-12", total = 50.0))
        assertFalse(DuplicateReceiptLogic.shouldCheck(false, dateIso = "2026-03-12", total = 50.0))
    }

    @Test
    fun aReceiptWithNothingToCompareIsNotChecked() {
        // The receipt number is no longer part of the key, so an unreadable date or total is the
        // only thing that skips the check.
        assertFalse(DuplicateReceiptLogic.shouldCheck(true, dateIso = null, total = 50.0))
        assertFalse(DuplicateReceiptLogic.shouldCheck(true, dateIso = "  ", total = 50.0))
        assertFalse(DuplicateReceiptLogic.shouldCheck(true, dateIso = "2026-03-12", total = null))
        assertFalse(DuplicateReceiptLogic.shouldCheck(true, dateIso = "2026-03-12", total = 0.0))
    }

    @Test
    fun pickMatchUsesStoreDateAndTotalAndKeepsTheNewestCopy() {
        val older = receipt("old", createdAt = 10L, merchant = "Shell", total = 50.0)
        val newer = receipt("new", createdAt = 20L, merchant = "Shell", total = 50.0)
        val otherTotal = receipt("amount", createdAt = 30L, merchant = "Shell", total = 62.15)
        val otherDate = receipt("day", createdAt = 40L, total = 50.0, dateIso = "2026-03-11")
        val all = listOf(older, newer, otherTotal, otherDate)
        assertEquals(newer, DuplicateReceiptLogic.pickMatch("Shell", "2026-03-12", 50.0, all))
        assertEquals(
            older,
            DuplicateReceiptLogic.pickMatch("Shell", "2026-03-12", 50.0, all, excludingId = "new"),
        )
        assertEquals(otherTotal, DuplicateReceiptLogic.pickMatch("Shell", "2026-03-12", 62.15, all))
        assertEquals(otherDate, DuplicateReceiptLogic.pickMatch("Store", "2026-03-11", 50.0, all))
    }

    @Test
    fun twoStoresOnTheSameDayForTheSameAmountAreNotDuplicates() {
        val costco = receipt("costco", createdAt = 10L, merchant = "Costco", total = 20.0)
        val shell = receipt("shell", createdAt = 20L, merchant = "Shell", total = 20.0)
        val all = listOf(costco, shell)
        assertEquals(costco, DuplicateReceiptLogic.pickMatch("Costco", "2026-03-12", 20.0, all))
        assertEquals(shell, DuplicateReceiptLogic.pickMatch("Shell", "2026-03-12", 20.0, all))
        assertNull(DuplicateReceiptLogic.pickMatch("T&T Supermarket", "2026-03-12", 20.0, all))
    }

    @Test
    fun storeNamesMatchAcrossCasingAndSpacingAndWhenBothAreMissing() {
        val saved = receipt("saved", createdAt = 10L, merchant = "T&T  Supermarket", total = 41.18)
        assertEquals(
            saved,
            DuplicateReceiptLogic.pickMatch("t&t supermarket ", "2026-03-12", 41.18, listOf(saved)),
        )
        val nameless = receipt("nameless", createdAt = 10L, merchant = null, total = 41.18)
        assertEquals(
            nameless,
            DuplicateReceiptLogic.pickMatch(null, "2026-03-12", 41.18, listOf(nameless)),
        )
        // A nameless scan is not treated as the same purchase as a named one.
        assertNull(DuplicateReceiptLogic.pickMatch(null, "2026-03-12", 41.18, listOf(saved)))
        assertEquals("", DuplicateReceiptLogic.normalizeMerchant("   "))
    }

    @Test
    fun pickMatchIgnoresTheReceiptNumberEntirely() {
        // Different numbers, and a blank one, still match on store, date and total.
        val numbered = receipt("numbered", createdAt = 10L, receiptNumber = "004821", total = 50.0)
        val blank = receipt("blank", createdAt = 20L, receiptNumber = null, total = 50.0)
        assertEquals(
            blank,
            DuplicateReceiptLogic.pickMatch("Store", "2026-03-12", 50.0, listOf(numbered, blank)),
        )
        assertEquals(
            numbered,
            DuplicateReceiptLogic.pickMatch("Store", "2026-03-12", 50.0, listOf(numbered), excludingId = "x"),
        )
    }

    @Test
    fun pickMatchReturnsNothingWhenThereIsNoKeyOrNoMatch() {
        val saved = receipt("saved", createdAt = 10L, total = 50.0)
        assertNull(DuplicateReceiptLogic.pickMatch("Store", null, 50.0, listOf(saved)))
        assertNull(DuplicateReceiptLogic.pickMatch("Store", "2026-03-12", null, listOf(saved)))
        assertNull(DuplicateReceiptLogic.pickMatch("Store", "2026-03-12", 50.0, emptyList()))
        assertNull(
            DuplicateReceiptLogic.pickMatch("Store", "2026-03-12", 50.0, listOf(saved), excludingId = "saved"),
        )
    }

    @Test
    fun totalsMatchToTheCentSoStoredDoublesStillLineUp() {
        assertTrue(DuplicateReceiptLogic.sameTotal(50.0, 50.0))
        assertTrue(DuplicateReceiptLogic.sameTotal(0.1 + 0.2, 0.3))
        assertFalse(DuplicateReceiptLogic.sameTotal(50.0, 50.01))
        assertFalse(DuplicateReceiptLogic.sameTotal(null, 50.0))
        assertFalse(DuplicateReceiptLogic.sameTotal(50.0, null))
    }

    @Test
    fun warningListsStoreDateTotalItemsAndWhenItWasAdded() {
        val body = DuplicateReceiptLogic.warning(
            merchant = "Canadian Tire",
            dateIso = "2026-03-12",
            total = 19.99,
            currency = "CAD",
            lineItems = listOf(LineItem("Hammer", 1.0, 19.99)),
            createdAtMs = 1_787_951_700_000L,
            locale = Locale.US,
            timeZone = TimeZone.getTimeZone("UTC"),
        ).body
        assertTrue(
            body.startsWith("A receipt with the same store, date and total is already in your list."),
        )
        assertTrue(body.contains("Store: Canadian Tire"))
        assertTrue(body.contains("Purchased date: 2026-03-12"))
        assertTrue(body.contains("Total: 19.99 CAD"))
        assertTrue(body.contains("Items: Hammer x1 @ 19.99"))
        assertTrue(body.contains("Added date: Aug 28, 2026 at 9:15 PM"))
        // The customer is told outright that a separate purchase can look identical.
        assertTrue(body.contains("Skip this copy, or save it anyway if it is a separate purchase."))
        assertEquals("This might already be saved", DuplicateReceiptLogic.TITLE)
        assertEquals("Skip", DuplicateReceiptLogic.SKIP)
        assertEquals("Save anyway", DuplicateReceiptLogic.SAVE_AGAIN)
    }

    @Test
    fun warningUsesPlaceholdersWhenDetailsAreMissing() {
        val body = DuplicateReceiptLogic.body(
            merchant = "  ",
            dateIso = null,
            total = null,
            items = "",
            addedAt = "Aug 1, 2026 at 10:00 AM",
        )
        assertTrue(body.contains("Store: Not listed"))
        assertTrue(body.contains("Purchased date: Not listed"))
        assertTrue(body.contains("Total: Not listed"))
        assertTrue(body.contains("Items: None listed"))
        assertTrue(body.contains("Added date: Aug 1, 2026 at 10:00 AM"))
    }

    @Test
    fun afterReviewSkipsToHomeWhenTheCopyIsNotSaved() {
        assertEquals(
            AfterReviewAction.RETURN_HOME,
            DuplicateReceiptLogic.afterReview(savedId = null, hasNextImport = false, importBatchSize = 0),
        )
        assertEquals(
            AfterReviewAction.OPEN_SAVED,
            DuplicateReceiptLogic.afterReview(savedId = "abc", hasNextImport = false, importBatchSize = 0),
        )
        assertEquals(
            AfterReviewAction.CONTINUE_IMPORT,
            DuplicateReceiptLogic.afterReview(savedId = null, hasNextImport = true, importBatchSize = 3),
        )
        assertEquals(
            AfterReviewAction.FINISH_IMPORT,
            DuplicateReceiptLogic.afterReview(savedId = null, hasNextImport = false, importBatchSize = 3),
        )
    }

    private fun receipt(
        id: String,
        createdAt: Long,
        total: Double?,
        dateIso: String? = "2026-03-12",
        merchant: String? = "Store",
        receiptNumber: String? = null,
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
        subtotal = 10.0,
        taxTotal = 0.0,
        taxJson = "[]",
        total = total,
        totalConfidence = 1f,
        paymentMethod = null,
        gstHstNumber = null,
        receiptNumber = receiptNumber,
        lineItemsJson = "[]",
        purpose = "PERSONAL",
        categoryId = null,
        rawText = "",
        year = 2026,
    )
}
