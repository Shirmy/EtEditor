package com.eteditor

import com.eteditor.core.DocumentKind

fun EditorController.runEditorTool(editorToolId: String, manual: Boolean = false): Boolean {
    val tool = editorTools.firstOrNull { it.id == editorToolId } ?: return false
    return runConfiguredTool(tool, manual)
}

internal fun EditorController.runConfiguredTool(tool: EditorTool, manual: Boolean = false): Boolean {
    if (!this.canRunToolInCurrentDocument(tool.toolId)) {
        statusMessage = "${toolLabel(tool.toolId)} \u4e0d\u652f\u6301\u5f53\u524d\u6587\u6863"
        return false
    }
    if (tool.toolId == "file_rename") {
        return runFileRenameTool(tool, manual)
    }
    if (tool.toolId == "text_replace") {
        return runTextReplaceTool(tool, manual)
    }
    if (tool.toolId == "chapter_title_rename") {
        return runTitleRenameTool(tool, manual)
    }
    if (tool.toolId == "title_format") {
        return runTitleFormatTool(tool, manual)
    }
    if (tool.toolId == "generate_cover") {
        statusMessage = needsConfirmationMessage()
        return false
    }
    if (tool.toolId == "fetch_info") {
        statusMessage = needsConfirmationMessage()
        return false
    }
    if (tool.toolId == "insert_chapter") {
        statusMessage = needsConfirmationMessage()
        return false
    }
    return runTool(tool.toolId)
}

internal suspend fun EditorController.runConfiguredToolAsync(tool: EditorTool, manual: Boolean = false): Boolean {
    if (!this.canRunToolInCurrentDocument(tool.toolId)) {
        statusMessage = "${toolLabel(tool.toolId)} 不支持当前文档"
        return false
    }
    if (tool.toolId == "text_replace") {
        return runTextReplaceToolAsync(tool, manual)
    }
    if (tool.toolId == "generate_cover") {
        return runCoverToolAsync(tool)
    }
    return runConfiguredTool(tool, manual)
}

fun EditorController.runBuiltInTool(toolId: String, manual: Boolean = true): Boolean {
    return runConfiguredTool(builtInEditorTool(toolId), manual)
}

fun EditorController.runTool(toolId: String): Boolean {
    if (busy || kind == DocumentKind.None) return false
    if (!this.canRunToolInCurrentDocument(toolId)) {
        statusMessage = "${toolLabel(toolId)} \u4e0d\u652f\u6301\u5f53\u524d\u6587\u6863"
        return false
    }
    return when (toolId) {
        "chapter_title_rename" -> {
            runTitleRenameTool(builtInEditorTool(toolId), manual = false)
        }
        "file_rename" -> {
            if (kind != DocumentKind.Epub || chapters.isEmpty()) return false
            runFileRenameTool(builtInEditorTool(toolId), manual = false)
        }
        "text_replace" -> {
            runTextReplaceTool(builtInEditorTool(toolId), manual = false)
        }
        "title_format" -> {
            runTitleFormatTool(builtInEditorTool(toolId), manual = false)
        }
        "generate_cover" -> {
            statusMessage = needsConfirmationMessage()
            false
        }
        "fetch_info" -> {
            statusMessage = needsConfirmationMessage()
            false
        }
        "insert_chapter" -> {
            statusMessage = needsConfirmationMessage()
            false
        }
        else -> {
            statusMessage = "${toolLabel(toolId)} \u7684\u6846\u67b6\u5df2\u5c31\u7eea\uff0c\u5177\u4f53\u529f\u80fd\u5f85\u5b9e\u73b0"
            appendAutomationLog(statusMessage)
            false
        }
    }
}

fun EditorController.applyPreparedTextReplace(toolId: String): Boolean {
    if (textSearchToolId != toolId || textSearchResults.isEmpty()) {
        statusMessage = "\u6ca1\u6709\u53ef\u6267\u884c\u7684\u66ff\u6362\u9884\u89c8"
        return false
    }
    statusMessage = ""
    val tool = if (toolId == builtInToolPlanId("text_replace")) {
        builtInEditorTool("text_replace")
    } else {
        editorTools.firstOrNull { it.id == toolId }
            ?: automationStepToolForPreview(toolId)
            ?: return false
    }
    val parameters = textReplaceParameters(tool).copy(preview = false)
    if (parameters.isReplacementMode()) {
        statusMessage = "\u8bf7\u5728 .replacement \u5206\u7ec4\u9884\u89c8\u4e2d\u6267\u884c"
        return false
    }
    val rules = textReplaceRules(parameters) ?: return false
    applyDeferredTxtTextReplacementRefresh()
    val result = try {
        replaceWithParameters(parameters, rules)
    } catch (error: IllegalArgumentException) {
        statusMessage = textReplaceRegexErrorMessage(error)
        return false
    }
    if (result.replacements <= 0) {
        statusMessage = textReplaceNoMatchMessage(kind, statusMessage, parameters, rules)
        return false
    }
    checkReport = null
    markDocumentChanged()
    clearTextSearchState()
    refreshChapters()
    statusMessage = "\u66ff\u6362\u5b8c\u6210\uff1a${result.filesChanged} \u4e2a\u6587\u4ef6\uff0c${result.replacements} \u5904"
    return true
}
