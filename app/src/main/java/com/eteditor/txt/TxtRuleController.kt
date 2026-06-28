package com.eteditor

import com.eteditor.core.DocumentKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun EditorController.txtChapterRuleItems(): List<TxtChapterRuleItem> {
    return effectiveTxtChapterRuleItems()
}

private fun EditorController.commitTxtChapterRuleEdit(
    result: TxtChapterRuleEditResult,
    deferRefresh: Boolean = false,
    refreshAfterCommit: Boolean = true,
    updateEnabledKeys: Boolean = true
): Boolean {
    val state = applyTxtChapterRuleEditState(
        currentEnabledKeys = txtEnabledChapterRuleKeys,
        result = result,
        updateEnabledKeys = updateEnabledKeys
    ) ?: return false
    txtChapterRulesText = state.rulesText
    txtEnabledChapterRuleKeys = state.enabledKeys
    persistTxtChapterRules()
    if (refreshAfterCommit) refreshTxtChapterRulesIfNeeded(deferRefresh)
    return true
}

fun EditorController.addTxtChapterRule(
    name: String = "",
    pattern: String = "",
    replacement: String = "",
    deferRefresh: Boolean = false
): Boolean {
    if (warnTxtMoveChapterSyncPending("\u65b0\u589e\u76ee\u5f55\u89c4\u5219")) return false
    val nextPattern = pattern.trim()
    if (!validateTxtChapterRulePattern(nextPattern)) return false
    val nextName = name.trim()
    val nextReplacement = replacement.trim()
    val duplicated = txtChapterRuleIsDuplicated(
        items = parseTxtChapterRuleItems(txtChapterRulesText),
        name = nextName,
        pattern = nextPattern,
        replacement = nextReplacement
    )
    if (duplicated) {
        statusMessage = "请不要重复输入"
        return false
    }
    return commitTxtChapterRuleEdit(
        addTxtChapterRuleModel(
            rulesText = txtChapterRulesText,
            enabledKeys = txtEnabledChapterRuleKeys,
            name = nextName,
            pattern = nextPattern,
            replacement = nextReplacement
        ),
        deferRefresh = deferRefresh
    )
}

fun EditorController.updateTxtChapterRuleName(index: Int, name: String) {
    commitTxtChapterRuleEdit(
        renameTxtChapterRuleModel(
            rulesText = txtChapterRulesText,
            enabledKeys = txtEnabledChapterRuleKeys,
            index = index,
            name = name
        ),
        refreshAfterCommit = false
    )
}

fun EditorController.updateTxtChapterRule(index: Int, pattern: String, deferRefresh: Boolean = false): Boolean {
    if (warnTxtMoveChapterSyncPending("\u4fee\u6539\u76ee\u5f55\u89c4\u5219")) return false
    val nextPattern = pattern.trim()
    if (!validateTxtChapterRulePattern(nextPattern)) return false
    return commitTxtChapterRuleEdit(
        updateTxtChapterRulePatternModel(
            rulesText = txtChapterRulesText,
            enabledKeys = txtEnabledChapterRuleKeys,
            index = index,
            pattern = nextPattern
        ),
        deferRefresh = deferRefresh
    )
}

fun EditorController.updateTxtChapterRuleItem(
    index: Int,
    name: String,
    pattern: String,
    replacement: String = "",
    deferRefresh: Boolean = false
): Boolean {
    if (warnTxtMoveChapterSyncPending("\u4fee\u6539\u76ee\u5f55\u89c4\u5219")) return false
    val nextPattern = pattern.trim()
    if (!validateTxtChapterRulePattern(nextPattern)) return false
    return commitTxtChapterRuleEdit(
        updateTxtChapterRuleItemModel(
            rulesText = txtChapterRulesText,
            enabledKeys = txtEnabledChapterRuleKeys,
            index = index,
            name = name,
            pattern = nextPattern,
            replacement = replacement.trim()
        ),
        deferRefresh = deferRefresh
    )
}

