package com.receiptledger.engine.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Describes what a backup archive holds. Written as the first entry in the zip so a reader can
 * decide whether the archive is usable before unpacking any of it.
 */
@Serializable
data class BackupManifest(
    val formatVersion: Int = FORMAT_VERSION,
    val databaseVersion: Int,
    val createdAtMs: Long,
    val appVersionName: String = "",
    val receiptCount: Int,
    val photoCount: Int,
) {
    companion object {
        /** Bumped only when the archive layout changes in a way older readers cannot handle. */
        const val FORMAT_VERSION = 1

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(manifest: BackupManifest): String =
            json.encodeToString(serializer(), manifest)

        fun decode(text: String): BackupManifest? =
            runCatching { json.decodeFromString<BackupManifest>(text) }.getOrNull()
    }
}

sealed class RestoreCheck {
    data object Ok : RestoreCheck()

    /**
     * The archive came from a newer build. Room cannot migrate a database backwards, so restoring
     * it would leave the app crashing on every launch.
     */
    data class DatabaseTooNew(val archiveVersion: Int, val appVersion: Int) : RestoreCheck() {
        val message: String
            get() = "This backup was made by a newer version of Receipt Ledger. " +
                "Update the app before restoring it."
    }

    data class UnsupportedFormat(val formatVersion: Int) : RestoreCheck() {
        val message: String get() = "This backup file is not readable by this version of the app."
    }

    data object Unreadable : RestoreCheck() {
        const val MESSAGE = "This file is not a Receipt Ledger backup."
        val message: String get() = MESSAGE
    }

    val allowed: Boolean get() = this is Ok

    val problem: String?
        get() = when (this) {
            Ok -> null
            is DatabaseTooNew -> message
            is UnsupportedFormat -> message
            Unreadable -> Unreadable.MESSAGE
        }
}

object BackupCompatibility {
    fun check(
        manifest: BackupManifest?,
        appDatabaseVersion: Int,
        appFormatVersion: Int = BackupManifest.FORMAT_VERSION,
    ): RestoreCheck {
        if (manifest == null) return RestoreCheck.Unreadable
        if (manifest.formatVersion > appFormatVersion) {
            return RestoreCheck.UnsupportedFormat(manifest.formatVersion)
        }
        if (manifest.databaseVersion > appDatabaseVersion) {
            return RestoreCheck.DatabaseTooNew(manifest.databaseVersion, appDatabaseVersion)
        }
        return RestoreCheck.Ok
    }
}
