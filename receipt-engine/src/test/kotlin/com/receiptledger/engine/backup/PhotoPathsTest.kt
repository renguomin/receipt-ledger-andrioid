package com.receiptledger.engine.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhotoPathsTest {
    private val primary = "/data/data/com.receiptledger.app/files/receipts"
    private val secondaryProfile = "/data/user/10/com.receiptledger.app/files/receipts"

    @Test
    fun aPathFromAnotherProfileIsPointedAtTheLocalPhotoDirectory() {
        assertEquals(
            "$primary/abc.jpg",
            PhotoPaths.rebase("$secondaryProfile/abc.jpg", primary),
        )
    }

    @Test
    fun aTrailingSlashOnTheDirectoryDoesNotDoubleUp() {
        assertEquals(
            "$primary/abc.jpg",
            PhotoPaths.rebase("$secondaryProfile/abc.jpg", "$primary/"),
        )
    }

    @Test
    fun aPathWithNoFileNameIsLeftUntouched() {
        assertEquals("", PhotoPaths.rebase("", primary))
        assertEquals("$secondaryProfile/", PhotoPaths.rebase("$secondaryProfile/", primary))
    }

    @Test
    fun aSameDeviceRestoreProducesNoUpdatesAtAll() {
        val stored = mapOf("r1" to "$primary/a.jpg", "r2" to "$primary/b.jpg")
        assertTrue(PhotoPaths.rebaseAll(stored, primary).isEmpty())
    }

    @Test
    fun onlyTheRowsThatActuallyMoveAreRewritten() {
        val stored = mapOf(
            "stays" to "$primary/a.jpg",
            "moves" to "$secondaryProfile/b.jpg",
        )
        assertEquals(
            mapOf("moves" to "$primary/b.jpg"),
            PhotoPaths.rebaseAll(stored, primary),
        )
    }
}
