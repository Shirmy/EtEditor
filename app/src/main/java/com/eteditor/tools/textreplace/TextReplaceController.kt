package com.eteditor

import com.eteditor.core.DocumentKind
import com.eteditor.core.ReplaceResult
import com.eteditor.core.TxtDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

fun EditorController.applySelectedTextSearchResult(toolId: String): Boolean {
    if (textSearchToolId != toolId) {
        statusMessage = "没有选中的替换位置"
        return false
    }
    val selectedResultIndex = selectedTextSearchResultId
        ?.let { selectedId -> textSearchResults.indexOfFirst { it.id == selectedId } }
        ?: -1
    val result = selectedTextSearchResultId
        ?.let { selectedId -> textSearchResults.firstOrNull { it.id == selectedId } }
        ?: run {
            statusMessage = "请先选择一处匹配"
            return false
        }
    val tool = textReplaceToolForPreview(toolId) ?: return false
    val parameters = textReplaceParameters(tool).copy(preview = false)
    if (parameters.isReplacementMode()) {
        statusMessage = ".replacement 预览请在分组预览中替换"
        return false
    }
    val activeRules = textReplaceRules(parameters)
        ?.filter { it.enabled && it.find.isNotEmpty() }
        ?: return false
    val rule = activeRules.getOrNull(result.ruleIndex) ?: run {
        statusMessage = "替换规则已变化，请重新预览"
        return false
    }
    val replacementDelta = try {
        applySingleTextReplacement(
            chapterIndex = result.chapterIndex,
            sourceStart = result.sourceStart,
            sourceEnd = result.sourceEnd,
            rule = rule,
            caseSensitive = false,
            deferTxtRefresh = kind == DocumentKind.Txt
        )
    } catch (error: IllegalArgumentException) {
        statusMessage = "替换内容错误"
        return false
    }
    if (replacementDelta == null) {
        statusMessage = "此处已无法替换，请重新预览"
        return false
    }

    checkReport = null
    markDocumentChanged()
    clearPreviewHighlight()
    textSearchToolId = toolId
    if (kind == DocumentKind.Txt) {
        textSearchResults = textSearchResultsAfterSingleReplacement(
            results = textSearchResults,
            sourceText = txt?.text,
            replacedId = result.id,
            sourceStart = result.sourceStart,
            sourceEnd = result.sourceEnd,
            replacementDelta = replacementDelta,
            resolveLocation = ::textSearchResultLocation
        )
    } else {
        refreshChapters()
        textSearchResults = try {
            buildTextSearchResults(activeRules, parameters)
        } catch (error: IllegalArgumentException) {
            emptyList()
        }
    }
    val nextResult = when {
        textSearchResults.isEmpty() -> null
        selectedResultIndex in textSearchResults.indices -> textSearchResults[selectedResultIndex]
        selectedResultIndex > 0 -> textSearchResults.getOrNull((selectedResultIndex - 1).coerceAtMost(textSearchResults.lastIndex))
        else -> textSearchResults.firstOrNull()
    }
    val nextSelected = nextResult?.let { selectTextSearchResult(it.id) } == true
    if (nextResult == null) {
        selectedTextSearchResultId = null
    } else if (!nextSelected) {
        clearPreviewHighlight()
    }
    statusMessage = if (textSearchResults.isEmpty()) {
        "已替换此处，没有剩余匹配"
    } else if (nextResult == null) {
        "已替换此处，没有下一项"
    } else if (selectedResultIndex !in textSearchResults.indices) {
        "已替换此处，已定位上一项，剩余 ${textSearchResults.size} 处"
    } else {
        "已替换此处，剩余 ${textSearchResults.size} 处"
    }
    return true
}

suspend fun EditorController.applySelectedTextSearchResultsWithProgress(
    toolId: String,
    resultIds: Set<String>,
    onProgress: (completed: Int, total: Int) -> Unit
): Boolean {
    if (textSearchToolId != toolId) {
        statusMessage = "没有可执行的替换预览"
        return false
    }
    if (resultIds.isEmpty()) {
        statusMessage = "没有勾选可替换内容"
        return false
    }
    val tool = textReplaceToolForPreview(toolId) ?: return false
    val parameters = textReplaceParameters(tool).copy(preview = false)
    if (parameters.isReplacementMode()) {
        statusMessage = ".replacement 预览请在分组预览中替换"
        return false
    }
    val activeRules = textReplaceRules(parameters)
        ?.filter { it.enabled && it.find.isNotEmpty() }
        ?: return false
    val selectedResults = textSearchResults.filter { result -> result.id in resultIds }
    val total = (selectedResults.size + 1).coerceAtLeast(1)
    onProgress(0, total)
    yield()
    val plans = try {
        val builtPlans = mutableListOf<ReplacementMatchPlan>()
        for ((index, result) in selectedResults.withIndex()) {
            val rule = activeRules.getOrNull(result.ruleIndex)
            if (rule != null) {
                builtPlans += ReplacementMatchPlan(
                    chapterIndex = result.chapterIndex,
                    sourceStart = result.sourceStart,
                    sourceEnd = result.sourceEnd,
                    replacementText = singleMatchReplacement(result.matchText, rule, caseSensitive = false)
                )
            }
            onProgress(index + 1, total)
            yield()
        }
        builtPlans
    } catch (error: IllegalArgumentException) {
        statusMessage = textReplaceRegexErrorMessage(error)
        return false
    }
    if (plans.isEmpty()) {
        statusMessage = "没有勾选可替换内容"
        return false
    }
    applyDeferredTxtTextReplacementRefresh()
    val applied = applyReplacementMatchPlans(plans)
    if (applied <= 0) {
        statusMessage = "选中内容已无法替换，请重新预览"
        return false
    }
    onProgress(total, total)
    yield()

    checkReport = null
    markDocumentChanged()
    clearPreviewHighlight()
    refreshTextSearchPreviewAfterSelectedReplacement(
        toolId = toolId,
        parameters = parameters,
        activeRules = activeRules
    )
    statusMessage = "替换完成：$applied 处"
    return true
}

