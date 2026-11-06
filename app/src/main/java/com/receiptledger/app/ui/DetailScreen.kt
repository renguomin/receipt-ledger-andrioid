package com.receiptledger.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receiptledger.app.data.CategoryCatalog
import com.receiptledger.app.data.ReceiptEntity
import com.receiptledger.app.data.ReceiptRepository
import com.receiptledger.engine.model.LabeledValue
import com.receiptledger.engine.model.ReceiptRecordLogic
import com.receiptledger.engine.tax.TaxCategories
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    receiptId: String,
    repository: ReceiptRepository,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    var row by remember { mutableStateOf<ReceiptEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewingPhoto by remember { mutableStateOf(false) }
    val custom by repository.customCategories.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()

    LaunchedEffect(receiptId) {
        row = repository.get(receiptId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        row?.merchant ?: "Receipt",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
    ) { padding ->
        val current = row
        if (current == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text("Loading…", modifier = Modifier.padding(top = 16.dp))
            }
            return@Scaffold
        }
        val presented = remember(current, custom) {
            DetailScreenLogic.present(
                ReceiptRecordLogic.detailFields(
                    parsed = repository.toParsed(current),
                    purpose = current.purpose,
                    category = CategoryCatalog.label(current.categoryId, custom),
                    dateAdded = ReceiptRecordLogic.formatDateAdded(current.createdAt),
                ),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (current.imagePath.isNotBlank()) {
                ReceiptPhotoCard(
                    path = current.imagePath,
                    onOpen = { viewingPhoto = true },
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        presented.total,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    presented.purchasedOn?.let { purchased ->
                        Text(
                            purchased,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    presented.addedOn?.let { added ->
                        Text(
                            added,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            presented.sections.forEach { section ->
                DetailSectionCard(section)
            }
            if (TaxCategories.find(current.categoryId.orEmpty())?.mealsFiftyPercent == true) {
                Text(
                    DetailScreenLogic.MEALS_NOTE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Button(
                onClick = onEdit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) {
                Text("Edit fields")
            }
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Delete")
            }
        }
        if (viewingPhoto) {
            ReceiptPhotoViewer(
                path = current.imagePath,
                onDismiss = { viewingPhoto = false },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this receipt?") },
            text = { Text("The photo and the typed fields will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.delete(receiptId)
                        confirmDelete = false
                        onDeleted()
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DetailSectionCard(section: DetailSection) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            section.title,
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
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                section.rows.forEachIndexed { index, row ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                    DetailRow(row)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(row: LabeledValue) {
    if (row.label == "Items") {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                row.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DetailScreenLogic.itemLines(row.value).forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            row.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            row.value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
