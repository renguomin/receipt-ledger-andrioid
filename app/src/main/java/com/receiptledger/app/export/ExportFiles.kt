package com.receiptledger.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ExportFiles {
    const val PDF_MIME = "application/pdf"
    const val CSV_MIME = "text/csv"

    fun share(context: Context, file: File, mime: String = PDF_MIME) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export receipts"))
    }

    fun copy(context: Context, from: File, to: Uri): Boolean {
        val output = context.contentResolver.openOutputStream(to) ?: return false
        return runCatching {
            output.use { stream ->
                from.inputStream().use { input -> input.copyTo(stream) }
            }
        }.isSuccess
    }
}