fun EditorController.applySelectedReplacementPreviewMatch(): Boolean {
    val preview = replacementFilePreview ?: run {
        statusMessage = "没有可用的规则预览"
        return false
    }
    val selectedId = selectedReplacementPreviewMatchId ?: run {
        statusMessage = "请先选择一处匹配"
        return false
    }
    if ((preview.multiRules + preview.singleRules).none { rule -> rule.matches.any { it.id == selectedId } }) {
        statusMessage = "选中的匹配已不存在"
        return false
    }
    return applyReplacementPreviewMatches(setOf(selectedId))
}

fun EditorController.applyReplacementPreviewMatches(matchIds: Set<String>): Boolean {
    val preview = replacementFilePreview ?: run {
        statusMessage = "没有可用的规则预览"
        return false
    }
    if (matchIds.isEmpty()) {
        statusMessage = "没有勾选可替换内容"
        return false
    }
    val sourceRules = (preview.multiRules + preview.singleRules + preview.zeroRules).sortedBy { it.lineNo }
    val plans = (preview.multiRules + preview.singleRules)
        .flatMap { rule -> rule.matches }
        .filter { it.id in matchIds }
        .map { match ->
            ReplacementMatchPlan(
                chapterIndex = match.chapterIndex,
                sourceStart = match.sourceStart,
                sourceEnd = match.sourceEnd,
                replacementText = match.replacementText
            )
        }
    if (plans.isEmpty()) {
        statusMessage = "没有勾选可替换内容"
        return false
    }
    val tool = textReplaceToolForPreview(preview.toolId) ?: return false
    val parameters = effectiveTextReplaceParameters(tool)
    applyDeferredTxtTextReplacementRefresh()
    val applied = applyReplacementMatchPlans(plans)
    if (applied <= 0) {
        statusMessage = "选中内容已无法替换，请重新预览"
        return false
    }

    checkReport = null
    markDocumentChanged()
    clearPreviewHighlight()
    refreshChapters()
    replacementFilePreview = buildReplacementFilePreview(
        toolId = preview.toolId,
        parameters = parameters,
        parsedRules = sourceRules.map {
            ParsedReplacementRule(
                lineNo = it.lineNo,
                pattern = it.pattern,
                replacement = it.replacement,
                regex = it.regex
            )
        },
        skippedRules = preview.skippedRules
    )
    textSearchToolId = null
    textSearchResults = emptyList()
    statusMessage = "已替换 $applied 处"
    return true
}

suspend fun EditorController.applySelectedReplacementPreviewWithProgress(
    matchIds: Set<String>,
    onProgress: (completed: Int, total: Int) -> Unit
): Boolean {
    val preview = replacementFilePreview ?: run {
        statusMessage = "没有可用的规则预览"
        return false
    }
    if (matchIds.isEmpty()) {
        statusMessage = "没有勾选可替换内容"
        return false
    }
    val sourceRules = (preview.multiRules + preview.singleRules + preview.zeroRules).sortedBy { it.lineNo }
    // 勾选意图按「规则」判定：
    // - 规则已达预览上限(未展示全)且展示出来的匹配全部勾选 -> 视为整条规则全要，交引擎全量替换以覆盖未展示部分
    // - 其余(未达上限，或仅勾选部分) -> 只按预览快照里被勾选的位置精确替换，不误碰未展示内容
    val engineRules = mutableListOf<TextReplaceRule>()
    val plans = mutableListOf<ReplacementMatchPlan>()
    for (rule in preview.multiRules + preview.singleRules) {
        val selectedMatches = rule.matches.filter { it.id in matchIds }
        if (selectedMatches.isEmpty()) continue
        val fullySelected = selectedMatches.size == rule.matches.size
        val reachedPreviewLimit = rule.matches.size >= REPLACEMENT_PREVIEW_MAX_MATCHES_PER_RULE
        if (fullySelected && reachedPreviewLimit && rule.pattern.isNotEmpty()) {
            engineRules += TextReplaceRule(
                find = rule.pattern,
                replacement = rule.replacement,
                regex = rule.regex,
                textOnly = false,
                caseSensitive = true
            )
        } else {
            selectedMatches.forEach { match ->
                plans += ReplacementMatchPlan(
                    chapterIndex = match.chapterIndex,
                    sourceStart = match.sourceStart,
                    sourceEnd = match.sourceEnd,
                    replacementText = match.replacementText
                )
            }
        }
    }
    if (plans.isEmpty() && engineRules.isEmpty()) {
        statusMessage = "没有勾选可替换内容"
        return false
    }
    val tool = textReplaceToolForPreview(preview.toolId) ?: return false
    val parameters = effectiveTextReplaceParameters(tool)
    val total = (plans.size + engineRules.size + 1).coerceAtLeast(1)
    var completed = 0
    onProgress(completed, total)
    yield()
    applyDeferredTxtTextReplacementRefresh()
    var applied = 0
    // 先按快照位置替换(从后往前互不影响)，再让引擎在替换后的文本上全量补齐超限规则
    if (plans.isNotEmpty()) {
        applied += applyReplacementMatchPlans(plans)
        completed += plans.size
        onProgress(completed.coerceAtMost(total), total)
        yield()
    }
    if (engineRules.isNotEmpty()) {
        val result = try {
            replaceWithParametersAsync(parameters, engineRules)
        } catch (error: IllegalArgumentException) {
            statusMessage = textReplaceRegexErrorMessage(error)
            return false
        }
        applied += result.replacements
        completed += engineRules.size
        onProgress(completed.coerceAtMost(total), total)
        yield()
    }
    if (applied <= 0) {
        statusMessage = "选中内容已无法替换，请重新预览"
        return false
    }
    onProgress(total, total)
    yield()

    checkReport = null
    markDocumentChanged()
    clearPreviewHighlight()
    refreshChapters()
    replacementFilePreview = buildReplacementFilePreview(
        toolId = preview.toolId,
        parameters = parameters,
        parsedRules = sourceRules.map {
            ParsedReplacementRule(
                lineNo = it.lineNo,
                pattern = it.pattern,
                replacement = it.replacement,
                regex = it.regex
            )
        },
        skippedRules = preview.skippedRules
    )
    textSearchToolId = null
    textSearchResults = emptyList()
    statusMessage = "已替换 $applied 处"
    return true
}

