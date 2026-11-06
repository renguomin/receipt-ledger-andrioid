package com.receiptledger.engine.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BackupArchiveTest {
    @TempDir
    lateinit var temp: File

    private fun manifest(
        databaseVersion: Int = 3,
        receiptCount: Int = 2,
        photoCount: Int = 2,
    ) = BackupManifest(
        databaseVersion = databaseVersion,
        createdAtMs = 1_757_000_000_000L,
        appVersionName = "1.0",
        receiptCount = receiptCount,
        photoCount = photoCount,
    )

    private fun sourceTree(): Triple<File, File, List<File>> {
        val db = File(temp, "src/receipt-ledger.db").apply {
            parentFile.mkdirs()
            writeText("sqlite-bytes")
        }
        val settings = File(temp, "src/settings.preferences_pb").apply { writeText("prefs") }
        val photos = listOf("a.jpg", "b.jpg").map { name ->
            File(temp, "src/receipts/$name").apply {
                parentFile.mkdirs()
                writeText("jpeg-$name")
            }
        }
        return Triple(db, settings, photos)
    }

    @Test
    fun anArchiveRoundTripsTheDatabaseSettingsAndPhotos() {
        val (db, settings, photos) = sourceTree()
        val archive = BackupArchive.write(
            target = File(temp, "out/backup.zip"),
            manifest = manifest(),
            database = db,
            settings = settings,
            photos = photos,
        )
        assertTrue(archive.length() > 0)

        val dbTarget = File(temp, "restored/databases/receipt-ledger.db")
        val settingsTarget = File(temp, "restored/datastore/settings.preferences_pb")
        val photosDir = File(temp, "restored/receipts")
        val result = BackupArchive.restore(archive, dbTarget, settingsTarget, photosDir)

        assertEquals("sqlite-bytes", dbTarget.readText())
        assertEquals("prefs", settingsTarget.readText())
        assertEquals(2, result.photosWritten)
        assertTrue(result.settingsRestored)
        assertEquals("jpeg-a.jpg", File(photosDir, "a.jpg").readText())
        assertEquals("jpeg-b.jpg", File(photosDir, "b.jpg").readText())
        assertEquals(3, result.manifest.databaseVersion)
        assertEquals(2, result.manifest.receiptCount)
    }

    @Test
    fun theManifestIsReadableWithoutUnpackingTheArchive() {
        val (db, settings, photos) = sourceTree()
        val archive = BackupArchive.write(
            target = File(temp, "out/backup.zip"),
            manifest = manifest(receiptCount = 44),
            database = db,
            settings = settings,
            photos = photos,
        )
        assertEquals(44, BackupArchive.readManifest(archive)?.receiptCount)
        assertEquals(BackupManifest.FORMAT_VERSION, BackupArchive.readManifest(archive)?.formatVersion)
    }

    @Test
    fun aFileThatIsNotAnArchiveReadsAsNoManifest() {
        val bogus = File(temp, "notes.txt").apply { writeText("hello") }
        assertNull(BackupArchive.readManifest(bogus))
    }

    @Test
    fun archivingWorksWithoutASettingsFile() {
        val (db, _, photos) = sourceTree()
        val archive = BackupArchive.write(
            target = File(temp, "out/backup.zip"),
            manifest = manifest(photoCount = 2),
            database = db,
            settings = null,
            photos = photos,
        )
        val result = BackupArchive.restore(
            archive = archive,
            databaseTarget = File(temp, "restored/receipt-ledger.db"),
            settingsTarget = File(temp, "restored/settings.preferences_pb"),
            photosDir = File(temp, "restored/receipts"),
        )
        assertFalse(result.settingsRestored)
        assertEquals(2, result.photosWritten)
    }

    @Test
    fun progressCountsEveryPhotoPlusTheDatabaseAndUpload() {
        val (db, settings, photos) = sourceTree()
        val seen = mutableListOf<Pair<Int, Int>>()
        BackupArchive.write(
            target = File(temp, "out/backup.zip"),
            manifest = manifest(),
            database = db,
            settings = settings,
            photos = photos,
        ) { completed, total, _ -> seen += completed to total }
        assertEquals(listOf(0 to 4, 1 to 4, 2 to 4), seen)
    }

    @Test
    fun restoringClearsTheWriteAheadLogSoItIsNotReplayedOverTheRestoredDatabase() {
        val (db, settings, photos) = sourceTree()
        val archive = BackupArchive.write(
            target = File(temp, "out/backup.zip"),
            manifest = manifest(),
            database = db,
            settings = settings,
            photos = photos,
        )
        val dbTarget = File(temp, "live/receipt-ledger.db").apply {
            parentFile.mkdirs()
            writeText("old")
        }
        val wal = File(temp, "live/receipt-ledger.db-wal").apply { writeText("stale wal") }
        val shm = File(temp, "live/receipt-ledger.db-shm").apply { writeText("stale shm") }

        BackupArchive.restore(archive, dbTarget, null, File(temp, "live/receipts"))

        assertEquals("sqlite-bytes", dbTarget.readText())
        assertFalse(wal.exists())
        assertFalse(shm.exists())
    }

    @Test
    fun entriesThatTryToEscapeTheTargetDirectoryAreRejected() {
        assertTrue(BackupArchive.isSafeEntry("photos/a.jpg"))
        assertFalse(BackupArchive.isSafeEntry("../../etc/passwd"))
        assertFalse(BackupArchive.isSafeEntry("/etc/passwd"))
        assertFalse(BackupArchive.isSafeEntry("photos/../../escape.jpg"))
        assertFalse(BackupArchive.isSafeEntry(""))
    }

    @Test
    fun aBackupFromANewerBuildIsRefusedBecauseRoomCannotMigrateBackwards() {
        val check = BackupCompatibility.check(manifest(databaseVersion = 4), appDatabaseVersion = 3)
        assertTrue(check is RestoreCheck.DatabaseTooNew)
        assertFalse(check.allowed)
        assertTrue(check.problem!!.contains("newer version"))
    }

    @Test
    fun matchingAndOlderDatabaseVersionsRestore() {
        assertTrue(BackupCompatibility.check(manifest(databaseVersion = 3), 3).allowed)
        assertTrue(BackupCompatibility.check(manifest(databaseVersion = 2), 3).allowed)
    }

    @Test
    fun anUnknownArchiveLayoutOrMissingManifestIsRefused() {
        val future = manifest().copy(formatVersion = BackupManifest.FORMAT_VERSION + 1)
        assertTrue(BackupCompatibility.check(future, 3) is RestoreCheck.UnsupportedFormat)
        assertEquals(RestoreCheck.Unreadable, BackupCompatibility.check(null, 3))
        assertFalse(BackupCompatibility.check(null, 3).allowed)
    }

    @Test
    fun theManifestSurvivesEncodingAndToleratesUnknownFields() {
        val encoded = BackupManifest.encode(manifest())
        assertEquals(manifest(), BackupManifest.decode(encoded))
        val withExtra = encoded.dropLast(1) + ""","somethingNew":true}"""
        assertEquals(3, BackupManifest.decode(withExtra)?.databaseVersion)
        assertNull(BackupManifest.decode("not json"))
    }
}
