package com.receiptledger.app.backup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.receiptledger.app.ReceiptLedgerApp
import com.receiptledger.engine.backup.BackupOutcome
import com.receiptledger.engine.backup.DailyBackupLogic

/** Replaces the remembered backup file once a day, with no screen and no sign-in. */
class DailyBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ReceiptLedgerApp ?: return Result.failure()
        val outcome = runCatching { app.container.backup.runUnattendedBackup() }
            .getOrElse { error ->
                Log.w(TAG, "Daily backup threw", error)
                BackupOutcome.WRITE_FAILED
            }
        Log.i(TAG, "Daily backup finished: $outcome")
        return when {
            outcome == BackupOutcome.SAVED -> Result.success()
            DailyBackupLogic.isRetryable(outcome) -> Result.retry()
            // Nothing to write or nowhere to write it: succeed so the timer is not spun on.
            else -> Result.success()
        }
    }

    private companion object {
        const val TAG = "DailyBackup"
    }
}
