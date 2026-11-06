package com.receiptledger.app.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PdfExportStateTest {
    @Test
    fun originOrNullTracksSettingsAndHomeJobs() {
        assertNull(PdfExportState.Idle.originOrNull())
        assertEquals(
            PdfExportOrigin.SETTINGS,
            PdfExportState.Running(
                origin = PdfExportOrigin.SETTINGS,
                completed = 1,
                total = 3,
                message = "Writing table 1 of 1",
            ).originOrNull(),
        )
        assertEquals(
            PdfExportOrigin.HOME,
            PdfExportState.Ready(
                origin = PdfExportOrigin.HOME,
                filePath = "/tmp/receipts-selected.pdf",
                fileName = "receipts-selected.pdf",
                receiptCount = 2,
            ).originOrNull(),
        )
    }
}
