package com.receiptledger.app.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object NoticeLogic {
    const val HOLD_MS = 4_000L
}

suspend fun SnackbarHostState.showHeldNotice(message: String) {
    coroutineScope {
        val job = launch {
            showSnackbar(message = message, duration = SnackbarDuration.Indefinite)
        }
        try {
            delay(NoticeLogic.HOLD_MS)
        } finally {
            currentSnackbarData?.dismiss()
            job.cancel()
        }
    }
}
