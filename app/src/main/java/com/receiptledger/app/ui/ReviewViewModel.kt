package com.receiptledger.app.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.receiptledger.app.AppContainer
import com.receiptledger.app.data.AppSettings
import com.receiptledger.app.data.CategoryCatalog
import com.receiptledger.app.data.CategoryChoice
import com.receiptledger.app.data.CustomCategoryEntity
import com.receiptledger.app.gemini.GeminiClient
import com.receiptledger.app.import.ImportDraft
import com.receiptledger.engine.gemini.ReceiptMerger
import com.receiptledger.engine.model.Country
import com.receiptledger.engine.model.Field
import com.receiptledger.engine.model.FieldSource
import com.receiptledger.engine.model.ParsedReceipt
import com.receiptledger.engine.model.Purpose
import com.receiptledger.engine.model.ReceiptRecordLogic
import com.receiptledger.engine.ocr.ImageOrientation
import com.receiptledger.engine.parse.GeometryReceiptParser
import com.receiptledger.engine.tax.TaxCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReviewUiState(
    val loading: Boolean = true,
    val aiBusy: Boolean = false,
    val error: String? = null,
    val aiError: String? = null,
    val imagePath: String? = null,
    val parsed: ParsedReceipt = ParsedReceipt(),
    val purpose: Purpose = Purpose.PERSONAL,
    val categoryId: String? = null,
    val settings: AppSettings = AppSettings(),
    val customCategories: List<CustomCategoryEntity> = emptyList(),
    val existingId: String? = null,
    val originalMerchant: String? = null,
    val aiSummary: String? = null,
    val importIndex: Int = 0,
    val importTotal: Int = 0,
    val pendingNewCategoryLabel: String? = null,
    val duplicateWarning: DuplicateWarning? = null,
) {
    val categoryOptions: List<CategoryChoice>
        get() = CategoryCatalog.visible(settings.country, customCategories, pendingNewCategoryLabel)
}

