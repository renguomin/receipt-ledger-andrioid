package com.receiptledger.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receiptledger.app.data.ReceiptEntity
import com.receiptledger.app.export.ExportFiles
import com.receiptledger.app.export.PdfExportOrigin
import com.receiptledger.app.export.PdfExportState
import com.receiptledger.app.export.originOrNull
import com.receiptledger.app.ocr.rememberReceiptScanner
import com.receiptledger.engine.export.PdfExportLogic
import com.receiptledger.engine.model.Purpose
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToInt

private val FabSize = 84.dp
private val FabIconSize = 36.dp

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onScan: (Uri) -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pdfExport by viewModel.pdfExportState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val launchScanner = rememberReceiptScanner(
        onScanned = { uris -> uris.firstOrNull()?.let(onScan) },
        onError = { message -> scope.launch { snackbar.showSnackbar(message) } },
    )
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val selectedIdsNow = rememberUpdatedState(selectedIds)
    var selectAnchor by remember { mutableIntStateOf(-1) }
    var sweeping by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var csvReady by remember { mutableStateOf<WrittenExport?>(null) }
    val selecting = selectedIds.isNotEmpty()
    val targetRows = HomeListLogic.actionReceipts(state.receipts, selectedIds)
    val showAddNewHint = HomeListLogic.showAddNewHint(
        loaded = state.addNewHintReady,
        hasSeenHint = state.hasSeenAddNewHint,
        selecting = selecting,
    )
    var addNewHintElapsedMs by remember { mutableLongStateOf(0L) }
    val addNewHintAlpha = HomeListLogic.addNewHintAlpha(addNewHintElapsedMs)
    val addNewHintVisible = showAddNewHint && HomeListLogic.addNewHintVisible(addNewHintAlpha)
    val onAddNew = {
        viewModel.dismissAddNewHint()
        launchScanner()
    }

    LaunchedEffect(showAddNewHint) {
        if (!showAddNewHint) {
            addNewHintElapsedMs = 0L
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (true) {
            val elapsed = (withFrameNanos { it } - start) / 1_000_000L
            addNewHintElapsedMs = elapsed
            if (!HomeListLogic.addNewHintVisible(HomeListLogic.addNewHintAlpha(elapsed))) {
                viewModel.dismissAddNewHint()
                break
            }
        }
    }

    BackHandler(enabled = selecting) {
        selectedIds = emptySet()
        selectAnchor = -1
    }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (addNewHintVisible) {
                    AddNewHint(
                        onClick = onAddNew,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .alpha(addNewHintAlpha),
                    )
                }
                FloatingActionButton(
                    onClick = onAddNew,
                    modifier = Modifier.size(FabSize),
                ) {
                    Icon(
                        Icons.Outlined.PhotoCamera,
                        contentDescription = if (addNewHintVisible) {
                            "Add new. Scan receipt"
                        } else {
                            "Scan receipt"
                        },
                        modifier = Modifier.size(FabIconSize),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selecting) {
                    IconButton(onClick = {
                        selectedIds = emptySet()
                        selectAnchor = -1
                    }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear selection")
                    }
                    Text(
                        "${selectedIds.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    OutlinedTextField(
                        value = state.filters.query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text("Search Merchant") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                    )
                }
                Box {
                    IconButton(onClick = { showFilters = true }) {
                        Icon(
                            Icons.Outlined.FilterAlt,
                            contentDescription = "Filter",
                            tint = if (state.filters.isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    if (state.filters.selectedCount > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .heightIn(min = 16.dp)
                                .sizeIn(minWidth = 16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                state.filters.selectedCount.toString(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 12.sp,
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        ReceiptSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label()) },
                                onClick = {
                                    viewModel.setSort(option)
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                }
                val menuActions = HomeListLogic.overflowActions(selecting)
                if (HomeListLogic.showOverflowMenu(selecting)) {
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            menuActions.forEach { action ->
                                when (action) {
                                    HomeMenuAction.EXPORT_CSV -> DropdownMenuItem(
                                        text = { Text("Export CSV") },
                                        leadingIcon = { Icon(Icons.Outlined.IosShare, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.csv(targetRows) { csv ->
                                                if (targetRows.isEmpty()) {
                                                    scope.launch { snackbar.showSnackbar("Nothing to export yet.") }
                                                } else {
                                                    val written = SettingsScreenLogic.writeCsvExport(
                                                        cacheDir = context.cacheDir,
                                                        csv = csv,
                                                        allReceipts = false,
                                                        date = LocalDate.now(),
                                                    )
                                                    if (written == null) {
                                                        scope.launch {
                                                            snackbar.showSnackbar("Nothing to export yet.")
                                                        }
                                                    } else {
                                                        csvReady = written
                                                    }
                                                }
                                            }
                                        },
                                    )
                                    HomeMenuAction.EXPORT_PDF -> DropdownMenuItem(
                                        text = { Text("Export PDF") },
                                        leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            if (targetRows.isEmpty()) {
                                                scope.launch { snackbar.showSnackbar("Nothing to export yet.") }
                                            } else if (viewModel.pdfExportBusy()) {
                                                scope.launch { snackbar.showSnackbar("An export is already running.") }
                                            } else {
                                                val years = state.filters.years.sortedDescending()
                                                viewModel.exportPdf(
                                                    rows = targetRows,
                                                    title = if (years.isEmpty()) {
                                                        "Receipts all years"
                                                    } else {
                                                        "Receipts ${years.joinToString(", ")}"
                                                    },
                                                    categoryLabels = state.categories.associate { it.id to it.label },
                                                )
                                            }
                                        },
                                    )
                                    HomeMenuAction.DELETE -> DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Delete",
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            confirmDelete = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                if (!selecting) {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            }
            Text(
                "Sorted by ${state.filters.sort.label().lowercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
            if (state.receipts.isEmpty()) {
                Text(
                    "No receipts in this list yet. Scan one, or change the filter.",
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                Box(Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = !sweeping,
                        contentPadding = PaddingValues(end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(state.listItems) {
                                val items = state.listItems
                                awaitEachGesture {
                                    val down = awaitFirstDown(
                                        requireUnconsumed = false,
                                        pass = PointerEventPass.Initial,
                                    )
                                    val held = awaitLongPressInInitial(down)
                                        ?: return@awaitEachGesture
                                    val startIndex = listState.indexAt(held.position.y)
                                        ?: return@awaitEachGesture
                                    held.consume()
                                    sweeping = true
                                    selectAnchor = startIndex
                                    val base = selectedIdsNow.value
                                    fun cover(index: Int) {
                                        selectedIds = HomeListLogic.applySweep(
                                            base,
                                            HomeListLogic.idsInRange(items, startIndex, index),
                                        )
                                    }
                                    cover(startIndex)
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                                ?: break
                                            change.consume()
                                            if (change.changedToUpIgnoreConsumed()) break
                                            val y = change.position.y
                                            val edge = 72f
                                            if (y < edge) {
                                                scope.launch { listState.scrollBy(-48f) }
                                            } else if (y > size.height - edge) {
                                                scope.launch { listState.scrollBy(48f) }
                                            }
                                            val index = listState.indexAt(y) ?: continue
                                            cover(index)
                                        }
                                    } finally {
                                        sweeping = false
                                    }
                                }
                            },
                    ) {
                        itemsIndexed(
                            state.listItems,
                            key = { _, item ->
                                when (item) {
                                    is HomeListItem.Header -> "h:${item.label}"
                                    is HomeListItem.Receipt -> item.row.id
                                }
                            },
                        ) { index, item ->
                            when (item) {
                                is HomeListItem.Header -> SectionHeader(item.label)
                                is HomeListItem.Receipt -> {
                                    val row = item.row
                                    ReceiptRow(
                                        row = row,
                                        sort = state.filters.sort,
                                        selected = row.id in selectedIds,
                                        selecting = selecting,
                                        clickEnabled = !sweeping,
                                        onClick = {
                                            if (selecting) {
                                                selectedIds = HomeListLogic.toggle(selectedIds, row.id)
                                                selectAnchor = index
                                            } else {
                                                onOpen(row.id)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    FastScrollbar(
                        listState = listState,
                        items = state.listItems,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
    if (showFilters) {
        val maxFilterHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f
        Dialog(
            onDismissRequest = { showFilters = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(max = maxFilterHeight),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                FilterSheet(
                    state = state,
                    viewModel = viewModel,
                    onDone = { showFilters = false },
                )
            }
        }
    }
    if (confirmDelete) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(
                    if (count == 1) "Delete this receipt?" else "Delete $count receipts?",
                )
            },
            text = {
                Text("The photos and typed fields will be removed from this phone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = selectedIds
                        viewModel.delete(ids) {
                            selectedIds = emptySet()
                            selectAnchor = -1
                            confirmDelete = false
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
    csvReady?.let { ready ->
        AlertDialog(
            onDismissRequest = { csvReady = null },
            title = { Text("CSV ready") },
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
                TextButton(onClick = { csvReady = null }) { Text("Close") }
            },
        )
    }
    if (pdfExport.originOrNull() == PdfExportOrigin.HOME) {
        PdfExportDialog(
            state = pdfExport,
            onDismiss = viewModel::dismissPdfExport,
            onNotice = { notice -> scope.launch { snackbar.showHeldNotice(notice) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    state: HomeUiState,
    viewModel: HomeViewModel,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Filter",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::clearFilters) { Text("Clear") }
            TextButton(onClick = onDone) { Text("Done") }
        }
        HorizontalDivider()
        var expandedSections by remember {
            mutableStateOf(FilterSheetLogic.initiallyExpanded())
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CollapsibleFilterSection(
                section = FilterSheetSection.YEAR,
                selectedCount = state.filters.years.size,
                expanded = FilterSheetLogic.isExpanded(expandedSections, FilterSheetSection.YEAR),
                onToggle = {
                    expandedSections = FilterSheetLogic.toggle(expandedSections, FilterSheetSection.YEAR)
                },
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.availableYears.forEach { year ->
                        SelectableFilterTag(
                            label = year.toString(),
                            selected = year in state.filters.years,
                            onClick = { viewModel.toggleYear(year) },
                        )
                    }
                }
            }
            CollapsibleFilterSection(
                section = FilterSheetSection.DATE_ADDED,
                selectedCount = if (state.filters.dateAddedSelected) 1 else 0,
                expanded = FilterSheetLogic.isExpanded(expandedSections, FilterSheetSection.DATE_ADDED),
                onToggle = {
                    expandedSections =
                        FilterSheetLogic.toggle(expandedSections, FilterSheetSection.DATE_ADDED)
                },
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DateAddedWindow.entries
                        .filter { it != DateAddedWindow.ANY }
                        .forEach { window ->
                            SelectableFilterTag(
                                label = window.label,
                                selected = state.filters.dateAdded == window,
                                onClick = { viewModel.setDateAdded(window) },
                            )
                        }
                }
            }
            CollapsibleFilterSection(
                section = FilterSheetSection.TYPE,
                selectedCount = state.filters.purposes.size,
                expanded = FilterSheetLogic.isExpanded(expandedSections, FilterSheetSection.TYPE),
                onToggle = {
                    expandedSections = FilterSheetLogic.toggle(expandedSections, FilterSheetSection.TYPE)
                },
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Purpose.entries.forEach { purpose ->
                        SelectableFilterTag(
                            label = purpose.name.lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) },
                            selected = state.filters.purposes.any { it.equals(purpose.name, ignoreCase = true) },
                            onClick = { viewModel.togglePurpose(purpose.name) },
                        )
                    }
                }
            }
            CollapsibleFilterSection(
                section = FilterSheetSection.CATEGORY,
                selectedCount = state.filters.categoryIds.size,
                expanded = FilterSheetLogic.isExpanded(expandedSections, FilterSheetSection.CATEGORY),
                onToggle = {
                    expandedSections = FilterSheetLogic.toggle(expandedSections, FilterSheetSection.CATEGORY)
                },
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.categories.forEach { category ->
                        SelectableFilterTag(
                            label = category.label,
                            selected = category.id in state.filters.categoryIds,
                            onClick = { viewModel.toggleCategory(category.id) },
                        )
                    }
                }
            }
            CollapsibleFilterSection(
                section = FilterSheetSection.AMOUNT,
                selectedCount = if (state.filters.amountSelected) 1 else 0,
                expanded = FilterSheetLogic.isExpanded(expandedSections, FilterSheetSection.AMOUNT),
                onToggle = {
                    expandedSections = FilterSheetLogic.toggle(expandedSections, FilterSheetSection.AMOUNT)
                },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.filters.minTotalText,
                        onValueChange = viewModel::setMinTotal,
                        label = { Text("From") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.filters.maxTotalText,
                        onValueChange = viewModel::setMaxTotal,
                        label = { Text("To") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsibleFilterSection(
    section: FilterSheetSection,
    selectedCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val title = FilterSheetLogic.title(section)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterSectionHeading(
            title = title,
            selectedCount = selectedCount,
            expanded = expanded,
            onToggle = onToggle,
        )
        if (expanded) content()
    }
}

@Composable
private fun FilterSectionHeading(
    title: String,
    selectedCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box(
            Modifier
                .heightIn(min = 20.dp)
                .sizeIn(minWidth = 20.dp)
                .clip(CircleShape)
                .background(
                    if (selectedCount > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                selectedCount.toString(),
                color = if (selectedCount > 0) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(Modifier.weight(1f))
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = FilterSheetLogic.expandActionLabel(title, expanded),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectableFilterTag(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
private fun AddNewHint(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondary,
        shadowElevation = 2.dp,
    ) {
        Text(
            HomeListLogic.ADD_NEW_HINT_LABEL,
            color = MaterialTheme.colorScheme.onSecondary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun ReceiptRow(
    row: ReceiptEntity,
    sort: ReceiptSort,
    selected: Boolean,
    selecting: Boolean,
    clickEnabled: Boolean,
    onClick: () -> Unit,
) {
    val money = remember(row.currency, row.total) {
        val format = NumberFormat.getCurrencyInstance(Locale.CANADA)
        runCatching { format.currency = Currency.getInstance(row.currency ?: "CAD") }
        format.format(row.total ?: 0.0)
    }
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(enabled = clickEnabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = null)
        }
        Column(Modifier.weight(1f)) {
            Text(
                row.merchant ?: "Unknown store",
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                HomeListLogic.rowSubtitle(row, sort),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(money, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FastScrollbar(
    listState: LazyListState,
    items: List<HomeListItem>,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 4.dp,
) {
    val density = LocalDensity.current
    val spacingPx = with(density) { itemSpacing.toPx() }
    listState.firstVisibleItemIndex
    listState.firstVisibleItemScrollOffset
    val metrics = listState.scrollbarMetrics(spacingPx) ?: return
    val marks = HomeListLogic.scrollbarSectionMarks(
        items = items,
        maxScroll = metrics.maxScroll,
        avgItemSize = metrics.avgItemSize,
        spacingPx = spacingPx,
    )
    val currentHeader = HomeListLogic.currentSectionHeader(items, listState.firstVisibleItemIndex)
    val active = marks.firstOrNull { it.rawLabel == currentHeader }
        ?: HomeListLogic.activeSectionMark(marks, metrics.progress)
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier
            .width(22.dp)
            .pointerInput(listState, spacingPx) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val current = listState.scrollbarMetrics(spacingPx) ?: return@detectVerticalDragGestures
                        val fraction = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                        val stride = current.avgItemSize + spacingPx
                        if (stride <= 0f) return@detectVerticalDragGestures
                        val target = fraction * current.maxScroll
                        val lastIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                        val index = (target / stride).toInt().coerceIn(0, lastIndex)
                        val itemOffset = (target - index * stride).roundToInt()
                        scope.launch { listState.scrollToItem(index, itemOffset) }
                    },
                )
            },
    ) {
        val thumbH = (maxHeight * metrics.thumbFraction).coerceAtLeast(36.dp)
        val travel = (maxHeight - thumbH).coerceAtLeast(0.dp)
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        )
        marks.forEach { mark ->
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = travel * mark.progress)
                    .width(8.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (mark.index == active?.index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                        },
                    ),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = travel * metrics.progress)
                .width(12.dp)
                .height(thumbH)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
        )
        if (dragging) {
            marks.forEach { mark ->
                val emphasized = mark.index == active?.index
                Text(
                    mark.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (emphasized) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .wrapContentWidth(unbounded = true, align = Alignment.End)
                        .offset(x = (-16).dp, y = travel * mark.progress - 8.dp)
                        .then(
                            if (emphasized) {
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            } else {
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            },
                        ),
                )
            }
        }
    }
}

private fun ReceiptSort.label(): String = HomeListLogic.sortLabel(this)

private suspend fun AwaitPointerEventScope.awaitLongPressInInitial(
    down: PointerInputChange,
): PointerInputChange? {
    val slop = viewConfiguration.touchSlop
    val finishedBeforeTimeout = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull Unit
            if (change.changedToUpIgnoreConsumed()) return@withTimeoutOrNull Unit
            if ((change.position - down.position).getDistance() > slop) {
                return@withTimeoutOrNull Unit
            }
        }
    }
    if (finishedBeforeTimeout != null) return null
    return currentEvent.changes.firstOrNull { it.id == down.id } ?: down
}

private fun LazyListState.indexAt(y: Float): Int? =
    HomeListLogic.indexAt(
        layoutInfo.visibleItemsInfo.map { VisibleRow(it.index, it.offset, it.size) },
        y,
    )

private fun LazyListState.scrollbarMetrics(spacingPx: Float): ScrollbarMetrics? {
    val info = layoutInfo
    return HomeListLogic.scrollbarMetrics(
        visible = info.visibleItemsInfo.map { VisibleRow(it.index, it.offset, it.size) },
        totalItems = info.totalItemsCount,
        viewportStart = info.viewportStartOffset,
        viewportEnd = info.viewportEndOffset,
        beforePadding = info.beforeContentPadding,
        afterPadding = info.afterContentPadding,
        spacingPx = spacingPx,
    )
}
