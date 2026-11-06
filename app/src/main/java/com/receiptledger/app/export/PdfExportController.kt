package com.receiptledger.app.export

import com.receiptledger.app.data.ReceiptEntity
import com.receiptledger.engine.export.PdfExportLogic
import com.receiptledger.engine.tax.TaxCategories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

enum class PdfExportOrigin {
    SETTINGS,
    HOME,
}

sealed class PdfExportState {
    data object Idle : PdfExportState()

    data class Running(
        val origin: PdfExportOrigin,
        val completed: Int,
        val total: Int,
        val message: String,
    ) : PdfExportState() {
        val fraction: Float get() = PdfExportLogic.fraction(completed, total)
    }

    data class Ready(
        val origin: PdfExportOrigin,
        val filePath: String,
        val fileName: String,
        val receiptCount: Int,
    ) : PdfExportState() {
        val file: File get() = File(filePath)
        val summary: String get() = PdfExportLogic.readySummary(receiptCount)
    }

    data class Failed(
        val origin: PdfExportOrigin,
        val message: String,
    ) : PdfExportState()
}

fun PdfExportState.originOrNull(): PdfExportOrigin? = when (this) {
    is PdfExportState.Running -> origin
    is PdfExportState.Ready -> origin
    is PdfExportState.Failed -> origin
    PdfExportState.Idle -> null
}

class PdfExportController(context: android.content.Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<PdfExportState>(PdfExportState.Idle)
    val state: StateFlow<PdfExportState> = _state
    private var job: Job? = null

    fun isRunning(): Boolean = _state.value is PdfExportState.Running

    fun start(
        rows: List<ReceiptEntity>,
        title: String,
        categoryLabels: Map<String, String>,
        origin: PdfExportOrigin,
        allReceipts: Boolean,
    ) {
        if (isRunning()) return
        val snapshot = rows.toList()
        val labels = categoryLabels.toMap()
        val dir = File(appContext.cacheDir, "export").also { it.mkdirs() }
        val output = File(dir, PdfExportLogic.fileName(allReceipts, LocalDate.now()))
        val total = PdfExportLogic.workUnits(snapshot.size)
        job = scope.launch {
            _state.value = PdfExportState.Running(
                origin = origin,
                completed = 0,
                total = total,
                message = PdfExportLogic.startingMessage(),
            )
            try {
                ReceiptPdf.write(
                    rows = snapshot,
                    title = title,
                    output = output,
                    categoryLabel = { id ->
                        labels[id.orEmpty()] ?: TaxCategories.find(id.orEmpty())?.label.orEmpty()
                    },
                ) { completed, workTotal, message ->
                    ensureActive()
                    _state.value = PdfExportState.Running(
                        origin = origin,
                        completed = completed,
                        total = workTotal,
                        message = message,
                    )
                }
                _state.value = PdfExportState.Ready(
                    origin = origin,
                    filePath = output.absolutePath,
                    fileName = output.name,
                    receiptCount = snapshot.size,
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = PdfExportState.Failed(
                    origin = origin,
                    message = error.message?.takeIf { it.isNotBlank() } ?: "Could not create the PDF.",
                )
            }
        }
    }

    fun dismiss(origin: PdfExportOrigin) {
        when (val current = _state.value) {
            is PdfExportState.Ready -> if (current.origin == origin) {
                _state.value = PdfExportState.Idle
            }
            is PdfExportState.Failed -> if (current.origin == origin) {
                _state.value = PdfExportState.Idle
            }
            else -> Unit
        }
    }
}
