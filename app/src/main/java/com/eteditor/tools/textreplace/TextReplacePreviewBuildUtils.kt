package com.eteditor

internal const val REPLACEMENT_PREVIEW_MAX_MATCHES_PER_RULE = 50
internal const val TEXT_SEARCH_DISPLAY_BATCH_SIZE = 30

internal fun buildTextReplaceSearchResultsForRules(
    rules: List<TextReplaceRule>,
    parameters: TextReplaceParameters,
    sourceResolver: (TextReplaceParameters) -> List<SearchSource>,
    resolveLocation: (Int, Int, Int, String) -> TextSearchResultLocation,
    maxMatches: Int = REPLACEMENT_PREVIEW_MAX_MATCHES_PER_RULE
): List<TextSearchResult> {
    return rules.flatMapIndexed { index, rule ->
        val sources = sourceResolver(
            parameters.copy(
                target = if (rule.textOnly) {
                    TEXT_REPLACE_TARGET_VISIBLE
                } else {
                    TEXT_REPLACE_TARGET_SOURCE
                }
            )
        )
        if (sources.isEmpty()) return@flatMapIndexed emptyList()
        buildTextSearchResults(
            sources = sources,
            rule = rule,
            caseSensitive = false,
            ruleIndex = index,
            idPrefix = "rule-$index",
            maxMatches = maxMatches,
            resolveLocation = resolveLocation
        )
    }
}

internal fun buildReplacementFilePreviewForParameters(
    toolId: String,
    parameters: TextReplaceParameters,
    input: String,
    sourceResolver: (TextReplaceParameters) -> List<SearchSource>,
    resolveLocation: (Int, Int, Int, String) -> TextSearchResultLocation
): ReplacementFilePreview {
    return buildReplacementFilePreviewModel(
        toolId = toolId,
        input = input,
        sources = sourceResolver(parameters),
        resolveLocation = resolveLocation
    )
}

internal fun buildReplacementFilePreviewForParameters(
    toolId: String,
    parameters: TextReplaceParameters,
    parsedRules: List<ParsedReplacementRule>,
    skippedRules: List<ReplacementSkippedRule>,
    sourceResolver: (TextReplaceParameters) -> List<SearchSource>,
    resolveLocation: (Int, Int, Int, String) -> TextSearchResultLocation
): ReplacementFilePreview {
    return buildReplacementFilePreviewModel(
        toolId = toolId,
        parsedRules = parsedRules,
        skippedRules = skippedRules,
        sources = sourceResolver(parameters),
        resolveLocation = resolveLocation
    )
}

internal fun buildReplacementFilePreviewModel(
    toolId: String,
    input: String,
    sources: List<SearchSource>,
    resolveLocation: (Int, Int, Int, String) -> TextSearchResultLocation
): ReplacementFilePreview {
    val (parsedRules, skippedRules) = parseReplacementRules(input)
    return buildReplacementFilePreviewModel(
        toolId = toolId,
        parsedRules = parsedRules,
        skippedRules = skippedRules,
        sources = sources,
        resolveLocation = resolveLocation
    )
}

internal fun buildReplacementFilePreviewModel(
    toolId: String,
    parsedRules: List<ParsedReplacementRule>,
    skippedRules: List<ReplacementSkippedRule>,
    sources: List<SearchSource>,
    resolveLocation: (Int, Int, Int, String) -> TextSearchResultLocation
): ReplacementFilePreview {
    val rules = mutableListOf<ReplacementPreviewRule>()
    var limitReached = false
    for ((index, rule) in parsedRules.withIndex()) {
        val previewRule = buildReplacementPreviewRule(
            index = index,
            rule = rule,
            sources = sources,
            maxMatches = REPLACEMENT_PREVIEW_MAX_MATCHES_PER_RULE,
            resolveLocation = resolveLocation
        )
        rules += previewRule
        if (previewRule.matches.size >= REPLACEMENT_PREVIEW_MAX_MATCHES_PER_RULE) {
            limitReached = true
        }
    }
    return replacementFilePreviewFromRules(
        toolId = toolId,
        totalRules = parsedRules.size + skippedRules.size,
        validRuleCount = parsedRules.size,
        skippedRules = skippedRules,
        rules = rules,
        previewLimitReached = limitReached
    )
}

