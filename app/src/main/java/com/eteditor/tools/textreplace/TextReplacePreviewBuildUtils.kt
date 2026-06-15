package com.eteditor

internal const val REPLACEMENT_PREVIEW_MAX_MATCHES_PER_RULE = 50

internal fun buildTextReplaceSearchResultsForRules(
    rules: List<TextReplaceRule>,
    parameters: TextReplaceParameters,
    sourceResolver: (TextReplaceParameters) -> List<SearchSource>,
    resolveLocation: (Int, Int, Int, String) -> TextSearchResultLocation
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
            caseSensitive = false,
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
