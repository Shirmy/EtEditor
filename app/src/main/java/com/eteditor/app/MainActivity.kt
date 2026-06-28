package com.eteditor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.eteditor.ui.theme.EtEditorTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enterImmersiveMode()
        setContent {
            EtEditorTheme {
                val controller = remember { EditorController(applicationContext) }
                DisposableEffect(controller) {
                    onDispose {
                        controller.dispose()
                    }
                }
                val scope = rememberCoroutineScope()

                val filePicker = rememberLauncherForActivityResult(OpenEditableDocument()) { uri ->
                    val pickedUri = uri ?: return@rememberLauncherForActivityResult
                    persistEditableUri(pickedUri)
                    controller.showStatusMessage("打开文件...")
                    scope.launch {
                        yieldToAppUiBeforeHeavyWork()
                        controller.openFile(pickedUri)
                    }
                }
                val configExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                    val pickedUri = uri ?: return@rememberLauncherForActivityResult
                    persistEditableUri(pickedUri)
                    controller.showStatusMessage("导出配置...")
                    scope.launch {
                        yieldToAppUiBeforeHeavyWork()
                        controller.exportConfigTo(pickedUri)
                    }
                }
                val configImport = rememberLauncherForActivityResult(OpenEditableDocument()) { uri ->
                    val pickedUri = uri ?: return@rememberLauncherForActivityResult
                    persistEditableUri(pickedUri)
                    controller.showStatusMessage("导入配置...")
                    scope.launch {
                        yieldToAppUiBeforeHeavyWork()
                        controller.importConfigFrom(pickedUri)
                    }
                }
                var pendingTextReplaceRuleFile by remember { mutableStateOf<((String) -> Unit)?>(null) }
                var textReplaceRuleFileMessage by remember { mutableStateOf<String?>(null) }
                var pendingTxtMoveSyncAction by remember { mutableStateOf<PendingTxtMoveSyncAction?>(null) }
                var showUnsavedExitDialog by remember { mutableStateOf(false) }
                val textReplaceRuleFilePicker = rememberLauncherForActivityResult(OpenReplacementRuleDocument()) { uri ->
                    if (uri == null) {
                        pendingTextReplaceRuleFile = null
                        return@rememberLauncherForActivityResult
                    }
                    val onPicked = pendingTextReplaceRuleFile
                    persistEditableUri(uri)
                    pendingTextReplaceRuleFile = null
                    if (onPicked == null) return@rememberLauncherForActivityResult
                    controller.showStatusMessage("加载规则文件...")
                    scope.launch {
                        yieldToAppUiBeforeHeavyWork()
                        onPicked(uri.toString())
                    }
                }

                fun finishWithUnsavedCheck() {
                    if (controller.hasUnsavedChanges) {
                        showUnsavedExitDialog = true
                    } else {
                        finish()
                    }
                }

                fun performPendingTxtMoveAction(action: PendingTxtMoveSyncAction) {
                    when (action) {
                        PendingTxtMoveSyncAction.OpenFile -> filePicker.launch(arrayOf("application/epub+zip", "text/plain", "application/octet-stream"))
                        PendingTxtMoveSyncAction.Save -> scope.launch { controller.saveToOriginal() }
                        PendingTxtMoveSyncAction.Exit -> finishWithUnsavedCheck()
                    }
                }

                fun requestExit() {
                    if (controller.txtMoveChapterSyncPending) {
                        pendingTxtMoveSyncAction = PendingTxtMoveSyncAction.Exit
                    } else {
                        finishWithUnsavedCheck()
                    }
                }

                fun requestTxtMoveSafeAction(action: PendingTxtMoveSyncAction) {
                    if (action.waitBeforeAction && controller.txtMoveChapterSyncPending) {
                        pendingTxtMoveSyncAction = action
                    } else {
                        performPendingTxtMoveAction(action)
                    }
                }

                BackHandler(enabled = controller.txtMoveChapterSyncPending || controller.hasUnsavedChanges) {
                    requestExit()
                }
                EtEditorApp(
                    controller = controller,
                    onOpenFile = {
                        requestTxtMoveSafeAction(PendingTxtMoveSyncAction.OpenFile)
                    },
                    onSave = { requestTxtMoveSafeAction(PendingTxtMoveSyncAction.Save) },
                    onExportConfig = {
                        configExport.launch(controller.configExportFileName())
                    },
                    onImportConfig = {
                        configImport.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    },
                    onExit = { requestExit() },
                    onPickTextReplaceRuleFile = { onPicked ->
                        pendingTextReplaceRuleFile = onPicked
                        textReplaceRuleFilePicker.launch(Unit)
                    }
                )
                textReplaceRuleFileMessage?.let { message ->
                    ToolRunMessageDialog(
                        title = "规则文件",
                        message = message,
                        onDismiss = { textReplaceRuleFileMessage = null }
                    )
                }
                controller.txtMoveChapterSyncWarningMessage?.let { message ->
                    ToolRunMessageDialog(
                        title = "章节移动未完成",
                        message = message,
                        onDismiss = { controller.dismissTxtMoveChapterSyncWarning() }
                    )
                }
                pendingTxtMoveSyncAction?.let { action ->
                    TxtMoveSyncConfirmDialog(
                        action = action,
                        onConfirm = {
                            pendingTxtMoveSyncAction = null
                            scope.launch {
                                if (controller.awaitTxtMoveChapterSyncBefore(action.label)) {
                                    performPendingTxtMoveAction(action)
                                }
                            }
                        },
                        onDismiss = { pendingTxtMoveSyncAction = null }
                    )
                }
                if (showUnsavedExitDialog) {
                    UnsavedExitConfirmDialog(
                        busy = controller.busy,
                        onExitWithoutSave = {
                            showUnsavedExitDialog = false
                            finish()
                        },
                        onSaveAndExit = {
                            scope.launch {
                                controller.saveToOriginal()
                                if (!controller.hasUnsavedChanges) {
                                    showUnsavedExitDialog = false
                                    finish()
                                }
                            }
                        },
                        onDismiss = { showUnsavedExitDialog = false }
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun persistEditableUri(uri: Uri) {
        pruneOldPersistedUriPermissions(applicationContext, uri)
        val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { contentResolver.takePersistableUriPermission(uri, readWrite) }
            .recoverCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}

@Composable
private fun UnsavedExitConfirmDialog(
    busy: Boolean,
    onExitWithoutSave: () -> Unit,
    onSaveAndExit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .adaptiveDialogWidth(AdaptiveDialogWidth.Compact)
            .dialogBorder(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = "未保存修改",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = "当前文件有未保存修改，可以不保存退出，或保存后退出",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onExitWithoutSave,
                    enabled = !busy,
                    shape = ControlShape,
                    contentPadding = CompactButtonPadding
                ) {
                    Text("不保存退出")
                }
                Button(
                    onClick = onSaveAndExit,
                    enabled = !busy,
                    shape = ControlShape,
                    contentPadding = CompactButtonPadding
                ) {
                    Text("保存退出")
                }
            }
        },
        shape = PreviewShape,
        containerColor = MaterialTheme.colorScheme.surface
    )
}