internal fun buildReplacementPreviewRule(
    index: Int,
    rule: ParsedReplacementRule,
    sources: List<SearchSource>,
    maxMatches: Int,
    resolveLocation: (Int, Int, Int, String) -> TextSearchResultLocation
): ReplacementPreviewRule {
    return ReplacementPreviewRule(
        id = "replacement-rule-$index",
        lineNo = rule.lineNo,
        pattern = rule.pattern,
        replacement = rule.replacement,
        regex = rule.regex,
        matches = buildReplacementPreviewMatches(
            sources = sources,
            rule = rule,
            // 静读专用对齐网页版/静读/Sigil 语义：区分大小写匹配
            caseSensitive = true,
            idPrefix = "replacement-$index",
            resolveLocation = resolveLocation,
            maxMatches = maxMatches
        )
    )
}

internal fun replacementFilePreviewFromRules(
    toolId: String,
    totalRules: Int,
    validRuleCount: Int,
    skippedRules: List<ReplacementSkippedRule>,
    rules: List<ReplacementPreviewRule>,
    previewLimitReached: Boolean
): ReplacementFilePreview {
    return ReplacementFilePreview(
        toolId = toolId,
        totalRules = totalRules,
        multiRules = rules.filter { it.matches.size > 1 },
        singleRules = rules.filter { it.matches.size == 1 },
        zeroRules = rules.filter { it.matches.isEmpty() },
        skippedRules = skippedRules,
        validRuleCount = validRuleCount,
        scannedRuleCount = rules.size,
        previewLimitReached = previewLimitReached
    )
}

internal fun textSearchPreviewLimitReached(results: List<TextSearchResult>): Boolean {
    if (results.isEmpty()) return false
    return results.groupingBy { it.ruleIndex }
        .eachCount()
        .any { it.value >= REPLACEMENT_PREVIEW_MAX_MATCHES_PER_RULE }
}

internal fun textSearchFoundStatusMessage(results: List<TextSearchResult>): String {
    return if (textSearchPreviewLimitReached(results)) {
        "命中较多，每条规则仅显示前 $REPLACEMENT_PREVIEW_MAX_MATCHES_PER_RULE 处；点“全选”执行会替换全部"
    } else {
        textSearchFoundMessage(results.size)
    }
}

// 单条文本搜索替换已改为全量计数+分批展示：命中超过一批时提示可展开查看全部，全选执行替换全部。
// totalMatchCount 为轻量计数扫描拿到的真实总数，不随展示批次变化。
internal fun textSearchFoundStatusMessageForDisplay(totalMatchCount: Int): String {
    if (totalMatchCount <= 0) return textSearchFoundMessage(0)
    val overBatch = totalMatchCount > TEXT_SEARCH_DISPLAY_BATCH_SIZE
    return if (overBatch) {
        "命中 $totalMatchCount 处，默认显示前 $TEXT_SEARCH_DISPLAY_BATCH_SIZE 条，点“展开更多”查看全部；勾选当前展示项后“执行替换”会替换全部"
    } else {
        textSearchFoundMessage(totalMatchCount)
    }
}

// 预览超限时的"全选"意图判定：某条规则展示已达上限（未展示全）且展示出来的匹配全部勾选，
// 视为整条规则全要，交引擎按与预览相同口径扫全书替换以覆盖未展示部分；否则只按快照位置精确替换。
internal fun replacementSelectionTriggersFullScan(
    totalMatches: Int,
    selectedMatches: Int,
    maxMatches: Int,
    findPatternNotEmpty: Boolean
): Boolean {
    return selectedMatches > 0 &&
        selectedMatches == totalMatches &&
        totalMatches >= maxMatches &&
        findPatternNotEmpty
}

// 分批展示时的"全选"意图判定：当前展示出来的匹配全部勾选，且还有未展示的命中，
// 视为整条规则全要，交引擎按与预览相同口径扫全书替换以覆盖未展示部分；否则只按快照位置精确替换。
internal fun replacementSelectionTriggersFullScanByDisplay(
    totalMatches: Int,
    displayedMatches: Int,
    selectedMatches: Int,
    findPatternNotEmpty: Boolean
): Boolean {
    return selectedMatches > 0 &&
        selectedMatches == displayedMatches &&
        displayedMatches < totalMatches &&
        findPatternNotEmpty
}
