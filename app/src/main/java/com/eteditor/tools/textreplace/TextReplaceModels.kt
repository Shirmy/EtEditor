package com.eteditor

internal data class TextReplaceRule(
    val find: String,
    val replacement: String,
    val regex: Boolean,
    val textOnly: Boolean = false,
    val enabled: Boolean = true
)

internal data class ParsedReplacementRule(
    val lineNo: Int,
    val pattern: String,
    val replacement: String,
    val regex: Boolean
)

internal data class TextReplaceRuleBuildResult(
    val rules: List<TextReplaceRule>?,
    val message: String = ""
)

internal fun TextReplaceParameters.isReplacementMode(): Boolean {
    return mode == TEXT_REPLACE_MODE_REPLACEMENT ||
        (mode == TEXT_REPLACE_MODE_BATCH &&
            batchSource == TEXT_REPLACE_BATCH_FILE)
}

data class ReplacementPreviewMatch(
    val id: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val fileName: String,
    val context: String,
    val matchText: String,
    val contextMatchStart: Int,
    val contextMatchEnd: Int,
    val sourceStart: Int,
    val sourceEnd: Int,
    val replacementText: String
)

data class ReplacementPreviewRule(
    val id: String,
    val lineNo: Int,
    val pattern: String,
    val replacement: String,
    val regex: Boolean,
    val matches: List<ReplacementPreviewMatch>
)

data class ReplacementSkippedRule(
    val lineNo: Int,
    val reason: String,
    val text: String
)

data class ReplacementFilePreview(
    val toolId: String,
    val totalRules: Int,
    val multiRules: List<ReplacementPreviewRule>,
    val singleRules: List<ReplacementPreviewRule>,
    val zeroRules: List<ReplacementPreviewRule>,
    val skippedRules: List<ReplacementSkippedRule>,
    val validRuleCount: Int = multiRules.size + singleRules.size + zeroRules.size,
    val scannedRuleCount: Int = multiRules.size + singleRules.size + zeroRules.size,
    val previewLimitReached: Boolean = false
) {
    val validRules: Int get() = validRuleCount
    val displayedMatches: Int get() = (multiRules + singleRules).sumOf { it.matches.size }
}
