package com.eteditor

import com.eteditor.core.DocumentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolAvailabilityRuntimeModelUtilsTest {
    @Test
    fun toolIdsFollowDocumentKindAndEditorScope() {
        assertEquals(EPUB_TOOL_IDS, builtInToolIdsForDocumentKind(DocumentKind.Epub))
        assertEquals(TXT_TOOL_IDS, builtInToolIdsForDocumentKind(DocumentKind.Txt))
        assertEquals(emptySet<String>(), builtInToolIdsForDocumentKind(DocumentKind.None))
        assertEquals(EPUB_EDITOR_TOOL_IDS, editorToolIdsForDocumentKind(DocumentKind.Epub))
        assertEquals(TXT_EDITOR_TOOL_IDS, editorToolIdsForDocumentKind(DocumentKind.Txt))
    }

    @Test
    fun toolListsFilterAndOrderDefinitionsByAllowedDocumentTools() {
        val definitions = listOf(
            definition("text_replace", "替换"),
            definition("fetch_info", "抓取"),
            definition("generate_cover", "封面"),
            definition("unused", "不用")
        )
        val toolsById = definitions.associateBy { it.id }
        val editorTools = listOf(
            tool("preset-1", "替换方案", "text_replace"),
            tool("preset-2", "封面方案", "generate_cover"),
            tool("preset-3", "TXT 不支持", "unused")
        )

        assertEquals(
            listOf("text_replace" to "替换", "fetch_info" to "抓取", "generate_cover" to "封面"),
            editorToolFunctionOptionsForDocumentKind(DocumentKind.Epub, toolsById)
        )
        assertEquals(
            listOf("preset-1", "preset-2"),
            editorToolsForDocumentKind(DocumentKind.Epub, editorTools).map { it.id }
        )
        assertEquals(emptyList<EditorTool>(), editorToolsForDocumentKind(DocumentKind.Txt, editorTools))
        assertEquals(
            listOf("text_replace", "fetch_info", "generate_cover"),
            automationFunctionToolDefinitions(toolsById).map { it.id }
        )
        assertEquals(
            listOf("text_replace", "fetch_info", "generate_cover"),
            builtInToolsForDocumentKind(DocumentKind.Epub, toolsById).map { it.id }
        )
    }

    @Test
    fun toolAvailabilitySkipsMissingDefinitionsButKeepsAllowedOrder() {
        val toolsById = listOf(
            definition("generate_cover", "封面"),
            definition("text_replace", "替换"),
            definition("file_rename", "重命名")
        ).associateBy { it.id }

        assertEquals(
            listOf("text_replace" to "替换", "file_rename" to "重命名", "generate_cover" to "封面"),
            editorToolFunctionOptionsForDocumentKind(DocumentKind.Epub, toolsById)
        )
        assertEquals(
            listOf("text_replace", "file_rename", "generate_cover"),
            builtInToolsForDocumentKind(DocumentKind.Epub, toolsById).map { it.id }
        )
        assertEquals(
            listOf("text_replace", "file_rename", "generate_cover"),
            automationFunctionToolDefinitions(toolsById).map { it.id }
        )
    }

    @Test
    fun toolAvailabilityReturnsEmptyForNoneDocumentKind() {
        val toolsById = defaultEditorToolDefinitions().associateBy { it.id }
        val editorTools = listOf(
            tool("preset-1", "替换", "text_replace"),
            tool("preset-2", "抓取", "fetch_info")
        )

        assertEquals(emptySet<String>(), editorToolIdsForDocumentKind(DocumentKind.None))
        assertEquals(emptyList<Pair<String, String>>(), editorToolFunctionOptionsForDocumentKind(DocumentKind.None, toolsById))
        assertEquals(emptyList<ToolDefinition>(), builtInToolsForDocumentKind(DocumentKind.None, toolsById))
        assertEquals(emptyList<EditorTool>(), editorToolsForDocumentKind(DocumentKind.None, editorTools))
    }

    @Test
    fun epubAutomationPresetToolsFilterUnsupportedToolIds() {
        val tools = listOf(
            tool("preset-1", "替换", "text_replace"),
            tool("preset-2", "重命名", "file_rename"),
            tool("preset-3", "未知", "unused"),
            tool("preset-4", "空", "")
        )

        assertEquals(
            listOf("preset-1", "preset-2"),
            editorToolsForEpubAutomationModel(tools).map { it.id }
        )
    }

    @Test
    fun runtimeToolModelsPreferPresetButInlineBlankPresetSteps() {
        val preset = tool(
            id = "preset-1",
            name = "预设",
            toolId = "text_replace",
            parameterOverrides = mapOf("find" to "A")
        )
        val presetStep = AutomationStep(
            id = "step-1",
            name = "",
            toolId = "text_replace",
            parameterOverrides = mapOf("find" to "B"),
            presetId = "preset-1"
        )
        val inlineStep = AutomationStep(
            id = "step-2",
            name = "",
            toolId = "fetch_info",
            parameterOverrides = mapOf("query" to "Book")
        )

        assertEquals(preset, automationStepSourceToolModel(presetStep, listOf(preset), "替换"))
        assertEquals(
            preset.copy(id = "step-1"),
            automationStepToolForRunModel(presetStep, listOf(preset), "替换")
        )
        assertEquals(
            EditorTool(
                id = "step-2",
                name = "抓取",
                toolId = "fetch_info",
                parameterOverrides = mapOf("query" to "Book")
            ),
            automationStepSourceToolModel(inlineStep, listOf(preset), "抓取")
        )
        assertNull(
            automationStepSourceToolModel(
                presetStep.copy(presetId = "missing"),
                listOf(preset),
                "替换"
            )
        )
    }

    @Test
    fun runtimeToolModelsUsePresetFieldsEvenWhenStepFieldsDiffer() {
        val preset = EditorTool(
            id = "preset-1",
            name = "预设替换",
            group = "常用",
            toolId = "text_replace",
            parameterOverrides = mapOf("find" to "A")
        )
        val step = AutomationStep(
            id = "step-1",
            name = "步骤名称",
            toolId = "fetch_info",
            parameterOverrides = mapOf("query" to "Book"),
            presetId = "preset-1"
        )

        assertEquals(preset, automationStepSourceToolModel(step, listOf(preset), "抓取"))
        assertEquals(preset.copy(id = "step-1"), automationStepToolForRunModel(step, listOf(preset), "抓取"))
    }

    @Test
    fun runtimeToolModelsKeepExplicitInlineStepName() {
        val step = AutomationStep(
            id = "step-1",
            name = "自定义步骤",
            toolId = "text_replace",
            parameterOverrides = mapOf("find" to "A")
        )

        assertEquals(
            EditorTool(
                id = "step-1",
                name = "自定义步骤",
                toolId = "text_replace",
                parameterOverrides = mapOf("find" to "A")
            ),
            automationStepSourceToolModel(step, editorTools = emptyList(), toolLabel = "替换")
        )
    }

    @Test
    fun builtInEditorToolModelKeepsPlanIdentityAndOverrides() {
        assertEquals(
            EditorTool(
                id = "plan-1",
                name = "内置替换",
                toolId = "text_replace",
                parameterOverrides = mapOf("find" to "A")
            ),
            builtInEditorToolModel(
                toolId = "text_replace",
                planId = "plan-1",
                label = "内置替换",
                parameterOverrides = mapOf("find" to "A")
            )
        )
    }

    @Test
    fun mergedEditorToolParametersKeepsDefaultsAndEditableOverridesOnly() {
        val definition = definition(
            id = "fetch_info",
            title = "抓取",
            parameters = listOf(
                ToolParameterDefinition("query", "查询", "默认书名"),
                ToolParameterDefinition("content", "内容", FETCH_INFO_CONTENT_CATALOG)
            )
        )
        val tool = tool(
            id = "preset-1",
            name = "抓取方案",
            toolId = "fetch_info",
            parameterOverrides = mapOf(
                "query" to "导入书名",
                "unknown" to "旧参数",
                FETCH_INFO_PARAM_AUTH_COOKIE to "secret"
            )
        )

        assertEquals(
            mapOf(
                "query" to "导入书名",
                "content" to FETCH_INFO_CONTENT_CATALOG
            ),
            mergedEditorToolParameters(tool, mapOf("fetch_info" to definition))
        )
    }

    @Test
    fun mergedEditorToolParametersAllowsEditableBlankOverrideAndKeepsMissingDefaults() {
        val definition = definition(
            id = "fetch_info",
            title = "抓取",
            parameters = listOf(
                ToolParameterDefinition("query", "查询", "默认书名"),
                ToolParameterDefinition("content", "内容", FETCH_INFO_CONTENT_INTRO)
            )
        )
        val tool = tool(
            id = "preset-1",
            name = "抓取方案",
            toolId = "fetch_info",
            parameterOverrides = mapOf("query" to "")
        )

        assertEquals(
            mapOf(
                "query" to "",
                "content" to FETCH_INFO_CONTENT_INTRO
            ),
            mergedEditorToolParameters(tool, mapOf("fetch_info" to definition))
        )
    }

    @Test
    fun mergedEditorToolParametersDropsAllOverridesWhenDefinitionIsMissing() {
        val tool = tool(
            id = "preset-1",
            name = "旧方案",
            toolId = "missing_tool",
            parameterOverrides = mapOf("query" to "Book", "cookie" to "secret")
        )

        assertEquals(emptyMap<String, String>(), mergedEditorToolParameters(tool, emptyMap()))
    }

    private fun definition(id: String, title: String): ToolDefinition {
        return ToolDefinition(
            id = id,
            title = title,
            category = "工具",
            description = "",
            implemented = true
        )
    }

    private fun definition(
        id: String,
        title: String,
        parameters: List<ToolParameterDefinition>
    ): ToolDefinition {
        return ToolDefinition(
            id = id,
            title = title,
            category = "工具",
            description = "",
            implemented = true,
            parameters = parameters
        )
    }

    private fun tool(
        id: String,
        name: String,
        toolId: String,
        parameterOverrides: Map<String, String> = emptyMap()
    ): EditorTool {
        return EditorTool(
            id = id,
            name = name,
            toolId = toolId,
            parameterOverrides = parameterOverrides
        )
    }
}
