package com.eteditor

fun EditorController.updateTextReplacePreset(
    toolId: String,
    name: String,
    find: String,
    replace: String,
    regex: Boolean
): Boolean {
    val cleanName = name.trim()
    if (cleanName.isBlank()) {
        statusMessage = "请输入预设名字"
        return false
    }
    if (find.isEmpty()) {
        statusMessage = "请输入查找内容"
        return false
    }
    val current = editorTools.firstOrNull { it.id == toolId && it.toolId == "text_replace" } ?: run {
        statusMessage = "预设不存在"
        return false
    }
    val nextOverrides = current.parameterOverrides.toMutableMap()
    nextOverrides[TEXT_REPLACE_PARAM_FIND] = find
    nextOverrides[TEXT_REPLACE_PARAM_REPLACE] = replace
    nextOverrides[TEXT_REPLACE_PARAM_FIND_REGEX] = regex.toString()
    val updateResult = updateEditorToolById(editorTools, toolId) { tool ->
        tool.copy(
            name = cleanName,
            parameterOverrides = cleanParameterOverrides("text_replace", nextOverrides)
        )
    } ?: return false
    editorTools = updateResult.tools
    if (draftEditorTool?.id == toolId) {
        draftEditorTool = updateResult.updatedTool.copy()
    }
    persistEditorTools()
    statusMessage = "预设已更新：$cleanName"
    return true
}

fun EditorController.saveTxtTextReplacePreset(name: String): Boolean {
    val cleanName = name.trim()
    if (cleanName.isBlank()) {
        statusMessage = "\u8bf7\u8f93\u5165\u9884\u8bbe\u540d\u5b57"
        return false
    }
    val find = builtInToolParameterValue("text_replace", TEXT_REPLACE_PARAM_FIND)
    val replace = builtInToolParameterValue("text_replace", TEXT_REPLACE_PARAM_REPLACE)
    val regex = builtInToolParameterValue("text_replace", TEXT_REPLACE_PARAM_FIND_REGEX) == BOOL_TRUE
    val scope = builtInToolParameterValue("text_replace", TEXT_REPLACE_PARAM_SCOPE)
    if (find.isEmpty()) {
        statusMessage = "\u8bf7\u8f93\u5165\u67e5\u627e\u5185\u5bb9"
        return false
    }
    val previousPresets = txtTextReplacePresets
    val previousNextNumber = nextTxtTextReplacePresetNumber
    val addResult = appendNumberedEditorTool(
        tools = txtTextReplacePresets,
        idPrefix = "txt-search-",
        nextNumber = nextTxtTextReplacePresetNumber,
        name = cleanName,
        toolId = "text_replace",
        parameterOverrides = cleanTxtTextReplacePresetOverrides(find, replace, regex, scope)
    )
    nextTxtTextReplacePresetNumber = addResult.nextNumber
    txtTextReplacePresets = addResult.tools
    val saved = addResult.addedTool
    if (!persistTxtTextReplacePresets()) {
        txtTextReplacePresets = previousPresets
        nextTxtTextReplacePresetNumber = previousNextNumber
        statusMessage = "\u641c\u7d22\u9884\u8bbe\u4fdd\u5b58\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
        return false
    }
    statusMessage = "\u5df2\u4fdd\u5b58\u641c\u7d22\u9884\u8bbe\uff1a${saved.name}"
    return true
}

