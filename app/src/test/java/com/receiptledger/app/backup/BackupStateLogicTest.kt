package com.receiptledger.app.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupStateLogicTest {
    @Test
    fun oneRequestOpensOnePickerEvenWhenTheScreenAsksTwice() {
        // The bug this exists for: the effect that opens the picker re-runs a frame later, so the
        // same request opened a second picker that had to be dismissed by hand. Once the screen
        // reports the picker open, the request is spent and asking again gets nothing.
        assertEquals(
            BackupPicker.DESTINATION,
            BackupStateLogic.picker(BackupState.ChooseDestination("receipt-ledger-backup.zip")),
        )
        assertEquals(BackupPicker.SOURCE, BackupStateLogic.picker(BackupState.ChooseSource))
        assertNull(BackupStateLogic.picker(BackupState.AwaitingPick))
    }

    @Test
    fun noPickerIsAskedForWhenNothingIsWaitingOnOne() {
        assertNull(BackupStateLogic.picker(BackupState.Idle))
        assertNull(BackupStateLogic.picker(BackupState.Running(1, 4, "Packing…")))
        assertNull(BackupStateLogic.picker(BackupState.Ready("Backed up 51 receipts.")))
        assertNull(BackupStateLogic.picker(BackupState.Restored("Restored 51 receipts.")))
        assertNull(BackupStateLogic.picker(BackupState.Failed("Could not finish the backup.")))
    }

    @Test
    fun waitingOnThePickerCountsAsBusySoNothingElseStarts() {
        // Were this not busy, the buttons would come back to life behind the open picker.
        assertTrue(BackupStateLogic.isBusy(BackupState.AwaitingPick))
        assertTrue(BackupStateLogic.isBusy(BackupState.ChooseDestination("b.zip")))
        assertTrue(BackupStateLogic.isBusy(BackupState.ChooseSource))
        assertTrue(BackupStateLogic.isBusy(BackupState.Running(0, 4, "Preparing…")))
    }

    @Test
    fun aFinishedBackupIsNotBusySoTheButtonsWorkAgain() {
        assertFalse(BackupStateLogic.isBusy(BackupState.Idle))
        assertFalse(BackupStateLogic.isBusy(BackupState.Ready("Backed up 51 receipts.")))
        assertFalse(BackupStateLogic.isBusy(BackupState.Failed("Could not finish the backup.")))
        // A restore is over too; the dialog it shows is waiting on a restart, not on this app.
        assertFalse(BackupStateLogic.isBusy(BackupState.Restored("Restored 51 receipts.")))
    }
}
