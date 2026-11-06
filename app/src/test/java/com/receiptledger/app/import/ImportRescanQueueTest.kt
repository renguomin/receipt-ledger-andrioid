package com.receiptledger.app.import

import com.receiptledger.engine.model.Field
import com.receiptledger.engine.model.FieldSource
import com.receiptledger.engine.model.ParsedReceipt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImportRescanQueueTest {
    @Test
    fun startKeepsTheCurrentDraftUntilFinish() {
        val queue = ImportRescanQueue()
        val first = ImportDraft("/a.jpg", ocrPhoto = true)
        val second = ImportDraft(
            "/b.jpg",
            ocrPhoto = false,
            parsed = ParsedReceipt(merchant = Field("Store", 1f, FieldSource.USER)),
        )
        queue.start(listOf(first, second))
        assertEquals(first, queue.current())
        assertEquals(1, queue.progress()?.index)
        assertEquals(2, queue.progress()?.total)
        assertEquals(second, queue.finishCurrent())
        assertEquals(second, queue.current())
        assertEquals(2, queue.progress()?.index)
        assertNull(queue.finishCurrent())
        assertNull(queue.current())
        assertNull(queue.progress())
    }

    @Test
    fun skippedReceiptsAreCountedApartFromSavedOnes() {
        val queue = ImportRescanQueue()
        queue.start(
            listOf(
                ImportDraft("/a.jpg", ocrPhoto = true),
                ImportDraft("/b.jpg", ocrPhoto = true),
                ImportDraft("/c.jpg", ocrPhoto = true),
            ),
        )
        assertEquals(ImportOutcome(saved = 0, skipped = 0), queue.outcome())
        queue.finishCurrent(saved = true)
        queue.finishCurrent(saved = false)
        assertEquals(ImportOutcome(saved = 1, skipped = 1), queue.outcome())
        assertNull(queue.finishCurrent(saved = true))
        // The batch is over, but the tally must survive so the closing notice can report it.
        assertNull(queue.progress())
        assertEquals(ImportOutcome(saved = 2, skipped = 1), queue.outcome())
        assertEquals(3, queue.outcome().reviewed)
    }

    @Test
    fun finishingAnEmptyQueueDoesNotChangeTheTally() {
        val queue = ImportRescanQueue()
        queue.start(listOf(ImportDraft("/a.jpg", ocrPhoto = true)))
        queue.finishCurrent(saved = false)
        assertNull(queue.finishCurrent(saved = false))
        assertEquals(ImportOutcome(saved = 0, skipped = 1), queue.outcome())
    }

    @Test
    fun startingANewBatchResetsTheTally() {
        val queue = ImportRescanQueue()
        queue.start(listOf(ImportDraft("/a.jpg", ocrPhoto = true)))
        queue.finishCurrent(saved = false)
        queue.start(listOf(ImportDraft("/b.jpg", ocrPhoto = true)))
        assertEquals(ImportOutcome(saved = 0, skipped = 0), queue.outcome())
    }

    @Test
    fun clearDropsTheBatch() {
        val queue = ImportRescanQueue()
        queue.start(listOf(ImportDraft("/a.jpg", ocrPhoto = true)))
        assertTrue(queue.progress() != null)
        queue.clear()
        assertNull(queue.current())
        assertNull(queue.progress())
        assertFalse(queue.finishCurrent() != null)
        assertEquals(ImportOutcome(saved = 0, skipped = 0), queue.outcome())
    }
}
