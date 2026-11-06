package com.receiptledger.app.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.receiptledger.app.ReceiptLedgerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Debug-only hook for running the daily backup on demand:
 *
 * ```
 * adb shell am broadcast -a com.receiptledger.app.RUN_DAILY_BACKUP -p com.receiptledger.app
 * ```
 *
 * The periodic job refuses to run before its own scheduled hour, so without this the only way to
 * watch an unattended backup is to wait until the small hours. This file lives in the debug source
 * set and is not part of a release build.
 */
class DebugBackupTrigger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? ReceiptLedgerApp ?: return
        Log.i(TAG, "Triggering an unattended backup by request")
        app.container.dailyBackupScheduler.runNow()
    }

    private companion object {
        const val TAG = "DailyBackup"
    }
}

/**
 * Debug-only: prints every column the provider exposes for the remembered backup file, to find out
 * whether a folder or path can honestly be shown for it.
 *
 * ```
 * adb shell am broadcast -a com.receiptledger.app.DUMP_DESTINATION -p com.receiptledger.app
 * ```
 */
class DebugDestinationDump : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? ReceiptLedgerApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val raw = app.container.settingsStore.backupDocumentUri.first()
                if (raw == null) {
                    Log.i(TAG, "No destination remembered")
                    return@launch
                }
                val uri = Uri.parse(raw)
                Log.i(TAG, "uri=$uri")
                Log.i(TAG, "authority=${uri.authority}")
                Log.i(TAG, "documentId=${runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()}")
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        Log.i(TAG, "Document row is empty")
                        return@use
                    }
                    for (i in 0 until cursor.columnCount) {
                        val value = runCatching { cursor.getString(i) }.getOrNull()
                        Log.i(TAG, "  ${cursor.getColumnName(i)} = $value")
                    }
                } ?: Log.i(TAG, "Provider returned no cursor")
                // Is a parent reachable at all for a plain document URI?
                val parent = runCatching {
                    DocumentsContract.findDocumentPath(context.contentResolver, uri)
                }.exceptionOrNull()?.toString() ?: "supported"
                Log.i(TAG, "findDocumentPath -> $parent")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "DestinationDump"
    }
}
