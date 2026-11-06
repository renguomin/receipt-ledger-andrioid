package com.receiptledger.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receiptledger.app.export.ExportFiles
import com.receiptledger.app.export.PdfExportOrigin
import com.receiptledger.app.export.PdfExportState
import com.receiptledger.app.export.originOrNull
import com.receiptledger.app.gemini.GeminiClient
import com.receiptledger.engine.export.LedgerPdfParser
import com.receiptledger.engine.export.PdfExportLogic
import com.receiptledger.engine.model.Country
import com.receiptledger.engine.model.Purpose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onImport: (String) -> Unit,
    importedCount: Int = 0,
    skippedCount: Int = 0,
    onImportedMessageShown: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val export by viewModel.pdfExportState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var quickImport by remember { mutableStateOf(ImportScreenLogic.DEFAULT_QUICK_IMPORT) }
    var importRunning by remember { mutableStateOf(false) }
    var showOutsideWarning by remember { mutableStateOf(false) }
    var csvReady by remember { mutableStateOf<WrittenExport?>(null) }
    val quickImportNow = rememberUpdatedState(quickImport)
    val settingsExport = export.takeIf { it.originOrNull() == PdfExportOrigin.SETTINGS }
    LaunchedEffect(importedCount, skippedCount) {
        if (importedCount > 0 || skippedCount > 0) {
            snackbar.showHeldNotice(
                ImportScreenLogic.reviewBatchMessage(importedCount, skippedCount),
            )
            onImportedMessageShown()
        }
    }
    fun handleOutcome(outcome: PdfImportOutcome) {
        importRunning = false
        when (outcome) {
            is PdfImportOutcome.Review -> onImport(outcome.firstImageUri)
            is PdfImportOutcome.Saved -> {
                scope.launch { snackbar.showHeldNotice(ImportScreenLogic.successMessage(outcome.count)) }
            }
            PdfImportOutcome.ConfirmOutsidePdf -> showOutsideWarning = true
            is PdfImportOutcome.Failed -> {
                scope.launch { snackbar.showHeldNotice(outcome.message) }
            }
        }
    }
    val pickPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importRunning = true
        val quick = quickImportNow.value
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
            }
            if (bytes == null) {
                importRunning = false
                snackbar.showHeldNotice(LedgerPdfParser.UNREADABLE)
                return@launch
            }
            viewModel.importPdf(bytes, quick, ::handleOutcome)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsSection(
                SettingsScreenLogic.TAX_COUNTRY,
                SettingsScreenLogic.sectionHelp(SettingsScreenLogic.TAX_COUNTRY, settings.country),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.country == Country.CA,
                        onClick = { viewModel.setCountry(Country.CA) },
                        label = { Text("Canada") },
                    )
                    FilterChip(
                        selected = settings.country == Country.US,
                        onClick = { viewModel.setCountry(Country.US) },
                        label = { Text("United States") },
                    )
                    FilterChip(
                        selected = settings.country == Country.NONE,
                        onClick = { viewModel.setCountry(Country.NONE) },
                        label = { Text("None") },
                    )
                }
            }
            SettingsSection(
                SettingsScreenLogic.CATEGORIES,
                SettingsScreenLogic.sectionHelp(SettingsScreenLogic.CATEGORIES, settings.country),
            ) {
                CustomCategoryEditor(viewModel)
            }
            SettingsSection(
                SettingsScreenLogic.IMPORT_SECTION,
                SettingsScreenLogic.sectionHelp(
                    SettingsScreenLogic.IMPORT_SECTION,
                    settings.country,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            ImportScreenLogic.TOGGLE_LABEL,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            ImportScreenLogic.toggleHelp(quickImport),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = quickImport,
                        onCheckedChange = { quickImport = it },
                        enabled = !importRunning,
                    )
                }
                if (importRunning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                OutlinedButton(
                    onClick = { pickPdf.launch(arrayOf("application/pdf")) },
                    enabled = !importRunning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                    Text(
                        ImportScreenLogic.CHOOSE_PDF,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            SettingsSection(
                SettingsScreenLogic.EXPORT_SECTION,
                SettingsScreenLogic.sectionHelp(
                    SettingsScreenLogic.EXPORT_SECTION,
                    settings.country,
                ),
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.exportCsv { csv ->
                            val written = SettingsScreenLogic.writeCsvExport(
                                cacheDir = context.cacheDir,
                                csv = csv,
                                allReceipts = true,
                                date = LocalDate.now(),
                            )
                            if (written == null) {
                                scope.launch { snackbar.showHeldNotice(SettingsScreenLogic.NOTHING_TO_EXPORT) }
                            } else {
                                csvReady = written
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.IosShare, contentDescription = null)
                    Text("Export CSV", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = {
                        if (viewModel.pdfExportBusy()) {
                            scope.launch { snackbar.showHeldNotice(SettingsScreenLogic.EXPORT_BUSY) }
                            return@OutlinedButton
                        }
                        viewModel.exportAllPdf {
                            scope.launch { snackbar.showHeldNotice(SettingsScreenLogic.NOTHING_TO_EXPORT) }
                        }
                    },
                    enabled = export !is PdfExportState.Running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                    Text("Export PDF", modifier = Modifier.padding(start = 8.dp))
                }
            }
            SettingsSection(
                SettingsScreenLogic.BACKUP_SECTION,
                SettingsScreenLogic.sectionHelp(
                    SettingsScreenLogic.BACKUP_SECTION,
                    settings.country,
                ),
            ) {
                BackupSection(
                    viewModel,
                    onNotice = { notice -> scope.launch { snackbar.showHeldNotice(notice) } },
                )
            }
            SettingsSection(SettingsScreenLogic.DEFAULT_PURPOSE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Purpose.entries.forEach { purpose ->
                        FilterChip(
                            selected = settings.defaultPurpose == purpose,
                            onClick = { viewModel.setDefaultPurpose(purpose) },
                            label = { Text(purpose.name.lowercase()) },
                        )
                    }
                }
            }
            SettingsSection(
                SettingsScreenLogic.GEMINI,
                SettingsScreenLogic.sectionHelp(SettingsScreenLogic.GEMINI, settings.country),
            ) {
                OutlinedTextField(
                    value = settings.geminiApiKey,
                    onValueChange = viewModel::setGeminiKey,
                    label = { Text("Gemini API key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = settings.geminiModel,
                    onValueChange = viewModel::setGeminiModel,
                    label = { Text("Gemini model (optional)") },
                    placeholder = { Text(GeminiClient.DEFAULT_MODEL) },
                    supportingText = {
                        Text("Leave blank for ${GeminiClient.DEFAULT_MODEL}.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }
    }
    PdfExportDialog(
        state = settingsExport ?: PdfExportState.Idle,
        onDismiss = viewModel::dismissPdfExport,
        onNotice = { notice -> scope.launch { snackbar.showHeldNotice(notice) } },
    )
    csvReady?.let { ready ->
        AlertDialog(
            onDismissRequest = { csvReady = null },
            title = { Text(SettingsScreenLogic.CSV_READY_TITLE) },
            text = {
                ExportReadyActions(
                    file = ready.file,
                    fileName = ready.fileName,
                    summary = PdfExportLogic.readySummary(ready.receiptCount),
                    mime = ExportFiles.CSV_MIME,
                    receiptCount = ready.receiptCount,
                    saveFailed = SettingsScreenLogic.CSV_SAVE_FAILED,
                    onOutcome = { notice, saved ->
                        if (saved) csvReady = null
                        scope.launch { snackbar.showHeldNotice(notice) }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { csvReady = null }) {
                    Text(SettingsScreenLogic.CLOSE)
                }
            },
        )
    }
    if (showOutsideWarning) {
        AlertDialog(
            onDismissRequest = {
                showOutsideWarning = false
                viewModel.cancelOutsideImport()
            },
            title = { Text(ImportScreenLogic.OUTSIDE_WARNING_TITLE) },
            text = { Text(ImportScreenLogic.OUTSIDE_WARNING_BODY) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOutsideWarning = false
                        importRunning = true
                        viewModel.continueOutsideImport(saveWithoutReview = false, ::handleOutcome)
                    },
                ) {
                    Text(ImportScreenLogic.OUTSIDE_WARNING_REVIEW)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOutsideWarning = false
                        importRunning = true
                        viewModel.continueOutsideImport(saveWithoutReview = true, ::handleOutcome)
                    },
                ) {
                    Text(ImportScreenLogic.OUTSIDE_WARNING_SAVE_ALL)
                }
            },
        )
    }
}

/**
 * A titled block. [help] is reached through an info button on the title rather than printed in the
 * block, because explanatory text sitting among the controls reads like another thing to tap.
 */
@Composable
private fun SettingsSection(
    title: String,
    help: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var showHelp by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            help?.let {
                IconButton(
                    onClick = { showHelp = true },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = SettingsScreenLogic.helpButtonLabel(title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
    if (showHelp && help != null) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(title) },
            text = { Text(help) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text(SettingsScreenLogic.HELP_ACTION)
                }
            },
        )
    }
}

@Composable
private fun CustomCategoryEditor(viewModel: SettingsViewModel) {
    val custom by viewModel.customCategories.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("New category") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        TextButton(
            onClick = {
                viewModel.addCustomCategory(draft)
                draft = ""
            },
            enabled = draft.isNotBlank(),
        ) { Text("Add") }
    }
    custom.forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.label, modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.deleteCustomCategory(row.id) }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete ${row.label}")
            }
        }
    }
}
