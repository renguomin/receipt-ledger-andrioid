package com.receiptledger.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receiptledger.app.MainActivity
import com.receiptledger.app.backup.BackupState
import com.receiptledger.app.backup.BackupStateLogic
import com.receiptledger.engine.backup.BackupArchive
import com.receiptledger.engine.backup.BackupLogic
import com.receiptledger.engine.backup.DailyBackupLogic

/**
 * [onNotice] is expected to hand the message to a scope that outlives this section, because
 * announcing an outcome also clears it, and a notice launched from the effect that watches the
 * state would be cancelled by its own clearing.
 */
@Composable
fun BackupSection(
    viewModel: SettingsViewModel,
    onNotice: (String) -> Unit = {},
) {
    val state by viewModel.backupState.collectAsStateWithLifecycle()
    val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle()
    val destination by viewModel.backupDestination.collectAsStateWithLifecycle()
    val dailyEnabled by viewModel.dailyBackupEnabled.collectAsStateWithLifecycle()
    val destinationLabel by viewModel.backupDestinationLabel.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmRestore by remember { mutableStateOf(false) }

    val chooseDestination = rememberLauncherForActivityResult(
        ChooseBackupDestination,
    ) { uri -> viewModel.onBackupDestinationChosen(uri) }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> viewModel.onBackupSourceChosen(uri) }

    val onNoticeNow = rememberUpdatedState(onNotice)

    LaunchedEffect(state) {
        // Marked as opened right after launching, so a re-run of this effect finds the request
        // spent instead of stacking a second picker behind the first.
        when (val current = state) {
            is BackupState.ChooseDestination -> {
                chooseDestination.launch(current.fileName)
                viewModel.onBackupPickerOpened()
            }
            BackupState.ChooseSource -> {
                openDocument.launch(arrayOf(BackupArchive.MIME_TYPE, "*/*"))
                viewModel.onBackupPickerOpened()
            }
            else -> Unit
        }
        SettingsScreenLogic.backupNotice(state)?.let { notice ->
            onNoticeNow.value(notice)
            viewModel.dismissBackup()
        }
    }

    Text(
        BackupLogic.lastBackupLabel(lastBackupAt, System.currentTimeMillis()),
        style = MaterialTheme.typography.bodyMedium,
    )

    val running = state as? BackupState.Running
    val busy = BackupStateLogic.isBusy(state)
    if (running != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(
                progress = { running.fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(running.message, style = MaterialTheme.typography.bodySmall)
        }
    }

    OutlinedButton(
        onClick = viewModel::backUpNow,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.CloudUpload, contentDescription = null)
        Text(
            BackupLogic.backUpLabel(destination != null),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    OutlinedButton(
        onClick = { confirmRestore = true },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.CloudDownload, contentDescription = null)
        Text(BackupLogic.RESTORE, modifier = Modifier.padding(start = 8.dp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            DailyBackupLogic.TOGGLE_LABEL,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = dailyEnabled && destination != null,
            // Tappable even with nowhere to write, so the reason can float past like any other
            // message. A dead switch would have to explain itself inside the section instead.
            onCheckedChange = { wanted ->
                if (DailyBackupLogic.canEnable(destination != null)) {
                    viewModel.setDailyBackup(wanted)
                } else {
                    onNotice(DailyBackupLogic.NEEDS_DESTINATION)
                }
            },
        )
    }

    destinationLabel?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium)
    }

    if (destination != null && !busy) {
        TextButton(onClick = viewModel::chooseNewBackupDestination) {
            Text(BackupLogic.CHANGE_DESTINATION)
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(BackupLogic.RESTORE_CONFIRM_TITLE) },
            text = { Text(BackupLogic.RESTORE_CONFIRM_BODY) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        viewModel.restoreBackup()
                    },
                ) { Text(BackupLogic.RESTORE_CONFIRM_ACTION) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        viewModel.chooseAnotherBackup()
                    },
                ) { Text(BackupLogic.RESTORE_ANOTHER) }
            },
        )
    }

    (state as? BackupState.Restored)?.let { restored ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(BackupLogic.RESTART_TITLE) },
            text = { Text("${restored.summary} ${BackupLogic.RESTART_BODY}") },
            confirmButton = {
                TextButton(onClick = { restartApp(context) }) {
                    Text(BackupLogic.RESTART_ACTION)
                }
            },
        )
    }
}

/**
 * The only destination picker, because the person should not have to decide up front whether they
 * are replacing or creating. [Intent.ACTION_CREATE_DOCUMENT] is a save dialog and covers both:
 * tapping a file already in the folder returns that file, and a name the folder does not hold yet
 * comes back as a new one.
 *
 * Built by hand rather than with [ActivityResultContracts.CreateDocument] so the write and persist
 * flags can be added. Without them the grant dies with the process, and the unattended daily backup
 * has no picker to ask again with.
 */
private object ChooseBackupDestination : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(BackupArchive.MIME_TYPE)
            .putExtra(Intent.EXTRA_TITLE, input)
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Room holds the old database file open, so the swapped file only takes effect on a fresh process. */
private fun restartApp(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
