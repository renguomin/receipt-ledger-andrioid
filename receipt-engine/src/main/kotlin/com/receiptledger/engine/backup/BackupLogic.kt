package com.receiptledger.engine.backup

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Just enough about a chosen document to tell two of them apart, with no Android types involved. */
data class PickedDocument(val uri: String, val authority: String, val name: String?)

/** Wording, naming and progress arithmetic for the backup, kept out of the UI. */
object BackupLogic {
    const val SECTION_TITLE = "Backup"
    const val SECTION_BODY =
        "Saves your receipts, photos and settings as a single file. Pick Google Drive when asked " +
            "and it lives in your Google account. Later backups replace that same file in one tap."

    const val BACK_UP_NOW = "Back up now"
    const val CHOOSE_DESTINATION = "Choose where to save"
    const val RESTORE = "Restore from backup"
    const val RESTORE_ANOTHER = "Restore a different file"

    const val NEVER_BACKED_UP = "Not backed up yet"
    const val NOTHING_TO_BACK_UP = "Add a receipt before backing up."
    const val CANCELLED = "Backup cancelled."
    const val RESTORE_CANCELLED = "Restore cancelled."
    const val SAVE_FAILED =
        "Could not write the backup file. That file may be gone, so choose the location again."
    const val GENERIC_FAILURE = "Could not finish the backup."
    const val RESTORE_FAILURE = "Could not restore the backup."
    const val UNREADABLE_SOURCE = "Could not read that file."

    const val RESTORE_CONFIRM_TITLE = "Restore from backup?"
    const val RESTORE_CONFIRM_BODY =
        "This replaces the receipts, photos and settings on this device with the backup file. " +
            "Anything added since that backup is lost."
    const val RESTORE_CONFIRM_ACTION = "Restore"
    const val RESTART_TITLE = "Restore finished"
    const val RESTART_BODY = "Receipt Ledger needs to restart to open the restored ledger."
    const val RESTART_ACTION = "Restart now"

    /**
     * The result and the warning that sometimes rides along, as one line. Outcomes float past in
     * the same message row import and export use, and that row holds one string.
     */
    fun outcomeBody(summary: String, notice: String?): String =
        listOf(summary, notice.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" ")

    private const val PREFIX = "receipt-ledger-backup"

    /** One stable name, so repeat backups replace the same file instead of littering the folder. */
    fun backupFileName(): String = "$PREFIX.zip"

    fun timestampedFileName(createdAtMs: Long, zone: ZoneId): String {
        val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
            .withZone(zone)
            .format(Instant.ofEpochMilli(createdAtMs))
        return "$PREFIX-$stamp.zip"
    }

    fun isBackupFileName(name: String): Boolean =
        name.startsWith(PREFIX) && name.endsWith(".zip")

    /**
     * One unit per photo plus one for the database and one for writing it out, so the bar keeps
     * moving through both phases that take time.
     */
    fun workUnits(photoCount: Int): Int = photoCount + 2

    fun archivingMessage(done: Int, total: Int): String =
        if (total <= 0) "Preparing backup…" else "Packing photo $done of $total…"

    fun databaseMessage(): String = "Packing your receipts…"

    fun preparingMessage(): String = "Preparing backup…"

    fun savingMessage(): String = "Saving your backup…"

    fun readingMessage(): String = "Reading your backup…"

    fun restoringMessage(): String = "Restoring your receipts…"

