package com.receiptledger.engine.backup

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** How an attempt to write the archive out ended. */
enum class SaveOutcome {
    SAVED,

    /**
     * The bytes never landed, usually because the chosen file has since been deleted. The archive
     * is still on disk, so another destination can be offered without rebuilding it.
     */
    UNWRITABLE,

    /** Written, but the result did not check out. Already explained, so not worth restating. */
    REJECTED,
}

/** What the daily backup attempt ended up doing, which decides whether retrying makes sense. */
enum class BackupOutcome {
    SAVED,

    /** An empty ledger is not a failure; there is simply nothing worth writing. */
    NOTHING_TO_BACK_UP,

    /** Nowhere to write to, so retrying on a timer would burn battery forever. */
    NO_DESTINATION,

    /** Someone is using the screen; the next daily run will pick it up. */
    BUSY,

    /** The provider refused or the file came back wrong. Worth another go. */
    WRITE_FAILED,
}

/** Scheduling rules for the unattended daily backup, kept testable away from WorkManager. */
object DailyBackupLogic {
    const val WORK_NAME = "daily-backup"
    const val TOGGLE_LABEL = "Automatic back up daily"
    const val NEEDS_DESTINATION =
        "Choose where to save first, then daily backups can run on their own."

    /** The hour the run is aimed at: late enough to be asleep, and usually charging. */
    const val PREFERRED_HOUR = 2

    /** Without a remembered file there is nothing to overwrite, so the switch stays off. */
    fun canEnable(hasDestination: Boolean): Boolean = hasDestination

    fun statusLabel(enabled: Boolean, hasDestination: Boolean): String = when {
        !hasDestination -> NEEDS_DESTINATION
        enabled -> "Daily backup is on."
        else -> "Daily backup is off."
    }

    /**
     * Only a transient problem deserves a retry. Retrying when there is nowhere to write, or
     * nothing to write, would leave a job rescheduling itself forever.
     */
    fun isRetryable(outcome: BackupOutcome): Boolean = when (outcome) {
        BackupOutcome.WRITE_FAILED, BackupOutcome.BUSY -> true
        BackupOutcome.SAVED, BackupOutcome.NOTHING_TO_BACK_UP, BackupOutcome.NO_DESTINATION -> false
    }

    /** A backup bound for a cloud provider is pointless without a connection; a local one is not. */
    fun requiresNetwork(destinationAuthority: String): Boolean =
        BackupLogic.isOffDevice(destinationAuthority)

    /**
     * Milliseconds until the next [PREFERRED_HOUR] in local time. WorkManager cannot promise a
     * clock time, but starting the cycle overnight keeps it away from the middle of the day.
     */
    fun initialDelayMs(
        nowMs: Long,
        zone: ZoneId,
        hour: Int = PREFERRED_HOUR,
    ): Long {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        var next = now.with(LocalTime.of(hour, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.toInstant().toEpochMilli() - nowMs
    }
}