private fun EditorController.textReplaceToolForPreview(toolId: String): EditorTool? {
    return if (toolId == builtInToolPlanId("text_replace")) {
        builtInEditorTool("text_replace")
    } else {
        editorTools.firstOrNull { it.id == toolId } ?: automationStepToolForPreview(toolId)
    }
}

internal fun EditorController.rebuildReplacementFilePreviewAfterBodyTextChange(
    previousPreview: ReplacementFilePreview
): ReplacementFilePreview? {
    val tool = textReplaceToolForPreview(previousPreview.toolId) ?: return null
    val parameters = effectiveTextReplaceParameters(tool)
    return buildReplacementFilePreview(
        toolId = previousPreview.toolId,
        parameters = parameters,
        parsedRules = replacementPreviewSourceRules(previousPreview),
        skippedRules = previousPreview.skippedRules
    )
}

internal fun replacementPreviewSourceRules(preview: ReplacementFilePreview): List<ParsedReplacementRule> {
    return (preview.multiRules + preview.singleRules + preview.zeroRules)
        .sortedBy { it.lineNo }
        .map { rule ->
            ParsedReplacementRule(
                lineNo = rule.lineNo,
                pattern = rule.pattern,
                replacement = rule.replacement,
                regex = rule.regex
            )
        }
}

internal fun EditorController.rebuildCurrentTextSearchPreviewAfterDocumentChange(): Boolean {
    val toolId = textSearchToolId ?: return false
    if (replacementFilePreview != null) return false
    val tool = textReplaceToolForPreview(toolId) ?: return false
    val parameters = effectiveTextReplaceParameters(tool)
    if (parameters.isReplacementMode()) return false
    val activeRules = textReplaceRules(parameters)
        ?.filter { it.enabled && it.find.isNotEmpty() }
        ?: return false
    if (activeRules.isEmpty()) return false
    val rebuiltResults = try {
        buildTextSearchResults(activeRules, parameters)
    } catch (error: IllegalArgumentException) {
        statusMessage = textReplaceRegexErrorMessage(error)
        return false
    }
    textSearchToolId = toolId
    textSearchResults = rebuiltResults
    selectedTextSearchResultId = null
    selectedReplacementPreviewMatchId = null
    return true
}

private fun EditorController.refreshTextSearchPreviewAfterSelectedReplacement(
    toolId: String,
    parameters: TextReplaceParameters,
    activeRules: List<TextReplaceRule>
) {
    if (kind != DocumentKind.Epub) {
        clearTextSearchState()
        refreshChapters()
        return
    }
    refreshChapters()
    val rebuiltResults = try {
        buildTextSearchResults(activeRules, parameters)
    } catch (error: IllegalArgumentException) {
        emptyList()
    }
    if (rebuiltResults.isEmpty()) {
        clearTextSearchState()
    } else {
        textSearchToolId = toolId
        textSearchResults = rebuiltResults
        selectedTextSearchResultId = null
        selectedReplacementPreviewMatchId = null
    }
}