fun EditorController.updateTxtChapterRuleEnabled(index: Int, enabled: Boolean, deferRefresh: Boolean = false): Boolean {
    if (warnTxtMoveChapterSyncPending("\u542f\u7528\u76ee\u5f55\u89c4\u5219")) return false
    val items = parseTxtChapterRuleItems(txtChapterRulesText).toMutableList()
    val current = items.getOrNull(index) ?: return false
    if (enabled && !validateTxtChapterRulePattern(current.pattern)) return false
    return commitTxtChapterRuleEdit(
        updateTxtChapterRuleEnabledKeys(
            rulesText = txtChapterRulesText,
            enabledKeys = txtEnabledChapterRuleKeys,
            index = index,
            enabled = enabled
        ),
        deferRefresh = deferRefresh
    )
}

fun EditorController.deleteTxtChapterRule(index: Int, deferRefresh: Boolean = false) {
    if (warnTxtMoveChapterSyncPending("\u5220\u9664\u76ee\u5f55\u89c4\u5219")) return
    commitTxtChapterRuleEdit(
        deleteTxtChapterRuleModel(
            rulesText = txtChapterRulesText,
            enabledKeys = txtEnabledChapterRuleKeys,
            index = index
        ),
        deferRefresh = deferRefresh
    )
}

fun EditorController.moveTxtChapterRule(fromIndex: Int, toIndex: Int, deferRefresh: Boolean = false) {
    if (warnTxtMoveChapterSyncPending("\u79fb\u52a8\u76ee\u5f55\u89c4\u5219")) return
    commitTxtChapterRuleEdit(
        moveTxtChapterRuleModel(
            rulesText = txtChapterRulesText,
            fromIndex = fromIndex,
            toIndex = toIndex
        ),
        deferRefresh = deferRefresh,
        updateEnabledKeys = false
    )
}

fun EditorController.applyDeferredTxtChapterRuleRefresh() {
    if (!txtChapterRulesRefreshDeferred) return
    txtChapterRulesRefreshDeferred = false
    val document = txt ?: return
    startTxtCatalogDetection(
        document = document,
        sessionKey = documentSessionKey,
        doneLabel = "\u76ee\u5f55\u89c4\u5219\u5df2\u5e94\u7528"
    )
}

fun EditorController.applyDeferredTxtTextReplacementRefresh(): Boolean {
    if (!txtTextReplacementRefreshDeferred) return false
    if (txtTextReplacementRefreshApplying) return false
    txtTextReplacementRefreshApplying = true
    txtTextReplacementRefreshDeferred = false
    try {
        if (kind != DocumentKind.Txt) return false
        val document = txt ?: return false
        document.chapters = detectCurrentTxtChapters(document.text)
        checkReport = null
        refreshChapters()
        refreshPreview()
        return true
    } finally {
        txtTextReplacementRefreshApplying = false
    }
}

fun EditorController.scheduleDeferredTxtChapterRuleRefresh(delayMillis: Long = 80L) {
    if (!txtChapterRulesRefreshDeferred) return
    controllerScope.launch {
        delay(delayMillis)
        applyDeferredTxtChapterRuleRefresh()
    }
}

private fun EditorController.refreshTxtChapterRulesIfNeeded(deferRefresh: Boolean) {
    if (deferRefresh) {
        txtChapterRulesRefreshDeferred = true
        return
    }
    refreshTxtDocumentChapters()
    refreshChapters()
    refreshPreview()
}

private fun EditorController.persistTxtChapterRules() {
    settingsPreferences.saveTxtChapterRules(txtChapterRulesText)
}

private fun EditorController.validateTxtChapterRulePattern(pattern: String): Boolean {
    return validateRuleRegex("\u76ee\u5f55\u89c4\u5219", pattern)
}

private fun EditorController.validateTxtPurifyRulePattern(regex: Boolean, pattern: String): Boolean {
    return validateRuleRegex("\u51c0\u5316\u89c4\u5219", pattern, regex, setOf(RegexOption.MULTILINE))
}

private fun EditorController.validateTxtBookTitleRulePattern(regex: Boolean, pattern: String): Boolean {
    return validateRuleRegex("\u4e66\u540d\u8fc7\u6ee4\u89c4\u5219", pattern, regex)
}

private fun EditorController.validateTxtPurifyRuleItems(items: List<TxtPurifyRuleItem>): Boolean {
    return items.all { item -> validateTxtPurifyRulePattern(item.regex, item.pattern) }
}

