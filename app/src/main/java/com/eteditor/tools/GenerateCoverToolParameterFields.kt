package com.eteditor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eteditor.core.DocumentKind
import kotlinx.coroutines.launch

@Composable
internal fun GenerateCoverToolParameterPanel(
    controller: EditorController,
    tool: ToolDefinition,
    documentSessionKey: Int,
    resetKey: Int,
    startDirectRunFeedback: (String) -> Unit,
    onRunMessage: (Pair<String, String>) -> Unit
) {
    val scope = rememberCoroutineScope()
    val defaultCoverTitle = controller.defaultCoverTitle()
    val storedCoverMode = controller
        .builtInToolParameterValue(tool.id, COVER_PARAM_MODE)
        .ifBlank { COVER_MODE_INSERT }
    val storedCoverTitle = controller.builtInToolParameterValue(tool.id, COVER_PARAM_TITLE)
    val coverImageUri = controller.builtInToolParameterValue(tool.id, COVER_PARAM_IMAGE_URI)
    val storedCoverImageInsertType = controller
        .builtInToolParameterValue(tool.id, COVER_PARAM_IMAGE_INSERT_TYPE)
        .ifBlank { COVER_IMAGE_INSERT_NOTE }
    val storedCoverCompress = controller.builtInToolParameterValue(tool.id, COVER_PARAM_COMPRESS)
    val storedCoverPreview = controller.builtInToolParameterValue(tool.id, COVER_PARAM_PREVIEW)
    val coverMode = storedCoverMode
        .takeIf { value -> controller.coverModeOptions().any { it.first == value } }
        ?: COVER_MODE_INSERT
    val coverTitle = storedCoverTitle
    val coverCompress = storedCoverCompress.ifBlank { BOOL_TRUE } == BOOL_TRUE
    val coverImageInsertType = storedCoverImageInsertType
        .takeIf { value -> controller.coverImageInsertOptions().any { it.first == value } }
        ?: COVER_IMAGE_INSERT_NOTE
    var generating by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    var pickingImage by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    var writing by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    val coverPreviewEnabled = coverMode == COVER_MODE_GENERATE &&
        storedCoverPreview.ifBlank { BOOL_FALSE } == BOOL_TRUE
    var showCoverPreviewDialog by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    var loadedCoverImageUri by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<String?>(null) }
    var coverRunError by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<String?>(null) }
    var completedImageInfoLabel by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<String?>(null) }
    var completedImageModeKey by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<String?>(null) }
    val preview = controller.generatedCoverPreview
    val fileInputMode = coverMode == COVER_MODE_INSERT ||
        (coverMode == COVER_MODE_IMAGE_INSERT && coverImageInsertType == COVER_IMAGE_INSERT_CUSTOM)
    fun coverModeKey(): String = "$coverMode|$coverImageInsertType"

    LaunchedEffect(coverMode, coverImageInsertType) {
        if (completedImageModeKey != null && completedImageModeKey != coverModeKey()) {
            completedImageInfoLabel = null
            completedImageModeKey = null
        }
    }

    LaunchedEffect(coverPreviewEnabled) {
        if (!coverPreviewEnabled) {
            showCoverPreviewDialog = false
            controller.clearGeneratedCoverPreview()
        }
    }

    suspend fun prepareCoverPreviewForExecution(imageUriOverride: String? = null): Boolean {
        if (coverMode == COVER_MODE_IMAGE_INSERT) {
            coverRunError = "插入图片不需要预览"
            return false
        }
        if (coverMode == COVER_MODE_GENERATE) {
            val title = coverTitle.ifBlank { defaultCoverTitle }.trim()
            if (title.isBlank()) {
                coverRunError = "请输入封面标题"
                return false
            }
            if (preview?.title == title) return true
            generating = true
            val success = controller.generateCoverPreview(title)
            generating = false
            return success
        }
        val imageUri = imageUriOverride ?: coverImageUri.trim()
        if (imageUri.isBlank()) {
            coverRunError = "请选择封面图片"
            return false
        }
        if (preview != null && loadedCoverImageUri == imageUri) return true
        pickingImage = true
        val success = controller.prepareInsertedCoverPreview(Uri.parse(imageUri), coverCompress)
        pickingImage = false
        loadedCoverImageUri = if (success) imageUri else null
        return success
    }

    fun coverInputReady(): Boolean {
        return when (coverMode) {
            COVER_MODE_GENERATE -> coverTitle.ifBlank { defaultCoverTitle }.trim().isNotBlank()
            COVER_MODE_IMAGE_INSERT ->
                coverImageInsertType != COVER_IMAGE_INSERT_CUSTOM || coverImageUri.isNotBlank()
            else -> coverImageUri.isNotBlank()
        }
    }

    fun coverToolWithImageInput(imageUri: String): EditorTool {
        val overrides = controller.builtInEditorTool(tool.id).parameterOverrides.toMutableMap()
        overrides[COVER_PARAM_IMAGE_URI] = imageUri
        if (coverMode == COVER_MODE_IMAGE_INSERT) {
            overrides[COVER_PARAM_MODE] = COVER_MODE_IMAGE_INSERT
            overrides[COVER_PARAM_IMAGE_INSERT_TYPE] = COVER_IMAGE_INSERT_CUSTOM
        } else {
            overrides[COVER_PARAM_MODE] = COVER_MODE_INSERT
            overrides[COVER_PARAM_PREVIEW] = BOOL_FALSE
        }
        return controller.builtInEditorTool(tool.id).copy(parameterOverrides = overrides)
    }

    fun clearCoverImageInputState() {
        completedImageInfoLabel = null
        completedImageModeKey = null
        if (coverImageUri.isNotBlank()) {
            controller.updateBuiltInToolParameter(tool.id, COVER_PARAM_IMAGE_URI, "")
        }
    }

    fun runCoverAction(imageUriOverride: String? = null) {
        scope.launch {
            writing = true
            coverRunError = null
            val success = try {
                yieldToAppUiBeforeHeavyWork()
                if (coverPreviewEnabled && coverMode != COVER_MODE_IMAGE_INSERT) {
                    val prepared = prepareCoverPreviewForExecution(imageUriOverride)
                    if (prepared) {
                        showCoverPreviewDialog = true
                    }
                    prepared
                } else {
                    val runTool = imageUriOverride
                        ?.takeIf { it.isNotBlank() }
                        ?.let(::coverToolWithImageInput)
                        ?: controller.builtInEditorTool(tool.id)
                    controller.runConfiguredToolAsync(runTool, manual = true)
                }
            } catch (error: Throwable) {
                coverRunError = "图片处理失败：${error.message ?: error.javaClass.simpleName}"
                false
            } finally {
                writing = false
            }
            if (!success) {
                writing = false
                onRunMessage(tool.title to (coverRunError ?: controller.statusMessage.ifBlank { "图片处理失败" }))
            } else if (!coverPreviewEnabled || coverMode == COVER_MODE_IMAGE_INSERT) {
                imageUriOverride?.takeIf { it.isNotBlank() }?.let { uriText ->
                    completedImageInfoLabel = controller.coverImageInfoLabel(uriText)
                        .takeIf { it.isNotBlank() }
                        ?.let { "已完成 $it" }
                        ?: "已完成"
                    completedImageModeKey = coverModeKey()
                    if (coverImageUri.isNotBlank()) {
                        controller.updateBuiltInToolParameter(tool.id, COVER_PARAM_IMAGE_URI, "")
                    }
                }
                startDirectRunFeedback(tool.title)
            }
        }
    }

    val coverImagePicker = rememberLauncherForActivityResult(OpenImageDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val uriText = uri.toString()
        clearCoverImageInputState()
        if (coverMode == COVER_MODE_IMAGE_INSERT) {
            controller.updateBuiltInToolParameter(
                tool.id,
                COVER_PARAM_IMAGE_INSERT_TYPE,
                COVER_IMAGE_INSERT_CUSTOM
            )
        } else {
            controller.updateBuiltInToolParameter(tool.id, COVER_PARAM_MODE, COVER_MODE_INSERT)
            controller.updateBuiltInToolParameter(tool.id, COVER_PARAM_PREVIEW, BOOL_FALSE)
        }
        loadedCoverImageUri = null
        controller.clearGeneratedCoverPreview()
        if (fileInputMode) {
            runCoverAction(uriText)
        }
    }

    fun pickCoverImage() {
        coverImagePicker.launch(Unit)
    }

    fun updateCoverParameter(parameter: ToolParameterDefinition, value: String) {
        if (
            (parameter.key == COVER_PARAM_MODE && value != coverMode) ||
            (parameter.key == COVER_PARAM_IMAGE_INSERT_TYPE && value != coverImageInsertType) ||
            parameter.key == COVER_PARAM_IMAGE_URI
        ) {
            clearCoverImageInputState()
        }
        if (parameter.key in setOf(
                COVER_PARAM_MODE,
                COVER_PARAM_TITLE,
                COVER_PARAM_IMAGE_URI,
                COVER_PARAM_IMAGE_INSERT_TYPE,
                COVER_PARAM_COMPRESS
            )
        ) {
            loadedCoverImageUri = null
            controller.clearGeneratedCoverPreview()
        }
        if (
            (parameter.key == COVER_PARAM_MODE && value != COVER_MODE_GENERATE) ||
            (parameter.key == COVER_PARAM_PREVIEW && value != BOOL_TRUE)
        ) {
            showCoverPreviewDialog = false
            controller.clearGeneratedCoverPreview()
        }
        controller.updateBuiltInToolParameter(tool.id, parameter.key, value)
    }

    ToolParameterSection(
        controller = controller,
        toolId = tool.id,
        valueFor = { parameter -> controller.builtInToolParameterValue(tool.id, parameter.key) },
        onValueChange = ::updateCoverParameter,
        coverOptions = ToolParameterCoverOptions(
            imageFileContent = {}
        )
    )
    if (showCoverPreviewDialog) {
        GeneratedCoverPreviewDialog(
            preview = preview,
            onDismiss = {
                showCoverPreviewDialog = false
                controller.updateBuiltInToolParameter(tool.id, COVER_PARAM_PREVIEW, BOOL_FALSE)
                controller.clearGeneratedCoverPreview()
            },
            confirmEnabled = preview != null && !controller.busy && !generating && !pickingImage && !writing,
            onConfirm = {
                showCoverPreviewDialog = false
                scope.launch {
                    writing = true
                    coverRunError = null
                    val success = try {
                        yieldToAppUiBeforeHeavyWork()
                        controller.applyGeneratedCoverPreview()
                    } catch (error: Throwable) {
                        coverRunError = "图片处理失败：${error.message ?: error.javaClass.simpleName}"
                        false
                    } finally {
                        writing = false
                    }
                    if (success) {
                        controller.updateBuiltInToolParameter(tool.id, COVER_PARAM_PREVIEW, BOOL_FALSE)
                        startDirectRunFeedback(tool.title)
                    } else {
                        onRunMessage(tool.title to (coverRunError ?: controller.statusMessage.ifBlank { "图片处理失败" }))
                    }
                }
            }
        )
    }
    if (generating || pickingImage || writing) {
        ToolRunProgress(
            toolName = when {
                generating -> "生成中"
                pickingImage -> "读取中"
                else -> "写入中"
            },
            progress = when {
                generating -> 0.48f
                pickingImage -> 0.42f
                else -> 0.72f
            }
        )
    }
    completedImageInfoLabel
        ?.takeIf { completedImageModeKey == coverModeKey() && !generating && !pickingImage && !writing }
        ?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ButtonRow {
        Button(
            enabled = tool.implemented &&
                controller.kind == DocumentKind.Epub &&
                (fileInputMode || coverInputReady()) &&
                !controller.busy &&
                !generating &&
                !pickingImage &&
                !writing,
            onClick = {
                if (fileInputMode) {
                    pickCoverImage()
                } else {
                    runCoverAction()
                }
            },
            modifier = Modifier.weight(1f),
            shape = ControlShape,
            contentPadding = CompactButtonPadding
        ) {
            Icon(
                if (fileInputMode) Icons.Outlined.FolderOpen else Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(if (fileInputMode) "选择文件" else if (coverPreviewEnabled && coverMode != COVER_MODE_IMAGE_INSERT) "预览" else "执行")
        }
    }
}
