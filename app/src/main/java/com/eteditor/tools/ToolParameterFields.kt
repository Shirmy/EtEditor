package com.eteditor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal data class ToolParameterTitleFormatOptions(
    val useBottomActionForSelectedScope: Boolean = false,
    val chapterPickerRequestKey: Int = 0,
    val onSelectedChaptersConfirmed: () -> Unit = {}
)

internal data class ToolParameterInsertChapterOptions(
    val sourceFileContent: (@Composable (Modifier) -> Unit)? = null,
    val useBottomActionForUpload: Boolean = false
)

internal data class ToolParameterCoverOptions(
    val imageFileContent: (@Composable () -> Unit)? = null
)

@Composable
internal fun ToolParameterSection(
    controller: EditorController,
    toolId: String,
    valueFor: (ToolParameterDefinition) -> String,
    onValueChange: (ToolParameterDefinition, String) -> Unit,
    sectionTitle: String = toolParameterSectionTitle(toolId),
    titleFormatOptions: ToolParameterTitleFormatOptions = ToolParameterTitleFormatOptions(),
    insertChapterOptions: ToolParameterInsertChapterOptions = ToolParameterInsertChapterOptions(),
    coverOptions: ToolParameterCoverOptions = ToolParameterCoverOptions()
) {
    NativeFormSection(sectionTitle) {
        ToolParameterFields(
            controller = controller,
            toolId = toolId,
            valueFor = valueFor,
            onValueChange = onValueChange,
            titleFormatOptions = titleFormatOptions,
            insertChapterOptions = insertChapterOptions,
            coverOptions = coverOptions
        )
    }
}

@Composable
internal fun ToolParameterFields(
    controller: EditorController,
    toolId: String,
    valueFor: (ToolParameterDefinition) -> String,
    onValueChange: (ToolParameterDefinition, String) -> Unit,
    titleFormatOptions: ToolParameterTitleFormatOptions = ToolParameterTitleFormatOptions(),
    insertChapterOptions: ToolParameterInsertChapterOptions = ToolParameterInsertChapterOptions(),
    coverOptions: ToolParameterCoverOptions = ToolParameterCoverOptions()
) {
    when (toolId) {
        "file_rename" -> FileRenameParameterFields(
            controller = controller,
            toolId = toolId,
            valueFor = valueFor,
            onValueChange = onValueChange
        )
        "text_replace" -> TextReplaceParameterFields(
            controller = controller,
            toolId = toolId,
            valueFor = valueFor,
            onValueChange = onValueChange
        )
        "chapter_title_rename" -> TitleRenameParameterFields(
            controller = controller,
            toolId = toolId,
            valueFor = valueFor,
            onValueChange = onValueChange
        )
        "title_format" -> TitleFormatParameterFields(
            controller = controller,
            toolId = toolId,
            valueFor = valueFor,
            onValueChange = onValueChange,
            useBottomActionForSelectedScope = titleFormatOptions.useBottomActionForSelectedScope,
            chapterPickerRequestKey = titleFormatOptions.chapterPickerRequestKey,
            onSelectedChaptersConfirmed = titleFormatOptions.onSelectedChaptersConfirmed
        )
        "fetch_info" -> FetchInfoParameterFields(
            controller = controller,
            toolId = toolId,
            valueFor = valueFor,
            onValueChange = onValueChange
        )
        "insert_chapter" -> InsertChapterParameterFields(
            controller = controller,
            toolId = toolId,
            valueFor = valueFor,
            onValueChange = onValueChange,
            sourceFileContent = insertChapterOptions.sourceFileContent,
            useBottomActionForUpload = insertChapterOptions.useBottomActionForUpload
        )
        "generate_cover" -> CoverParameterFields(
            controller = controller,
            toolId = toolId,
            valueFor = valueFor,
            onValueChange = onValueChange,
            imageFileContent = coverOptions.imageFileContent
        )
        else -> GenericToolParameterFields(
            controller = controller,
            toolId = toolId,
            valueFor = valueFor,
            onValueChange = onValueChange
        )
    }
}

internal fun toolParameterSectionTitle(toolId: String): String {
    return if (toolId == "insert_chapter") "规则参数" else "普通参数"
}

@Composable
private fun GenericToolParameterFields(
    controller: EditorController,
    toolId: String,
    valueFor: (ToolParameterDefinition) -> String,
    onValueChange: (ToolParameterDefinition, String) -> Unit
) {
    val parameters = controller.toolParameterDefinitions(toolId)
    if (parameters.isEmpty()) {
        Text(
            text = "暂无可配置参数。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    parameters.forEach { parameter ->
        ToolParameterField(
            parameter = parameter,
            value = valueFor(parameter).ifBlank { parameter.defaultValue },
            onValueChange = { value -> onValueChange(parameter, value) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
