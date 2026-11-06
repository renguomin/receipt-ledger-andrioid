package com.receiptledger.engine.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhotoJanitorLogicTest {
    private val now = 1_800_000_000_000L
    private val old = now - 30L * 24 * 60 * 60 * 1000

    private fun photo(name: String, sizeBytes: Long = 1024, lastModifiedMs: Long = old) =
        StoredPhoto(name = name, sizeBytes = sizeBytes, lastModifiedMs = lastModifiedMs)

    @Test
    fun namesAreComparedSoPathsFromAnotherUserProfileStillMatch() {
        assertEquals("a.jpg", PhotoJanitorLogic.fileName("/data/data/com.receiptledger.app/files/receipts/a.jpg"))
        assertEquals("a.jpg", PhotoJanitorLogic.fileName("/data/user/10/com.receiptledger.app/files/receipts/a.jpg"))
        assertEquals("a.jpg", PhotoJanitorLogic.fileName("a.jpg"))
    }

    @Test
    fun anUnreferencedSettledPhotoIsTheOnlyThingDeleted() {
        val sweep = PhotoJanitorLogic.sweep(
            photos = listOf(photo("kept.jpg"), photo("leaked.jpg", sizeBytes = 4096)),
            referencedPaths = listOf("/files/receipts/kept.jpg"),
            nowMs = now,
        )
        assertEquals(listOf("leaked.jpg"), sweep.deletable.map { it.name })
        assertEquals(4096L, sweep.reclaimedBytes)
        assertEquals(1, sweep.keptReferenced)
    }

    @Test
    fun aPhotoStillOpenInReviewSurvivesEvenWithNoReceiptPointingAtIt() {
        val sweep = PhotoJanitorLogic.sweep(
            photos = listOf(photo("under-review.jpg")),
            referencedPaths = emptyList(),
            inFlightPaths = listOf("/files/receipts/under-review.jpg"),
            nowMs = now,
        )
        assertTrue(sweep.deletable.isEmpty())
        assertEquals(1, sweep.keptInFlight)
    }

    @Test
    fun freshPhotosAreLeftAloneBecauseTheirReceiptMayNotBeSavedYet() {
        val justWritten = photo("fresh.jpg", lastModifiedMs = now - 1000)
        val settled = photo("stale.jpg", lastModifiedMs = now - PhotoJanitorLogic.SETTLE_MS - 1)
        val sweep = PhotoJanitorLogic.sweep(
            photos = listOf(justWritten, settled),
            referencedPaths = emptyList(),
            nowMs = now,
        )
        assertEquals(listOf("stale.jpg"), sweep.deletable.map { it.name })
        assertEquals(1, sweep.keptTooFresh)
    }

    @Test
    fun theSettleBoundaryItselfCountsAsSettled() {
        val onBoundary = photo("edge.jpg", lastModifiedMs = now - PhotoJanitorLogic.SETTLE_MS)
        val sweep = PhotoJanitorLogic.sweep(
            photos = listOf(onBoundary),
            referencedPaths = emptyList(),
            nowMs = now,
        )
        assertEquals(listOf("edge.jpg"), sweep.deletable.map { it.name })
    }

    @Test
    fun theNoticeReportsReclaimedSpaceAndStaysQuietWhenNothingWasFreed() {
        val swept = PhotoJanitorLogic.sweep(
            photos = listOf(photo("a.jpg", sizeBytes = 2L * 1024 * 1024)),
            referencedPaths = emptyList(),
            nowMs = now,
        )
        assertEquals("Freed 2 MB from 1 unused photo.", PhotoJanitorLogic.reclaimedNotice(swept))

        val clean = PhotoJanitorLogic.sweep(
            photos = listOf(photo("a.jpg")),
            referencedPaths = listOf("a.jpg"),
            nowMs = now,
        )
        assertNull(PhotoJanitorLogic.reclaimedNotice(clean))
    }

    @Test
    fun theRealLedgerShapeIsHandled() {
        // 44 receipts referencing 44 of 642 photos, the rest abandoned imports.
        val referenced = (1..44).map { "/files/receipts/kept-$it.jpg" }
        val photos = (1..44).map { photo("kept-$it.jpg", sizeBytes = 220_000) } +
            (1..598).map { photo("orphan-$it.jpg", sizeBytes = 220_000) }
        val sweep = PhotoJanitorLogic.sweep(photos, referenced, nowMs = now)
        assertEquals(598, sweep.deletable.size)
        assertEquals(44, sweep.keptReferenced)
    }
}