fun EditorController.updateTxtTextReplacePreset(
    presetId: String,
    name: String,
    find: String,
    replace: String,
    regex: Boolean,
    scope: String
): Boolean {
    val cleanName = name.trim()
    if (cleanName.isBlank()) {
        statusMessage = "\u8bf7\u8f93\u5165\u9884\u8bbe\u540d\u5b57"
        return false
    }
    if (find.isEmpty()) {
        statusMessage = "\u8bf7\u8f93\u5165\u67e5\u627e\u5185\u5bb9"
        return false
    }
    if (txtTextReplacePresets.none { it.id == presetId }) {
        statusMessage = "\u9884\u8bbe\u4e0d\u5b58\u5728"
        return false
    }
    val previousPresets = txtTextReplacePresets
    val updateResult = updateEditorToolById(txtTextReplacePresets, presetId) { preset ->
        preset.copy(
            name = cleanName,
            parameterOverrides = cleanTxtTextReplacePresetOverrides(find, replace, regex, scope)
        )
    } ?: return false
    txtTextReplacePresets = updateResult.tools
    if (!persistTxtTextReplacePresets()) {
        txtTextReplacePresets = previousPresets
        statusMessage = "\u641c\u7d22\u9884\u8bbe\u4fdd\u5b58\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
        return false
    }
    statusMessage = "\u641c\u7d22\u9884\u8bbe\u5df2\u66f4\u65b0\uff1a$cleanName"
    return true
}

fun EditorController.deleteTxtTextReplacePreset(presetId: String): Boolean {
    val previousPresets = txtTextReplacePresets
    txtTextReplacePresets = deleteEditorToolById(txtTextReplacePresets, presetId) ?: return false
    if (!persistTxtTextReplacePresets()) {
        txtTextReplacePresets = previousPresets
        statusMessage = "\u641c\u7d22\u9884\u8bbe\u4fdd\u5b58\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
        return false
    }
    statusMessage = "\u641c\u7d22\u9884\u8bbe\u5df2\u5220\u9664"
    return true
}

fun EditorController.moveTxtTextReplacePreset(fromPresetId: String, toPresetId: String): Boolean {
    val previousPresets = txtTextReplacePresets
    txtTextReplacePresets = moveEditorToolById(txtTextReplacePresets, fromPresetId, toPresetId) ?: return false
    if (!persistTxtTextReplacePresets()) {
        txtTextReplacePresets = previousPresets
        statusMessage = "\u641c\u7d22\u9884\u8bbe\u4fdd\u5b58\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
        return false
    }
    return true
}

fun EditorController.saveEpubTextReplacePreset(name: String): Boolean {
    val cleanName = name.trim()
    if (cleanName.isBlank()) {
        statusMessage = "\u8bf7\u8f93\u5165\u9884\u8bbe\u540d\u5b57"
        return false
    }
    val mode = builtInToolParameterValue("text_replace", TEXT_REPLACE_PARAM_MODE)
        .ifBlank { TEXT_REPLACE_MODE_SINGLE }
    if (mode != TEXT_REPLACE_MODE_SINGLE) {
        statusMessage = "EPUB \u641c\u7d22\u9884\u8bbe\u4ec5\u652f\u6301\u5355\u6761\u6a21\u5f0f"
        return false
    }
    val find = builtInToolParameterValue("text_replace", TEXT_REPLACE_PARAM_FIND)
    val replace = builtInToolParameterValue("text_replace", TEXT_REPLACE_PARAM_REPLACE)
    val regex = builtInToolParameterValue("text_replace", TEXT_REPLACE_PARAM_FIND_REGEX) == BOOL_TRUE
    val scope = builtInToolParameterValue("text_replace", TEXT_REPLACE_PARAM_SCOPE)
        .takeIf { it == TOOL_SCOPE_ALL || it == TOOL_SCOPE_CURRENT }
        ?: TOOL_SCOPE_ALL
    val target = builtInToolParameterValue("text_replace", TEXT_REPLACE_PARAM_TARGET)
        .takeIf { it == TEXT_REPLACE_TARGET_VISIBLE || it == TEXT_REPLACE_TARGET_SOURCE }
        ?: TEXT_REPLACE_TARGET_SOURCE
    if (find.isEmpty()) {
        statusMessage = "\u8bf7\u8f93\u5165\u67e5\u627e\u5185\u5bb9"
        return false
    }
    val previousPresets = epubTextReplacePresets
    val previousNextNumber = nextEpubTextReplacePresetNumber
    val addResult = appendNumberedEditorTool(
        tools = epubTextReplacePresets,
        idPrefix = "epub-search-",
        nextNumber = nextEpubTextReplacePresetNumber,
        name = cleanName,
        toolId = "text_replace",
        parameterOverrides = cleanEpubTextReplacePresetOverrides(find, replace, regex, scope, target)
    )
    nextEpubTextReplacePresetNumber = addResult.nextNumber
    epubTextReplacePresets = addResult.tools
    val saved = addResult.addedTool
    if (!persistEpubTextReplacePresets()) {
        epubTextReplacePresets = previousPresets
        nextEpubTextReplacePresetNumber = previousNextNumber
        statusMessage = "EPUB \u641c\u7d22\u9884\u8bbe\u4fdd\u5b58\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
        return false
    }
    statusMessage = "\u5df2\u4fdd\u5b58 EPUB \u641c\u7d22\u9884\u8bbe\uff1a${saved.name}"
    return true
}