class ReviewViewModel(
    application: Application,
    private val container: AppContainer,
    private val existingId: String?,
    private val incomingImageUri: String?,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ReviewUiState(existingId = existingId))
    val state: StateFlow<ReviewUiState> = _state

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            container.repository.customCategories.collect { rows ->
                _state.update { current ->
                    val created = CategoryCatalog.existingIdForPending(
                        current.pendingNewCategoryLabel,
                        rows,
                    )
                    current.copy(
                        customCategories = rows,
                        categoryId = if (created != null && CategoryCatalog.isPending(current.categoryId)) {
                            created
                        } else {
                            current.categoryId
                        },
                        pendingNewCategoryLabel = if (created != null) null else current.pendingNewCategoryLabel,
                    )
                }
            }
        }
    }

    private suspend fun load() {
        ensureGeminiKeyFile()
        val settings = container.settingsStore.settings.first()
        val custom = container.repository.customCategories.first()
        val (importIndex, importTotal) = importProgress()
        _state.update {
            it.copy(
                settings = settings,
                purpose = settings.defaultPurpose,
                customCategories = custom,
                importIndex = importIndex,
                importTotal = importTotal,
            )
        }
        if (existingId != null) {
            val row = container.repository.get(existingId) ?: run {
                _state.update { it.copy(loading = false, error = "Receipt not found.") }
                return
            }
            val parsed = container.repository.toParsed(row)
            _state.update {
                it.copy(
                    loading = false,
                    imagePath = row.imagePath,
                    parsed = parsed,
                    purpose = runCatching { Purpose.valueOf(row.purpose) }.getOrDefault(Purpose.PERSONAL),
                    categoryId = row.categoryId,
                    originalMerchant = row.merchant,
                    error = null,
                    importIndex = 0,
                    importTotal = 0,
                )
            }
            return
        }
        val uriString = incomingImageUri
        if (uriString.isNullOrBlank()) {
            _state.update { it.copy(loading = false, error = "No scan to review.") }
            return
        }
        runCatching {
            val persisted = container.imageStore.persist(Uri.parse(uriString))
            val draft = container.importQueue.current()?.takeIf { it.imagePath == persisted }
            if (draft != null && !draft.ocrPhoto && draft.parsed != null) {
                applyDraft(draft, persisted, settings, custom)
                return@runCatching
            }
            // Both readings start together. The AI is the slow one and decides the fields; the
            // phone's own scan runs alongside so there is something to show if the AI never lands.
            val key = peekGeminiApiKey()
            val visible = CategoryCatalog.visible(settings.country, custom)
            val aiRead = if (key.isBlank()) {
                null
            } else {
                container.settingsStore.update { it.copy(geminiApiKey = key) }
                _state.update {
                    it.copy(settings = it.settings.copy(geminiApiKey = key), aiBusy = true)
                }
                viewModelScope.async(Dispatchers.IO) {
                    runCatching {
                        container.gemini.extract(
                            apiKey = key,
                            imageFile = File(persisted),
                            ocrText = "",
                            categories = ReviewScreenLogic.catalogForAi(visible),
                            model = GeminiClient.DEFAULT_MODEL,
                        )
                    }
                }
            }
            val scanned = withContext(Dispatchers.Default) {
                container.ocr.read(getApplication(), Uri.fromFile(File(persisted)))
            }
            val upright = ImageOrientation.correctUpsideDown(scanned)
            if (upright.rotated180) {
                container.imageStore.rotate180(persisted)
            }
            val page = upright.page
            val aliases = container.repository.aliasBook()
            val parsed = GeometryReceiptParser(aliases, settings.country).parse(page)
            val locally = if (parsed.currency.value == null) {
                parsed.copy(currency = Field(settings.currency, 0.4f, FieldSource.USER))
            } else {
                parsed
            }
            dumpOcr(locally.rawText)
            val ai = aiRead?.await()
            val fromAi = ai?.getOrNull()
            val aiFailure = when {
                ai == null -> NO_GEMINI_KEY
                fromAi != null -> null
                else -> ai.exceptionOrNull()?.message ?: "Gemini request failed."
            }
            val withCurrency = if (fromAi != null) {
                ReceiptMerger.merge(
                    locally,
                    fromAi.receipt,
                    aliases,
                    preferGemini = AiReadLogic.AI_WINS_OVER_LOCAL_SCAN,
                )
            } else {
                locally
            }
            fromAi?.let { Log.d("ReviewViewModel", it.summary) }
            aiFailure?.let { Log.d("ReviewViewModel", "Gemini read failed: $it") }
            val selected = ReviewScreenLogic.categorySelection(
                draftCategoryId = null,
                draftNewLabel = null,
                suggestedId = withCurrency.suggestedCategoryId.value,
                defaultId = defaultCategory(settings.country, settings.defaultPurpose, custom),
                options = visible,
            )
            _state.update {
                it.copy(
                    loading = false,
                    aiBusy = false,
                    imagePath = persisted,
                    parsed = withCurrency,
                    categoryId = selected.categoryId,
                    originalMerchant = withCurrency.merchant.value,
                    error = null,
                    aiError = aiFailure,
                    aiSummary = fromAi?.summary,
                    importIndex = importIndex,
                    importTotal = importTotal,
                )
            }
        }.onFailure { error ->
            _state.update { it.copy(loading = false, aiBusy = false, error = error.message) }
        }
    }

    private fun importProgress(): Pair<Int, Int> {
        if (existingId != null) return 0 to 0
        val progress = container.importQueue.progress() ?: return 0 to 0
        return progress.index to progress.total
    }

    private fun applyDraft(
        draft: ImportDraft,
        persisted: String,
        settings: AppSettings,
        custom: List<CustomCategoryEntity>,
    ) {
        val parsed = draft.parsed ?: ParsedReceipt()
        val withCurrency = if (parsed.currency.value == null) {
            parsed.copy(currency = Field(settings.currency, 0.4f, FieldSource.USER))
        } else {
            parsed
        }
        dumpOcr(withCurrency.rawText)
        val visible = CategoryCatalog.visible(settings.country, custom)
        val purpose = draft.purpose ?: settings.defaultPurpose
        val selected = ReviewScreenLogic.categorySelection(
            draftCategoryId = draft.categoryId,
            draftNewLabel = draft.newCategoryLabel,
            suggestedId = withCurrency.suggestedCategoryId.value,
            defaultId = defaultCategory(settings.country, purpose, custom),
            options = visible,
        )
        val (importIndex, importTotal) = importProgress()
        _state.update {
            it.copy(
                loading = false,
                imagePath = persisted,
                parsed = withCurrency,
                purpose = purpose,
                categoryId = selected.categoryId,
                pendingNewCategoryLabel = selected.pendingNewLabel,
                originalMerchant = withCurrency.merchant.value,
                error = null,
                importIndex = importIndex,
                importTotal = importTotal,
            )
        }
    }

    fun peekGeminiApiKey(): String {
        val fromFile = runCatching {
            geminiKeyFile().takeIf { it.isFile }?.readText()?.trim().orEmpty()
        }.getOrDefault("")
        if (fromFile.isNotEmpty()) return fromFile
        return _state.value.settings.geminiApiKey
    }

    private fun geminiKeyFile(): File {
        return File(getApplication<Application>().getExternalFilesDir(null), GeminiClient.KEY_FILE_NAME)
    }

    private fun ensureGeminiKeyFile() {
        viewModelScope.launch(Dispatchers.IO) {
            val file = geminiKeyFile()
            file.parentFile?.mkdirs()
            if (!file.exists()) {
                file.writeText("")
            }
        }
    }

    fun updateParsed(transform: (ParsedReceipt) -> ParsedReceipt) {
        _state.update { it.copy(parsed = transform(it.parsed).copy(rawText = it.parsed.rawText)) }
    }

    fun setPurpose(purpose: Purpose) {
        val current = _state.value
        _state.update {
            val keepCategory = ReviewScreenLogic.keepCategoryOnPurposeChange(
                it.categoryId,
                countryNone = current.settings.country == Country.NONE,
            )
            val nextCategory = when {
                keepCategory -> it.categoryId
                purpose == Purpose.PERSONAL &&
                    TaxCategories.find(it.categoryId.orEmpty())?.personal != true ->
                    TaxCategories.defaultPersonal(current.settings.country)?.id
                purpose == Purpose.BUSINESS &&
                    TaxCategories.find(it.categoryId.orEmpty())?.personal == true ->
                    it.parsed.suggestedCategoryId.value
                        ?: TaxCategories.forCountry(current.settings.country).firstOrNull { cat -> !cat.personal }?.id
                else -> it.categoryId
            }
            it.copy(purpose = purpose, categoryId = nextCategory)
        }
    }

    fun setCategory(id: String?) {
        _state.update { it.copy(categoryId = id) }
    }

    fun addCustomCategory(label: String) {
        viewModelScope.launch {
            val id = container.repository.addCustomCategoryIfNew(
                label,
                _state.value.customCategories,
            ) ?: return@launch
            _state.update { it.copy(categoryId = id) }
        }
    }

    fun addLineItem() {
        updateParsed { it.copy(lineItems = ReceiptRecordLogic.addLineItem(it.lineItems)) }
    }

    fun removeLineItem(index: Int) {
        updateParsed { it.copy(lineItems = ReceiptRecordLogic.removeLineItem(it.lineItems, index)) }
    }

    fun updateLineItem(
        index: Int,
        name: String? = null,
        quantityRaw: String? = null,
        amountRaw: String? = null,
    ) {
        updateParsed {
            it.copy(
                lineItems = ReceiptRecordLogic.updateLineItem(
                    items = it.lineItems,
                    index = index,
                    name = name,
                    quantityRaw = quantityRaw,
                    amountRaw = amountRaw,
                ),
            )
        }
    }

    fun warmupGemini() {
        viewModelScope.launch(Dispatchers.IO) {
            container.gemini.warmup(peekGeminiApiKey())
        }
    }

    fun saveOcrDump(onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val text = _state.value.parsed.rawText.trim()
            if (text.isEmpty()) {
                _state.update { it.copy(error = "No OCR text yet.") }
                return@launch
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(getApplication<Application>().getExternalFilesDir(null), "ocr")
                    check(dir.exists() || dir.mkdirs()) { "Could not create ${dir.absolutePath}" }
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    val textFile = File(dir, "ocr-$stamp.txt").also { it.writeText(text) }
                    val imageSrc = _state.value.imagePath?.let { File(it) }
                    if (imageSrc != null && imageSrc.exists()) {
                        imageSrc.copyTo(File(dir, "ocr-$stamp.jpg"), overwrite = true)
                    }
                    textFile
                }
            }.onSuccess { file ->
                Log.i(OCR_TAG, "Saved OCR dump in ${file.parent}")
                onSaved(file.parent ?: file.absolutePath)
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Could not save OCR.") }
            }
        }
    }

    fun dismissDuplicateWarning() {
        _state.update { it.copy(duplicateWarning = null) }
    }

    /** Leaves this receipt unsaved and hands control back so the import can move on. */
    fun skipReceipt(onDone: (String?) -> Unit) {
        _state.update { it.copy(duplicateWarning = null) }
        onDone(null)
    }

    fun skipDuplicate(onDone: (String?) -> Unit) = skipReceipt(onDone)

    fun saveToDisk(onDone: (String?) -> Unit, ignoreDuplicate: Boolean = false) {
        viewModelScope.launch {
            if (ignoreDuplicate) {
                _state.update { it.copy(duplicateWarning = null) }
            }
            runCatching { save(ignoreDuplicate) }
                .onSuccess { savedId ->
                    if (savedId != null) onDone(savedId)
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message ?: "Could not save.") }
                }
        }
    }

    suspend fun save(ignoreDuplicate: Boolean = false): String? {
        val current = _state.value
        val path = current.imagePath ?: error("No image to save")
        val parsed = current.parsed.copy(
            merchant = current.parsed.merchant.copy(source = FieldSource.USER, confidence = 1f),
            date = current.parsed.date.copy(source = FieldSource.USER, confidence = 1f),
            total = current.parsed.total.copy(source = FieldSource.USER, confidence = 1f),
        )
        if (!ignoreDuplicate &&
            DuplicateReceiptLogic.shouldCheck(
                isNewReceipt = current.existingId == null,
                dateIso = parsed.date.value,
                total = parsed.total.value,
            )
        ) {
            val match = DuplicateReceiptLogic.pickMatch(
                merchant = parsed.merchant.value,
                dateIso = parsed.date.value,
                total = parsed.total.value,
                existing = container.repository.getAll(),
                excludingId = current.existingId,
            )
            if (match != null) {
                _state.update {
                    it.copy(
                        duplicateWarning = DuplicateReceiptLogic.warning(
                            merchant = match.merchant,
                            dateIso = match.dateIso,
                            total = match.total,
                            currency = match.currency,
                            lineItems = container.repository.decodeItems(match.lineItemsJson),
                            createdAtMs = match.createdAt,
                        ),
                    )
                }
                return null
            }
        }
        val categoryId = ReviewScreenLogic.labelToCreateOnSave(
            current.categoryId,
            current.pendingNewCategoryLabel,
        )?.let { label ->
            container.repository.addCustomCategoryIfNew(label, current.customCategories)
        } ?: current.categoryId.takeUnless { CategoryCatalog.isPending(it) }
        _state.update { it.copy(duplicateWarning = null) }
        return container.repository.save(
            existingId = current.existingId,
            imagePath = path,
            draft = parsed,
            purpose = current.purpose,
            categoryId = categoryId,
            previousMerchant = current.originalMerchant,
        )
    }

    private fun defaultCategory(
        country: Country,
        purpose: Purpose,
        custom: List<CustomCategoryEntity>,
    ): String? = CategoryCatalog.defaultId(country, purpose, custom)

    private fun dumpOcr(raw: String) {
        Log.i(OCR_TAG, "----- BEGIN OCR -----")
        raw.split('\n').forEach { line -> Log.i(OCR_TAG, line) }
        Log.i(OCR_TAG, "----- END OCR -----")
    }

    companion object {
        const val OCR_TAG = "ReceiptOcr"
        const val NO_GEMINI_KEY =
            "No Gemini API key. Put it in gemini-api-key.txt (same folder as ocr/)."

        fun factory(
            application: Application,
            container: AppContainer,
            existingId: String?,
            imageUri: String?,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReviewViewModel(application, container, existingId, imageUri) as T
            }
        }
    }
}
