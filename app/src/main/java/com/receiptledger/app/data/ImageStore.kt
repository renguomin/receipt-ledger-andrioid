package com.receiptledger.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.receiptledger.engine.export.PdfExportLogic
import com.receiptledger.engine.ocr.ImageOrientation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID

class ImageStore(private val context: Context) {
    private val receiptsDir: File
        get() = File(context.filesDir, "receipts").also { it.mkdirs() }

    suspend fun persist(source: Uri): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val dest = File(receiptsDir, "$id.jpg")
        val bytes = when (source.scheme) {
            "file" -> {
                val file = source.path?.let(::File)
                if (file != null && file.isFile &&
                    file.canonicalPath.startsWith(receiptsDir.canonicalPath)
                ) {
                    return@withContext file.absolutePath
                }
                file?.takeIf { it.isFile }?.readBytes()
            }
            else -> context.contentResolver.openInputStream(source)?.use { input ->
                input.readBytes()
            }
        } ?: error("Could not read scanned image")
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (decoded != null) {
            val orientation = runCatching {
                ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val bitmap = applyExif(decoded, orientation)
            try {
                writeJpeg(bitmap, dest)
            } finally {
                if (bitmap !== decoded) decoded.recycle()
                bitmap.recycle()
            }
        } else {
            dest.outputStream().use { output -> output.write(bytes) }
        }
        dest.absolutePath
    }

    suspend fun persistJpeg(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val dest = File(receiptsDir, "$id.jpg")
        dest.writeBytes(bytes)
        dest.absolutePath
    }

    suspend fun persistImage(bytes: ByteArray): String {
        if (PdfExportLogic.isJpeg(bytes)) return persistJpeg(bytes)
        return withContext(Dispatchers.IO) {
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: error("Could not read imported image")
            val dest = File(receiptsDir, "${UUID.randomUUID()}.jpg")
            try {
                writeJpeg(decoded, dest)
            } finally {
                decoded.recycle()
            }
            dest.absolutePath
        }
    }

    suspend fun persistPlaceholder(): String = withContext(Dispatchers.IO) {
        val dest = File(receiptsDir, "${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        try {
            writeJpeg(bitmap, dest)
        } finally {
            bitmap.recycle()
        }
        dest.absolutePath
    }

    fun cacheDirectory(): File = context.cacheDir

    suspend fun rotate180(path: String) = withContext(Dispatchers.IO) {
        val src = BitmapFactory.decodeFile(path) ?: return@withContext
        val matrix = Matrix().apply { postRotate(180f) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        try {
            writeJpeg(rotated, File(path))
        } finally {
            if (rotated !== src) src.recycle()
            rotated.recycle()
        }
    }

    fun file(path: String): File = File(path)

    private fun writeJpeg(bitmap: Bitmap, dest: File) {
        dest.outputStream().use { output ->
            val ok = bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                PdfExportLogic.storedJpegQuality(),
                output,
            )
            if (!ok) error("Could not compress scanned image")
        }
    }
}

internal fun applyExif(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (ImageOrientation.exifAction(orientation)) {
        ImageOrientation.ExifAction.IDENTITY -> return bitmap
        ImageOrientation.ExifAction.FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ImageOrientation.ExifAction.ROTATE_180 -> matrix.setRotate(180f)
        ImageOrientation.ExifAction.FLIP_VERTICAL -> {
            matrix.setRotate(180f)
            matrix.postScale(-1f, 1f)
        }
        ImageOrientation.ExifAction.TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ImageOrientation.ExifAction.ROTATE_90_CW -> matrix.setRotate(90f)
        ImageOrientation.ExifAction.TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ImageOrientation.ExifAction.ROTATE_270_CW -> matrix.setRotate(-90f)
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
