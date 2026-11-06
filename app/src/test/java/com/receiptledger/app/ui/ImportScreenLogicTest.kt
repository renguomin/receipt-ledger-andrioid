package com.receiptledger.app.ui

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImportScreenLogicTest {
    @Test
    fun copyDescribesQuickImportAndWarnsAgainstOutsidePdfs() {
        assertEquals("Import receipts", ImportScreenLogic.TITLE)
        assertEquals("Import", ImportScreenLogic.SETTINGS_HEADING)
        assertEquals("Quick import", ImportScreenLogic.TOGGLE_LABEL)
        assertFalse(ImportScreenLogic.DEFAULT_QUICK_IMPORT)
        assertEquals("Choose PDF", ImportScreenLogic.CHOOSE_PDF)
        assertEquals(
            ImportScreenLogic.TOGGLE_HELP,
            ImportScreenLogic.toggleHelp(quickImport = false),
        )
        assertEquals(
            ImportScreenLogic.TOGGLE_HELP,
            ImportScreenLogic.toggleHelp(quickImport = true),
        )
        assertTrue(ImportScreenLogic.TOGGLE_HELP.contains("without opening Check the fields"))
        assertTrue(ImportScreenLogic.TOGGLE_HELP.contains("Not recommended"))
        assertTrue(ImportScreenLogic.TOGGLE_HELP.contains("created outside this app"))
        assertEquals("Review this PDF?", ImportScreenLogic.OUTSIDE_WARNING_TITLE)
        assertTrue(ImportScreenLogic.OUTSIDE_WARNING_BODY.contains("wasn’t created in Receipt Ledger"))
        assertEquals("Review each receipt", ImportScreenLogic.OUTSIDE_WARNING_REVIEW)
        assertEquals("Save all without reviewing", ImportScreenLogic.OUTSIDE_WARNING_SAVE_ALL)
    }

    @Test
    fun successMessageUsesSingularAndPlural() {
        assertEquals("1 receipt imported successfully.", ImportScreenLogic.successMessage(1))
        assertEquals("8 receipts imported successfully.", ImportScreenLogic.successMessage(8))
    }

    @Test
    fun reviewBatchMessageReportsSkippedReceiptsSeparately() {
        assertEquals(
            "8 receipts imported successfully.",
            ImportScreenLogic.reviewBatchMessage(saved = 8, skipped = 0),
        )
        assertEquals(
            "8 receipts imported successfully. 1 receipt skipped.",
            ImportScreenLogic.reviewBatchMessage(saved = 8, skipped = 1),
        )
        assertEquals(
            "1 receipt imported successfully. 2 receipts skipped.",
            ImportScreenLogic.reviewBatchMessage(saved = 1, skipped = 2),
        )
        assertEquals(
            "No receipts imported. 3 receipts skipped.",
            ImportScreenLogic.reviewBatchMessage(saved = 0, skipped = 3),
        )
        assertEquals(
            "No receipts imported. 1 receipt skipped.",
            ImportScreenLogic.reviewBatchMessage(saved = 0, skipped = 1),
        )
    }

    @Test
    fun outsideWarningOnlyWhenQuickImportAndNotThisAppsExport() {
        assertFalse(ImportScreenLogic.needsOutsideWarning(quickImport = false, fromLedgerExport = false))
        assertFalse(ImportScreenLogic.needsOutsideWarning(quickImport = false, fromLedgerExport = true))
        assertFalse(ImportScreenLogic.needsOutsideWarning(quickImport = true, fromLedgerExport = true))
        assertTrue(ImportScreenLogic.needsOutsideWarning(quickImport = true, fromLedgerExport = false))
    }

    @Test
    fun finishModeReviewsEachUnlessQuickImportSavesTheBatch() {
        assertEquals(
            ImportFinishMode.REVIEW_EACH,
            ImportScreenLogic.finishMode(quickImport = false, fromLedgerExport = true),
        )
        assertEquals(
            ImportFinishMode.REVIEW_EACH,
            ImportScreenLogic.finishMode(quickImport = false, fromLedgerExport = false),
        )
        assertEquals(
            ImportFinishMode.SAVE_ALL,
            ImportScreenLogic.finishMode(quickImport = true, fromLedgerExport = true),
        )
        assertEquals(
            ImportFinishMode.REVIEW_EACH,
            ImportScreenLogic.finishMode(
                quickImport = true,
                fromLedgerExport = false,
                saveOutsideWithoutReview = false,
            ),
        )
        assertEquals(
            ImportFinishMode.SAVE_ALL,
            ImportScreenLogic.finishMode(
                quickImport = true,
                fromLedgerExport = false,
                saveOutsideWithoutReview = true,
            ),
        )
    }

    @Test
    fun ledgerExportUsesPdfFieldsAndKeepsAPhotoWhenPresent() {
        assertTrue(ImportScreenLogic.usesPdfFields(fromLedgerExport = true))
        assertFalse(ImportScreenLogic.scansEachPhoto(fromLedgerExport = true, photoCount = 2))
        val plan = ImportScreenLogic.planImport(
            fromLedgerExport = true,
            receiptCount = 2,
            photoCount = 2,
        )
        assertEquals(2, plan.size)
        assertEquals(ImportPlanItem(0, 0, ocrPhoto = false), plan[0])
        assertEquals(ImportPlanItem(1, 1, ocrPhoto = false), plan[1])
    }

    @Test
    fun ledgerExportWithExtraPhotosKeepsOneRowPerReceipt() {
        val plan = ImportScreenLogic.planImport(
            fromLedgerExport = true,
            receiptCount = 1,
            photoCount = 8,
        )
        assertEquals(listOf(ImportPlanItem(0, 0, ocrPhoto = false)), plan)
    }

    @Test
    fun ledgerExportWithoutPhotosStillPlansEachReceipt() {
        val plan = ImportScreenLogic.planImport(
            fromLedgerExport = true,
            receiptCount = 1,
            photoCount = 0,
        )
        assertEquals(listOf(ImportPlanItem(0, 0, ocrPhoto = false)), plan)
    }

    @Test
    fun ledgerPhotoStaysWithItsReceiptWhenANeighborHasNoPhoto() {
        val first = byteArrayOf(1, 2, 3)
        val third = byteArrayOf(4, 5, 6)
        val embedded = listOf(first, null, third)
        assertEquals(first, ImportScreenLogic.photoForLedgerReceipt(0, embedded, emptyList()))
        assertNull(ImportScreenLogic.photoForLedgerReceipt(1, embedded, emptyList()))
        assertEquals(third, ImportScreenLogic.photoForLedgerReceipt(2, embedded, emptyList()))
    }

    @Test
    fun ledgerPhotoUsesAppendixPagesWhenNoneAreEmbedded() {
        val pages = listOf(
            byteArrayOf(1),
            byteArrayOf(2),
            byteArrayOf(10),
            byteArrayOf(20),
        )
        val embedded = listOf(null, null)
        assertArrayEquals(byteArrayOf(10), ImportScreenLogic.photoForLedgerReceipt(0, embedded, pages))
        assertArrayEquals(byteArrayOf(20), ImportScreenLogic.photoForLedgerReceipt(1, embedded, pages))
    }

    @Test
    fun photosLiftedFromAnOutsidePdfAreReadByTheAiOnSight() {
        assertTrue(ImportScreenLogic.aiScansPhoto(fromLedgerExport = false, ocrPhoto = true))
        assertFalse(ImportScreenLogic.aiScansPhoto(fromLedgerExport = true, ocrPhoto = true))
        assertFalse(ImportScreenLogic.aiScansPhoto(fromLedgerExport = false, ocrPhoto = false))
        assertFalse(ImportScreenLogic.aiScansPhoto(fromLedgerExport = true, ocrPhoto = false))
    }

    @Test
    fun everyPhotoInAnOutsideReportBecomesItsOwnAiReadReceipt() {
        val plan = ImportScreenLogic.planImport(
            fromLedgerExport = false,
            receiptCount = 27,
            photoCount = 27,
        )
        assertEquals(27, plan.size)
        assertEquals(List(27) { it }, plan.map { it.photoIndex })
        assertTrue(plan.all { it.ocrPhoto })
        assertTrue(
            plan.all { ImportScreenLogic.aiScansPhoto(fromLedgerExport = false, ocrPhoto = it.ocrPhoto) },
        )
    }

    @Test
    fun outsidePdfWithPhotosIsTreatedAsNewScans() {
        assertFalse(ImportScreenLogic.usesPdfFields(fromLedgerExport = false))
        assertTrue(ImportScreenLogic.scansEachPhoto(fromLedgerExport = false, photoCount = 3))
        val plan = ImportScreenLogic.planImport(
            fromLedgerExport = false,
            receiptCount = 1,
            photoCount = 3,
        )
        assertEquals(3, plan.size)
        plan.forEachIndexed { index, item ->
            assertEquals(index, item.photoIndex)
            assertNull(item.receiptIndex)
            assertTrue(item.ocrPhoto)
        }
    }

    @Test
    fun outsidePdfWithOnlyTextPlansEachTextReceipt() {
        assertFalse(ImportScreenLogic.scansEachPhoto(fromLedgerExport = false, photoCount = 0))
        val plan = ImportScreenLogic.planImport(
            fromLedgerExport = false,
            receiptCount = 2,
            photoCount = 0,
        )
        assertEquals(
            listOf(
                ImportPlanItem(null, 0, ocrPhoto = false),
                ImportPlanItem(null, 1, ocrPhoto = false),
            ),
            plan,
        )
    }

    @Test
    fun emptySourcesYieldNoPlan() {
        assertTrue(
            ImportScreenLogic.planImport(
                fromLedgerExport = true,
                receiptCount = 0,
                photoCount = 0,
            ).isEmpty(),
        )
        assertTrue(
            ImportScreenLogic.planImport(
                fromLedgerExport = false,
                receiptCount = 0,
                photoCount = 0,
            ).isEmpty(),
        )
    }
}
