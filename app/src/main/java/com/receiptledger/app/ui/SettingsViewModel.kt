package com.receiptledger.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.receiptledger.app.backup.BackupController
import com.receiptledger.app.backup.DailyBackupScheduler
import com.receiptledger.app.backup.BackupState
import com.receiptledger.app.data.AppSettings
import com.receiptledger.app.data.CategoryCatalog
import com.receiptledger.app.data.CustomCategoryEntity
import com.receiptledger.app.data.ImageStore
import com.receiptledger.app.data.ReceiptRepository
import com.receiptledger.app.data.SettingsStore
import com.receiptledger.app.export.PdfExportController
import com.receiptledger.app.export.PdfExportOrigin
import com.receiptledger.app.export.PdfExportState
import com.receiptledger.app.import.ImportDraft
import com.receiptledger.app.import.ImportRescanQueue
import com.receiptledger.app.import.PdfPageImages
import com.receiptledger.app.ocr.ReceiptOcr
import com.receiptledger.engine.export.LedgerPdfParseResult
import com.receiptledger.engine.export.LedgerPdfParser
import com.receiptledger.engine.export.ReceiptPdfImport
import com.receiptledger.engine.model.Country
import com.receiptledger.engine.model.Field
import com.receiptledger.engine.model.FieldSource
import com.receiptledger.engine.model.ParsedReceipt
import com.receiptledger.engine.model.Purpose
import com.receiptledger.engine.ocr.ImageOrientation
import com.receiptledger.engine.parse.GeometryReceiptParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val store: SettingsStore,
    private val repository: ReceiptRepository,
    private val pdfExport: PdfExportController,
    private val imageStore: ImageStore,
    private val importQueue: ImportRescanQueue,
    private val ocr: ReceiptOcr,
    private val backup: BackupController,
    private val dailyBackupScheduler: DailyBackupScheduler,
    private val application: Application,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = store.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )
    val customCategories: StateFlow<List<CustomCategoryEntity>> = repository.customCategories.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val pdfExportState: StateFlow<PdfExportState> = pdfExport.state
    val backupState: StateFlow<BackupState> = backup.state
    val lastBackupAt: StateFlow<Long?> = store.lastBackupAt.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )
    val dailyBackupEnabled: StateFlow<Boolean> = store.dailyBackupEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )
    val backupDestination: StateFlow<Uri?> = store.backupDocumentUri
        .map { raw -> raw?.let { runCatching { Uri.parse(it) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Where backups go, in two passes. The first comes out of the URI alone so the line follows a
     * change of destination immediately; the second adds the file name the provider reports, which
     * needs a call that can be slow enough to leave the old location on screen.
     */
    val backupDestinationLabel: StateFlow<String?> = backupDestination
        .flatMapLatest { uri ->
            if (uri == null) {
                flowOf<String?>(null)
            } else {
                flow<String?> {
                    emit(runCatching { backup.describeDestination(uri, readName = false) }.getOrNull())
                    emit(runCatching { backup.describeDestination(uri, readName = true) }.getOrNull())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var pendingOutsidePdf: PendingOutsidePdf? = null

    fun setCountry(country: Country) {
        viewModelScope.launch {
            store.update {
                it.copy(
                    country = country,
                    currency = when (country) {
                        Country.CA -> "CAD"
                        Country.US -> "USD"
                        Country.NONE -> it.currency
                    },
                )
            }
        }
    }

    fun setDefaultPurpose(purpose: Purpose) {
        viewModelScope.launch { store.update { it.copy(defaultPurpose = purpose) } }
    }

    fun setGeminiKey(value: String) {
        viewModelScope.launch { store.update { it.copy(geminiApiKey = value) } }
    }

    fun setGeminiModel(value: String) {
        viewModelScope.launch { store.update { it.copy(geminiModel = value) } }
    }

    fun addCustomCategory(label: String) {
        viewModelScope.launch {
            repository.addCustomCategoryIfNew(label, repository.customCategories.first())
        }
    }

    fun deleteCustomCategory(id: String) {
        viewModelScope.launch { repository.deleteCustomCategory(id) }
    }

    fun exportCsv(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val rows = repository.receipts.first()
            val custom = repository.customCategories.first()
            onReady(repository.toCsv(rows, custom))
        }
    }

    fun exportAllPdf(onEmpty: () -> Unit) {
        viewModelScope.launch {
            val rows = repository.receipts.first()
            if (rows.isEmpty()) {
                onEmpty()
                return@launch
            }
            val labels = CategoryCatalog.visible(settings.value.country, customCategories.value)
                .associate { it.id to it.label }
            pdfExport.start(
                rows = rows,
                title = "Receipts",
                categoryLabels = labels,
                origin = PdfExportOrigin.SETTINGS,
                allReceipts = true,
            )
        }
    }

    fun dismissPdfExport() {
        pdfExport.dismiss(PdfExportOrigin.SETTINGS)
    }

    fun pdfExportBusy(): Boolean = pdfExport.isRunning()

    fun backUpNow() = backup.backUpNow(backupDestination.value)

    fun restoreBackup() = backup.requestRestore(backupDestination.value)

    fun chooseAnotherBackup() = backup.chooseAnotherBackup()

    fun chooseNewBackupDestination() = backup.chooseNewDestination()

    fun onBackupPickerOpened() = backup.onPickerOpened()

    fun setDailyBackup(enabled: Boolean) {
        viewModelScope.launch {
            store.setDailyBackupEnabled(enabled)
            dailyBackupScheduler.sync()
        }
    }

    fun onBackupDestinationChosen(uri: Uri?) = backup.onDestinationChosen(uri)

    fun onBackupSourceChosen(uri: Uri?) = backup.onSourceChosen(uri)

    fun dismissBackup() = backup.dismiss()

    fun importPdf(bytes: ByteArray, quickImport: Boolean, onDone: (PdfImportOutcome) -> Unit) {
        viewModelScope.launch {
            val outcome = runCatching {
                val parsed = withContext(Dispatchers.Default) { LedgerPdfParser.parse(bytes) }
                when (parsed) {
                    is LedgerPdfParseResult.Failed -> PdfImportOutcome.Failed(parsed.message)
                    is LedgerPdfParseResult.Success -> {
                        if (ImportScreenLogic.needsOutsideWarning(quickImport, parsed.fromLedgerExport)) {
                            pendingOutsidePdf = PendingOutsidePdf(bytes, parsed)
                            PdfImportOutcome.ConfirmOutsidePdf
                        } else {
                            importParsed(
                                pdfBytes = bytes,
                                parsed = parsed,
                                saveAll = ImportScreenLogic.finishMode(
                                    quickImport,
                                    parsed.fromLedgerExport,
                                ) == ImportFinishMode.SAVE_ALL,
                            )
                        }
                    }
                }
            }.getOrElse { error ->
                PdfImportOutcome.Failed(
                    error.message?.takeIf { it.isNotBlank() } ?: LedgerPdfParser.UNREADABLE,
                )
            }
            onDone(outcome)
        }
    }

    fun continueOutsideImport(
        saveWithoutReview: Boolean,
        onDone: (PdfImportOutcome) -> Unit,
    ) {
        val pending = pendingOutsidePdf
        pendingOutsidePdf = null
        if (pending == null) {
            onDone(PdfImportOutcome.Failed(LedgerPdfParser.UNREADABLE))
            return
        }
        viewModelScope.launch {
            val outcome = runCatching {
                importParsed(
                    pdfBytes = pending.bytes,
                    parsed = pending.parsed,
                    saveAll = saveWithoutReview,
                )
            }.getOrElse { error ->
                PdfImportOutcome.Failed(
                    error.message?.takeIf { it.isNotBlank() } ?: LedgerPdfParser.UNREADABLE,
                )
            }
            onDone(outcome)
        }
    }

    fun cancelOutsideImport() {
        pendingOutsidePdf = null
    }

    private suspend fun importParsed(
        pdfBytes: ByteArray,
        parsed: LedgerPdfParseResult.Success,
        saveAll: Boolean,
    ): PdfImportOutcome {
        val receipts = parsed.receipts
        if (receipts.isEmpty()) return PdfImportOutcome.Failed(LedgerPdfParser.NO_RECEIPTS)
        val embeddedByReceipt = receipts.map { ReceiptPdfImport.photoBytes(it) }
        val renderedPages = if (parsed.fromLedgerExport && embeddedByReceipt.any { it != null }) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                PdfPageImages.renderJpegs(pdfBytes, imageStore.cacheDirectory())
            }.filter { it.isNotEmpty() }
        }
        val photos = if (parsed.fromLedgerExport) {
            emptyList()
        } else {
            val embedded = embeddedByReceipt.filterNotNull()
            (embedded.ifEmpty { renderedPages }).filter { it.isNotEmpty() }
        }
        val settings = store.settings.first()
        val custom = repository.customCategories.first()
        val catalog = CategoryCatalog.visible(settings.country, custom).map { it.id to it.label }
        val parsedDrafts = receipts.map { ReceiptPdfImport.toParsed(it, settings.country) }
        val plan = ImportScreenLogic.planImport(
            fromLedgerExport = parsed.fromLedgerExport,
            receiptCount = receipts.size,
            photoCount = photos.size,
        )
        if (plan.isEmpty()) {
            return PdfImportOutcome.Failed(LedgerPdfParser.NO_RECEIPTS)
        }
        val drafts = ArrayList<ImportDraft>(plan.size)
        for (item in plan) {
            val photo = if (parsed.fromLedgerExport) {
                item.receiptIndex?.let { index ->
                    ImportScreenLogic.photoForLedgerReceipt(index, embeddedByReceipt, renderedPages)
                }
            } else {
                item.photoIndex?.let { photos.getOrNull(it) }
            }
            val path = if (photo != null) {
                imageStore.persistImage(photo)
            } else {
                imageStore.persistPlaceholder()
            }
            val receipt = item.receiptIndex?.let { receipts.getOrNull(it) }
            val parsedReceipt = item.receiptIndex?.let { parsedDrafts.getOrNull(it) }
            val importedCategory = if (parsed.fromLedgerExport) {
                receipt?.let { ReceiptPdfImport.resolveCategory(it.category, catalog) }
            } else {
                null
            }
            drafts += ImportDraft(
                imagePath = path,
                ocrPhoto = item.ocrPhoto,
                aiScan = ImportScreenLogic.aiScansPhoto(parsed.fromLedgerExport, item.ocrPhoto),
                parsed = parsedReceipt.takeUnless { item.ocrPhoto },
                purpose = receipt?.let { ReceiptPdfImport.purposeOf(it.purpose) }
                    ?.takeIf { parsed.fromLedgerExport },
                categoryId = importedCategory?.id,
                newCategoryLabel = importedCategory?.newLabel,
            )
        }
        if (drafts.isEmpty()) {
            return PdfImportOutcome.Failed(LedgerPdfParser.NO_RECEIPTS)
        }
        if (saveAll) {
            var saved = 0
            for (draft in drafts) {
                runCatching { saveDraft(draft, settings) }.onSuccess { saved += 1 }
            }
            return if (saved == 0) {
                PdfImportOutcome.Failed(LedgerPdfParser.NO_RECEIPTS)
            } else {
                PdfImportOutcome.Saved(saved)
            }
        }
        importQueue.start(drafts)
        return PdfImportOutcome.Review(Uri.fromFile(File(drafts.first().imagePath)).toString())
    }

    private suspend fun saveDraft(
        draft: ImportDraft,
        settings: AppSettings,
    ) {
        val custom = repository.customCategories.first()
        val parsed = if (draft.ocrPhoto) {
            scanPhoto(draft.imagePath, settings)
        } else {
            val base = draft.parsed ?: ParsedReceipt()
            if (base.currency.value == null) {
                base.copy(currency = Field(settings.currency, 0.4f, FieldSource.USER))
            } else {
                base
            }
        }
        val purpose = draft.purpose ?: settings.defaultPurpose
        val visible = CategoryCatalog.visible(settings.country, custom)
        val selected = ReviewScreenLogic.categorySelection(
            draftCategoryId = draft.categoryId,
            draftNewLabel = draft.newCategoryLabel,
            suggestedId = parsed.suggestedCategoryId.value,
            defaultId = CategoryCatalog.defaultId(settings.country, purpose, custom),
            options = visible,
        )
        val categoryId = ReviewScreenLogic.labelToCreateOnSave(
            selected.categoryId,
            selected.pendingNewLabel,
        )?.let { label ->
            repository.addCustomCategoryIfNew(label, custom)
        } ?: selected.categoryId.takeUnless { CategoryCatalog.isPending(it) }
        repository.save(
            existingId = null,
            imagePath = draft.imagePath,
            draft = parsed,
            purpose = purpose,
            categoryId = categoryId,
            previousMerchant = parsed.merchant.value,
        )
    }

    private suspend fun scanPhoto(path: String, settings: AppSettings): ParsedReceipt {
        val scanned = withContext(Dispatchers.Default) {
            ocr.read(application, Uri.fromFile(File(path)))
        }
        val upright = ImageOrientation.correctUpsideDown(scanned)
        if (upright.rotated180) {
            imageStore.rotate180(path)
        }
        val aliases = repository.aliasBook()
        val parsed = GeometryReceiptParser(aliases, settings.country).parse(upright.page)
        return if (parsed.currency.value == null) {
            parsed.copy(currency = Field(settings.currency, 0.4f, FieldSource.USER))
        } else {
            parsed
        }
    }

    private data class PendingOutsidePdf(
        val bytes: ByteArray,
        val parsed: LedgerPdfParseResult.Success,
    )

    companion object {
        fun factory(
            store: SettingsStore,
            repository: ReceiptRepository,
            pdfExport: PdfExportController,
            imageStore: ImageStore,
            importQueue: ImportRescanQueue,
            ocr: ReceiptOcr,
            backup: BackupController,
            dailyBackupScheduler: DailyBackupScheduler,
            application: Application,
        ) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(
                        store,
                        repository,
                        pdfExport,
                        imageStore,
                        importQueue,
                        ocr,
                        backup,
                        dailyBackupScheduler,
                        application,
                    ) as T
                }
            }
    }
}
