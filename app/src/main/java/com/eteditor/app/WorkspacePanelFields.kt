package com.eteditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eteditor.core.DocumentKind

@Composable
internal fun FilePanel(
    controller: EditorController,
    onOpenFile: () -> Unit,
    onSave: () -> Unit,
    onOpenAutomation: (() -> Unit)? = null,
    automationOpen: Boolean = false,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    directoryOpen: Boolean = false,
    onToggleDirectory: (() -> Unit)? = null
) {
    var bodyEditing by remember { mutableStateOf(false) }
    LaunchedEffect(controller.documentSessionKey) {
        bodyEditing = false
    }
    if (controller.kind == DocumentKind.None) {
        EmptyFileHome(
            onOpenFile = onOpenFile,
            busy = controller.busy,
            modifier = modifier.fillMaxSize()
        )
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(if (compact) 0.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        DocumentSummary(
            controller = controller,
            documentSessionKey = controller.documentSessionKey,
            directoryOpen = directoryOpen,
            onToggleDirectory = onToggleDirectory,
            onOpenFile = onOpenFile,
            onSave = onSave,
            onOpenAutomation = onOpenAutomation,
            automationOpen = automationOpen,
            modifier = Modifier.height(WorkspaceHeaderHeight)
        )
        val saveProgress = controller.saveProgress
        val epubWordCountProgress = controller.epubWordCountProgress
        val bodyOperationProgress = controller.bodyOperationProgress
        val showBodyOperationProgress = bodyOperationProgress != null &&
            bodyProgressBelongsToFilePanel(controller.bodyOperationProgressText)
        val hasFeatureOperationProgress = bodyOperationProgress != null && !showBodyOperationProgress
        if (saveProgress != null) {
            SaveProgressIndicator(
                text = controller.saveProgressText.ifBlank { "保存中" },
                progress = saveProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        } else if (bodyOperationProgress != null && showBodyOperationProgress) {
            SaveProgressIndicator(
                text = controller.bodyOperationProgressText.ifBlank { "正文处理中" },
                progress = bodyOperationProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        } else if (epubWordCountProgress != null && !hasFeatureOperationProgress) {
            SaveProgressIndicator(
                text = controller.epubWordCountProgressText.ifBlank { "字数计算中" },
                progress = epubWordCountProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        } else if ((controller.busy || controller.txtCatalogParsing) && !hasFeatureOperationProgress) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(4.dp))
        BodyPreview(
            controller = controller,
            editing = bodyEditing,
            onEditingChange = { bodyEditing = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
    if (controller.saveFailureMessage.isNotBlank()) {
        ToolRunMessageDialog(
            title = "保存失败",
            message = controller.saveFailureMessage,
            onDismiss = { controller.clearSaveFailureMessage() }
        )
    }
}

private fun bodyProgressBelongsToFilePanel(text: String): Boolean {
    if (text.isBlank()) return true
    return text.startsWith("切换章节") ||
        text.startsWith("EPUB 分卷") ||
        text.startsWith("EPUB 分章") ||
        text.startsWith("TXT 分章")
}
