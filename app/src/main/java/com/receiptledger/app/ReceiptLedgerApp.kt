package com.receiptledger.app

import android.app.Application
import com.receiptledger.app.gemini.GeminiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class ReceiptLedgerApp : Application() {
    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val keyFile = File(getExternalFilesDir(null), GeminiClient.KEY_FILE_NAME)
        if (!keyFile.exists()) {
            keyFile.writeText("")
        }
        // Photos outlive the scans and imports that were abandoned before a receipt was saved.
        scope.launch { container.photoJanitor.sweep(container.importQueue.imagePaths()) }
        // Reconciles the queue with the switch, in case either changed while the app was dead.
        scope.launch { runCatching { container.dailyBackupScheduler.sync() } }
    }
}
