package com.receiptledger.app.ui

import com.receiptledger.app.backup.BackupState
import com.receiptledger.engine.backup.BackupLogic
import com.receiptledger.engine.backup.DailyBackupLogic
import com.receiptledger.engine.model.Country
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.LocalDate

class SettingsScreenLogicTest {
    @Test
    fun sectionsAreListedAsSeparateBlocksWithImportBeforeExport() {
        assertEquals(
            listOf(
                "Tax country",
                "Your categories",
                "Import",
                "Export",
                "Backup",
                "Default for new receipts",
                "AI scan",
            ),
            SettingsScreenLogic.sections(),
        )
        val importAt = SettingsScreenLogic.sections().indexOf(SettingsScreenLogic.IMPORT_SECTION)
        val exportAt = SettingsScreenLogic.sections().indexOf(SettingsScreenLogic.EXPORT_SECTION)
        val backupAt = SettingsScreenLogic.sections().indexOf(SettingsScreenLogic.BACKUP_SECTION)
        assertTrue(importAt >= 0)
        assertEquals(importAt + 1, exportAt)
        // Backup sits with the other data-movement blocks rather than among the preferences.
        assertEquals(exportAt + 1, backupAt)
        assertEquals(
            listOf(SettingsScreenLogic.IMPORT_SECTION, SettingsScreenLogic.EXPORT_SECTION),
            SettingsScreenLogic.fileTransferSections(),
        )
    }

    @Test
    fun exportCopyIsShortAndSaysCsvOmitsOriginalPhotos() {
        val body = SettingsScreenLogic.EXPORT_BODY
        assertTrue(body.contains("CSV"))
        assertTrue(body.contains("does not include original photos"))
        assertTrue(body.contains("PDF"))
        assertTrue(body.contains("photos"))
        assertTrue(body.contains("home"))
        assertTrue(!body.contains("every receipt on this phone"))
        assertTrue(!body.contains("opens in a spreadsheet"))
    }

    @Test
    fun everySectionThatHasSomethingToExplainSaysItBehindTheInfoButton() {
        // The explanation must be reachable from the title, never printed among the controls.
        listOf(
            SettingsScreenLogic.TAX_COUNTRY to SettingsScreenLogic.countryBody(Country.CA),
            SettingsScreenLogic.CATEGORIES to SettingsScreenLogic.CATEGORIES_BODY,
            SettingsScreenLogic.IMPORT_SECTION to ImportScreenLogic.BODY,
            SettingsScreenLogic.EXPORT_SECTION to SettingsScreenLogic.EXPORT_BODY,
            SettingsScreenLogic.BACKUP_SECTION to BackupLogic.SECTION_BODY,
            SettingsScreenLogic.GEMINI to AiReadLogic.SETTINGS_BODY,
        ).forEach { (section, body) ->
            assertEquals(body, SettingsScreenLogic.sectionHelp(section, Country.CA), section)
        }
        // Three chips need no paragraph, and a null help text is what hides the button.
        assertNull(SettingsScreenLogic.sectionHelp(SettingsScreenLogic.DEFAULT_PURPOSE, Country.CA))
        assertNull(SettingsScreenLogic.sectionHelp("Not a section", Country.CA))
        assertEquals(
            listOf(SettingsScreenLogic.DEFAULT_PURPOSE),
            SettingsScreenLogic.sections()
                .filter { SettingsScreenLogic.sectionHelp(it, Country.CA) == null },
        )
    }

    @Test
    fun theTaxCountryExplanationFollowsWhicheverChipIsPicked() {
        val ca = SettingsScreenLogic.sectionHelp(SettingsScreenLogic.TAX_COUNTRY, Country.CA)!!
        val us = SettingsScreenLogic.sectionHelp(SettingsScreenLogic.TAX_COUNTRY, Country.US)!!
        val none = SettingsScreenLogic.sectionHelp(SettingsScreenLogic.TAX_COUNTRY, Country.NONE)!!
        assertTrue(ca.contains("T2125"))
        assertTrue(us.contains("Schedule C"))
        assertTrue(none.contains("No tax-form list"))
        assertEquals(3, setOf(ca, us, none).size)
    }

    @Test
    fun theInfoButtonNamesItsSectionForTheScreenReader() {
        assertEquals("About Backup", SettingsScreenLogic.helpButtonLabel("Backup"))
        assertEquals("About Export", SettingsScreenLogic.helpButtonLabel("Export"))
        assertEquals("Got it", SettingsScreenLogic.HELP_ACTION)
    }

    @Test
    fun aFinishedBackupFloatsPastAsOneMessageAndLeavesNothingInTheSection() {
        val summary = BackupLogic.readySummary(51, 13_122_237, "Google Drive")
        assertEquals(summary, SettingsScreenLogic.backupNotice(BackupState.Ready(summary)))
        assertEquals(
            "$summary Freed 2.1 MB from 3 photos.",
            SettingsScreenLogic.backupNotice(
                BackupState.Ready(summary, "Freed 2.1 MB from 3 photos."),
            ),
        )
        // A failure floats past the same way rather than sitting in red under the buttons.
        assertEquals(
            BackupLogic.GENERIC_FAILURE,
            SettingsScreenLogic.backupNotice(BackupState.Failed(BackupLogic.GENERIC_FAILURE)),
        )
        // Nothing has finished, so there is nothing to float and no row appears.
        assertNull(SettingsScreenLogic.backupNotice(BackupState.Idle))
        assertNull(SettingsScreenLogic.backupNotice(BackupState.ChooseSource))
        assertNull(SettingsScreenLogic.backupNotice(BackupState.ChooseDestination("b.zip")))
        assertNull(SettingsScreenLogic.backupNotice(BackupState.AwaitingPick))
        assertNull(SettingsScreenLogic.backupNotice(BackupState.Running(1, 4, "Packing…")))
        // A restore has to ask for a restart, so it stays a dialog and never becomes a notice.
        assertNull(SettingsScreenLogic.backupNotice(BackupState.Restored(summary)))
        assertNull(SettingsScreenLogic.backupNotice(BackupState.Failed("  ")))
    }

