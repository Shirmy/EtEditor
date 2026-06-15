package com.eteditor

import com.eteditor.core.TxtChapter
import kotlin.math.roundToInt

internal data class TxtAutoChapterThresholds(
    val shortThreshold: Int,
    val longThreshold: Int,
    val sampleCount: Int,
    val medianWordCount: Int
)

internal data class TxtAutoChapterHintStats(
    val sampleCount: Int,
    val medianWordCount: Int?,
    val shortThreshold: Int?,
    val longThreshold: Int?,
    val shortHitCount: Int,
    val longHitCount: Int
) {
    val hasThresholds: Boolean
        get() = medianWordCount != null && shortThreshold != null && longThreshold != null
}

private const val TXT_AUTO_HINT_MIN_SAMPLE_COUNT = 8
internal const val TXT_AUTO_SHORT_CHAPTER_FACTOR = 0.5f
internal const val TXT_AUTO_LONG_CHAPTER_FACTOR = 2.0f
private val TxtAutoHintExcludedStatuses = setOf("疑似缺章", "重复序号", "序号回退", "重名")
private val TxtAutoHintExcludedTitleRegex = Regex(
    "(序章|楔子|引子|前言|番外|后记|尾声|作者的话|作者有话|完本感言|上架感言|请假|公告|感言|外传)",
    RegexOption.IGNORE_CASE
)

internal fun txtAutoChapterThresholds(chapters: List<TxtChapter>): TxtAutoChapterThresholds? {
    return txtAutoChapterThresholdsForCounts(txtAutoChapterWordCounts(chapters))
}

internal fun txtAutoChapterHintStats(chapters: List<TxtChapter>): TxtAutoChapterHintStats {
    val counts = txtAutoChapterWordCounts(chapters)
    val thresholds = txtAutoChapterThresholdsForCounts(counts)
    return TxtAutoChapterHintStats(
        sampleCount = counts.size,
        medianWordCount = thresholds?.medianWordCount,
        shortThreshold = thresholds?.shortThreshold,
        longThreshold = thresholds?.longThreshold,
        shortHitCount = thresholds?.let { resolved ->
            chapters.count { chapter -> chapter.wordCount in 1 until resolved.shortThreshold }
        } ?: 0,
        longHitCount = thresholds?.let { resolved ->
            chapters.count { chapter -> chapter.wordCount > resolved.longThreshold }
        } ?: 0
    )
}

private fun txtAutoChapterWordCounts(chapters: List<TxtChapter>): List<Int> {
    return chapters
        .asSequence()
        .filter { chapter -> chapter.wordCount > 0 }
        .filterNot { chapter -> chapter.status.any { it in TxtAutoHintExcludedStatuses } }
        .filterNot { chapter -> TxtAutoHintExcludedTitleRegex.containsMatchIn(chapter.title) }
        .map { chapter -> chapter.wordCount }
        .sorted()
        .toList()
}

private fun txtAutoChapterThresholdsForCounts(counts: List<Int>): TxtAutoChapterThresholds? {
    if (counts.size < TXT_AUTO_HINT_MIN_SAMPLE_COUNT) return null

    val median = medianInt(counts)
    val shortThreshold = (median * TXT_AUTO_SHORT_CHAPTER_FACTOR).roundToInt().coerceAtLeast(1)
    val longThreshold = (median * TXT_AUTO_LONG_CHAPTER_FACTOR).roundToInt().coerceAtLeast(shortThreshold + 1)
    return TxtAutoChapterThresholds(
        shortThreshold = shortThreshold,
        longThreshold = longThreshold,
        sampleCount = counts.size,
        medianWordCount = median
    )
}

internal fun resolveTxtChapterLengthHintConfig(
    config: TxtChapterDetectionConfig,
    chapters: List<TxtChapter>
): TxtChapterDetectionConfig {
    if (config.hintMode != TXT_CHAPTER_HINT_MODE_AUTO) return config
    val thresholds = txtAutoChapterThresholds(chapters) ?: return config
    return config.copy(
        shortThreshold = if (config.shortThreshold > 0) thresholds.shortThreshold else 0,
        longThreshold = if (config.longThreshold > 0) thresholds.longThreshold else 0
    )
}

internal fun initialTxtChapterDetectionConfig(config: TxtChapterDetectionConfig): TxtChapterDetectionConfig {
    return if (config.hintMode == TXT_CHAPTER_HINT_MODE_AUTO) {
        config.copy(shortThreshold = 0, longThreshold = 0)
    } else {
        config
    }
}

private fun medianInt(sortedCounts: List<Int>): Int {
    val middle = sortedCounts.size / 2
    return if (sortedCounts.size % 2 == 0) {
        ((sortedCounts[middle - 1] + sortedCounts[middle]) / 2f).roundToInt()
    } else {
        sortedCounts[middle]
    }
}
