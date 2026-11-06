package com.receiptledger.app.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoticeLogicTest {
    @Test
    fun importExportAndBackupNoticesAllHoverForFourSeconds() {
        assertEquals(4_000L, NoticeLogic.HOLD_MS)
    }
}
