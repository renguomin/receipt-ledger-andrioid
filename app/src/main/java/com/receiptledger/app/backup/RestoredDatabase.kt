package com.receiptledger.app.backup

import android.database.sqlite.SQLiteDatabase
import com.receiptledger.engine.backup.PhotoPaths
import java.io.File

/**
 * Touches the restored database file directly, before Room ever opens it, to point every receipt at
 * the photo directory on *this* install. Plain SQL is enough here: no schema is changed, so Room's
 * own version check still runs normally on the next launch.
 */
object RestoredDatabase {
    fun rebasePhotoPaths(database: File, receiptsDir: File): Int {
        if (!database.isFile) return 0
        return SQLiteDatabase.openDatabase(
            database.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { db ->
            val stored = LinkedHashMap<String, String>()
            db.rawQuery("SELECT id, imagePath FROM receipts", null).use { cursor ->
                while (cursor.moveToNext()) {
                    stored[cursor.getString(0)] = cursor.getString(1)
                }
            }
            val updates = PhotoPaths.rebaseAll(stored, receiptsDir.absolutePath)
            updates.forEach { (id, path) ->
                db.execSQL("UPDATE receipts SET imagePath = ? WHERE id = ?", arrayOf(path, id))
            }
            updates.size
        }
    }
}
