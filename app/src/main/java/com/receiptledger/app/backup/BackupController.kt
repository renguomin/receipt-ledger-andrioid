package com.receiptledger.app.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import com.receiptledger.app.BuildConfig
import com.receiptledger.app.data.PhotoJanitor
import com.receiptledger.app.data.RECEIPT_DATABASE_VERSION
import com.receiptledger.app.data.ReceiptDao
import com.receiptledger.app.data.SettingsStore
import com.receiptledger.engine.backup.BackupArchive
import com.receiptledger.engine.backup.BackupCompatibility
import com.receiptledger.engine.backup.BackupLogic
import com.receiptledger.engine.backup.BackupManifest
import com.receiptledger.engine.backup.BackupOutcome
import com.receiptledger.engine.backup.PickedDocument
import com.receiptledger.engine.backup.SaveOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

sealed class BackupState {
    data object Idle : BackupState()

    data class Running(val completed: Int, val total: Int, val message: String) : BackupState() {
        val fraction: Float get() = BackupLogic.fraction(completed, total)
    }

    /**
     * The archive is built and waiting; the screen has to open the system picker because only the
     * person can say where it goes. Nothing is uploaded by this app.
     *
     * One picker covers both outcomes: tapping a file that is already there writes over it, and a
     * name that is not in the folder yet becomes a new file.
     */
    data class ChooseDestination(val fileName: String) : BackupState()

    data object ChooseSource : BackupState()

    /**
     * A picker has been opened and the answer is still coming. Its own state because the screen
     * can ask to open one more than once for a single request: the effect that opens it re-runs on
     * recomposition, and the second ask stacks a second picker behind the first, which then has to
     * be dismissed by hand.
     */
    data object AwaitingPick : BackupState()

    data class Ready(val summary: String, val notice: String? = null) : BackupState()

    /** Room has to reopen the swapped database file, so the process restarts from here. */
    data class Restored(val summary: String) : BackupState()

    data class Failed(val message: String) : BackupState()
}

/**
 * Packs the ledger into one zip and hands it to Android's file picker. Saving to Google Drive is
 * then the Drive app's job, which is why this needs no Google account setup of any kind.
 */