internal fun EditorController.validateTxtBookTitleRuleItems(items: List<TxtBookTitleRuleItem>): Boolean {
    return items.all { item -> validateTxtBookTitleRulePattern(item.regex, item.pattern) }
}

internal fun EditorController.validateRuleRegex(
    label: String,
    pattern: String,
    regex: Boolean = true,
    options: Set<RegexOption> = emptySet()
): Boolean {
    val message = txtRuleRegexErrorMessage(label, pattern, regex, options) ?: return true
    statusMessage = message
    return false
}

fun EditorController.updateTxtPurifyRulesText(value: String) {
    txtPurifyRulesText = value
}

fun EditorController.txtPurifyRuleItems(): List<TxtPurifyRuleItem> {
    val document = txt
    return parseTxtPurifyRuleItems(txtPurifyRulesText)
        .map { item -> item.copy(matchCount = countTxtPurifyRuleMatches(document, item)) }
}

internal fun EditorController.commitTxtPurifyRuleEdit(
    result: TxtPurifyRuleEditResult,
    applyChangedRule: Boolean = true
): Boolean {
    val state = applyTxtPurifyRuleEditState(result) ?: return false
    txtPurifyRulesText = state.rulesText
    persistTxtPurifyRules()
    if (applyChangedRule) {
        state.changedRule?.let(::applyTxtBodyPurifyRulesAfterRuleChangeIfNeeded)
    }
    return true
}

fun EditorController.addTxtPurifyRule(
    target: String = TXT_PURIFY_TARGET_BODY,
    name: String = "",
    regex: Boolean = true,
    pattern: String = "",
    replacement: String = ""
): Boolean {
    val nextPattern = pattern.trim()
    if (!validateTxtPurifyRulePattern(regex, nextPattern)) return false
    return commitTxtPurifyRuleEdit(
        addTxtPurifyRuleModel(
            rulesText = txtPurifyRulesText,
            target = target,
            name = name,
            regex = regex,
            pattern = nextPattern,
            replacement = replacement
        )
    )
}

fun EditorController.updateTxtPurifyRuleTarget(index: Int, target: String) {
    commitTxtPurifyRuleEdit(updateTxtPurifyRuleTargetModel(txtPurifyRulesText, index, target))
}

fun EditorController.updateTxtPurifyRuleName(index: Int, name: String) {
    commitTxtPurifyRuleEdit(
        updateTxtPurifyRuleNameModel(txtPurifyRulesText, index, name),
        applyChangedRule = false
    )
}

fun EditorController.updateTxtPurifyRulePattern(index: Int, pattern: String): Boolean {
    val current = parseTxtPurifyRuleItems(txtPurifyRulesText).getOrNull(index) ?: return false
    val nextPattern = pattern.trim()
    if (!validateTxtPurifyRulePattern(current.regex, nextPattern)) return false
    return commitTxtPurifyRuleEdit(updateTxtPurifyRulePatternModel(txtPurifyRulesText, index, nextPattern))
}

fun EditorController.updateTxtPurifyRuleReplacement(index: Int, replacement: String) {
    commitTxtPurifyRuleEdit(updateTxtPurifyRuleReplacementModel(txtPurifyRulesText, index, replacement))
}

fun EditorController.updateTxtPurifyRuleRegex(index: Int, regex: Boolean): Boolean {
    val current = parseTxtPurifyRuleItems(txtPurifyRulesText).getOrNull(index) ?: return false
    if (!validateTxtPurifyRulePattern(regex, current.pattern)) return false
    return commitTxtPurifyRuleEdit(updateTxtPurifyRuleRegexModel(txtPurifyRulesText, index, regex))
}

fun EditorController.updateTxtPurifyRuleEnabled(index: Int, enabled: Boolean): Boolean {
    val current = parseTxtPurifyRuleItems(txtPurifyRulesText).getOrNull(index) ?: return false
    if (enabled && !validateTxtPurifyRulePattern(current.regex, current.pattern)) return false
    return commitTxtPurifyRuleEdit(updateTxtPurifyRuleEnabledModel(txtPurifyRulesText, index, enabled))
}