internal fun EditorController.runTextReplaceTool(tool: EditorTool, manual: Boolean): Boolean {
    if (kind == DocumentKind.None) {
        statusMessage = "请先打开 EPUB 或 TXT"
        return false
    }
    statusMessage = ""
    val parameters = effectiveTextReplaceParameters(tool)
    if (parameters.isReplacementMode()) {
        if (parameters.preview) {
            val ready = prepareReplacementFilePreview(tool)
            if (!ready) return false
            if (!manual && automationStatusMeansSkipped(statusMessage)) return true
            if (manual) return true
            statusMessage = needsConfirmationMessage()
            return false
        }
    }
    val rules = if (parameters.isReplacementMode()) {
        textReplaceRulesFromReplacementFile(parameters) ?: return false
    } else {
        textReplaceRules(parameters) ?: return false
    }
    if (rules.isEmpty()) {
        statusMessage = "请输入查找内容"
        return false
    }

    val activeRules = rules.filter { it.enabled && it.find.isNotEmpty() }
    if (activeRules.isEmpty()) {
        statusMessage = "请输入查找内容"
        return false
    }
    if (parameters.preview) {
        val results = try {
            buildTextSearchResults(activeRules, parameters)
        } catch (error: IllegalArgumentException) {
            statusMessage = textReplaceRegexErrorMessage(error)
            clearTextSearchState()
            return false
        }
        if (results.isEmpty()) {
            clearTextSearchState()
            statusMessage = textReplaceNoMatchMessage(kind, statusMessage, parameters, activeRules)
            return false
        }
        textSearchToolId = tool.id
        textSearchResults = results
        clearPreviewHighlight()
        statusMessage = textSearchFoundMessage(results.size)
        if (manual) return true
        statusMessage = needsConfirmationMessage()
        return false
    }

    val result = try {
        replaceWithParameters(parameters, activeRules)
    } catch (error: IllegalArgumentException) {
        statusMessage = textReplaceRegexErrorMessage(error)
        return false
    }
    if (result.replacements <= 0) {
        statusMessage = textReplaceNoMatchMessage(kind, statusMessage, parameters, activeRules)
        return false
    }
    checkReport = null
    markDocumentChanged()
    clearTextSearchState()
    refreshChapters()
    statusMessage = "替换完成：${result.filesChanged} 个文件，${result.replacements} 处"
    return true
}

internal suspend fun EditorController.runTextReplaceToolAsync(tool: EditorTool, manual: Boolean): Boolean {
    if (kind == DocumentKind.None) {
        statusMessage = "请先打开 EPUB 或 TXT"
        return false
    }
    statusMessage = ""
    val parameters = effectiveTextReplaceParameters(tool)
    if (parameters.isReplacementMode()) {
        if (parameters.preview) {
            val ready = prepareReplacementFilePreviewAsync(tool)
            if (!ready) return false
            if (!manual && automationStatusMeansSkipped(statusMessage)) return true
            if (manual) return true
            statusMessage = needsConfirmationMessage()
            return false
        }
    }
    val rules = if (parameters.isReplacementMode()) {
        textReplaceRulesFromReplacementFileAsync(parameters) ?: return false
    } else {
        textReplaceRules(parameters) ?: return false
    }
    if (rules.isEmpty()) {
        statusMessage = "请输入查找内容"
        return false
    }

    val activeRules = rules.filter { it.enabled && it.find.isNotEmpty() }
    if (activeRules.isEmpty()) {
        statusMessage = "请输入查找内容"
        return false
    }
    if (parameters.preview) {
        val results = try {
            buildTextSearchResultsWithProgress(activeRules, parameters) { _, _, _ -> }
        } catch (error: IllegalArgumentException) {
            statusMessage = textReplaceRegexErrorMessage(error)
            clearTextSearchState()
            return false
        }
        if (results.isEmpty()) {
            clearTextSearchState()
            statusMessage = textReplaceNoMatchMessage(kind, statusMessage, parameters, activeRules)
            return false
        }
        textSearchToolId = tool.id
        textSearchResults = results
        clearPreviewHighlight()
        statusMessage = textSearchFoundMessage(results.size)
        if (manual) return true
        statusMessage = needsConfirmationMessage()
        return false
    }

    val result = try {
        replaceWithParametersAsync(parameters, activeRules)
    } catch (error: IllegalArgumentException) {
        statusMessage = textReplaceRegexErrorMessage(error)
        return false
    }
    if (result.replacements <= 0) {
        statusMessage = textReplaceNoMatchMessage(kind, statusMessage, parameters, activeRules)
        return !manual
    }
    checkReport = null
    markDocumentChanged()
    clearTextSearchState()
    refreshChapters()
    statusMessage = "替换完成：${result.filesChanged} 个文件，${result.replacements} 处"
    return true
}

internal suspend fun EditorController.runTextReplaceToolForAutomationPreview(
    tool: EditorTool,
    onProgress: (phase: String, completed: Int, total: Int) -> Unit
): Boolean {
    if (kind == DocumentKind.None) {
        statusMessage = "请先打开 EPUB 或 TXT"
        return false
    }
    statusMessage = ""
    val parameters = effectiveTextReplaceParameters(tool)
    if (!parameters.preview) {
        return runTextReplaceToolAsync(tool, manual = false)
    }
    if (parameters.isReplacementMode()) {
        yield()
        val ready = prepareReplacementFilePreviewAsync(tool, onProgress)
        yield()
        if (!ready) return false
        if (automationStatusMeansSkipped(statusMessage)) return true
        statusMessage = needsConfirmationMessage()
        return false
    }
    val rules = textReplaceRules(parameters) ?: return false
    if (rules.isEmpty()) {
        statusMessage = "请输入查找内容"
        return false
    }

    val activeRules = rules.filter { it.enabled && it.find.isNotEmpty() }
    if (activeRules.isEmpty()) {
        statusMessage = "请输入查找内容"
        return false
    }
    val results = try {
        buildTextSearchResultsWithProgress(activeRules, parameters, onProgress)
    } catch (error: IllegalArgumentException) {
        statusMessage = textReplaceRegexErrorMessage(error)
        clearTextSearchState()
        return false
    }
    if (results.isEmpty()) {
        clearTextSearchState()
        statusMessage = textReplaceNoMatchMessage(kind, statusMessage, parameters, activeRules)
        return true
    }
    textSearchToolId = tool.id
    textSearchResults = results
    clearPreviewHighlight()
    statusMessage = textSearchFoundMessage(results.size)
    statusMessage = needsConfirmationMessage()
    return false
}