class BackupController(
    context: Context,
    private val receiptDao: ReceiptDao,
    private val settingsStore: SettingsStore,
    private val janitor: PhotoJanitor,
    private val checkpointDatabase: suspend () -> Unit,
    private val closeDatabase: suspend () -> Unit,
    /** A new destination can change whether the daily run needs a network, so it is rescheduled. */
    private val onDestinationChanged: suspend () -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state
    private var job: Job? = null

    /**
     * Built before the picker opens and claimed exactly once. The result of an Activity launch can
     * be delivered more than once, and two writes racing on one file in truncating mode corrupts
     * it, so claiming is an atomic swap rather than a null check.
     */
    private val stagedArchive = AtomicReference<File?>(null)

    @Volatile
    private var stagedReceiptCount: Int = 0

    private val databaseFile: File get() = appContext.getDatabasePath(RECEIPT_DATABASE_NAME)
    private val settingsFile: File get() = File(appContext.filesDir, SETTINGS_FILE)
    private val receiptsDir: File get() = File(appContext.filesDir, "receipts")
    private val stagingDir: File get() = File(appContext.cacheDir, "backup").also { it.mkdirs() }

    fun isBusy(): Boolean = BackupStateLogic.isBusy(_state.value)

    /**
     * The screen has the picker on screen, so the request is spent. Recorded here rather than in
     * the screen because a recomposition can re-run the effect that opens it, and a second picker
     * would stack behind the first.
     */
    fun onPickerOpened() {
        if (BackupStateLogic.picker(_state.value) != null) {
            _state.value = BackupState.AwaitingPick
        }
    }

    /**
     * Packs everything, then either overwrites the remembered file or asks where to put it.
     * [rememberedUri] is null the first time, or after the saved file has been moved or deleted.
     */
    fun backUpNow(rememberedUri: Uri?) {
        if (isBusy()) return
        job = scope.launch {
            val archive = buildArchive() ?: return@launch
            if (rememberedUri != null) {
                // Claim it here too: the remembered path writes without any picker round trip.
                if (stagedArchive.compareAndSet(archive, null)) {
                    val outcome = writeTo(rememberedUri, archive)
                    if (!BackupLogic.canTryAnotherDestination(outcome)) {
                        reportSaveFailure(outcome, archive)
                        return@launch
                    }
                    Log.i(TAG, "Remembered destination is gone; asking where to put it instead")
                    stagedArchive.set(archive)
                }
            }
            _state.value = BackupState.ChooseDestination(BackupLogic.backupFileName())
        }
    }

    /**
     * The daily run. Writes over the remembered file with no screen and no picker: the permission
     * taken when that file was chosen survives restarts, which is what makes this possible without
     * any Google sign-in.
     */
    suspend fun runUnattendedBackup(): BackupOutcome {
        if (isBusy()) {
            Log.i(TAG, "Skipping the daily backup while a backup is already in progress")
            return BackupOutcome.BUSY
        }
        val uri = settingsStore.backupDocumentUri.first()
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: return BackupOutcome.NO_DESTINATION
        if (receiptDao.getAll().isEmpty()) return BackupOutcome.NOTHING_TO_BACK_UP

        val archive = buildArchive() ?: return BackupOutcome.WRITE_FAILED
        if (!stagedArchive.compareAndSet(archive, null)) return BackupOutcome.BUSY
        val outcome = writeTo(uri, archive)
        // Nobody is here to pick a new destination, so the archive is no use either way.
        archive.delete()
        // The screen is not up, so leave no leftover banner for the next person who opens it.
        _state.value = BackupState.Idle
        return if (outcome == SaveOutcome.SAVED) BackupOutcome.SAVED else BackupOutcome.WRITE_FAILED
    }

    /** Lets a backup that landed somewhere unhelpful be pointed at Drive instead. */
    fun chooseNewDestination() {
        if (isBusy()) return
        job = scope.launch {
            buildArchive() ?: return@launch
            _state.value = BackupState.ChooseDestination(BackupLogic.backupFileName())
        }
    }

    /**
     * The picker closed with a destination, or with nothing. Whoever claims the staged archive owns
     * the outcome; a repeated delivery of the same result finds nothing left and does nothing.
     *
     * The document may be one the picker just created or one that was already sitting in the
     * folder, and the person was not asked which. Only the second case can destroy anything, so
     * that is the only case whose name has to look like one of our backups.
     */
    fun onDestinationChosen(uri: Uri?) {
        val archive = stagedArchive.getAndSet(null)
        if (archive == null) {
            Log.i(TAG, "Ignoring a picker result for an archive that was already handled")
            return
        }
        if (uri == null) {
            archive.delete()
            fail(BackupLogic.CANCELLED)
            return
        }
        if (!archive.isFile) {
            fail(BackupLogic.GENERIC_FAILURE)
            return
        }
        job = scope.launch {
            // Read before writing, because writing is what replaces the remembered destination.
            val previous = rememberedDocument()
            val name = displayName(uri)
            val existingBytes = reportedSize(uri)
            // Logged because "it saved somewhere else than I picked" is otherwise unprovable.
            Log.i(
                TAG,
                "Picked ${BackupLogic.locationName(uri.authority.orEmpty())} " +
                    "(authority=${uri.authority}, name=$name, bytes=$existingBytes), " +
                    "previously ${previous?.authority ?: "nowhere"}",
            )
            BackupLogic.overwriteProblem(name, existingBytes)?.let { problem ->
                Log.w(TAG, "Refusing to write over \"$name\" ($existingBytes bytes)")
                archive.delete()
                fail(problem)
                return@launch
            }
            val outcome = writeTo(uri, archive)
            if (outcome == SaveOutcome.SAVED) {
                dropDuplicateOf(previous, uri, name)
            }
            reportSaveFailure(outcome, archive)
        }
    }

    /**
     * What the picker handed back may be a fresh duplicate rather than the file the person meant
     * to replace, because typing a name a folder already holds creates a second document. The new
     * one now holds the current backup, so the old one is removed and a single file is left.
     */
    private fun dropDuplicateOf(previous: PickedDocument?, chosen: Uri, chosenName: String?) {
        val chosenDocument = PickedDocument(
            uri = chosen.toString(),
            authority = chosen.authority.orEmpty(),
            name = chosenName,
        )
        if (!BackupLogic.duplicatesPrevious(previous, chosenDocument)) return
        val stale = runCatching { Uri.parse(previous!!.uri) }.getOrNull() ?: return
        runCatching {
            DocumentsContract.deleteDocument(appContext.contentResolver, stale)
        }.onSuccess {
            Log.i(TAG, "Removed the copy the picker duplicated: ${previous!!.name}")
        }.onFailure {
            Log.w(TAG, "Could not remove the duplicated copy; two files may be left", it)
        }
    }

    /** The destination in use before this pick, so a duplicate of it can be recognised. */
    private suspend fun rememberedDocument(): PickedDocument? {
        val stored = settingsStore.backupDocumentUri.first() ?: return null
        val uri = runCatching { Uri.parse(stored) }.getOrNull() ?: return null
        return PickedDocument(
            uri = stored,
            authority = uri.authority.orEmpty(),
            // Null once the file is gone, and a file that is gone needs no cleaning up.
            name = displayName(uri),
        )
    }

    fun requestRestore(rememberedUri: Uri?) {
        if (isBusy()) return
        if (rememberedUri == null) {
            _state.value = BackupState.ChooseSource
            return
        }
        restoreFrom(rememberedUri)
    }

    fun chooseAnotherBackup() {
        if (isBusy()) return
        _state.value = BackupState.ChooseSource
    }

    fun onSourceChosen(uri: Uri?) {
        if (uri == null) {
            fail(BackupLogic.RESTORE_CANCELLED)
            return
        }
        restoreFrom(uri)
    }

    fun dismiss() {
        if (!isBusy()) _state.value = BackupState.Idle
    }

    private suspend fun buildArchive(): File? {
        try {
            _state.value = BackupState.Running(0, 2, BackupLogic.preparingMessage())
            // Abandoned scans leave photos behind; sweeping first keeps them out of the archive.
            runCatching { janitor.sweep() }
            val rows = receiptDao.getAll()
            if (rows.isEmpty()) {
                fail(BackupLogic.NOTHING_TO_BACK_UP)
                return null
            }
            checkpointDatabase()
            val photos = janitor.referencedPhotos()
            val createdAt = System.currentTimeMillis()
            val target = File(stagingDir, BackupLogic.backupFileName())
            BackupArchive.write(
                target = target,
                manifest = BackupManifest(
                    databaseVersion = RECEIPT_DATABASE_VERSION,
                    createdAtMs = createdAt,
                    appVersionName = BuildConfig.VERSION_NAME,
                    receiptCount = rows.size,
                    photoCount = photos.size,
                ),
                database = databaseFile,
                settings = settingsFile.takeIf { it.isFile },
                photos = photos,
            ) { completed, workTotal, message ->
                _state.value = BackupState.Running(completed, workTotal, message)
            }
            stagedArchive.getAndSet(target)?.delete()
            stagedReceiptCount = rows.size
            Log.i(TAG, "Archive built: ${rows.size} receipts, ${photos.size} photos, ${target.length()} bytes")
            return target
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Could not build the archive", error)
            fail(error.message?.takeIf(String::isNotBlank) ?: BackupLogic.GENERIC_FAILURE)
            return null
        }
    }

    private suspend fun writeTo(uri: Uri, archive: File): SaveOutcome {
        val total = BackupLogic.workUnits(0)
        _state.value = BackupState.Running(total - 1, total, BackupLogic.savingMessage())
        val expected = archive.length()
        val count = stagedReceiptCount
        val written = withContext(Dispatchers.IO) {
            runCatching {
                // "wt" truncates, so re-saving over a previous backup leaves no stale tail behind.
                appContext.contentResolver.openOutputStream(uri, "wt").use { output ->
                    checkNotNull(output) { "No output stream for $uri" }
                    archive.inputStream().buffered().use { input ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
                true
            }.getOrElse {
                Log.w(TAG, "Could not write the backup to $uri", it)
                false
            }
        }
        if (!written) {
            // The archive stays on disk: the chosen file may simply be gone, and the caller can
            // offer somewhere else without making the person wait through another build.
            return SaveOutcome.UNWRITABLE
        }

        val authority = uri.authority.orEmpty()
        // Remembered as soon as the bytes are down, and before any check that could be wrong about
        // them. Forgetting a file that was written sends the next backup to the picker, and a new
        // file gets created rather than this one replaced.
        persistDestination(uri)

        val reported = reportedSize(uri)
        val deferred = BackupLogic.isOffDevice(authority)
        BackupLogic.verifyWrittenSize(expected, reported, deferred)?.let { problem ->
            Log.w(TAG, "Destination reports $reported bytes, expected $expected")
            archive.delete()
            fail(problem)
            return SaveOutcome.REJECTED
        }

        archive.delete()
        settingsStore.setLastBackupAt(System.currentTimeMillis())
        val location = BackupLogic.locationName(authority)
        Log.i(TAG, "Backup saved to $location ($expected bytes, provider reports $reported)")
        _state.value = BackupState.Ready(
            summary = BackupLogic.readySummary(count, expected, location),
            notice = BackupLogic.onDeviceNotice(authority),
        )
        return SaveOutcome.SAVED
    }

    /** Reports whatever [writeTo] could not, and clears the staged archive when it is spent. */
    private fun reportSaveFailure(outcome: SaveOutcome, archive: File) {
        if (outcome == SaveOutcome.UNWRITABLE) archive.delete()
        BackupLogic.saveFailureMessage(outcome)?.let(::fail)
    }

    /**
     * The readable "where backups go" line for the screen. Google Drive exposes no folder or path
     * for a document, so for Drive this is the provider name and the file name.
     *
     * [readName] asks the provider what the file is called, which is a call that can be slow or
     * can hang on a document that has gone. The provider and folder come out of the URI itself, so
     * leaving it false gives a line that is still right about *where* without waiting on anything.
     */
    suspend fun describeDestination(uri: Uri, readName: Boolean = true): String =
        withContext(Dispatchers.IO) {
            BackupLogic.destinationSummary(
                authority = uri.authority.orEmpty(),
                fileName = if (readName) displayName(uri) else null,
                folder = BackupLogic.readableFolder(Uri.decode(uri.toString()).orEmpty()),
            )
        }

    /** Null when the provider declines to say, which the caller treats as "do not object". */
    private fun displayName(uri: Uri): String? = runCatching {
        appContext.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
    }.getOrNull()

    /** Null when the provider declines to say, which is not evidence of a problem. */
    private fun reportedSize(uri: Uri): Long? = runCatching {
        appContext.contentResolver
            .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
    }.getOrNull()

    private fun restoreFrom(uri: Uri) {
        job = scope.launch {
            var downloaded: File? = null
            try {
                _state.value = BackupState.Running(0, 2, BackupLogic.readingMessage())
                val local = File(stagingDir, "restore.zip").also { downloaded = it }
                val copied = withContext(Dispatchers.IO) {
                    runCatching {
                        appContext.contentResolver.openInputStream(uri).use { input ->
                            checkNotNull(input) { "No input stream for $uri" }
                            local.outputStream().buffered().use { input.copyTo(it) }
                        }
                        true
                    }.getOrDefault(false)
                }
                if (!copied) {
                    fail(BackupLogic.UNREADABLE_SOURCE)
                    return@launch
                }

                // Checked before anything is overwritten: a newer schema cannot be migrated back.
                val check = BackupCompatibility.check(
                    manifest = BackupArchive.readManifest(local),
                    appDatabaseVersion = RECEIPT_DATABASE_VERSION,
                )
                if (!check.allowed) {
                    fail(check.problem ?: BackupLogic.RESTORE_FAILURE)
                    return@launch
                }

                _state.value = BackupState.Running(1, 2, BackupLogic.restoringMessage())
                closeDatabase()
                val result = BackupArchive.restore(
                    archive = local,
                    databaseTarget = databaseFile,
                    settingsTarget = settingsFile,
                    photosDir = receiptsDir,
                )
                RestoredDatabase.rebasePhotoPaths(databaseFile, receiptsDir)
                persistDestination(uri)
                Log.i(TAG, "Restored ${result.manifest.receiptCount} receipts")
                _state.value = BackupState.Restored(
                    BackupLogic.restoredSummary(result.manifest.receiptCount, result.photosWritten),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Could not restore", error)
                fail(error.message?.takeIf(String::isNotBlank) ?: BackupLogic.RESTORE_FAILURE)
            } finally {
                downloaded?.delete()
            }
        }
    }

    /** Keeps read and write access to the chosen file across restarts, so later taps need no picker. */
    private suspend fun persistDestination(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure { Log.i(TAG, "Destination is not persistable; will ask again next time") }
        settingsStore.setBackupDocumentUri(uri.toString())
        runCatching { onDestinationChanged() }
    }

    private fun fail(message: String) {
        Log.w(TAG, "Backup stopped: $message")
        _state.value = BackupState.Failed(message)
    }

    companion object {
        const val RECEIPT_DATABASE_NAME = "receipt-ledger.db"
        const val SETTINGS_FILE = "datastore/settings.preferences_pb"
        private const val TAG = "BackupController"
    }
}
