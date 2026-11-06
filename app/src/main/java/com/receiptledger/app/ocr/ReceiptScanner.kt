package com.receiptledger.app.ocr

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import android.net.Uri

@Composable
fun rememberReceiptScanner(
    onScanned: (List<Uri>) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val activity = context as Activity
    val scanner = remember {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(4)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(
                if (ReceiptScannerLogic.AUTO_CAPTURE) {
                    GmsDocumentScannerOptions.SCANNER_MODE_FULL
                } else {
                    GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER
                },
            )
            .build()
        GmsDocumentScanning.getClient(options)
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        val uris = scan?.pages?.map { it.imageUri }.orEmpty()
        if (uris.isEmpty()) {
            onError("No page came back from the scanner.")
        } else {
            onScanned(uris)
        }
    }
    return {
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { error ->
                onError(error.message ?: "Could not start the document scanner.")
            }
    }
}