internal fun EditorController.prepareReplacementFilePreview(tool: EditorTool): Boolean {
    if (kind == DocumentKind.None) {
        statusMessage = "请先打开 EPUB 或 TXT"
        return false
    }
    statusMessage = ""
    val parameters = effectiveTextReplaceParameters(tool)
    if (!parameters.isReplacementMode()) {
        statusMessage = "请选择 replacement 模式"
        return false
    }
    val ruleText = readTextReplaceRuleFile(parameters.batchFile) ?: return false
    val preview = try {
        val (parsedRules, skippedRules) = parseReplacementRules(ruleText)
        buildReplacementFilePreview(tool.id, parameters, parsedRules, skippedRules)
    } catch (error: IllegalArgumentException) {
        statusMessage = error.message
            ?.takeIf { it.startsWith("规则数量过多") }
            ?: textReplaceRegexErrorMessage(error)
        return false
    }
    replacementFilePreview = preview
    textSearchToolId = null
    textSearchResults = emptyList()
    selectedTextSearchResultId = null
    selectedReplacementPreviewMatchId = null
    clearPreviewHighlight()
    statusMessage = replacementFilePreviewStatusMessage(preview)
    return true
}

internal suspend fun EditorController.prepareReplacementFilePreviewAsync(
    tool: EditorTool,
    onProgress: (phase: String, completed: Int, total: Int) -> Unit = { _, _, _ -> }
): Boolean {
    if (kind == DocumentKind.None) {
        statusMessage = "请先打开 EPUB 或 TXT"
        return false
    }
    statusMessage = "加载预览"
    yieldToAppUiBeforeHeavyWork()
    val parameters = effectiveTextReplaceParameters(tool)
    if (!parameters.isReplacementMode()) {
        statusMessage = "请选择 replacement 模式"
        return false
    }
    val ruleTextResult = withContext(Dispatchers.IO) {
        readTextReplaceRuleFileText(appContext.contentResolver, parameters.batchFile)
    }
    if (ruleTextResult.message.isNotBlank()) {
        statusMessage = ruleTextResult.message
        return false
    }
    val ruleText = ruleTextResult.text ?: return false
    val parsed = try {
        withContext(Dispatchers.Default) {
            parseReplacementRules(ruleText)
        }
    } catch (error: IllegalArgumentException) {
        statusMessage = error.message
            ?.takeIf { it.startsWith("规则数量过多") }
            ?: textReplaceRegexErrorMessage(error)
        return false
    }
    val sources = searchSources(parameters)
    val resolveLocation = textSearchResultLocationResolverSnapshot()
    val preview = try {
        buildReplacementFilePreviewWithProgress(
            toolId = tool.id,
            parsedRules = parsed.first,
            skippedRules = parsed.second,
            sources = sources,
            resolveLocation = resolveLocation,
            onProgress = onProgress
        )
    } catch (error: IllegalArgumentException) {
        statusMessage = error.message
            ?.takeIf { it.startsWith("规则数量过多") }
            ?: textReplaceRegexErrorMessage(error)
        return false
    }
    replacementFilePreview = preview
    textSearchToolId = null
    textSearchResults = emptyList()
    selectedTextSearchResultId = null
    selectedReplacementPreviewMatchId = null
    clearPreviewHighlight()
    statusMessage = replacementFilePreviewStatusMessage(preview)
    return true
}

private fun replacementFilePreviewStatusMessage(preview: ReplacementFilePreview): String {
    val matchedRules = preview.multiRules.size + preview.singleRules.size
    val baseMessage = when {
        preview.totalRules == 0 -> "规则文件为空或没有可读取规则"
        preview.validRules == 0 -> "没有有效规则：无效 ${preview.skippedRules.size}/${preview.totalRules}"
        matchedRules == 0 -> "无匹配内容"
        else -> "规则预览：有效 ${preview.validRules}/${preview.totalRules}，命中 $matchedRules 条，无效 ${preview.skippedRules.size}/${preview.totalRules}"
    }
    return if (preview.previewLimitReached && preview.displayedMatches > 0) {
        "$baseMessage，部分规则仅显示前 $REPLACEMENT_PREVIEW_MAX_MATCHES_PER_RULE 处"
    } else {
        baseMessage
    }
}

private fun EditorController.effectiveTextReplaceParameters(tool: EditorTool): TextReplaceParameters {
    return effectiveTextReplaceParametersForRun(textReplaceParameters(tool))
}

internal fun EditorController.textReplaceRules(parameters: TextReplaceParameters): List<TextReplaceRule>? {
    val result = buildTextReplaceRulesForParameters(parameters)
    if (result.message.isNotBlank()) statusMessage = result.message
    return result.rules
}

private fun EditorController.readTextReplaceRuleFile(pathOrUri: String): String? {
    val result = readTextReplaceRuleFileText(appContext.contentResolver, pathOrUri)
    if (result.message.isNotBlank()) statusMessage = result.message
    return result.text
}

private suspend fun EditorController.textReplaceRulesFromReplacementFileAsync(parameters: TextReplaceParameters): List<TextReplaceRule>? {
    val readResult = withContext(Dispatchers.IO) {
        readTextReplaceRuleFileText(appContext.contentResolver, parameters.batchFile)
    }
    val ruleText = readResult.text
    if (ruleText == null) {
        if (readResult.message.isNotBlank()) statusMessage = readResult.message
        return null
    }
    val buildResult = withContext(Dispatchers.Default) {
        buildTextReplaceRulesFromReplacementFileText(ruleText)
    }
    if (buildResult.message.isNotBlank()) {
        statusMessage = buildResult.message
    } else if (readResult.message.isNotBlank()) {
        statusMessage = readResult.message
    }
    return buildResult.rules
}

