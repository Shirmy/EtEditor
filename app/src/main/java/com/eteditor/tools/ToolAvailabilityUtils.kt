package com.eteditor

import com.eteditor.core.DocumentKind

internal val EPUB_TOOL_IDS = linkedSetOf(
    "text_replace",
    "file_rename",
    "title_format",
    "chapter_title_rename",
    "fetch_info",
    "insert_chapter",
    "generate_cover"
)

internal val TXT_TOOL_IDS = linkedSetOf(
    "text_replace"
)

internal val EPUB_EDITOR_TOOL_IDS = linkedSetOf(
    "text_replace",
    "file_rename",
    "title_format",
    "chapter_title_rename",
    "fetch_info",
    "insert_chapter",
    "generate_cover"
)

internal val TXT_EDITOR_TOOL_IDS = linkedSetOf<String>()

internal val EDITOR_TOOL_IDS = EPUB_EDITOR_TOOL_IDS + TXT_EDITOR_TOOL_IDS

internal fun builtInToolIdsForDocumentKind(documentKind: DocumentKind): Set<String> {
    return when (documentKind) {
        DocumentKind.Epub -> EPUB_TOOL_IDS
        DocumentKind.Txt -> TXT_TOOL_IDS
        DocumentKind.None -> emptySet()
    }
}

internal fun editorToolIdsForDocumentKind(documentKind: DocumentKind): Set<String> {
    return when (documentKind) {
        DocumentKind.Epub -> EPUB_EDITOR_TOOL_IDS
        DocumentKind.Txt -> TXT_EDITOR_TOOL_IDS
        DocumentKind.None -> emptySet()
    }
}

internal fun editorToolFunctionOptionsForDocumentKind(
    documentKind: DocumentKind,
    toolsById: Map<String, ToolDefinition>
): List<Pair<String, String>> {
    return editorToolIdsForDocumentKind(documentKind).mapNotNull { toolId ->
        toolsById[toolId]?.let { definition -> definition.id to definition.title }
    }
}

internal fun editorToolsForDocumentKind(
    documentKind: DocumentKind,
    tools: List<EditorTool>
): List<EditorTool> {
    val allowed = editorToolIdsForDocumentKind(documentKind)
    return tools.filter { it.toolId in allowed }
}

internal fun editorToolsForEpubAutomationModel(tools: List<EditorTool>): List<EditorTool> {
    return tools.filter { it.toolId in EPUB_EDITOR_TOOL_IDS }
}

internal fun automationFunctionToolDefinitions(toolsById: Map<String, ToolDefinition>): List<ToolDefinition> {
    return EPUB_EDITOR_TOOL_IDS.mapNotNull(toolsById::get)
}

internal fun builtInToolsForDocumentKind(
    documentKind: DocumentKind,
    toolsById: Map<String, ToolDefinition>
): List<ToolDefinition> {
    return builtInToolIdsForDocumentKind(documentKind).mapNotNull(toolsById::get)
}
