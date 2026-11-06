package com.receiptledger.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receiptledger.app.BuildConfig
import com.receiptledger.engine.model.LineItem
import com.receiptledger.engine.model.Purpose
import com.receiptledger.engine.model.ReceiptRecordLogic
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    onDone: (String?) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var viewingPhoto by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.warmupGemini()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(ReviewScreenLogic.TITLE) },
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
        when {
            state.loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    ImportProgressHeader(
                        index = state.importIndex,
                        total = state.importTotal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .align(Alignment.TopCenter),
                    )
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Text(
                            AiReadLogic.loadingLabel(state.aiBusy),
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
            else -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ImportProgressHeader(
                        index = state.importIndex,
                        total = state.importTotal,
                    )
                    state.imagePath?.let { path ->
                        ReceiptPhotoCard(
                            path = path,
                            onOpen = { viewingPhoto = true },
                        )
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text(
                        ReviewScreenLogic.VERIFY_COPY,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ReviewSection("Details") {
                        ReviewField(
                            label = "Store",
                            value = state.parsed.merchant.value.orEmpty(),
                            onChange = { value ->
                                viewModel.updateParsed {
                                    it.copy(merchant = it.merchant.copy(value = value.ifBlank { null }))
                                }
                            },
                        )
                        ReviewField(
                            label = ReceiptRecordLogic.PURCHASED_DATE_LABEL,
                            value = state.parsed.date.value.orEmpty(),
                            placeholder = "YYYY-MM-DD",
                            onChange = { value ->
                                viewModel.updateParsed {
                                    it.copy(date = it.date.copy(value = value.ifBlank { null }))
                                }
                            },
                        )
                        ReviewField(
                            label = "Receipt number",
                            value = state.parsed.receiptNumber.value.orEmpty(),
                            onChange = { value ->
                                viewModel.updateParsed {
                                    it.copy(receiptNumber = it.receiptNumber.copy(value = value.ifBlank { null }))
                                }
                            },
                        )
                        ReviewField(
                            label = "Payment",
                            value = state.parsed.paymentMethod.value.orEmpty(),
                            onChange = { value ->
                                viewModel.updateParsed {
                                    it.copy(paymentMethod = it.paymentMethod.copy(value = value.ifBlank { null }))
                                }
                            },
                        )
                        ReviewField(
                            label = "GST/HST number",
                            value = state.parsed.gstHstNumber.value.orEmpty(),
                            onChange = { value ->
                                viewModel.updateParsed {
                                    it.copy(gstHstNumber = it.gstHstNumber.copy(value = value.ifBlank { null }))
                                }
                            },
                        )
                    }
                    ReviewSection("Amounts") {
                        ReviewField(
                            label = "Total",
                            value = state.parsed.total.value?.toPlain().orEmpty(),
                            keyboardType = KeyboardType.Decimal,
                            onChange = { value ->
                                viewModel.updateParsed {
                                    it.copy(total = it.total.copy(value = value.toDoubleOrNull()))
                                }
                            },
                        )
                        ReviewField(
                            label = "Subtotal",
                            value = state.parsed.subtotal.value?.toPlain().orEmpty(),
                            keyboardType = KeyboardType.Decimal,
                            onChange = { value ->
                                viewModel.updateParsed {
                                    it.copy(subtotal = it.subtotal.copy(value = value.toDoubleOrNull()))
                                }
                            },
                        )
                        ReviewField(
                            label = "Tax total",
                            value = state.parsed.taxTotal.takeIf { it > 0 }?.toPlain().orEmpty(),
                            keyboardType = KeyboardType.Decimal,
                            onChange = { },
                            readOnly = true,
                        )
                        if (state.parsed.taxes.isNotEmpty()) {
                            Text(
                                state.parsed.taxes.joinToString(" · ") { tax ->
                                    ReceiptRecordLogic.formatTax(tax)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ReviewField(
                            label = "Currency",
                            value = state.parsed.currency.value.orEmpty(),
                            onChange = { value ->
                                viewModel.updateParsed {
                                    it.copy(currency = it.currency.copy(value = value.ifBlank { null }))
                                }
                            },
                        )
                    }
                    // Items pack four controls into one row, so they get the width back.
                    ReviewSection("Items", contentPadding = 10.dp) {
                        LineItemsEditor(
                            items = state.parsed.lineItems,
                            onNameChange = { index, value -> viewModel.updateLineItem(index, name = value) },
                            onQuantityChange = { index, value ->
                                viewModel.updateLineItem(index, quantityRaw = value)
                            },
                            onAmountChange = { index, value ->
                                viewModel.updateLineItem(index, amountRaw = value)
                            },
                            onRemove = viewModel::removeLineItem,
                            onAdd = viewModel::addLineItem,
                        )
                    }
                    ReviewSection("This purchase is") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Purpose.entries.forEach { purpose ->
                                ReviewFilterChip(
                                    label = ReviewScreenLogic.purposeLabel(purpose),
                                    selected = state.purpose == purpose,
                                    onClick = { viewModel.setPurpose(purpose) },
                                )
                            }
                        }
                        CategoryPicker(
                            options = state.categoryOptions.map { it.id to it.label },
                            selected = state.categoryId,
                            onSelect = viewModel::setCategory,
                            onAdd = viewModel::addCustomCategory,
                        )
                    }
                    val aiStatus = AiReadLogic.status(
                        busy = state.aiBusy,
                        error = state.aiError,
                        summary = state.aiSummary,
                    )
                    if (aiStatus != null) {
                        Text(
                            aiStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (
                                AiReadLogic.statusIsFallback(state.aiBusy, state.aiError)
                            ) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Button(
                        onClick = { viewModel.saveToDisk(onDone) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Text("Save receipt")
                    }
                    if (ReviewScreenLogic.showSkipButton(state.importTotal)) {
                        OutlinedButton(
                            onClick = { viewModel.skipReceipt(onDone) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(22.dp),
                        ) {
                            Text(ReviewScreenLogic.SKIP_LABEL)
                        }
                    }
                    if (ReviewScreenLogic.showOcrDumpAction(BuildConfig.DEBUG)) {
                        TextButton(
                            onClick = {
                                viewModel.saveOcrDump { path ->
                                    scope.launch {
                                        snackbar.showSnackbar("Saved scan + OCR in $path")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(ReviewScreenLogic.SAVE_OCR_DUMP_LABEL)
                        }
                    }
                }
            }
        }
    }

    val photoPath = state.imagePath
    if (viewingPhoto && photoPath != null) {
        ReceiptPhotoViewer(
            path = photoPath,
            onDismiss = { viewingPhoto = false },
        )
    }

    val duplicateWarning = state.duplicateWarning
    if (duplicateWarning != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicateWarning,
            title = { Text(DuplicateReceiptLogic.TITLE) },
            text = { Text(duplicateWarning.body) },
            confirmButton = {
                TextButton(onClick = { viewModel.skipDuplicate(onDone) }) {
                    Text(DuplicateReceiptLogic.SKIP)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.saveToDisk(onDone, ignoreDuplicate = true)
                    },
                ) {
                    Text(DuplicateReceiptLogic.SAVE_AGAIN)
                }
            },
        )
    }
}

@Composable
private fun ReviewSection(
    title: String,
    contentPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = { content() },
            )
        }
    }
}

private val LABEL_COLUMN = 112.dp
private val QTY_COLUMN = 62.dp
private val AMOUNT_COLUMN = 92.dp
private val ROW_ICON = 32.dp
private val ROW_GAP = 6.dp

/** Label on the left, input on the right, so every value reads as a single row. */
@Composable
private fun ReviewField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String? = null,
    readOnly: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        RowLabel(label, Modifier.width(LABEL_COLUMN))
        CompactField(
            value = value,
            onChange = onChange,
            modifier = Modifier.weight(1f),
            placeholder = placeholder,
            readOnly = readOnly,
            keyboardType = keyboardType,
        )
    }
}

