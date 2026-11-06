package com.receiptledger.app.data

import android.content.Context
import android.util.Log
import com.receiptledger.engine.backup.PhotoJanitorLogic
import com.receiptledger.engine.backup.PhotoSweep
import com.receiptledger.engine.backup.StoredPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Deletes receipt photos that no receipt points at. Photos are written to disk before the receipt
 * that owns one is saved, so abandoning a scan or a multi-page import strands the file.
 */
class PhotoJanitor(
    context: Context,
    private val receiptDao: ReceiptDao,
) {
    private val appContext = context.applicationContext

    private val receiptsDir: File
        get() = File(appContext.filesDir, "receipts")

    suspend fun referencedPhotos(): List<File> = withContext(Dispatchers.IO) {
        val dir = receiptsDir
        receiptDao.getAll()
            .map { PhotoJanitorLogic.fileName(it.imagePath) }
            .distinct()
            .map { File(dir, it) }
            .filter { it.isFile }
    }

    suspend fun sweep(inFlightPaths: Collection<String> = emptyList()): PhotoSweep =
        withContext(Dispatchers.IO) {
            val dir = receiptsDir
            val onDisk = dir.listFiles()?.filter { it.isFile }.orEmpty()
            val sweep = PhotoJanitorLogic.sweep(
                photos = onDisk.map { StoredPhoto(it.name, it.length(), it.lastModified()) },
                referencedPaths = receiptDao.getAll().map { it.imagePath },
                inFlightPaths = inFlightPaths,
                nowMs = System.currentTimeMillis(),
            )
            sweep.deletable.forEach { photo -> File(dir, photo.name).delete() }
            PhotoJanitorLogic.reclaimedNotice(sweep)?.let { Log.i(TAG, it) }
            sweep
        }

    private companion object {
        const val TAG = "PhotoJanitor"
    }
}