fun EditorController.updateEpubTextReplacePreset(
    presetId: String,
    name: String,
    find: String,
    replace: String,
    regex: Boolean,
    scope: String,
    target: String
): Boolean {
    val cleanName = name.trim()
    if (cleanName.isBlank()) {
        statusMessage = "\u8bf7\u8f93\u5165\u9884\u8bbe\u540d\u5b57"
        return false
    }
    if (find.isEmpty()) {
        statusMessage = "\u8bf7\u8f93\u5165\u67e5\u627e\u5185\u5bb9"
        return false
    }
    if (epubTextReplacePresets.none { it.id == presetId }) {
        statusMessage = "\u9884\u8bbe\u4e0d\u5b58\u5728"
        return false
    }
    val cleanTarget = target
        .takeIf { it == TEXT_REPLACE_TARGET_VISIBLE || it == TEXT_REPLACE_TARGET_SOURCE }
        ?: TEXT_REPLACE_TARGET_SOURCE
    val cleanScope = scope
        .takeIf { it == TOOL_SCOPE_ALL || it == TOOL_SCOPE_CURRENT }
        ?: TOOL_SCOPE_ALL
    val previousPresets = epubTextReplacePresets
    val updateResult = updateEditorToolById(epubTextReplacePresets, presetId) { preset ->
        preset.copy(
            name = cleanName,
            parameterOverrides = cleanEpubTextReplacePresetOverrides(find, replace, regex, cleanScope, cleanTarget)
        )
    } ?: return false
    epubTextReplacePresets = updateResult.tools
    if (!persistEpubTextReplacePresets()) {
        epubTextReplacePresets = previousPresets
        statusMessage = "EPUB \u641c\u7d22\u9884\u8bbe\u4fdd\u5b58\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
        return false
    }
    statusMessage = "EPUB \u641c\u7d22\u9884\u8bbe\u5df2\u66f4\u65b0\uff1a$cleanName"
    return true
}

fun EditorController.deleteEpubTextReplacePreset(presetId: String): Boolean {
    val previousPresets = epubTextReplacePresets
    epubTextReplacePresets = deleteEditorToolById(epubTextReplacePresets, presetId) ?: return false
    if (!persistEpubTextReplacePresets()) {
        epubTextReplacePresets = previousPresets
        statusMessage = "EPUB \u641c\u7d22\u9884\u8bbe\u4fdd\u5b58\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
        return false
    }
    statusMessage = "EPUB \u641c\u7d22\u9884\u8bbe\u5df2\u5220\u9664"
    return true
}

fun EditorController.moveEpubTextReplacePreset(fromPresetId: String, toPresetId: String): Boolean {
    val previousPresets = epubTextReplacePresets
    epubTextReplacePresets = moveEditorToolById(epubTextReplacePresets, fromPresetId, toPresetId) ?: return false
    if (!persistEpubTextReplacePresets()) {
        epubTextReplacePresets = previousPresets
        statusMessage = "EPUB \u641c\u7d22\u9884\u8bbe\u4fdd\u5b58\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
        return false
    }
    return true
}