@Composable
private fun RowLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun CompactField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    readOnly: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    dense: Boolean = false,
) {
    val textStyle = if (dense) {
        MaterialTheme.typography.bodySmall
    } else {
        MaterialTheme.typography.bodyMedium
    }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = placeholder?.let {
            {
                Text(
                    it,
                    style = textStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        readOnly = readOnly,
        singleLine = true,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        colors = reviewFieldColors(),
        modifier = modifier,
    )
}

@Composable
private fun reviewFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
)

@Composable
private fun ReviewFilterChip(
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
private fun LineItemsEditor(
    items: List<LineItem>,
    onNameChange: (Int, String) -> Unit,
    onQuantityChange: (Int, String) -> Unit,
    onAmountChange: (Int, String) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    if (items.isEmpty()) {
        Text(
            ReviewScreenLogic.ITEMS_EMPTY,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (ReviewScreenLogic.showItemsHeader(items.size)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
        ) {
            ItemHeader(ReviewScreenLogic.ITEM_HEADER, Modifier.weight(1f))
            ItemHeader(ReviewScreenLogic.QTY_HEADER, Modifier.width(QTY_COLUMN))
            ItemHeader(ReviewScreenLogic.AMOUNT_HEADER, Modifier.width(AMOUNT_COLUMN))
            Spacer(Modifier.width(ROW_ICON))
        }
    }
    items.forEachIndexed { index, item ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
        ) {
            CompactField(
                value = item.name,
                onChange = { onNameChange(index, it) },
                modifier = Modifier.weight(1f),
                placeholder = ReviewScreenLogic.itemNamePlaceholder(index),
                dense = true,
            )
            CompactField(
                value = item.quantity?.let { ReceiptRecordLogic.formatQuantity(it) }.orEmpty(),
                onChange = { onQuantityChange(index, it) },
                modifier = Modifier.width(QTY_COLUMN),
                keyboardType = KeyboardType.Decimal,
                dense = true,
            )
            CompactField(
                value = item.amount?.let { ReceiptRecordLogic.formatAmount(it) }.orEmpty(),
                onChange = { onAmountChange(index, it) },
                modifier = Modifier.width(AMOUNT_COLUMN),
                keyboardType = KeyboardType.Decimal,
                dense = true,
            )
            IconButton(
                onClick = { onRemove(index) },
                modifier = Modifier.size(ROW_ICON),
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = ReviewScreenLogic.removeItemDescription(index),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
    OutlinedButton(
        onClick = onAdd,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
    ) {
        Text(ReviewScreenLogic.ADD_ITEM_LABEL)
    }
}

@Composable
private fun ItemHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPicker(
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val label = ReviewScreenLogic.categoryLabel(selected, options)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        RowLabel(ReviewScreenLogic.CATEGORY_LABEL, Modifier.width(LABEL_COLUMN))
        ExposedDropdownMenuBox(
            expanded = open,
            onExpandedChange = { open = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                shape = RoundedCornerShape(14.dp),
                colors = reviewFieldColors(),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onSelect(id)
                            open = false
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Add category") },
                    onClick = {
                        open = false
                        adding = true
                    },
                )
            }
        }
    }
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("New category") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAdd(draft)
                        draft = ""
                        adding = false
                    },
                    enabled = draft.isNotBlank(),
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { adding = false }) { Text("Cancel") }
            },
        )
    }
}

private fun Double.toPlain(): String = "%.2f".format(this)

@Composable
private fun ImportProgressHeader(
    index: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    if (!ReviewScreenLogic.showImportProgress(total)) return
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            ReviewScreenLogic.importDetectedLabel(total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            ReviewScreenLogic.importProgressLabel(index, total),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LinearProgressIndicator(
            progress = { ReviewScreenLogic.importProgressFraction(index, total) },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
