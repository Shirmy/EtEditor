package com.eteditor

import com.eteditor.core.DocumentKind
import com.eteditor.core.TxtDocument

fun EditorController.applyTxtBookTitleFilter(showNoMatchMessage: Boolean = true): Boolean {
    if (txt == null) return false
    if (!validateTxtBookTitleRuleItems(parseTxtBookTitleRuleItems(txtBookTitleRulesText))) return false
    val result = resolveTxtBookTitleFilter(txtBookTitleFilterSources())
    if (result.ruleIndex == null) {
        if (result.filteredTitle.isNotBlank() && result.filteredTitle != title) {
            title = result.filteredTitle
            markDocumentChanged()
            statusMessage = "书名过滤完成：没有命中规则，已恢复原书名：${result.filteredTitle}"
            return true
        }
        if (showNoMatchMessage) {
            statusMessage = "书名过滤完成：没有命中规则"
        }
        return false
    }
    if (result.filteredTitle == title) {
        if (showNoMatchMessage) {
            statusMessage = "书名过滤完成：第 ${result.ruleIndex + 1} 条已生效，标题无需变化"
        }
        return true
    }
    title = result.filteredTitle
    markDocumentChanged()
    statusMessage = "书名过滤完成：第 ${result.ruleIndex + 1} 条，${result.sourceTitle} -> ${result.filteredTitle}"
    return true
}

fun EditorController.applyTxtPurifyRules(): Boolean {
    if (warnTxtMoveChapterSyncPending("正文净化")) return false
    return applyTxtPurifyRulesInternal(
        showNoopMessage = true,
        successOnNoop = true,
        successMessage = { catalogCount, bodyCount -> "净化完成：目录 $catalogCount 个，正文 $bodyCount 处" }
    )
}

internal fun EditorController.applyTxtPurifyRulesAfterOpen(): Boolean {
    return applyTxtPurifyRulesInternal(
        showNoopMessage = false,
        successOnNoop = false,
        successMessage = { catalogCount, bodyCount -> "打开 TXT 自动净化：目录 $catalogCount 个，正文 $bodyCount 处" }
    )
}

private fun EditorController.applyTxtPurifyRulesInternal(
    showNoopMessage: Boolean,
    successOnNoop: Boolean,
    successMessage: (catalogCount: Int, bodyCount: Int) -> String
): Boolean {
    val result = applyTxtPurifyTargets(
        applyBody = true,
        applyCatalog = true
    ) ?: return false
    if (!result.changed) {
        if (showNoopMessage) {
            statusMessage = if (!result.hasRules) {
                "净化完成：没有启用的净化规则"
            } else {
                "净化完成：没有匹配内容"
            }
        }
        return successOnNoop
    }
    statusMessage = successMessage(result.catalogCount, result.bodyCount)
    return true
}

internal fun EditorController.applyTxtCatalogPurifyRulesAfterCatalogChange(): Boolean {
    return applyTxtPurifyTargets(applyBody = false, applyCatalog = true)?.changed == true
}

internal fun EditorController.applyTxtPurifyTargets(
    applyBody: Boolean,
    applyCatalog: Boolean
): TxtPurifyApplyResult? {
    val document = txt ?: return TxtPurifyApplyResult(hasRules = false, bodyCount = 0, catalogCount = 0)
    if (!validateTxtPurifyRegexCost(document, applyBody, applyCatalog)) return null
    val result = applyTxtPurifyTargetsToDocument(
        document = document,
        rulesText = txtPurifyRulesText,
        applyBody = applyBody,
        applyCatalog = applyCatalog,
        detectChapters = ::detectCurrentTxtChapters,
        onInvalidRule = { rule -> statusMessage = "净化规则错误：${rule.pattern}" }
    ) ?: return null
    if (!result.changed) return result
    checkReport = null
    markDocumentChanged()
    clearTextSearchState()
    refreshChapters()
    return result
}

internal fun EditorController.txtPurifyRules(): List<TxtPurifyRuleItem> {
    return enabledTxtPurifyRules(txtPurifyRulesText)
}

internal fun EditorController.autoSelectTxtPurifyRulesAfterOpen(document: TxtDocument) {
    if (!validateTxtPurifyRegexCost(
            document = document,
            applyBody = true,
            applyCatalog = true,
            rules = parseTxtPurifyRuleItems(txtPurifyRulesText),
            requireEnabled = false
        )
    ) {
        return
    }
    commitTxtPurifyRuleEdit(
        autoSelectTxtPurifyRulesAfterOpenModel(document, txtPurifyRulesText),
        applyChangedRule = false
    )
}

private fun EditorController.validateTxtPurifyRegexCost(
    document: TxtDocument,
    applyBody: Boolean,
    applyCatalog: Boolean,
    rules: List<TxtPurifyRuleItem> = enabledTxtPurifyRules(txtPurifyRulesText),
    requireEnabled: Boolean = true
): Boolean {
    val warning = txtPurifyRegexCostWarningForDocument(
        document = document,
        rules = rules,
        applyBody = applyBody,
        applyCatalog = applyCatalog,
        requireEnabled = requireEnabled
    ) ?: return true
    statusMessage = warning
    return false
}

fun EditorController.txtPurifyEnabledRuleCount(): Int = txtPurifyRules().size

internal fun EditorController.txtFilteredBookTitle(sourceTitle: String): String {
    return resolveTxtBookTitleFilter(sourceTitle).filteredTitle
}

internal fun EditorController.txtBookTitleFilterSources(): List<String> {
    return buildTxtBookTitleFilterSources(
        originalName = txt?.originalName,
        sourceDisplayName = sourceUri?.let { uri -> documentDisplayName(appContext, uri) },
        fallbackTitle = title.takeIf { kind == DocumentKind.Txt }?.trim().orEmpty()
    )
}

internal fun EditorController.resolveTxtBookTitleFilter(sourceTitle: String): TxtBookTitleFilterResult {
    return resolveTxtBookTitleFilterWithRules(
        sourceTitle = sourceTitle,
        rules = txtBookTitleRules(),
        onRegexError = { error, pattern ->
            statusMessage = "书名过滤规则正则错误：${error.message ?: pattern}"
        }
    )
}

internal fun EditorController.resolveTxtBookTitleFilter(sourceTitles: List<String>): TxtBookTitleFilterResult {
    return resolveTxtBookTitleFilterWithRules(
        sourceTitles = sourceTitles,
        rules = txtBookTitleRules(),
        onRegexError = { error, pattern ->
            statusMessage = "书名过滤规则正则错误：${error.message ?: pattern}"
        }
    )
}

internal fun EditorController.txtBookTitleRules(): List<TxtBookTitleRuleItem> {
    return enabledTxtBookTitleRules(txtBookTitleRulesText)
}