private fun EditorController.textReplaceRulesFromReplacementFile(parameters: TextReplaceParameters): List<TextReplaceRule>? {
    val result = readReplacementFileRules(appContext.contentResolver, parameters.batchFile)
    if (result.message.isNotBlank()) statusMessage = result.message
    return result.rules
}

private fun EditorController.buildTextSearchResults(
    rules: List<TextReplaceRule>,
    parameters: TextReplaceParameters
): List<TextSearchResult> {
    return buildTextReplaceSearchResultsForRules(
        rules = rules,
        parameters = parameters,
        sourceResolver = ::searchSources,
        resolveLocation = ::textSearchResultLocation
    )
}

private suspend fun EditorController.buildTextSearchResultsWithProgress(
    rules: List<TextReplaceRule>,
    parameters: TextReplaceParameters,
    onProgress: (phase: String, completed: Int, total: Int) -> Unit
): List<TextSearchResult> {
    val jobs = rules.mapIndexed { index, rule ->
        val sources = searchSources(
            parameters.copy(
                target = if (rule.textOnly) {
                    TEXT_REPLACE_TARGET_VISIBLE
                } else {
                    TEXT_REPLACE_TARGET_SOURCE
                }
            )
        )
        Triple(index, rule, sources)
    }
    val total = jobs.sumOf { (_, _, sources) -> sources.size.coerceAtLeast(1) }
        .coerceAtLeast(1)
    var completed = 0
    val results = mutableListOf<TextSearchResult>()
    onProgress("加载预览", completed, total)
    yield()
    for ((index, rule, sources) in jobs) {
        currentCoroutineContext().ensureActive()
        if (sources.isEmpty()) {
            completed += 1
            onProgress("加载预览", completed, total)
            yield()
        } else {
            for (source in sources) {
                currentCoroutineContext().ensureActive()
                val resolveLocation = textSearchResultLocationResolverSnapshot()
                val chunk = withContext(Dispatchers.Default) {
                    currentCoroutineContext().ensureActive()
                    buildTextSearchResults(
                        sources = listOf(source),
                        rule = rule,
                        caseSensitive = false,
                        ruleIndex = index,
                        idPrefix = "rule-$index",
                        resolveLocation = resolveLocation
                    )
                }
                results += chunk
                completed += 1
                onProgress("加载预览", completed, total)
                yield()
            }
        }
    }
    return results
}

private suspend fun buildReplacementFilePreviewWithProgress(
    toolId: String,
    parsedRules: List<ParsedReplacementRule>,
    skippedRules: List<ReplacementSkippedRule>,
    sources: List<SearchSource>,
    resolveLocation: (Int, Int, Int, String) -> TextSearchResultLocation,
    onProgress: (phase: String, completed: Int, total: Int) -> Unit
): ReplacementFilePreview {
    val total = parsedRules.size.coerceAtLeast(1)
    onProgress("加载预览", 0, total)
    yield()
    if (parsedRules.isEmpty()) {
        onProgress("加载预览", 1, total)
        yield()
        return replacementFilePreviewFromRules(
            toolId = toolId,
            totalRules = skippedRules.size,
            validRuleCount = 0,
            skippedRules = skippedRules,
            rules = emptyList(),
            previewLimitReached = false
        )
    }
    // 多条规则并行扫描（每条一个 Default 协程），按 index 顺序汇总并增量上报进度
    val rules = coroutineScope {
        val deferred = parsedRules.mapIndexed { index, rule ->
            async(Dispatchers.Default) {
                currentCoroutineContext().ensureActive()
                buildReplacementPreviewRule(
                    index = index,
                    rule = rule,
                    sources = sources,
                    maxMatches = REPLACEMENT_PREVIEW_MAX_MATCHES_PER_RULE,
                    resolveLocation = resolveLocation
                )
            }
        }
        val collected = ArrayList<ReplacementPreviewRule>(deferred.size)
        for ((index, job) in deferred.withIndex()) {
            collected += job.await()
            onProgress("加载预览", index + 1, total)
            yield()
        }
        collected
    }
    val limitReached = rules.any { it.matches.size >= REPLACEMENT_PREVIEW_MAX_MATCHES_PER_RULE }
    return replacementFilePreviewFromRules(
        toolId = toolId,
        totalRules = parsedRules.size + skippedRules.size,
        validRuleCount = parsedRules.size,
        skippedRules = skippedRules,
        rules = rules,
        previewLimitReached = limitReached
    )
}

private fun EditorController.buildReplacementFilePreview(
    toolId: String,
    parameters: TextReplaceParameters,
    input: String
): ReplacementFilePreview {
    return buildReplacementFilePreviewForParameters(
        toolId = toolId,
        parameters = parameters,
        input = input,
        sourceResolver = ::searchSources,
        resolveLocation = ::textSearchResultLocation
    )
}

private fun EditorController.buildReplacementFilePreview(
    toolId: String,
    parameters: TextReplaceParameters,
    parsedRules: List<ParsedReplacementRule>,
    skippedRules: List<ReplacementSkippedRule>
): ReplacementFilePreview {
    return buildReplacementFilePreviewForParameters(
        toolId = toolId,
        parameters = parameters,
        parsedRules = parsedRules,
        skippedRules = skippedRules,
        sourceResolver = ::searchSources,
        resolveLocation = ::textSearchResultLocation
    )
}