fun EditorController.updateTxtPurifyRule(
    index: Int,
    target: String,
    name: String,
    regex: Boolean,
    pattern: String,
    replacement: String
): Boolean {
    if (parseTxtPurifyRuleItems(txtPurifyRulesText).getOrNull(index) == null) return false
    val nextPattern = pattern.trim()
    if (!validateTxtPurifyRulePattern(regex, nextPattern)) return false
    return commitTxtPurifyRuleEdit(
        updateTxtPurifyRuleModel(
            rulesText = txtPurifyRulesText,
            index = index,
            target = target,
            name = name,
            regex = regex,
            pattern = nextPattern,
            replacement = replacement
        )
    )
}

fun EditorController.deleteTxtPurifyRule(index: Int) {
    commitTxtPurifyRuleEdit(deleteTxtPurifyRuleModel(txtPurifyRulesText, index), applyChangedRule = false)
}

fun EditorController.moveTxtPurifyRule(fromIndex: Int, toIndex: Int) {
    commitTxtPurifyRuleEdit(
        moveTxtPurifyRuleModel(txtPurifyRulesText, fromIndex, toIndex),
        applyChangedRule = false
    )
}

fun EditorController.saveTxtPurifyRules(): Boolean {
    if (!validateTxtPurifyRuleItems(parseTxtPurifyRuleItems(txtPurifyRulesText))) return false
    persistTxtPurifyRules()
    applyTxtBodyPurifyRulesAfterRuleChange()
    statusMessage = "TXT \u51c0\u5316\u89c4\u5219\u5df2\u4fdd\u5b58"
    return true
}

private fun EditorController.applyTxtBodyPurifyRulesAfterRuleChangeIfNeeded(rule: TxtPurifyRuleItem) {
    if (!rule.enabled || normalizeTxtPurifyTarget(rule.target) != TXT_PURIFY_TARGET_BODY) return
    applyTxtBodyPurifyRulesAfterRuleChange()
}

private fun EditorController.applyTxtBodyPurifyRulesAfterRuleChange() {
    if (kind != DocumentKind.Txt || txtMoveChapterSyncPending) return
    val result = applyTxtPurifyTargets(applyBody = true, applyCatalog = false) ?: return
    if (result.changed) {
        statusMessage = "\u6b63\u6587\u51c0\u5316\u89c4\u5219\u5df2\u5e94\u7528\uff1a\u6b63\u6587 ${result.bodyCount} \u5904"
    }
}

private fun EditorController.persistTxtPurifyRules() {
    settingsPreferences.saveTxtPurifyRules(txtPurifyRulesText)
}

fun EditorController.updateTxtBookTitleRulesText(value: String) {
    txtBookTitleRulesText = value
}

fun EditorController.txtBookTitleRuleItems(): List<TxtBookTitleRuleItem> {
    val items = parseTxtBookTitleRuleItems(txtBookTitleRulesText)
    val matchedIndex = resolveTxtBookTitleFilter(txtBookTitleFilterSources()).ruleIndex
    return items.map { item ->
        item.copy(matchCount = if (item.index == matchedIndex) 1 else 0)
    }
}

private fun EditorController.commitTxtBookTitleRuleEdit(result: TxtBookTitleRuleEditResult): Boolean {
    val matchedIndexBeforeEdit = currentTxtBookTitleMatchedRuleIndex()
    txtBookTitleRulesText = applyTxtBookTitleRuleEditText(result) ?: return false
    persistTxtBookTitleRules()
    reapplyTxtBookTitleFilterAfterRuleEdit(matchedIndexBeforeEdit)
    return true
}

private fun EditorController.currentTxtBookTitleMatchedRuleIndex(): Int? {
    if (kind != DocumentKind.Txt) return null
    return resolveTxtBookTitleFilter(txtBookTitleFilterSources()).ruleIndex
}

private fun EditorController.reapplyTxtBookTitleFilterAfterRuleEdit(matchedIndexBeforeEdit: Int?) {
    if (kind != DocumentKind.Txt) return
    val matchedIndexAfterEdit = currentTxtBookTitleMatchedRuleIndex()
    if (matchedIndexBeforeEdit != null || matchedIndexAfterEdit != null) {
        applyTxtBookTitleFilter(showNoMatchMessage = false)
    }
}

