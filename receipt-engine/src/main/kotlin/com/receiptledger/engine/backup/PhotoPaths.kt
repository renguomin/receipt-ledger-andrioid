package com.receiptledger.engine.backup

/**
 * Receipts store an absolute photo path. That path is stable for one install on one user profile,
 * but a backup restored onto another device or a secondary profile carries paths that no longer
 * point anywhere, so every restored row is rebased onto the local photo directory.
 */
object PhotoPaths {
    fun rebase(storedPath: String, receiptsDirPath: String): String {
        val name = PhotoJanitorLogic.fileName(storedPath)
        if (name.isBlank()) return storedPath
        return "${receiptsDirPath.trimEnd('/')}/$name"
    }

    /** Rows whose path already matches are skipped so a same-device restore writes nothing. */
    fun rebaseAll(
        stored: Map<String, String>,
        receiptsDirPath: String,
    ): Map<String, String> = stored.mapNotNull { (id, path) ->
        val next = rebase(path, receiptsDirPath)
        if (next == path) null else id to next
    }.toMap()
}
