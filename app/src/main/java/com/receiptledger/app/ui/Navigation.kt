package com.receiptledger.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.receiptledger.app.AppContainer
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun ReceiptLedgerNav(container: AppContainer) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as Application

    NavHost(
        navController = nav,
        startDestination = "home",
        modifier = Modifier.fillMaxSize(),
    ) {
        composable("home") {
            val vm: HomeViewModel = viewModel(
                factory = HomeViewModel.factory(
                    container.repository,
                    container.settingsStore,
                    container.pdfExport,
                ),
            )
            HomeScreen(
                viewModel = vm,
                onScan = { uri ->
                    container.importQueue.clear()
                    val encoded = URLEncoder.encode(uri.toString(), StandardCharsets.UTF_8.toString())
                    nav.navigate("review?image=$encoded")
                },
                onOpen = { id -> nav.navigate("detail/$id") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable(
            route = REVIEW_ROUTE,
            arguments = listOf(
                navArgument("image") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("id") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val image = entry.arguments?.getString("image")?.takeIf { it.isNotBlank() }?.let {
                URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
            }
            val id = entry.arguments?.getString("id")?.takeIf { it.isNotBlank() }
            val vm: ReviewViewModel = viewModel(
                key = ReviewScreenLogic.sessionKey(id, image),
                factory = ReviewViewModel.factory(app, container, id, image),
            )
            key(ReviewScreenLogic.sessionKey(id, image)) {
            ReviewScreen(
                viewModel = vm,
                onDone = { savedId ->
                    val progress = container.importQueue.progress()
                    val next = container.importQueue.finishCurrent(saved = savedId != null)
                    val outcome = container.importQueue.outcome()
                    when (
                        DuplicateReceiptLogic.afterReview(
                            savedId = savedId,
                            hasNextImport = next != null,
                            importBatchSize = progress?.total ?: 0,
                        )
                    ) {
                        AfterReviewAction.CONTINUE_IMPORT -> next?.let { draft ->
                            val encoded = URLEncoder.encode(
                                Uri.fromFile(File(draft.imagePath)).toString(),
                                StandardCharsets.UTF_8.toString(),
                            )
                            nav.navigate("review?image=$encoded") {
                                popUpTo(REVIEW_ROUTE) { inclusive = true }
                            }
                        }
                        AfterReviewAction.FINISH_IMPORT -> {
                            nav.previousBackStackEntry?.savedStateHandle?.let { handle ->
                                handle[IMPORT_SUCCESS_COUNT] = outcome.saved
                                handle[IMPORT_SKIPPED_COUNT] = outcome.skipped
                            }
                            nav.popBackStack()
                        }
                        AfterReviewAction.OPEN_SAVED -> savedId?.let { id ->
                            nav.navigate("detail/$id") {
                                popUpTo("home")
                            }
                        }
                        AfterReviewAction.RETURN_HOME -> nav.popBackStack()
                    }
                },
                onBack = {
                    container.importQueue.clear()
                    nav.popBackStack()
                },
            )
            }
        }
        composable("detail/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            DetailScreen(
                receiptId = id,
                repository = container.repository,
                onEdit = {
                    container.importQueue.clear()
                    nav.navigate("review?id=$id")
                },
                onBack = { nav.popBackStack() },
                onDeleted = {
                    nav.popBackStack("home", inclusive = false)
                },
            )
        }
        composable("settings") { entry ->
            val importedCount by entry.savedStateHandle
                .getStateFlow(IMPORT_SUCCESS_COUNT, 0)
                .collectAsStateWithLifecycle()
            val skippedCount by entry.savedStateHandle
                .getStateFlow(IMPORT_SKIPPED_COUNT, 0)
                .collectAsStateWithLifecycle()
            val vm: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(
                    container.settingsStore,
                    container.repository,
                    container.pdfExport,
                    container.imageStore,
                    container.importQueue,
                    container.ocr,
                    container.backup,
                    container.dailyBackupScheduler,
                    app,
                ),
            )
            SettingsScreen(
                viewModel = vm,
                onBack = { nav.popBackStack() },
                onImport = { firstImageUri ->
                    val encoded = URLEncoder.encode(firstImageUri, StandardCharsets.UTF_8.toString())
                    nav.navigate("review?image=$encoded")
                },
                importedCount = importedCount,
                skippedCount = skippedCount,
                onImportedMessageShown = {
                    entry.savedStateHandle[IMPORT_SUCCESS_COUNT] = 0
                    entry.savedStateHandle[IMPORT_SKIPPED_COUNT] = 0
                },
            )
        }
    }
}

private const val IMPORT_SUCCESS_COUNT = "importSuccessCount"
private const val IMPORT_SKIPPED_COUNT = "importSkippedCount"
private const val REVIEW_ROUTE = "review?image={image}&id={id}"