fun EditorController.addTxtBookTitleRule(
    name: String = "",
    pattern: String = "",
    replacement: String = ""
): Boolean {
    val nextPattern = pattern.trim()
    if (!validateTxtBookTitleRulePattern(true, nextPattern)) return false
    return commitTxtBookTitleRuleEdit(
        addTxtBookTitleRuleModel(
            rulesText = txtBookTitleRulesText,
            name = name,
            pattern = nextPattern,
            replacement = replacement
        )
    )
}

fun EditorController.updateTxtBookTitleRuleName(index: Int, name: String) {
    commitTxtBookTitleRuleEdit(updateTxtBookTitleRuleNameModel(txtBookTitleRulesText, index, name))
}

fun EditorController.updateTxtBookTitleRulePattern(index: Int, pattern: String): Boolean {
    if (parseTxtBookTitleRuleItems(txtBookTitleRulesText).getOrNull(index) == null) return false
    val nextPattern = pattern.trim()
    if (!validateTxtBookTitleRulePattern(true, nextPattern)) return false
    return commitTxtBookTitleRuleEdit(
        updateTxtBookTitleRulePatternModel(txtBookTitleRulesText, index, nextPattern)
    )
}

fun EditorController.updateTxtBookTitleRuleReplacement(index: Int, replacement: String) {
    commitTxtBookTitleRuleEdit(updateTxtBookTitleRuleReplacementModel(txtBookTitleRulesText, index, replacement))
}

fun EditorController.updateTxtBookTitleRuleRegex(index: Int, @Suppress("UNUSED_PARAMETER") regex: Boolean): Boolean {
    val current = parseTxtBookTitleRuleItems(txtBookTitleRulesText).getOrNull(index) ?: return false
    if (!validateTxtBookTitleRulePattern(true, current.pattern)) return false
    return commitTxtBookTitleRuleEdit(updateTxtBookTitleRuleRegexModel(txtBookTitleRulesText, index))
}

fun EditorController.updateTxtBookTitleRule(
    index: Int,
    name: String,
    pattern: String,
    replacement: String
): Boolean {
    if (parseTxtBookTitleRuleItems(txtBookTitleRulesText).getOrNull(index) == null) return false
    val nextPattern = pattern.trim()
    if (!validateTxtBookTitleRulePattern(true, nextPattern)) return false
    return commitTxtBookTitleRuleEdit(
        updateTxtBookTitleRuleModel(
            rulesText = txtBookTitleRulesText,
            index = index,
            name = name,
            pattern = nextPattern,
            replacement = replacement
        )
    )
}

fun EditorController.deleteTxtBookTitleRule(index: Int) {
    commitTxtBookTitleRuleEdit(deleteTxtBookTitleRuleModel(txtBookTitleRulesText, index))
}

fun EditorController.moveTxtBookTitleRule(fromIndex: Int, toIndex: Int) {
    commitTxtBookTitleRuleEdit(moveTxtBookTitleRuleModel(txtBookTitleRulesText, fromIndex, toIndex))
}

fun EditorController.updateTxtBookTitle(value: String): Boolean {
    if (kind != DocumentKind.Txt) return false
    val result = updateTxtBookTitleModel(title, value)
    statusMessage = result.message
    if (!result.success) return false
    title = result.title
    return true
}

fun EditorController.saveTxtBookTitleRules(): Boolean {
    if (!validateTxtBookTitleRuleItems(parseTxtBookTitleRuleItems(txtBookTitleRulesText))) return false
    persistTxtBookTitleRules()
    statusMessage = "TXT \u4e66\u540d\u8fc7\u6ee4\u89c4\u5219\u5df2\u4fdd\u5b58"
    applyTxtBookTitleFilter(showNoMatchMessage = false)
    return true
}

private fun EditorController.persistTxtBookTitleRules() {
    settingsPreferences.saveTxtBookTitleRules(txtBookTitleRulesText)
}

