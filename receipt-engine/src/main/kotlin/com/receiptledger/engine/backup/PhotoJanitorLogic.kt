package com.receiptledger.engine.backup

/** A photo on disk, described without touching the file system so the rules stay testable. */
data class StoredPhoto(
    val name: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
)

data class PhotoSweep(
    val deletable: List<StoredPhoto>,
    val keptReferenced: Int,
    val keptInFlight: Int,
    val keptTooFresh: Int,
) {
    val reclaimedBytes: Long get() = deletable.sumOf { it.sizeBytes }
}

/**
 * Photos are written to disk before the receipt that points at one exists, so abandoning a scan or
 * an import leaves the file behind with nothing referencing it. These rules decide which of those
 * leftovers are safe to delete.
 */
object PhotoJanitorLogic {
    /**
     * How long a photo is left alone before it can be judged unused. Writing the photo and saving
     * its receipt are separate steps with a person in between, so a young file with no receipt is
     * far more likely to be mid-review than abandoned.
     */
    const val SETTLE_MS = 6L * 60 * 60 * 1000

    /** Rows store absolute paths, which differ between user profiles; the UUID name does not. */
    fun fileName(path: String): String = path.trim().substringAfterLast('/')

    fun sweep(
        photos: List<StoredPhoto>,
        referencedPaths: Collection<String>,
        inFlightPaths: Collection<String> = emptyList(),
        nowMs: Long,
        settleMs: Long = SETTLE_MS,
    ): PhotoSweep {
        val referenced = referencedPaths.map(::fileName).toHashSet()
        val inFlight = inFlightPaths.map(::fileName).toHashSet()
        val deletable = ArrayList<StoredPhoto>()
        var keptReferenced = 0
        var keptInFlight = 0
        var keptTooFresh = 0
        for (photo in photos) {
            when {
                photo.name in referenced -> keptReferenced += 1
                photo.name in inFlight -> keptInFlight += 1
                nowMs - photo.lastModifiedMs < settleMs -> keptTooFresh += 1
                else -> deletable += photo
            }
        }
        return PhotoSweep(
            deletable = deletable,
            keptReferenced = keptReferenced,
            keptInFlight = keptInFlight,
            keptTooFresh = keptTooFresh,
        )
    }

    fun reclaimedNotice(sweep: PhotoSweep): String? {
        val count = sweep.deletable.size
        if (count <= 0) return null
        val photos = if (count == 1) "1 unused photo" else "$count unused photos"
        return "Freed ${BackupLogic.formatBytes(sweep.reclaimedBytes)} from $photos."
    }
}
