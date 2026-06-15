package com.eteditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ConfiguredToolEditorFields(
    controller: EditorController,
    tool: EditorTool,
    baseTool: ToolDefinition
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NativeFormSection("预设信息") {
            ToolTextInputField(
                value = tool.name,
                onValueChange = controller::renameEditorTool,
                label = "预设名字",
                modifier = Modifier.fillMaxWidth()
            )
        }
        ToolParameterSection(
            controller = controller,
            toolId = baseTool.id,
            valueFor = { parameter -> controller.editorToolParameterValue(tool, parameter.key) },
            onValueChange = { parameter, value ->
                if (parameter.key == INSERT_CHAPTER_PARAM_SOURCE_TYPE) {
                    controller.clearInsertChapterSourcePreview()
                }
                controller.updateEditorToolParameter(parameter.key, value)
            },
            coverOptions = ToolParameterCoverOptions(imageFileContent = {})
        )
    }
}
