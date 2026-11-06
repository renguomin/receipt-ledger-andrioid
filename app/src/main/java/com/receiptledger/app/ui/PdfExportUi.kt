package com.receiptledger.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.receiptledger.app.export.ExportFiles
import com.receiptledger.app.export.PdfExportState
import java.io.File

@Composable
fun PdfExportProgressBlock(
    state: PdfExportState.Running,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { state.fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(state.message, style = MaterialTheme.typography.bodyMedium)
        Text(
            "${(state.fraction * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * [onOutcome] is told what happened and whether it worked, so a failed save can be announced
 * without taking the Save and Share buttons away from a second attempt.
 */
@Composable
fun ExportReadyActions(
    file: File,
    fileName: String,
    summary: String,
    mime: String,
    receiptCount: Int,
    saveFailed: String,
    onOutcome: (notice: String, saved: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val onOutcomeNow = rememberUpdatedState(onOutcome)
    val receiptCountNow = rememberUpdatedState(receiptCount)
    val saveFailedNow = rememberUpdatedState(saveFailed)
    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mime),
    ) { uri ->
        val copied = uri != null && ExportFiles.copy(context, file, uri)
        val notice = SettingsScreenLogic.saveNotice(
            uriSelected = uri != null,
            copied = copied,
            receiptCount = receiptCountNow.value,
            failed = saveFailedNow.value,
        ) ?: return@rememberLauncherForActivityResult
        onOutcomeNow.value(notice, copied)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$fileName · $summary",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { save.launch(fileName) }) {
                Text("Save")
            }
            OutlinedButton(onClick = { ExportFiles.share(context, file, mime) }) {
                Text("Share")
            }
        }
    }
}

/**
 * Progress and the finished file, which both need the screen. A failure is only a sentence, so it
 * goes to [onNotice] and floats past like every other message instead of demanding a tap.
 */
@Composable
fun PdfExportDialog(
    state: PdfExportState,
    onDismiss: () -> Unit,
    onNotice: (notice: String) -> Unit = {},
) {
    val onNoticeNow = rememberUpdatedState(onNotice)
    val onDismissNow = rememberUpdatedState(onDismiss)
    when (state) {
        is PdfExportState.Running -> Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 3.dp) {
                Column(
                    Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        SettingsScreenLogic.EXPORT_RUNNING_TITLE,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    PdfExportProgressBlock(state)
                }
            }
        }
        is PdfExportState.Ready -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(SettingsScreenLogic.PDF_READY_TITLE) },
            text = {
                ExportReadyActions(
                    file = state.file,
                    fileName = state.fileName,
                    summary = state.summary,
                    mime = ExportFiles.PDF_MIME,
                    receiptCount = state.receiptCount,
                    saveFailed = SettingsScreenLogic.PDF_SAVE_FAILED,
                    onOutcome = { notice, saved ->
                        if (saved) onDismiss()
                        onNotice(notice)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(SettingsScreenLogic.CLOSE) }
            },
        )
        is PdfExportState.Failed -> {
            val message = state.message
            // Reported once and cleared, so a rotation does not float the same failure again.
            LaunchedEffect(message) {
                onNoticeNow.value(message)
                onDismissNow.value()
            }
        }
        PdfExportState.Idle -> Unit
    }
}