fun EditorController.updateTxtChapterThresholds(shortThreshold: Int, longThreshold: Int) {
    if (warnTxtMoveChapterSyncPending("\u4fee\u6539\u7ae0\u8282\u63d0\u793a\u8bbe\u7f6e")) return
    txtShortChapterThreshold = shortThreshold.coerceAtLeast(0)
    txtLongChapterThreshold = longThreshold.coerceAtLeast(0)
    settingsPreferences.saveTxtChapterThresholds(txtShortChapterThreshold, txtLongChapterThreshold)
    refreshTxtDocumentChapters()
    refreshChapters()
    statusMessage = "TXT \u7ae0\u8282\u9608\u503c\u5df2\u4fdd\u5b58"
}

fun EditorController.updateTxtChapterHintSettings(
    shortHintEnabled: Boolean,
    longHintEnabled: Boolean,
    shortThreshold: Int,
    longThreshold: Int,
    hintMode: String
) {
    if (warnTxtMoveChapterSyncPending("\u4fee\u6539\u7ae0\u8282\u63d0\u793a\u8bbe\u7f6e")) return
    txtShortChapterHintEnabled = shortHintEnabled
    txtLongChapterHintEnabled = longHintEnabled
    txtShortChapterThreshold = shortThreshold.coerceAtLeast(0)
    txtLongChapterThreshold = longThreshold.coerceAtLeast(0)
    txtChapterHintMode = hintMode.takeIf { it in TXT_CHAPTER_HINT_MODES } ?: TXT_CHAPTER_HINT_MODE_AUTO
    settingsPreferences.saveTxtChapterHintSettings(
        shortHintEnabled = txtShortChapterHintEnabled,
        longHintEnabled = txtLongChapterHintEnabled,
        shortThreshold = txtShortChapterThreshold,
        longThreshold = txtLongChapterThreshold,
        hintMode = txtChapterHintMode
    )
    refreshTxtDocumentChapters()
    refreshChapters()
    statusMessage = "TXT \u7ae0\u8282\u63d0\u793a\u8bbe\u7f6e\u5df2\u4fdd\u5b58"
}

fun EditorController.updateTxtShortChapterHintEnabled(enabled: Boolean) {
    if (warnTxtMoveChapterSyncPending("\u4fee\u6539\u77ed\u7ae0\u63d0\u793a")) return
    txtShortChapterHintEnabled = enabled
    settingsPreferences.saveTxtShortChapterHintEnabled(enabled)
    refreshTxtDocumentChapters()
    refreshChapters()
    statusMessage = if (enabled) {
        "\u77ed\u7ae0\u63d0\u793a\u5df2\u5f00\u542f"
    } else {
        "\u77ed\u7ae0\u63d0\u793a\u5df2\u5173\u95ed"
    }
}

fun EditorController.updateTxtLongChapterHintEnabled(enabled: Boolean) {
    if (warnTxtMoveChapterSyncPending("\u4fee\u6539\u957f\u7ae0\u63d0\u793a")) return
    txtLongChapterHintEnabled = enabled
    settingsPreferences.saveTxtLongChapterHintEnabled(enabled)
    refreshTxtDocumentChapters()
    refreshChapters()
    statusMessage = if (enabled) {
        "\u957f\u7ae0\u63d0\u793a\u5df2\u5f00\u542f"
    } else {
        "\u957f\u7ae0\u63d0\u793a\u5df2\u5173\u95ed"
    }
}

fun EditorController.updateTxtAutoNumberOnSave(enabled: Boolean) {
    txtAutoNumberOnSave = enabled
    settingsPreferences.saveTxtAutoNumberOnSave(enabled)
    statusMessage = if (enabled) {
        "\u4fdd\u5b58\u81ea\u52a8\u7f16\u53f7\u5df2\u5f00\u542f"
    } else {
        "\u4fdd\u5b58\u81ea\u52a8\u7f16\u53f7\u5df2\u5173\u95ed"
    }
}

fun EditorController.updateTxtChapterNumberStartAtOneOnSave(enabled: Boolean) {
    txtChapterNumberStartAtOneOnSave = enabled
    settingsPreferences.saveTxtChapterNumberStartAtOneOnSave(enabled)
    statusMessage = if (enabled) {
        "保存章节编号从 1 开始"
    } else {
        "保存章节编号从 0 开始"
    }
}