    fun fraction(completed: Int, total: Int): Float =
        if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)

    /** The button only promises one tap once somewhere to save has actually been chosen. */
    fun backUpLabel(hasDestination: Boolean): String =
        if (hasDestination) BACK_UP_NOW else CHOOSE_DESTINATION

    fun readySummary(receiptCount: Int, sizeBytes: Long, location: String): String {
        val receipts = if (receiptCount == 1) "1 receipt" else "$receiptCount receipts"
        return "Backed up $receipts (${formatBytes(sizeBytes)}) to $location."
    }

    /** Names the provider the picker wrote to, so "backed up" is never a mystery destination. */
    fun locationName(authority: String): String = when {
        authority.contains("apps.docs") -> "Google Drive"
        authority.contains("providers.downloads") -> "Downloads on this phone"
        authority.contains("externalstorage") -> "storage on this phone"
        authority.contains("skydrive") || authority.contains("onedrive") -> "OneDrive"
        authority.contains("dropbox") -> "Dropbox"
        authority.contains("nextcloud") -> "Nextcloud"
        authority.isBlank() -> "the file you chose"
        else -> "the location you chose"
    }

    /**
     * Pulls a readable folder out of a document URI. Local providers embed a real path, so the
     * exact folder can be shown — which matters, because a backup can quietly land somewhere like
     * `Download/WeiXin`. Drive's URIs are opaque handles with no path in them, so this gives up.
     */
    fun readableFolder(decodedUri: String): String? {
        val path = when {
            decodedUri.contains(LOCAL_ROOT) -> decodedUri.substringAfter(LOCAL_ROOT)
            decodedUri.contains(PRIMARY_ROOT) -> decodedUri.substringAfter(PRIMARY_ROOT)
            else -> return null
        }
        return path.substringBeforeLast('/', missingDelimiterValue = "")
            .trim('/')
            .takeIf { it.isNotBlank() }
    }

    private const val LOCAL_ROOT = "/storage/emulated/0/"
    private const val PRIMARY_ROOT = "primary:"

    /** The "where backups go" line, so the destination is never a mystery before changing it. */
    fun destinationSummary(authority: String, fileName: String?, folder: String?): String {
        val where = locationName(authority)
        val path = listOfNotNull(folder, fileName).joinToString("/")
        return if (path.isBlank()) "Saving to $where" else "Saving to $where · $path"
    }

    /**
     * A copy on the phone dies with the phone; say so rather than implying the job is done. Kept
     * short because it rides along behind the summary in a message row that holds two lines.
     */
    fun onDeviceNotice(authority: String): String? =
        if (isOffDevice(authority)) null else "That copy is lost if the phone is."

    fun isOffDevice(authority: String): Boolean =
        listOf("apps.docs", "skydrive", "onedrive", "dropbox", "nextcloud", "box.android")
            .any { authority.contains(it) }

    const val CHANGE_DESTINATION = "Change where it saves"

    /** Overwriting is destructive, so the target has to look like one of our own backups. */
    const val NOT_A_BACKUP_FILE =
        "That is not a Receipt Ledger backup, so it was left alone. Tap a backup file or type a " +
            "new name."

    /**
     * Whether writing to the chosen document destroys something. The picker hands back a file it
     * has just created as an empty one, so size is what separates "saved somewhere new" from
     * "saved over what was already there" — no question to the person needed.
     */
    fun isOverwrite(reportedSizeBytes: Long?): Boolean = (reportedSizeBytes ?: 0L) > 0L

    /**
     * Guards an overwrite: tapping the wrong file in a folder of documents would destroy it. A file
     * the picker has just created holds nothing yet, so any name the person typed is fine.
     */
    fun overwriteProblem(fileName: String?, reportedSizeBytes: Long?): String? =
        if (isOverwrite(reportedSizeBytes)) replaceTargetProblem(fileName) else null

    /** Names that we are willing to write over, which is only our own backups. */
    fun replaceTargetProblem(fileName: String?): String? =
        if (fileName == null || isBackupFileName(fileName)) null else NOT_A_BACKUP_FILE

    /**
     * Whether the document just picked is a second copy of the one already in use. Typing a name
     * that a folder already holds does not write over that file; the picker quietly creates
     * another document with the same name. Afterwards that shows up as the same provider and the
     * same name with a different document behind it.
     *
     * The older file is deleted on the strength of this, so it has to be one of our own backups,
     * and the provider has to be the same one. A [previous] whose name cannot be read is already
     * gone, and nothing is claimed about it.
     */
    fun duplicatesPrevious(previous: PickedDocument?, chosen: PickedDocument): Boolean {
        val previousName = previous?.name ?: return false
        return previous.uri != chosen.uri &&
            previous.authority == chosen.authority &&
            previousName == chosen.name &&
            isBackupFileName(previousName)
    }

    /**
     * Only a write that never landed leaves anything to retry. Offering the picker again after the
     * file was written but rejected would just repeat the same bad result.
     */
    fun canTryAnotherDestination(outcome: SaveOutcome): Boolean = outcome == SaveOutcome.UNWRITABLE

    /** Null when the outcome has already been reported, so nothing overwrites that message. */
    fun saveFailureMessage(outcome: SaveOutcome): String? = when (outcome) {
        SaveOutcome.SAVED, SaveOutcome.REJECTED -> null
        SaveOutcome.UNWRITABLE -> SAVE_FAILED
    }

    const val EMPTY_FILE_WRITTEN =
        "The backup file came back empty. Nothing was saved, so keep your previous backup."

    /**
     * Checks the size the provider reports back against what was written.
     *
     * [deferredWrite] is true for providers that upload in the background, Google Drive among them.
     * Those report 0 bytes until the upload finishes, so no size read straight after writing means
     * anything, and acting on one reports a perfectly good backup as empty. Only a provider that
     * writes synchronously can be held to the number it gives.
     */
    fun verifyWrittenSize(
        expectedBytes: Long,
        reportedBytes: Long?,
        deferredWrite: Boolean,
    ): String? = when {
        deferredWrite -> null
        reportedBytes == null -> null
        reportedBytes == expectedBytes -> null
        reportedBytes == 0L -> EMPTY_FILE_WRITTEN
        else ->
            "The backup file is ${formatBytes(reportedBytes)} but should be " +
                "${formatBytes(expectedBytes)}. Try saving it again."
    }

    fun restoredSummary(receiptCount: Int, photoCount: Int): String {
        val receipts = if (receiptCount == 1) "1 receipt" else "$receiptCount receipts"
        val photos = if (photoCount == 1) "1 photo" else "$photoCount photos"
        return "Restored $receipts and $photos."
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "0 B"
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit += 1
        }
        val rounded = String.format(Locale.US, "%.1f", value).removeSuffix(".0")
        return "$rounded ${units[unit]}"
    }

    fun lastBackupLabel(
        lastBackupAtMs: Long?,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        if (lastBackupAtMs == null || lastBackupAtMs <= 0) return NEVER_BACKED_UP
        return "Last backed up ${relativeTimestamp(lastBackupAtMs, nowMs, zone, locale)}"
    }

    /** "today at 8:06 PM", "yesterday at 9:15 AM", "Sep 2", "Dec 30, 2025". */
    fun relativeTimestamp(
        atMs: Long,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val then = Instant.ofEpochMilli(atMs).atZone(zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val time = DateTimeFormatter.ofPattern("h:mm a", locale).format(then)
        return when (then.toLocalDate()) {
            today -> "today at $time"
            today.minusDays(1) -> "yesterday at $time"
            else -> {
                val pattern = if (then.toLocalDate().year == today.year) "MMM d" else "MMM d, yyyy"
                DateTimeFormatter.ofPattern(pattern, locale).format(then)
            }
        }
    }

    /** Only the photos a receipt still points at are worth packing. */
    fun photoNamesToArchive(referencedPaths: Collection<String>): List<String> =
        referencedPaths.asSequence()
            .filter { it.isNotBlank() }
            .map(PhotoJanitorLogic::fileName)
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .toList()
}
