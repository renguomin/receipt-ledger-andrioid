package com.receiptledger.app.ui

import com.receiptledger.app.backup.BackupState
import com.receiptledger.engine.backup.BackupLogic
import com.receiptledger.engine.export.PdfExportLogic
import com.receiptledger.engine.model.Country
import java.io.File
import java.time.LocalDate

data class WrittenExport(
    val file: File,
    val fileName: String,
    val receiptCount: Int,
)

object SettingsScreenLogic {
    const val TAX_COUNTRY = "Tax country"
    const val CATEGORIES = "Your categories"
    const val IMPORT_SECTION = ImportScreenLogic.SETTINGS_HEADING
    const val EXPORT_SECTION = "Export"
    const val EXPORT_BODY =
        "CSV is a spreadsheet and does not include original photos. PDF includes details and photos. Select receipts on home to export some."
    const val CATEGORIES_BODY =
        "Add names you actually use, such as Client lunch or Software. They appear on receipts " +
            "and filters alongside the tax-form list."
    const val PDF_SAVE_FAILED = "Could not save the PDF."
    const val CSV_SAVE_FAILED = "Could not save the CSV."
    const val BACKUP_SECTION = BackupLogic.SECTION_TITLE
    const val DEFAULT_PURPOSE = "Default for new receipts"
    const val GEMINI = AiReadLogic.ACTION_NAME
    const val HELP_ACTION = "Got it"
    const val CLOSE = "Close"
    const val CSV_READY_TITLE = "CSV ready"
    const val PDF_READY_TITLE = "PDF ready"
    const val EXPORT_RUNNING_TITLE = "Exporting PDF"
    const val NOTHING_TO_EXPORT = "Nothing to export yet."
    const val EXPORT_BUSY = "An export is already running."

    /** Spoken by the screen reader, so it names the section rather than saying "info" seven times. */
    fun helpButtonLabel(section: String): String = "About $section"

    /**
     * The floating row for a backup that has finished, worded the same whether it worked or not so
     * it reads like the import and export notices. Null while a backup is still in flight or idle,
     * which is what keeps the row off the screen.
     */
    fun backupNotice(state: BackupState): String? = when (state) {
        is BackupState.Ready ->
            BackupLogic.outcomeBody(state.summary, state.notice).takeIf { it.isNotBlank() }
        is BackupState.Failed -> state.message.takeIf { it.isNotBlank() }
        else -> null
    }

    fun countryBody(country: Country): String = when (country) {
        Country.CA -> "Canada uses T2125-style lines. You can still add your own categories below."
        Country.US -> "United States uses Schedule C lines. You can still add your own categories below."
        Country.NONE ->
            "No tax-form list is shown. Only the categories you add below appear on receipts and filters."
    }

    /**
     * What sits behind a section title's info button. Explanations live here rather than in the
     * section body, where a paragraph of grey text reads like one more setting to tap. Null for
     * sections whose own controls say everything, and that is what leaves the button off.
     */
    fun sectionHelp(section: String, country: Country): String? = when (section) {
        TAX_COUNTRY -> countryBody(country)
        CATEGORIES -> CATEGORIES_BODY
        IMPORT_SECTION -> ImportScreenLogic.BODY
        EXPORT_SECTION -> EXPORT_BODY
        BACKUP_SECTION -> BackupLogic.SECTION_BODY
        GEMINI -> AiReadLogic.SETTINGS_BODY
        else -> null
    }

    fun csvReceiptCount(csv: String): Int =
        csv.lineSequence().drop(1).count { it.isNotBlank() }

    fun exportSuccessMessage(count: Int): String =
        if (count == 1) "1 receipt exported successfully."
        else "$count receipts exported successfully."

    fun saveNotice(
        uriSelected: Boolean,
        copied: Boolean,
        receiptCount: Int,
        failed: String,
    ): String? {
        if (!uriSelected) return null
        return if (copied) exportSuccessMessage(receiptCount) else failed
    }

    fun pdfSaveNotice(uriSelected: Boolean, copied: Boolean, receiptCount: Int): String? =
        saveNotice(uriSelected, copied, receiptCount, PDF_SAVE_FAILED)

    fun csvSaveNotice(uriSelected: Boolean, copied: Boolean, receiptCount: Int): String? =
        saveNotice(uriSelected, copied, receiptCount, CSV_SAVE_FAILED)

    fun writeCsvExport(
        cacheDir: File,
        csv: String,
        allReceipts: Boolean,
        date: LocalDate,
    ): WrittenExport? {
        val count = csvReceiptCount(csv)
        if (count <= 0) return null
        val fileName = PdfExportLogic.fileName(allReceipts, date, "csv")
        val dir = File(cacheDir, "export").also { it.mkdirs() }
        val file = File(dir, fileName).also { it.writeText(csv) }
        return WrittenExport(file, fileName, count)
    }

    fun sections(): List<String> = listOf(
        TAX_COUNTRY,
        CATEGORIES,
        IMPORT_SECTION,
        EXPORT_SECTION,
        BACKUP_SECTION,
        DEFAULT_PURPOSE,
        GEMINI,
    )

    fun fileTransferSections(): List<String> = listOf(IMPORT_SECTION, EXPORT_SECTION)
}
