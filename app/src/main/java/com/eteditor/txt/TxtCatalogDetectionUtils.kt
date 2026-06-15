package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.TxtChapter

internal fun detectTxtChaptersWithCatalogConfig(
    text: String,
    config: TxtChapterDetectionConfig,
    autoKeys: Set<String>,
    supplementedCatalogLines: List<TxtSupplementedCatalogLine>,
    applyRuleReplacementToTitle: Boolean = true,
    forcedLineIndicesOverride: Set<Int>? = null
): List<TxtChapter> {
    val detectionConfig = initialTxtChapterDetectionConfig(config)
    val forcedLineIndices = (forcedLineIndicesOverride ?: supplementedCatalogLines
        .asSequence()
        .map { it.lineIndex }
        .toSet())
        .filterNot { it in detectionConfig.hiddenLineIndices }
        .toSet()
    val chapters = ChapterDetector.detectTxtChapters(
        text = text,
        shortThreshold = detectionConfig.shortThreshold,
        longThreshold = detectionConfig.longThreshold,
        customRules = activeTxtChapterPatternRules(detectionConfig, autoKeys),
        applyRuleReplacementToTitle = applyRuleReplacementToTitle,
        hiddenLineIndices = detectionConfig.hiddenLineIndices,
        forcedLineIndices = forcedLineIndices
    ).mapIndexed { index, chapter -> chapter.copy(index = index + 1) }
    return refreshTxtChapterStatuses(chapters, config)
}

internal fun detectTxtChaptersWithMappingsAppliedToText(
    text: String,
    config: TxtChapterDetectionConfig,
    autoKeys: Set<String>,
    supplementedCatalogLines: List<TxtSupplementedCatalogLine>
): TxtCatalogDetectionResult {
    val mappedChapters = detectTxtChaptersWithCatalogConfig(
        text = text,
        config = config,
        autoKeys = autoKeys,
        supplementedCatalogLines = supplementedCatalogLines,
        applyRuleReplacementToTitle = true
    )
    return TxtCatalogDetectionResult(
        enabledKeys = autoKeys,
        text = text,
        chapters = mappedChapters,
        mappedTitleCount = countTxtMappedChapterTitles(text, mappedChapters)
    )
}

internal fun detectTxtChaptersWithAutoSelectedRules(
    text: String,
    config: TxtChapterDetectionConfig,
    detectWithKeys: (Set<String>) -> TxtCatalogDetectionResult
): TxtCatalogDetectionResult {
    val candidates = txtMatchingChapterRuleKeys(text, config)
    if (candidates.isEmpty()) {
        return detectWithKeys(emptySet())
    }

    val enabledKeys = linkedSetOf<String>()
    var acceptedResult: TxtCatalogDetectionResult? = null
    for (key in candidates) {
        val trialKeys = enabledKeys + key
        val trialResult = detectWithKeys(trialKeys)
        val currentResult = acceptedResult
        val currentMissingCount = currentResult?.let { txtAutoMissingChapterCount(it) } ?: 0
        val trialMissingCount = txtAutoMissingChapterCount(trialResult)

        if (currentResult != null && currentMissingCount <= 0 && trialMissingCount > 0) {
            return currentResult
        }

        if (trialMissingCount > 0) {
            enabledKeys += key
            acceptedResult = trialResult
            continue
        }

        if (currentResult == null || currentMissingCount > 0) {
            enabledKeys += key
            acceptedResult = trialResult
            if (txtAutoLongChapterCount(trialResult) > 0) continue
            return trialResult
        }

        if (txtAutoLongChapterCount(currentResult) <= 0) {
            return currentResult
        }

        if (!txtAutoLongChapterTrialImproves(currentResult, trialResult)) {
            return currentResult
        }

        enabledKeys += key
        acceptedResult = trialResult
        if (txtAutoLongChapterCount(trialResult) <= 0) {
            return trialResult
        }
    }
    return acceptedResult ?: detectWithKeys(emptySet())
}

internal fun countTxtMappedChapterTitles(
    text: String,
    chapters: List<TxtChapter>
): Int {
    if (chapters.isEmpty()) return 0
    val lines = text.split('\n').map { it.removeSuffix("\r") }
    return chapters.count { chapter ->
        chapter.title.isNotBlank() &&
            chapter.lineIndex in lines.indices &&
            chapter.title != lines[chapter.lineIndex]
    }
}

private fun txtAutoLongChapterTrialImproves(
    current: TxtCatalogDetectionResult,
    trial: TxtCatalogDetectionResult
): Boolean {
    if (trial.chapters.any { chapter ->
            chapter.status.any { status -> status == "疑似缺章" || status == "重复序号" || status == "序号回退" }
        }
    ) {
        return false
    }
    if (trial.chapters.size <= current.chapters.size) return false
    val currentLongCount = txtAutoLongChapterCount(current)
    val trialLongCount = txtAutoLongChapterCount(trial)
    if (trialLongCount < currentLongCount) return true
    return txtAutoMaxChapterWordCount(trial) < txtAutoMaxChapterWordCount(current)
}

private fun txtAutoMissingChapterCount(result: TxtCatalogDetectionResult): Int {
    return result.chapters.count { chapter -> "疑似缺章" in chapter.status }
}

private fun txtAutoLongChapterCount(result: TxtCatalogDetectionResult): Int {
    return result.chapters.count { chapter -> "超长章" in chapter.status }
}

private fun txtAutoMaxChapterWordCount(result: TxtCatalogDetectionResult): Int {
    return result.chapters.maxOfOrNull { chapter -> chapter.wordCount } ?: 0
}

private fun txtMatchingChapterRuleKeys(
    text: String,
    config: TxtChapterDetectionConfig
): List<String> {
    val items = parseTxtChapterRuleItems(config.rulesText)
        .filter { it.pattern.isNotBlank() }
    if (items.isEmpty()) {
        return emptyList()
    }
    val autoKeys = linkedSetOf<String>()
    items.forEach { item ->
        if (txtChapterRuleHasMatch(text, item.pattern)) {
            autoKeys += txtChapterRuleKey(item)
        }
    }
    return autoKeys.toList()
}
