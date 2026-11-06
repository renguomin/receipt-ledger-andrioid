package com.receiptledger.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.receiptledger.app.data.AppSettings
import com.receiptledger.app.data.CategoryCatalog
import com.receiptledger.app.data.CategoryChoice
import com.receiptledger.app.data.ReceiptEntity
import com.receiptledger.app.data.ReceiptRepository
import com.receiptledger.app.data.SettingsStore
import com.receiptledger.app.export.PdfExportController
import com.receiptledger.app.export.PdfExportOrigin
import com.receiptledger.app.export.PdfExportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

enum class ReceiptSort {
    DATE,
    DATE_ADDED,
    MERCHANT,
    TOTAL,
}

/**
 * Windows counting back from today rather than calendar years, because the reason to search by
 * date added is almost always "what did I just put in" after an import or a shoebox session.
 */
enum class DateAddedWindow(val label: String) {
    ANY("Any time"),
    TODAY("Today"),
    LAST_7_DAYS("Last 7 days"),
    LAST_30_DAYS("Last 30 days"),
    THIS_YEAR("This year"),
}

data class HomeFilters(
    val query: String = "",
    val years: Set<Int> = emptySet(),
    val purposes: Set<String> = emptySet(),
    val categoryIds: Set<String> = emptySet(),
    val dateAdded: DateAddedWindow = DateAddedWindow.ANY,
    val minTotalText: String = "",
    val maxTotalText: String = "",
    val sort: ReceiptSort = ReceiptSort.DATE,
) {
    val minTotal: Double? get() = minTotalText.toDoubleOrNull()
    val maxTotal: Double? get() = maxTotalText.toDoubleOrNull()
    val amountSelected: Boolean get() = minTotal != null || maxTotal != null
    val dateAddedSelected: Boolean get() = dateAdded != DateAddedWindow.ANY
    val selectedCount: Int
        get() = years.size + purposes.size + categoryIds.size +
            (if (dateAddedSelected) 1 else 0) + if (amountSelected) 1 else 0
    val isActive: Boolean get() = selectedCount > 0
}

data class HomeUiState(
    val receipts: List<ReceiptEntity> = emptyList(),
    val listItems: List<HomeListItem> = emptyList(),
    val filters: HomeFilters = HomeFilters(),
    val availableYears: List<Int> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val categories: List<CategoryChoice> = emptyList(),
    val addNewHintReady: Boolean = false,
    val hasSeenAddNewHint: Boolean = true,
)

sealed class HomeListItem {
    data class Header(val label: String) : HomeListItem()
    data class Receipt(val row: ReceiptEntity) : HomeListItem()
}

sealed class PdfImportOutcome {
    data class Review(val firstImageUri: String) : PdfImportOutcome()
    data class Saved(val count: Int) : PdfImportOutcome()
    data object ConfirmOutsidePdf : PdfImportOutcome()
    data class Failed(val message: String) : PdfImportOutcome()
}

class HomeViewModel(
    private val repository: ReceiptRepository,
    private val settingsStore: SettingsStore,
    private val pdfExport: PdfExportController,
) : ViewModel() {
    private val filters = MutableStateFlow(HomeFilters())
    private val addNewHintDismissed = MutableStateFlow(false)
    private val hasSeenAddNewHint = combine(
        settingsStore.seenAddNewHint,
        addNewHintDismissed,
    ) { seen, dismissed -> seen || dismissed }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.receipts,
        filters,
        settingsStore.settings,
        repository.customCategories,
        hasSeenAddNewHint,
    ) { receipts, current, settings, custom, seenHint ->
        val years = receipts.map { it.year }.distinct().sortedDescending()
        val filtered = HomeListLogic.apply(receipts, current)
        HomeUiState(
            receipts = filtered,
            listItems = HomeListLogic.sectioned(filtered, current.sort),
            filters = current,
            availableYears = years.ifEmpty { listOf(Calendar.getInstance().get(Calendar.YEAR)) },
            settings = settings,
            categories = CategoryCatalog.visible(settings.country, custom),
            addNewHintReady = true,
            hasSeenAddNewHint = seenHint,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val pdfExportState: StateFlow<PdfExportState> = pdfExport.state

    fun setQuery(value: String) {
        filters.update { it.copy(query = value) }
    }

    fun toggleYear(value: Int) {
        filters.update { it.copy(years = HomeListLogic.toggle(it.years, value)) }
    }

    fun togglePurpose(value: String) {
        filters.update { it.copy(purposes = HomeListLogic.toggle(it.purposes, value)) }
    }

    fun toggleCategory(value: String) {
        filters.update { it.copy(categoryIds = HomeListLogic.toggle(it.categoryIds, value)) }
    }

    /** One window at a time; tapping the chosen one again goes back to showing everything. */
    fun setDateAdded(value: DateAddedWindow) {
        filters.update {
            it.copy(
                dateAdded = if (it.dateAdded == value) DateAddedWindow.ANY else value,
            )
        }
    }

    fun setMinTotal(value: String) {
        filters.update { it.copy(minTotalText = value) }
    }

    fun setMaxTotal(value: String) {
        filters.update { it.copy(maxTotalText = value) }
    }

    fun setSort(value: ReceiptSort) {
        filters.update { it.copy(sort = value) }
    }

    fun clearFilters() {
        filters.update { HomeFilters(query = it.query, sort = it.sort) }
    }

    fun csv(rows: List<ReceiptEntity>, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val custom = repository.customCategories.first()
            onReady(repository.toCsv(rows, custom))
        }
    }

    fun delete(ids: Collection<String>, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.delete(ids)
            onDone()
        }
    }

    fun exportPdf(
        rows: List<ReceiptEntity>,
        title: String,
        categoryLabels: Map<String, String>,
    ) {
        pdfExport.start(
            rows = rows,
            title = title,
            categoryLabels = categoryLabels,
            origin = PdfExportOrigin.HOME,
            allReceipts = false,
        )
    }

    fun dismissPdfExport() {
        pdfExport.dismiss(PdfExportOrigin.HOME)
    }

    fun pdfExportBusy(): Boolean = pdfExport.isRunning()

    fun dismissAddNewHint() {
        if (addNewHintDismissed.value) return
        addNewHintDismissed.value = true
        viewModelScope.launch {
            settingsStore.markAddNewHintSeen()
        }
    }

    companion object {
        fun factory(
            repository: ReceiptRepository,
            settingsStore: SettingsStore,
            pdfExport: PdfExportController,
        ) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(repository, settingsStore, pdfExport) as T
                }
            }
    }
}