private fun EditorController.searchSources(): List<SearchSource> {
    return when (kind) {
        DocumentKind.Epub -> epub?.let(::epubChapterBodySearchSources).orEmpty()
        DocumentKind.Txt -> txt?.let { document -> txtSearchSources(document) }.orEmpty()
        DocumentKind.None -> emptyList()
    }
}

private fun EditorController.txtSearchSources(document: TxtDocument, scope: String = TOOL_SCOPE_ALL): List<SearchSource> {
    return txtSearchSourcesForPreview(
        document = document,
        scope = scope,
        previewChapterIndex = previewChapterIndex,
        prefaceEndIndex = txtPrefaceEndIndex(document)
    )
}

private fun EditorController.textSearchResultLocation(
    absoluteStart: Int,
    absoluteEnd: Int,
    fallbackChapterIndex: Int,
    fallbackTitle: String
): TextSearchResultLocation {
    val document = txt
    return textSearchResultLocationForDocument(
        kind = kind,
        document = document,
        absoluteStart = absoluteStart,
        absoluteEnd = absoluteEnd,
        fallbackChapterIndex = fallbackChapterIndex,
        fallbackTitle = fallbackTitle,
        prefaceEndIndex = document?.let(::txtPrefaceEndIndex)
    )
}

private fun EditorController.textSearchResultLocationResolverSnapshot(): (Int, Int, Int, String) -> TextSearchResultLocation {
    val locationKind = kind
    val document = txt?.let { current ->
        current.copy(chapters = current.chapters.toList())
    }
    val prefaceEndIndex = document?.let(::txtPrefaceEndIndex)
    return { absoluteStart, absoluteEnd, fallbackChapterIndex, fallbackTitle ->
        textSearchResultLocationForDocument(
            kind = locationKind,
            document = document,
            absoluteStart = absoluteStart,
            absoluteEnd = absoluteEnd,
            fallbackChapterIndex = fallbackChapterIndex,
            fallbackTitle = fallbackTitle,
            prefaceEndIndex = prefaceEndIndex
        )
    }
}

private fun EditorController.searchSources(parameters: TextReplaceParameters): List<SearchSource> {
    if (kind == DocumentKind.Epub) {
        val sources = epubPackageTextSearchSources(parameters)
        if (parameters.target == TEXT_REPLACE_TARGET_VISIBLE) {
            return sources.flatMap(::visibleTextSearchSources)
        }
        return sources
    }
    if (kind == DocumentKind.Txt) {
        val document = txt ?: return emptyList()
        return txtSearchSources(document, parameters.scope)
    }
    val all = searchSources()
    if (all.isEmpty()) return emptyList()
    val targetIndices = targetChapterIndices(
        scope = parameters.scope,
        size = all.size,
        matchPattern = parameters.matchPattern,
        matchRegexEnabled = parameters.matchRegexEnabled
    ).toSet()
    val filtered = all.filter { it.chapterIndex in targetIndices }
    if (kind != DocumentKind.Epub || parameters.target == TEXT_REPLACE_TARGET_SOURCE) {
        return filtered
    }
    return filtered.flatMap(::visibleTextSearchSources)
}

private fun EditorController.epubPackageTextReplaceTargets(parameters: TextReplaceParameters): List<EpubPackageTextTarget> {
    val book = epub ?: return emptyList()
    return epubPackageTextReplaceTargets(
        book = book,
        scope = parameters.scope,
        currentPath = book.chapters.getOrNull(previewChapterIndex)?.path,
        introPath = defaultFetchInfoIntroTarget(book)
    )
}

private fun EditorController.epubPackageTextSearchSources(parameters: TextReplaceParameters): List<SearchSource> {
    val book = epub ?: return emptyList()
    return epubPackageTextSearchSources(
        book = book,
        parameters = parameters,
        currentPath = book.chapters.getOrNull(previewChapterIndex)?.path,
        introPath = defaultFetchInfoIntroTarget(book)
    )
}

private fun EditorController.applySingleTextReplacement(
    chapterIndex: Int,
    sourceStart: Int,
    sourceEnd: Int,
    rule: TextReplaceRule,
    caseSensitive: Boolean,
    deferTxtRefresh: Boolean = false
): Int? {
    if (sourceStart < 0 || sourceEnd <= sourceStart) return null
    return when (kind) {
        DocumentKind.Epub -> {
            val book = epub ?: return null
            applySingleEpubPackageTextReplacement(
                book = book,
                introPath = defaultFetchInfoIntroTarget(book),
                sourceIndex = chapterIndex,
                sourceStart = sourceStart,
                sourceEnd = sourceEnd,
                rule = rule,
                caseSensitive = caseSensitive
            )
        }
        DocumentKind.Txt -> {
            if (warnTxtMoveChapterSyncPending("替换文本")) return null
            val document = txt ?: return null
            if (sourceEnd > document.text.length) return null
            val originalText = document.text.substring(sourceStart, sourceEnd)
            val replacement = singleMatchReplacement(originalText, rule, caseSensitive)
            document.text = document.text.replaceRange(sourceStart, sourceEnd, replacement)
            if (deferTxtRefresh) {
                txtTextReplacementRefreshDeferred = true
                document.chapters = shiftTxtChaptersAfterTextChange(
                    chapters = document.chapters,
                    sourceStart = sourceStart,
                    sourceEnd = sourceEnd,
                    originalText = originalText,
                    replacementText = replacement
                )
            } else {
                document.chapters = detectCurrentTxtChapters(document.text)
            }
            replacement.length - (sourceEnd - sourceStart)
        }
        DocumentKind.None -> null
    }
}