    @Test
    fun everySettingsMessageIsOneSentenceFitForTheFloatingBar() {
        // A one-line report belongs in the bar, so none of these may grow into a paragraph.
        listOf(
            SettingsScreenLogic.NOTHING_TO_EXPORT,
            SettingsScreenLogic.EXPORT_BUSY,
            SettingsScreenLogic.PDF_SAVE_FAILED,
            SettingsScreenLogic.CSV_SAVE_FAILED,
            SettingsScreenLogic.exportSuccessMessage(4),
            BackupLogic.GENERIC_FAILURE,
            BackupLogic.SAVE_FAILED,
            BackupLogic.CANCELLED,
            BackupLogic.RESTORE_CANCELLED,
            BackupLogic.NOTHING_TO_BACK_UP,
            BackupLogic.NOT_A_BACKUP_FILE,
            BackupLogic.EMPTY_FILE_WRITTEN,
            BackupLogic.UNREADABLE_SOURCE,
            BackupLogic.RESTORE_FAILURE,
            DailyBackupLogic.NEEDS_DESTINATION,
        ).forEach { message ->
            assertTrue(message.isNotBlank(), message)
            assertTrue(message.length <= 120, "too long for one bar: $message")
            assertTrue(!message.contains("\n"), "must be one row: $message")
        }
        assertEquals("Nothing to export yet.", SettingsScreenLogic.NOTHING_TO_EXPORT)
        assertEquals("An export is already running.", SettingsScreenLogic.EXPORT_BUSY)
    }

    @Test
    fun onlyTheThingsNeedingAChoiceOrAnActionStayOnTheScreen() {
        // Titles for the two blocks that cannot float: they hold Save and Share buttons.
        assertEquals("PDF ready", SettingsScreenLogic.PDF_READY_TITLE)
        assertEquals("CSV ready", SettingsScreenLogic.CSV_READY_TITLE)
        assertEquals("Exporting PDF", SettingsScreenLogic.EXPORT_RUNNING_TITLE)
        assertEquals("Close", SettingsScreenLogic.CLOSE)
    }

    @Test
    fun exportSuccessMessageMatchesImportTone() {
        assertEquals(0, SettingsScreenLogic.csvReceiptCount("date,merchant\n"))
        assertEquals(1, SettingsScreenLogic.csvReceiptCount("date,merchant\n2026-01-01,Store\n"))
        assertEquals(
            2,
            SettingsScreenLogic.csvReceiptCount("date,merchant\n2026-01-01,A\n2026-01-02,B\n"),
        )
        assertEquals("1 receipt exported successfully.", SettingsScreenLogic.exportSuccessMessage(1))
        assertEquals("8 receipts exported successfully.", SettingsScreenLogic.exportSuccessMessage(8))
    }

    @Test
    fun pdfSaveNoticeFiresAfterTheFileIsWrittenNotWhenThePdfIsReady() {
        assertEquals(null, SettingsScreenLogic.pdfSaveNotice(uriSelected = false, copied = false, 3))
        assertEquals(
            "Could not save the PDF.",
            SettingsScreenLogic.pdfSaveNotice(uriSelected = true, copied = false, 3),
        )
        assertEquals(
            "1 receipt exported successfully.",
            SettingsScreenLogic.pdfSaveNotice(uriSelected = true, copied = true, 1),
        )
        assertEquals(
            "4 receipts exported successfully.",
            SettingsScreenLogic.pdfSaveNotice(uriSelected = true, copied = true, 4),
        )
        assertEquals(
            "Could not save the CSV.",
            SettingsScreenLogic.csvSaveNotice(uriSelected = true, copied = false, 2),
        )
        assertEquals(
            "2 receipts exported successfully.",
            SettingsScreenLogic.csvSaveNotice(uriSelected = true, copied = true, 2),
        )
    }

    @Test
    fun csvExportWritesADatedFileAndSkipsEmptySheets() {
        val dir = Files.createTempDirectory("csv-export").toFile()
        val date = LocalDate.of(2026, 8, 28)
        assertNull(
            SettingsScreenLogic.writeCsvExport(
                cacheDir = dir,
                csv = "date,merchant\n",
                allReceipts = true,
                date = date,
            ),
        )
        val csv = "date,merchant\n2026-01-01,Store\n"
        val all = SettingsScreenLogic.writeCsvExport(dir, csv, allReceipts = true, date = date)!!
        assertEquals("receipts-all-2026-08-28.csv", all.fileName)
        assertEquals(1, all.receiptCount)
        assertEquals(csv, all.file.readText())
        val selected = SettingsScreenLogic.writeCsvExport(
            dir,
            "date,merchant\n2026-01-01,A\n2026-01-02,B\n",
            allReceipts = false,
            date = date,
        )!!
        assertEquals("receipts-selected-2026-08-28.csv", selected.fileName)
        assertEquals(2, selected.receiptCount)
    }
}
