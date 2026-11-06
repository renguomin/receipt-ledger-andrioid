package com.receiptledger.engine.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class BackupLogicTest {
    private val zone: ZoneId = ZoneId.of("America/Vancouver")
    private val BACKUP_NAME = BackupLogic.backupFileName()

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun theFileNameIsStableSoRepeatBackupsReplaceTheSameFile() {
        assertEquals("receipt-ledger-backup.zip", BackupLogic.backupFileName())
        assertTrue(BackupLogic.isBackupFileName(BackupLogic.backupFileName()))
        assertFalse(BackupLogic.isBackupFileName("receipts-all-2026-09-14.pdf"))
        assertFalse(BackupLogic.isBackupFileName("receipt-ledger-backup.zip.tmp"))
    }

    @Test
    fun aTimestampedNameIsAvailableForKeepingASeparateCopy() {
        assertEquals(
            "receipt-ledger-backup-2026-09-14-2006.zip",
            BackupLogic.timestampedFileName(at(2026, 9, 14, 20, 6), zone),
        )
    }

    @Test
    fun theButtonOnlyPromisesOneTapOnceSomewhereToSaveIsKnown() {
        assertEquals(BackupLogic.CHOOSE_DESTINATION, BackupLogic.backUpLabel(hasDestination = false))
        assertEquals(BackupLogic.BACK_UP_NOW, BackupLogic.backUpLabel(hasDestination = true))
    }

    @Test
    fun workUnitsCoverEveryPhotoPlusTheDatabaseAndTheUpload() {
        assertEquals(2, BackupLogic.workUnits(0))
        assertEquals(46, BackupLogic.workUnits(44))
        assertEquals(0f, BackupLogic.fraction(0, 46), 0.001f)
        assertEquals(1f, BackupLogic.fraction(46, 46), 0.001f)
        assertEquals(1f, BackupLogic.fraction(99, 46), 0.001f)
        assertEquals(0f, BackupLogic.fraction(1, 0), 0.001f)
    }

    @Test
    fun eachPhaseHasItsOwnMessage() {
        assertEquals("Packing your receipts…", BackupLogic.databaseMessage())
        assertEquals("Packing photo 3 of 44…", BackupLogic.archivingMessage(3, 44))
        assertEquals("Preparing backup…", BackupLogic.archivingMessage(0, 0))
        assertEquals("Saving your backup…", BackupLogic.savingMessage())
        assertEquals("Reading your backup…", BackupLogic.readingMessage())
        assertEquals("Restoring your receipts…", BackupLogic.restoringMessage())
    }

    @Test
    fun theSummaryNamesWhereItLandedSoDriveVersusPhoneIsObvious() {
        // The real authorities seen on device: Downloads was what a stray picker tap produced.
        assertEquals(
            "Google Drive",
            BackupLogic.locationName("com.google.android.apps.docs.storage"),
        )
        assertEquals(
            "Downloads on this phone",
            BackupLogic.locationName("com.android.providers.downloads.documents"),
        )
        assertEquals(
            "storage on this phone",
            BackupLogic.locationName("com.android.externalstorage.documents"),
        )
        assertEquals("the location you chose", BackupLogic.locationName("com.example.unknown"))
        assertEquals(
            "Backed up 48 receipts (11.1 MB) to Google Drive.",
            BackupLogic.readySummary(48, 11_615_796, "Google Drive"),
        )
    }

    @Test
    fun aLocalUriGivesUpItsRealFolderIncludingTheSurprisingOnes() {
        // The exact URI from the device, where a stray picker tap put the backup in a WeChat folder.
        assertEquals(
            "Download/WeiXin",
            BackupLogic.readableFolder(
                "content://com.android.providers.downloads.documents/document/" +
                    "raw:/storage/emulated/0/Download/WeiXin/receipt-ledger-backup.zip",
            ),
        )
        assertEquals(
            "Download",
            BackupLogic.readableFolder(
                "content://com.android.externalstorage.documents/document/" +
                    "primary:Download/receipt-ledger-backup.zip",
            ),
        )
        // A file sitting at the root of storage has no folder worth naming.
        assertNull(
            BackupLogic.readableFolder(
                "content://com.android.externalstorage.documents/document/primary:backup.zip",
            ),
        )
        // Drive hands out opaque handles with no path inside them.
        assertNull(
            BackupLogic.readableFolder(
                "content://com.google.android.apps.docs.storage/document/acc=1;doc=encoded=abc123",
            ),
        )
    }

    @Test
    fun theOutcomeIsOneLineBecauseTheFloatingRowOnlyHoldsOne() {
        val summary = BackupLogic.readySummary(51, 13_122_237, "Google Drive")
        assertEquals("Backed up 51 receipts (12.5 MB) to Google Drive.", summary)
        assertEquals(summary, BackupLogic.outcomeBody(summary, null))
        assertEquals(summary, BackupLogic.outcomeBody(summary, ""))
        assertEquals(summary, BackupLogic.outcomeBody(summary, "   "))
        // The warning used to be a second line under the summary; the row says both at once.
        assertEquals(
            "$summary Freed 2.1 MB from 3 photos.",
            BackupLogic.outcomeBody(summary, "Freed 2.1 MB from 3 photos."),
        )
        assertEquals("Freed 2.1 MB.", BackupLogic.outcomeBody("", "Freed 2.1 MB."))
        assertEquals("", BackupLogic.outcomeBody("", null))
    }

    @Test
    fun theDestinationLineNamesTheProviderAndTheFile() {
        assertEquals(
            "Saving to Google Drive · receipt-ledger-backup.zip",
            BackupLogic.destinationSummary(
                authority = "com.google.android.apps.docs.storage",
                fileName = "receipt-ledger-backup.zip",
                folder = null,
            ),
        )
        assertEquals(
            "Saving to Downloads on this phone · Download/WeiXin/receipt-ledger-backup.zip",
            BackupLogic.destinationSummary(
                authority = "com.android.providers.downloads.documents",
                fileName = "receipt-ledger-backup.zip",
                folder = "Download/WeiXin",
            ),
        )
        // A provider that reveals neither still names itself rather than showing a raw URI.
        assertEquals(
            "Saving to Google Drive",
            BackupLogic.destinationSummary(
                authority = "com.google.android.apps.docs.storage",
                fileName = null,
                folder = null,
            ),
        )
    }

    @Test
    fun switchingFromDriveToThePhoneChangesTheLineWithoutAskingAnyProvider() {
        // Where it saves comes out of the authority, so the line can be right before any call is
        // made. The reported bug was Drive still being named after a local file had been picked.
        val local = "com.android.externalstorage.documents"
        assertEquals(
            "Saving to storage on this phone",
            BackupLogic.destinationSummary(local, fileName = null, folder = null),
        )
        assertEquals(
            "Saving to storage on this phone · Documents",
            BackupLogic.destinationSummary(local, fileName = null, folder = "Documents"),
        )
        // Naming nothing at all is still better than naming the wrong place.
        assertEquals(
            "Saving to the location you chose",
            BackupLogic.destinationSummary("com.sec.android.app.myfiles", null, null),
        )
        val drive = BackupLogic.destinationSummary(
            "com.google.android.apps.docs.storage",
            fileName = null,
            folder = null,
        )
        assertTrue(drive.contains("Google Drive"))
        assertFalse(
            BackupLogic.destinationSummary(local, null, null).contains("Google Drive"),
        )
    }

    @Test
    fun aBackupLeftOnThePhoneSaysSoAndACloudOneStaysQuiet() {
        assertNull(BackupLogic.onDeviceNotice("com.google.android.apps.docs.storage"))
        assertNull(BackupLogic.onDeviceNotice("com.dropbox.android.FileCache"))
        val notice = BackupLogic.onDeviceNotice("com.android.providers.downloads.documents")
        assertNotNull(notice)
        assertTrue(notice!!.contains("lost"))
        // It trails the summary in one message row, so the pair has to stay readable there.
        val summary = BackupLogic.readySummary(51, 13_122_237, "Downloads on this phone")
        assertTrue(BackupLogic.outcomeBody(summary, notice).length <= 140)
    }

    @Test
    fun aZeroLengthWriteIsCaughtOnAProviderThatWritesImmediately() {
        assertEquals(
            BackupLogic.EMPTY_FILE_WRITTEN,
            BackupLogic.verifyWrittenSize(11_615_796, reportedBytes = 0, deferredWrite = false),
        )
        // A provider that reports nothing is not evidence of failure.
        assertNull(BackupLogic.verifyWrittenSize(11_615_796, null, deferredWrite = false))
        assertNull(BackupLogic.verifyWrittenSize(11_615_796, 11_615_796, deferredWrite = false))
        val short = BackupLogic.verifyWrittenSize(11_615_796, 4_096, deferredWrite = false)
        assertNotNull(short)
        assertTrue(short!!.contains("4 KB"))
        assertTrue(short.contains("11.1 MB"))
    }

    @Test
    fun aCloudProviderStillUploadingIsNeverCalledEmpty() {
        // Drive genuinely reports 0 bytes until its background upload finishes. Treating that as
        // failure reported a good backup as empty, and lost the destination with it.
        assertNull(BackupLogic.verifyWrittenSize(11_615_796, reportedBytes = 0, deferredWrite = true))
        // Nor is a partially uploaded size worth accusing it over.
        assertNull(BackupLogic.verifyWrittenSize(11_615_796, 4_096, deferredWrite = true))
        assertNull(BackupLogic.verifyWrittenSize(11_615_796, null, deferredWrite = true))
    }

    @Test
    fun theDestinationIsTreatedAsDeferredForExactlyTheCloudProviders() {
        // This is the flag that decides whether a zero reading is believed.
        assertTrue(BackupLogic.isOffDevice("com.google.android.apps.docs.storage"))
        assertFalse(BackupLogic.isOffDevice("com.android.providers.downloads.documents"))
    }

    @Test
    fun replacingRefusesAnyFileThatIsNotOneOfOurBackups() {
        // Overwriting destroys what is chosen, so a mistaken tap must not take a document with it.
        assertEquals(BackupLogic.NOT_A_BACKUP_FILE, BackupLogic.replaceTargetProblem("taxes-2025.pdf"))
        assertEquals(BackupLogic.NOT_A_BACKUP_FILE, BackupLogic.replaceTargetProblem("photo.jpg"))
        assertEquals(BackupLogic.NOT_A_BACKUP_FILE, BackupLogic.replaceTargetProblem("backup.zip"))
    }

    @Test
    fun replacingAcceptsOurOwnBackupsIncludingTimestampedAndRenamedCopies() {
        assertNull(BackupLogic.replaceTargetProblem("receipt-ledger-backup.zip"))
        assertNull(BackupLogic.replaceTargetProblem("receipt-ledger-backup-2026-09-14-2006.zip"))
        // Drive appends a suffix when a name collides, and that copy is still ours to replace.
        assertNull(BackupLogic.replaceTargetProblem("receipt-ledger-backup (1).zip"))
        // A provider that will not give a name cannot be second-guessed, so it is allowed through.
        assertNull(BackupLogic.replaceTargetProblem(null))
    }

    @Test
    fun aNameTypedOverAnExistingBackupLeavesOneFileRatherThanTwo() {
        val drive = "com.google.android.apps.docs.storage"
        val previous = PickedDocument("content://$drive/document/aaa", drive, BACKUP_NAME)
        // The case reported: the name was typed rather than tapped, so the picker made a second
        // document instead of writing over the first. The first one has been superseded.
        assertTrue(
            BackupLogic.duplicatesPrevious(
                previous,
                PickedDocument("content://$drive/document/bbb", drive, BACKUP_NAME),
            ),
        )
        // Tapping the file itself returns the same document, and there is nothing to clean up.
        assertFalse(
            BackupLogic.duplicatesPrevious(
                previous,
                PickedDocument("content://$drive/document/aaa", drive, BACKUP_NAME),
            ),
        )
    }

    @Test
    fun nothingIsDeletedUnlessItIsPlainlyOurOwnSupersededBackup() {
        val drive = "com.google.android.apps.docs.storage"
        val downloads = "com.android.providers.downloads.documents"
        val previous = PickedDocument("content://$drive/document/aaa", drive, BACKUP_NAME)
        // Moving to another provider is a real move; the old copy is not ours to remove.
        assertFalse(
            BackupLogic.duplicatesPrevious(
                previous,
                PickedDocument("content://$downloads/document/1", downloads, BACKUP_NAME),
            ),
        )
        // A different name is a different file, duplicate or not.
        assertFalse(
            BackupLogic.duplicatesPrevious(
                previous,
                PickedDocument("content://$drive/document/bbb", drive, "other-backup.zip"),
            ),
        )
        // Never touch something that is not one of our backups, whatever the picker returned.
        assertFalse(
            BackupLogic.duplicatesPrevious(
                PickedDocument("content://$drive/document/aaa", drive, "taxes-2025.pdf"),
                PickedDocument("content://$drive/document/bbb", drive, "taxes-2025.pdf"),
            ),
        )
        // Nothing remembered, or a file already gone, so there is nothing to delete.
        assertFalse(
            BackupLogic.duplicatesPrevious(
                null,
                PickedDocument("content://$drive/document/bbb", drive, BACKUP_NAME),
            ),
        )
        assertFalse(
            BackupLogic.duplicatesPrevious(
                PickedDocument("content://$drive/document/aaa", drive, null),
                PickedDocument("content://$drive/document/bbb", drive, BACKUP_NAME),
            ),
        )
    }

    @Test
    fun aWriteThatNeverLandedCanBeRetriedSomewhereElse() {
        // The case that broke: the remembered file had been deleted, so the archive must survive
        // long enough to be written wherever the person points next.
        assertTrue(BackupLogic.canTryAnotherDestination(SaveOutcome.UNWRITABLE))
        assertEquals(BackupLogic.SAVE_FAILED, BackupLogic.saveFailureMessage(SaveOutcome.UNWRITABLE))
    }

    @Test
    fun aWriteThatLandedIsNotOfferedAnotherDestinationOrASecondMessage() {
        assertFalse(BackupLogic.canTryAnotherDestination(SaveOutcome.SAVED))
        assertFalse(BackupLogic.canTryAnotherDestination(SaveOutcome.REJECTED))
        assertNull(BackupLogic.saveFailureMessage(SaveOutcome.SAVED))
        // Already explained by the size check; a second message would replace the useful one.
        assertNull(BackupLogic.saveFailureMessage(SaveOutcome.REJECTED))
    }

    @Test
    fun theFileTheyTappedDecidesOverwriteVersusNewWithoutAskingThem() {
        // A document the picker just made is empty, and that is the whole tell.
        assertFalse(BackupLogic.isOverwrite(0))
        assertFalse(BackupLogic.isOverwrite(null))
        assertTrue(BackupLogic.isOverwrite(1))
        assertTrue(BackupLogic.isOverwrite(13_122_237))
    }

    @Test
    fun aNameIsOnlyVettedWhenTheWriteWouldDestroySomething() {
        // Tapping a document in the folder: this write overwrites it, so the name has to be ours.
        assertEquals(
            BackupLogic.NOT_A_BACKUP_FILE,
            BackupLogic.overwriteProblem("taxes-2025.pdf", 48_204),
        )
        assertNull(BackupLogic.overwriteProblem("receipt-ledger-backup.zip", 13_122_237))
        // Typing a name the folder does not hold yet: nothing is there to lose, so anything goes.
        assertNull(BackupLogic.overwriteProblem("taxes-2025.pdf", 0))
        assertNull(BackupLogic.overwriteProblem("my-own-name.zip", 0))
        assertNull(BackupLogic.overwriteProblem("my-own-name.zip", null))
    }

    @Test
    fun theSectionCopyPromisesNoAccountSetup() {
        // The whole point of the picker route: nothing to configure, and Drive is just one choice.
        assertTrue(BackupLogic.SECTION_BODY.contains("Google Drive"))
        assertTrue(BackupLogic.SECTION_BODY.contains("one tap"))
        assertFalse(BackupLogic.SECTION_BODY.contains("sign in", ignoreCase = true))
    }

    @Test
    fun summariesAgreeWithSingularAndPluralCounts() {
        assertEquals(
            "Backed up 1 receipt (2 MB) to Google Drive.",
            BackupLogic.readySummary(1, 2L * 1024 * 1024, "Google Drive"),
        )
        assertEquals(
            "Backed up 44 receipts (9.5 MB) to Google Drive.",
            BackupLogic.readySummary(44, 9_961_472, "Google Drive"),
        )
        assertEquals("Restored 1 receipt and 1 photo.", BackupLogic.restoredSummary(1, 1))
        assertEquals("Restored 44 receipts and 44 photos.", BackupLogic.restoredSummary(44, 44))
    }

    @Test
    fun byteSizesReadTheWayAPersonWouldSayThem() {
        assertEquals("0 B", BackupLogic.formatBytes(0))
        assertEquals("512 B", BackupLogic.formatBytes(512))
        assertEquals("1 KB", BackupLogic.formatBytes(1024))
        assertEquals("608 KB", BackupLogic.formatBytes(622_592))
        assertEquals("9.5 MB", BackupLogic.formatBytes(9_961_472))
        assertEquals("134.2 MB", BackupLogic.formatBytes(140_724_666))
        assertEquals("1.5 GB", BackupLogic.formatBytes(1_610_612_736))
        assertEquals("0 B", BackupLogic.formatBytes(-5))
    }

    @Test
    fun theLastBackupLineGetsMoreSpecificTheMoreRecentItIs() {
        val now = at(2026, 9, 14, 20, 6)
        assertEquals(BackupLogic.NEVER_BACKED_UP, BackupLogic.lastBackupLabel(null, now, zone, Locale.US))
        assertEquals(BackupLogic.NEVER_BACKED_UP, BackupLogic.lastBackupLabel(0, now, zone, Locale.US))
        assertEquals(
            "Last backed up today at 8:06 PM",
            BackupLogic.lastBackupLabel(now, now, zone, Locale.US),
        )
        assertEquals(
            "Last backed up yesterday at 9:15 AM",
            BackupLogic.lastBackupLabel(at(2026, 9, 13, 9, 15), now, zone, Locale.US),
        )
        assertEquals(
            "Last backed up Sep 2",
            BackupLogic.lastBackupLabel(at(2026, 9, 2, 9, 15), now, zone, Locale.US),
        )
        assertEquals(
            "Last backed up Dec 30, 2025",
            BackupLogic.lastBackupLabel(at(2025, 12, 30, 9, 15), now, zone, Locale.US),
        )
    }

    @Test
    fun onlyPhotosAReceiptStillPointsAtAreUploadedAndEachOnlyOnce() {
        val names = BackupLogic.photoNamesToArchive(
            listOf(
                "/data/data/com.receiptledger.app/files/receipts/b.jpg",
                "/data/user/10/com.receiptledger.app/files/receipts/a.jpg",
                "/data/data/com.receiptledger.app/files/receipts/b.jpg",
                "",
                "   ",
            ),
        )
        assertEquals(listOf("a.jpg", "b.jpg"), names)
    }
}
