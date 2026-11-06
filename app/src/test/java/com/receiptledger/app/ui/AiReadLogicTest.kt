package com.receiptledger.app.ui

import com.receiptledger.app.gemini.GeminiHttp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiReadLogicTest {
    @Test
    fun geminiWaitsUpToThirtySeconds() {
        assertEquals(30L, GeminiHttp.CALL_TIMEOUT_SECONDS)
    }

    @Test
    fun theAiReadingAlwaysBeatsThePhonesOwnScan() {
        assertTrue(AiReadLogic.AI_WINS_OVER_LOCAL_SCAN)
    }

    @Test
    fun theStatusLineSaysWhatIsHappeningThenHowItWent() {
        assertEquals(
            AiReadLogic.BUSY_STATUS,
            AiReadLogic.status(busy = true, error = "ignored while busy", summary = null),
        )
        assertEquals(
            "Read 9 fields",
            AiReadLogic.status(busy = false, error = null, summary = "Read 9 fields"),
        )
        assertNull(AiReadLogic.status(busy = false, error = null, summary = null))
        assertNull(AiReadLogic.status(busy = false, error = null, summary = "  "))
    }

    @Test
    fun aFailedReadSaysThePhoneFilledTheFieldsInstead() {
        val error = "Gemini took longer than 30 seconds. ${AiReadLogic.FALLBACK_HINT}"
        assertEquals(
            AiReadLogic.FALLBACK_STATUS,
            AiReadLogic.status(busy = false, error = error, summary = "stale"),
        )
        assertTrue(AiReadLogic.statusIsFallback(busy = false, error = error))
        assertFalse(AiReadLogic.statusIsFallback(busy = true, error = error))
        assertFalse(AiReadLogic.statusIsFallback(busy = false, error = null))
        assertFalse(AiReadLogic.FALLBACK_HINT.contains("tap", ignoreCase = true))
    }

    @Test
    fun nothingInTheCopyStillPointsAtAButton() {
        val copy = listOf(
            AiReadLogic.ACTION_NAME,
            AiReadLogic.BUSY_STATUS,
            AiReadLogic.FALLBACK_STATUS,
            AiReadLogic.FALLBACK_HINT,
            AiReadLogic.SETTINGS_BODY,
        )
        copy.forEach {
            assertFalse(it.contains("tap", ignoreCase = true), it)
            assertFalse(it.contains("button", ignoreCase = true), it)
            assertFalse(it.contains("enhance", ignoreCase = true), it)
        }
        assertEquals("AI scan", SettingsScreenLogic.GEMINI)
    }

    @Test
    fun settingsSayEveryPhotoGoesToGoogleAndWhatHappensWhenItCannot() {
        val body = AiReadLogic.SETTINGS_BODY
        assertTrue(body.contains("Every receipt photo is sent"))
        assertTrue(body.contains("phone"))
        assertFalse(body.contains("Nothing is sent until", ignoreCase = true))
        assertFalse(body.contains("Off by default"))
    }
}
