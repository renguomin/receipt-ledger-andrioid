package com.receiptledger.app.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.receiptledger.app.data.SettingsStore
import com.receiptledger.engine.backup.DailyBackupLogic
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Keeps WorkManager's queue in step with the daily backup switch. WorkManager persists its own
 * queue across reboots, so this only has to be told when something changes.
 */
class DailyBackupScheduler(
    context: Context,
    private val settingsStore: SettingsStore,
) {
    private val appContext = context.applicationContext

    /** Called at startup, when the switch moves, and when the destination changes. */
    suspend fun sync() {
        val enabled = settingsStore.dailyBackupEnabled.first()
        val destination = settingsStore.backupDocumentUri.first()
        if (!enabled || !DailyBackupLogic.canEnable(destination != null)) {
            cancel()
            return
        }
        schedule(destination!!)
    }

    private fun schedule(destination: String) {
        val authority = runCatching { Uri.parse(destination).authority }.getOrNull().orEmpty()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (DailyBackupLogic.requiresNetwork(authority)) {
                    NetworkType.CONNECTED
                } else {
                    NetworkType.NOT_REQUIRED
                },
            )
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<DailyBackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setInitialDelay(
                DailyBackupLogic.initialDelayMs(System.currentTimeMillis(), ZoneId.systemDefault()),
                TimeUnit.MILLISECONDS,
            )
            .build()

        // UPDATE rather than REPLACE, so changing the destination does not reset the daily cycle.
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            DailyBackupLogic.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        Log.i(TAG, "Daily backup scheduled (network required: ${DailyBackupLogic.requiresNetwork(authority)})")
    }

    /**
     * Runs the daily backup immediately, as its own one-off job. The periodic request will not run
     * ahead of its own schedule, so waiting for the small hours is otherwise the only way to see
     * an unattended backup happen.
     */
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<DailyBackupWorker>().build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "$WORK_NAME-now",
            ExistingWorkPolicy.REPLACE,
            request,
        )
        Log.i(TAG, "Daily backup requested on demand")
    }

    private fun cancel() {
        WorkManager.getInstance(appContext).cancelUniqueWork(DailyBackupLogic.WORK_NAME)
    }

    private companion object {
        const val TAG = "DailyBackup"
        const val WORK_NAME = DailyBackupLogic.WORK_NAME
    }
}
