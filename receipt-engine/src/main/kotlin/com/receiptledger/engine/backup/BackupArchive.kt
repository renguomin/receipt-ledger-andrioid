package com.receiptledger.engine.backup

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class RestoreResult(
    val manifest: BackupManifest,
    val photosWritten: Int,
    val settingsRestored: Boolean,
)

/**
 * The backup archive: a zip holding the manifest, the Room database, the settings file and every
 * photo a receipt still points at.
 */
object BackupArchive {
    const val MANIFEST_ENTRY = "manifest.json"
    const val DATABASE_ENTRY = "database/receipt-ledger.db"
    const val SETTINGS_ENTRY = "settings/settings.preferences_pb"
    const val PHOTO_PREFIX = "photos/"
    const val MIME_TYPE = "application/zip"

    fun photoEntry(name: String): String = PHOTO_PREFIX + name

    fun write(
        target: File,
        manifest: BackupManifest,
        database: File,
        settings: File?,
        photos: List<File>,
        onProgress: (completed: Int, total: Int, message: String) -> Unit = { _, _, _ -> },
    ): File {
        target.parentFile?.mkdirs()
        val total = BackupLogic.workUnits(photos.size)
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            // Manifest goes first so a reader can check compatibility without unpacking the rest.
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(BackupManifest.encode(manifest).toByteArray())
            zip.closeEntry()

            onProgress(0, total, BackupLogic.databaseMessage())
            zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
            database.inputStream().buffered().use { it.copyTo(zip) }
            zip.closeEntry()

            if (settings != null && settings.isFile) {
                zip.putNextEntry(ZipEntry(SETTINGS_ENTRY))
                settings.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }

            photos.forEachIndexed { index, photo ->
                if (photo.isFile) {
                    zip.putNextEntry(ZipEntry(photoEntry(photo.name)))
                    photo.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                onProgress(index + 1, total, BackupLogic.archivingMessage(index + 1, photos.size))
            }
        }
        return target
    }

    fun readManifest(archive: File): BackupManifest? =
        runCatching {
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == MANIFEST_ENTRY) {
                        return@use BackupManifest.decode(zip.readBytes().decodeToString())
                    }
                    entry = zip.nextEntry
                }
                null
            }
        }.getOrNull()

    fun restore(
        archive: File,
        databaseTarget: File,
        settingsTarget: File?,
        photosDir: File,
    ): RestoreResult {
        val manifest = readManifest(archive) ?: error(RestoreCheck.Unreadable.MESSAGE)
        databaseTarget.parentFile?.mkdirs()
        photosDir.mkdirs()
        var photos = 0
        var settingsRestored = false
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    entry.isDirectory || !isSafeEntry(name) -> Unit
                    name == DATABASE_ENTRY -> copyTo(zip, databaseTarget)
                    name == SETTINGS_ENTRY -> if (settingsTarget != null) {
                        copyTo(zip, settingsTarget)
                        settingsRestored = true
                    }
                    name.startsWith(PHOTO_PREFIX) -> {
                        val fileName = name.removePrefix(PHOTO_PREFIX)
                        if (fileName.isNotBlank()) {
                            copyTo(zip, File(photosDir, fileName))
                            photos += 1
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        // A stale write-ahead log would be replayed on top of the database we just restored.
        clearSidecars(databaseTarget)
        return RestoreResult(manifest, photos, settingsRestored)
    }

    /** Guards against archive entries that try to escape the directory they are unpacked into. */
    internal fun isSafeEntry(name: String): Boolean =
        name.isNotBlank() &&
            !name.startsWith("/") &&
            !name.startsWith("\\") &&
            !name.contains("..") &&
            !name.contains(":")

    internal fun clearSidecars(database: File) {
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            File(database.parentFile, database.name + suffix).takeIf { it.exists() }?.delete()
        }
    }

    private fun copyTo(input: InputStream, target: File) {
        target.parentFile?.mkdirs()
        target.outputStream().buffered().use { input.copyTo(it) }
    }
}
