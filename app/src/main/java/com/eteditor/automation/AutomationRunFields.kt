package com.eteditor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun AutomationRunView(
    controller: EditorController,
    selectedChain: AutomationChain,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onPickTextReplaceRuleFile: TextReplaceRuleFilePicker
) {
    val confirmationRequest = controller.automationConfirmationRequest
    val confirmationState = confirmationRequest?.let(controller::automationConfirmationState)
    val pendingPreviewToolId = controller.automationPendingPreviewToolId
    val pendingPreviewDataId = controller.automationPendingPreviewDataId ?: pendingPreviewToolId
    val pendingPreviewLabel = controller.automationPendingPreviewLabel
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uploadSourcePicker = rememberLauncherForActivityResult(OpenEditableDocument()) { uri ->
        val pickedUri = uri ?: return@rememberLauncherForActivityResult
        val fileError = insertChapterSourceFileError(context, pickedUri)
        if (fileError != null) {
            controller.showStatusMessage(fileError)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            yieldToAppUiBeforeHeavyWork()
            controller.prepareInsertChapterUploadForAutomationConfirmation(pickedUri.toString())
        }
    }
    val uploadImagePicker = rememberLauncherForActivityResult(OpenImageDocument()) { uri ->
        val pickedUri = uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            yieldToAppUiBeforeHeavyWork()
            controller.prepareCoverImageUploadForAutomationConfirmation(pickedUri.toString())
        }
    }
    val textReplaceConfirmationRequest = confirmationRequest
        ?.takeIf { request ->
            request.toolId == "text_replace" &&
                confirmationState == AutomationRunStepState.NeedsConfirmation
        }
    if (textReplaceConfirmationRequest != null) {
        val previewReady = (
            controller.textSearchToolId == textReplaceConfirmationRequest.stepId &&
                controller.textSearchResults.isNotEmpty()
            ) || controller.replacementFilePreview?.toolId == textReplaceConfirmationRequest.stepId
        if (previewReady) {
            TextSearchResultsPane(
                controller = controller,
                toolId = textReplaceConfirmationRequest.stepId,
                onDismiss = { controller.cancelAutomationConfirmation() },
                onApplied = {
                    controller.controllerScope.launch {
                        controller.continueSelectedAutomationChainAfterConfirmation()
                    }
                },
                modifier = modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 8.dp)
            )
        } else {
            AutomationConfirmationFallback(
                request = textReplaceConfirmationRequest,
                message = "没有可用的替换预览",
                onDismiss = { controller.cancelAutomationConfirmation() },
                modifier = modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 8.dp)
            )
        }
        return
    }
    val fullPageConfirmationRequest = confirmationRequest
        ?.takeIf { request ->
            request.toolId != "text_replace" &&
                request.toolId != "fetch_info" &&
                request.toolId != "insert_chapter" &&
                confirmationState == AutomationRunStepState.NeedsConfirmation
        }
    if (fullPageConfirmationRequest != null) {
        AutomationConfirmationPreviewPane(
            controller = controller,
            request = fullPageConfirmationRequest,
            modifier = modifier
                .fillMaxSize()
                .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 8.dp)
        )
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "返回", modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = selectedChain.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        val uploadAction = if (confirmationRequest != null && confirmationState == AutomationRunStepState.NeedsUpload) {
            {
                if (confirmationRequest.toolId == "text_replace") {
                    onPickTextReplaceRuleFile { uri ->
                        scope.launch {
                            yieldToAppUiBeforeHeavyWork()
                            controller.prepareTextReplaceRuleFileUploadForAutomationConfirmation(uri)
                        }
                    }
                } else if (confirmationRequest.toolId == "generate_cover") {
                    uploadImagePicker.launch(Unit)
                } else {
                    uploadSourcePicker.launch(INSERT_CHAPTER_SOURCE_MIME_TYPES)
                }
            }
        } else {
            null
        }
        val pendingPreviewVisible = confirmationRequest == null &&
            pendingPreviewToolId != null &&
            pendingPreviewLabel != null &&
            pendingPreviewToolId != "text_replace"
        if (!pendingPreviewVisible) {
            AutomationRunSequence(
                controller = controller,
                selectedChain = selectedChain,
                uploadAction = uploadAction,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (pendingPreviewVisible) {
            AutomationPendingPreviewPane(
                controller = controller,
                toolId = pendingPreviewToolId.orEmpty(),
                previewId = pendingPreviewDataId.orEmpty(),
                label = pendingPreviewLabel.orEmpty(),
                modifier = Modifier.weight(1f)
            )
            AutomationLogView(
                controller = controller,
                fillAvailableHeight = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 112.dp, max = 168.dp)
            )
        } else {
            AutomationLogView(
                controller = controller,
                fillAvailableHeight = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }

    confirmationRequest
        ?.takeIf { request ->
            request.toolId == "insert_chapter" &&
                confirmationState == AutomationRunStepState.NeedsConfirmation
        }
        ?.let { request ->
            AutomationConfirmationPreviewPane(
                controller = controller,
                request = request
            )
        }
    confirmationRequest
        ?.takeIf { request ->
            request.toolId == "fetch_info" &&
                confirmationState == AutomationRunStepState.NeedsConfirmation
        }
        ?.let { request ->
            AutomationConfirmationPreviewPane(
                controller = controller,
                request = request
            )
        }
}
