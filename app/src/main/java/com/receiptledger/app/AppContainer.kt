package com.receiptledger.app

import android.content.Context
import androidx.room.Room
import com.receiptledger.app.backup.BackupController
import com.receiptledger.app.backup.DailyBackupScheduler
import com.receiptledger.app.data.AliasDao
import com.receiptledger.app.data.ImageStore
import com.receiptledger.app.data.PhotoJanitor
import com.receiptledger.app.data.MIGRATION_1_2
import com.receiptledger.app.data.MIGRATION_2_3
import com.receiptledger.app.data.ReceiptDao
import com.receiptledger.app.data.ReceiptDatabase
import com.receiptledger.app.data.ReceiptRepository
import com.receiptledger.app.data.SettingsStore
import com.receiptledger.app.export.PdfExportController
import com.receiptledger.app.gemini.GeminiClient
import com.receiptledger.app.gemini.GeminiHttp
import com.receiptledger.app.import.ImportRescanQueue
import com.receiptledger.app.ocr.FallbackReceiptOcr
import com.receiptledger.app.ocr.MlKitOcr
import com.receiptledger.app.ocr.RapidOcr
import com.receiptledger.app.ocr.ReceiptOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database: ReceiptDatabase = Room.databaseBuilder(
        appContext,
        ReceiptDatabase::class.java,
        BackupController.RECEIPT_DATABASE_NAME,
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

    val receiptDao: ReceiptDao = database.receiptDao()
    val aliasDao: AliasDao = database.aliasDao()
    val customCategoryDao = database.customCategoryDao()
    val imageStore = ImageStore(appContext)
    val settingsStore = SettingsStore(appContext)
    val ocr: ReceiptOcr = FallbackReceiptOcr(RapidOcr(), MlKitOcr())
    val gemini = GeminiClient(GeminiHttp.client())
    val repository = ReceiptRepository(
        receiptDao = receiptDao,
        aliasDao = aliasDao,
        customCategoryDao = customCategoryDao,
        imageStore = imageStore,
    )
    val pdfExport = PdfExportController(appContext)
    val importQueue = ImportRescanQueue()
    val photoJanitor = PhotoJanitor(appContext, receiptDao)
    val dailyBackupScheduler = DailyBackupScheduler(appContext, settingsStore)
    val backup = BackupController(
        context = appContext,
        receiptDao = receiptDao,
        settingsStore = settingsStore,
        janitor = photoJanitor,
        checkpointDatabase = ::checkpointDatabase,
        closeDatabase = ::closeDatabase,
        onDestinationChanged = dailyBackupScheduler::sync,
    )

    /**
     * Flushes the write-ahead log into the database file so a plain file copy holds every receipt.
     */
    private suspend fun checkpointDatabase() = withContext(Dispatchers.IO) {
        runCatching {
            database.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { it.moveToFirst() }
        }
        Unit
    }

    private suspend fun closeDatabase() = withContext(Dispatchers.IO) {
        runCatching { database.close() }
        Unit
    }
}