private fun EditorController.applyReplacementMatchPlans(plans: List<ReplacementMatchPlan>): Int {
    if (plans.isEmpty()) return 0
    return when (kind) {
        DocumentKind.Epub -> {
            val book = epub ?: return 0
            applyReplacementMatchPlansToEpubPackageText(
                book = book,
                introPath = defaultFetchInfoIntroTarget(book),
                plans = plans
            )
        }
        DocumentKind.Txt -> {
            if (warnTxtMoveChapterSyncPending("替换文本")) return 0
            val document = txt ?: return 0
            val (nextText, changed) = applyReplacementPlansToText(document.text, plans)
            if (changed > 0) {
                document.text = nextText
                document.chapters = detectCurrentTxtChapters(document.text)
                txtTextReplacementRefreshDeferred = false
            }
            changed
        }
        DocumentKind.None -> 0
    }
}

internal fun EditorController.replaceWithParameters(parameters: TextReplaceParameters, rules: List<TextReplaceRule>): ReplaceResult {
    return when (kind) {
        DocumentKind.Epub -> replaceInEpub(parameters, rules)
        DocumentKind.Txt -> replaceInTxt(parameters, rules)
        DocumentKind.None -> ReplaceResult(0, 0)
    }
}

internal suspend fun EditorController.replaceWithParametersAsync(
    parameters: TextReplaceParameters,
    rules: List<TextReplaceRule>
): ReplaceResult {
    return when (kind) {
        DocumentKind.Epub -> replaceInEpubAsync(parameters, rules)
        DocumentKind.Txt -> replaceInTxtAsync(parameters, rules)
        DocumentKind.None -> ReplaceResult(0, 0)
    }
}

private fun EditorController.replaceInEpub(parameters: TextReplaceParameters, rules: List<TextReplaceRule>): ReplaceResult {
    val book = epub ?: return ReplaceResult(0, 0)
    return replaceInEpubPackageText(
        book = book,
        parameters = parameters,
        currentPath = book.chapters.getOrNull(previewChapterIndex)?.path,
        introPath = defaultFetchInfoIntroTarget(book),
        rules = rules
    )
}

private suspend fun EditorController.replaceInEpubAsync(
    parameters: TextReplaceParameters,
    rules: List<TextReplaceRule>
): ReplaceResult {
    val sourceBook = epub ?: return ReplaceResult(0, 0)
    val nextBook = sourceBook.mutableDeepCopy()
    val currentPath = sourceBook.chapters.getOrNull(previewChapterIndex)?.path
    val introPath = defaultFetchInfoIntroTarget(sourceBook)
    val result = withContext(Dispatchers.Default) {
        val context = currentCoroutineContext()
        replaceInEpubPackageText(
            book = nextBook,
            parameters = parameters,
            currentPath = currentPath,
            introPath = introPath,
            rules = rules,
            ensureActive = { context.ensureActive() }
        )
    }
    if (result.replacements > 0) {
        epub = nextBook
    }
    return result
}

private fun EditorController.replaceInTxt(parameters: TextReplaceParameters, rules: List<TextReplaceRule>): ReplaceResult {
    if (warnTxtMoveChapterSyncPending("替换文本")) return ReplaceResult(0, 0)
    val document = txt ?: return ReplaceResult(0, 0)
    val currentIndex = previewChapterIndex.takeIf { it >= 0 } ?: txtPrefaceChapterIndex()
    val result = replaceInTxtDocumentText(
        document = document,
        parameters = parameters,
        currentChapterIndex = currentIndex,
        prefaceEndIndex = txtPrefaceEndIndex(document),
        rules = rules
    )
    if (result.replacements > 0) {
        document.text = result.text
        document.chapters = detectCurrentTxtChapters(document.text)
        txtTextReplacementRefreshDeferred = false
    }
    return ReplaceResult(result.changedSources, result.replacements)
}

private suspend fun EditorController.replaceInTxtAsync(
    parameters: TextReplaceParameters,
    rules: List<TextReplaceRule>
): ReplaceResult {
    if (warnTxtMoveChapterSyncPending("替换文本")) return ReplaceResult(0, 0)
    val document = txt ?: return ReplaceResult(0, 0)
    val currentIndex = previewChapterIndex.takeIf { it >= 0 } ?: txtPrefaceChapterIndex()
    val prefaceEndIndex = txtPrefaceEndIndex(document)
    val snapshot = document.copy(chapters = document.chapters.toList())
    val result = withContext(Dispatchers.Default) {
        val context = currentCoroutineContext()
        replaceInTxtDocumentText(
            document = snapshot,
            parameters = parameters,
            currentChapterIndex = currentIndex,
            prefaceEndIndex = prefaceEndIndex,
            rules = rules,
            ensureActive = { context.ensureActive() }
        )
    }
    if (result.replacements > 0) {
        val config = currentTxtChapterDetectionConfig()
        val autoKeys = txtEnabledChapterRuleKeys
        val supplementedCatalogLines = txtSupplementedCatalogLines
        val nextChapters = withContext(Dispatchers.Default) {
            detectTxtChaptersWithCatalogConfig(
                text = result.text,
                config = config,
                autoKeys = autoKeys,
                supplementedCatalogLines = supplementedCatalogLines
            )
        }
        document.text = result.text
        document.chapters = nextChapters
        txtTextReplacementRefreshDeferred = false
    }
    return ReplaceResult(result.changedSources, result.replacements)
}
