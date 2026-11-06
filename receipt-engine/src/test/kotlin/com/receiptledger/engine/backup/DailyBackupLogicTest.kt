package com.receiptledger.engine.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DailyBackupLogicTest {
    private val zone: ZoneId = ZoneId.of("America/Vancouver")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun theSwitchCannotBeTurnedOnUntilThereIsAFileToOverwrite() {
        assertFalse(DailyBackupLogic.canEnable(hasDestination = false))
        assertTrue(DailyBackupLogic.canEnable(hasDestination = true))
        assertEquals(
            DailyBackupLogic.NEEDS_DESTINATION,
            DailyBackupLogic.statusLabel(enabled = true, hasDestination = false),
        )
        assertEquals(
            "Daily backup is on.",
            DailyBackupLogic.statusLabel(enabled = true, hasDestination = true),
        )
        assertEquals(
            "Daily backup is off.",
            DailyBackupLogic.statusLabel(enabled = false, hasDestination = true),
        )
    }

    @Test
    fun onlyATransientProblemIsWorthRetrying() {
        // Retrying these would reschedule the job forever and cost battery for nothing.
        assertFalse(DailyBackupLogic.isRetryable(BackupOutcome.NO_DESTINATION))
        assertFalse(DailyBackupLogic.isRetryable(BackupOutcome.NOTHING_TO_BACK_UP))
        assertFalse(DailyBackupLogic.isRetryable(BackupOutcome.SAVED))
        assertTrue(DailyBackupLogic.isRetryable(BackupOutcome.WRITE_FAILED))
        assertTrue(DailyBackupLogic.isRetryable(BackupOutcome.BUSY))
    }

    @Test
    fun aCloudDestinationWaitsForAConnectionAndALocalOneDoesNot() {
        assertTrue(DailyBackupLogic.requiresNetwork("com.google.android.apps.docs.storage"))
        assertTrue(DailyBackupLogic.requiresNetwork("com.dropbox.android.FileCache"))
        assertFalse(DailyBackupLogic.requiresNetwork("com.android.providers.downloads.documents"))
        assertFalse(DailyBackupLogic.requiresNetwork("com.android.externalstorage.documents"))
    }

    @Test
    fun theFirstRunIsAimedAtTheNextQuietHour() {
        val eightPm = at(2026, 9, 15, 20, 0)
        assertEquals(
            6 * 60 * 60 * 1000L,
            DailyBackupLogic.initialDelayMs(eightPm, zone),
        )
        // Already past the hour today, so it waits for tomorrow rather than firing immediately.
        val threeAm = at(2026, 9, 15, 3, 0)
        assertEquals(
            23 * 60 * 60 * 1000L,
            DailyBackupLogic.initialDelayMs(threeAm, zone),
        )
        // Exactly on the hour counts as past, so a run is never scheduled with zero delay.
        assertTrue(DailyBackupLogic.initialDelayMs(at(2026, 9, 15, 2, 0), zone) > 0)
    }

    @Test
    fun theToggleSaysItRunsOnItsOwn() {
        assertEquals("Automatic back up daily", DailyBackupLogic.TOGGLE_LABEL)
    }
}
