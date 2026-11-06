package com.receiptledger.app.ui

/**
 * The AI reads every receipt photo on its own, so there is no button to press. What is left to
 * say is how that read went, and when it did not go at all, that the fields on screen came from
 * the phone reading the photo by itself instead.
 */
object AiReadLogic {
    const val ACTION_NAME = "AI scan"
    const val LOCAL_STATUS = "Reading the receipt…"
    const val BUSY_STATUS = "AI is reading the receipt…"
    const val FALLBACK_STATUS = "AI could not read this one, so the phone's own scan filled the fields."
    const val FALLBACK_HINT = "The phone's own scan was used instead."
    const val SETTINGS_BODY =
        "Every receipt photo is sent to Google so it can read merchant, date, total, and other " +
            "details. The phone also reads the photo by itself at the same time, and those fields " +
            "are used if the AI cannot be reached."

    /**
     * The AI read the photo the customer is looking at, while the phone's own scan is only there
     * for when the AI cannot be reached, so the AI's fields win wherever the two disagree.
     */
    const val AI_WINS_OVER_LOCAL_SCAN = true

    /** Named while the customer waits, so the wait is attributed to whoever is doing the reading. */
    fun loadingLabel(aiBusy: Boolean): String = if (aiBusy) BUSY_STATUS else LOCAL_STATUS

    /** The one line shown under the fields: what is happening, or how it turned out. */
    fun status(busy: Boolean, error: String?, summary: String?): String? = when {
        busy -> BUSY_STATUS
        !error.isNullOrBlank() -> FALLBACK_STATUS
        else -> summary?.takeIf { it.isNotBlank() }
    }

    /** Failure is worth colouring, since it changes where the fields below came from. */
    fun statusIsFallback(busy: Boolean, error: String?): Boolean =
        !busy && !error.isNullOrBlank()
}
