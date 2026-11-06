package com.receiptledger.app.import

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.receiptledger.engine.export.PdfExportLogic
import java.io.ByteArrayOutputStream
import java.io.File

object PdfPageImages {
    fun renderJpegs(pdfBytes: ByteArray, cacheDir: File): List<ByteArray> {
        val file = File(cacheDir, "import-pages.pdf")
        file.parentFile?.mkdirs()
        file.writeBytes(pdfBytes)
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    (0 until renderer.pageCount).map { index ->
                        renderer.openPage(index).use { page ->
                            val width = (page.width * 2).coerceAtLeast(1)
                            val height = (page.height * 2).coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            try {
                                val out = ByteArrayOutputStream()
                                bitmap.compress(
                                    Bitmap.CompressFormat.JPEG,
                                    PdfExportLogic.storedJpegQuality(),
                                    out,
                                )
                                out.toByteArray()
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
        } finally {
            file.delete()
        }
    }
}
