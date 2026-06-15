package com.eteditor

import com.eteditor.core.EpubBook
import com.eteditor.core.TxtDocument

private const val DEFAULT_SEARCH_CONTEXT_CHARS = 36

internal data class SearchSource(
    val chapterIndex: Int,
    val title: String,
    val fileName: String,
    val text: String,
    val sourceOffset: Int = 0
)

internal data class SearchContext(
    val text: String,
    val matchStart: Int,
    val matchEnd: Int
)

internal data class TextSearchResultLocation(
    val chapterIndex: Int,
    val title: String
)

internal data class ReplacementMatchPlan(
    val chapterIndex: Int,
    val sourceStart: Int,
    val sourceEnd: Int,
    val replacementText: String
)

internal data class TxtTextReplaceApplyResult(
    val text: String,
    val changedSources: Int,
    val replacements: Int
)

internal fun buildTextSearchResults(
    sources: List<SearchSource>,
    rule: TextReplaceRule,
    caseSensitive: Boolean,
    ruleIndex: Int,
    idPrefix: String = "rule-0",
    resolveLocation: (Int, Int, Int, String) -> TextSearchResultLocation
): List<TextSearchResult> {
    val results = mutableListOf<TextSearchResult>()
    sources.forEach { source ->
        val matches = if (rule.regex) {
            val options = if (caseSensitive) emptySet<RegexOption>() else setOf(RegexOption.IGNORE_CASE)
            regexSearchRanges(source.text, Regex(rule.find, options))
        } else {
            plainSearchRanges(source.text, rule.find, caseSensitive)
        }
        matches.forEachIndexed { matchIndex, (start, end) ->
            val context = searchContext(source.text, start, end)
            val absoluteStart = source.sourceOffset + start
            val absoluteEnd = source.sourceOffset + end
            val location = resolveLocation(
                absoluteStart,
                absoluteEnd,
                source.chapterIndex,
                source.title
            )
            results += TextSearchResult(
                id = "$idPrefix-${source.chapterIndex}-${source.sourceOffset}-$matchIndex-$start",
                ruleIndex = ruleIndex,
                chapterIndex = location.chapterIndex,
                chapterTitle = location.title,
                context = context.text,
                matchText = source.text.substring(start, end),
                contextMatchStart = context.matchStart,
                contextMatchEnd = context.matchEnd,
                sourceStart = absoluteStart,
                sourceEnd = absoluteEnd
            )
        }
    }
    return results
}

internal fun textSearchResultsAfterSingleReplacement(
    results: List<TextSearchResult>,
    sourceText: String?,
    replacedId: String,
    sourceStart: Int,
    sourceEnd: Int,
    replacementDelta: Int,
    resolveLocation: (Int, Int, Int, String) -> TextSearchResultLocation
): List<TextSearchResult> {
    return results.mapNotNull { result ->
        when {
            result.id == replacedId -> null
            result.sourceStart < sourceEnd && result.sourceEnd > sourceStart -> null
            else -> {
                val shiftedStart = if (result.sourceStart >= sourceEnd) {
                    result.sourceStart + replacementDelta
                } else {
                    result.sourceStart
                }
                val shiftedEnd = if (result.sourceStart >= sourceEnd) {
                    result.sourceEnd + replacementDelta
                } else {
                    result.sourceEnd
                }
                if (sourceText == null || shiftedStart < 0 || shiftedEnd > sourceText.length || shiftedEnd <= shiftedStart) {
                    result.copy(
                        sourceStart = shiftedStart,
                        sourceEnd = shiftedEnd
                    )
                } else {
                    val context = searchContext(sourceText, shiftedStart, shiftedEnd)
                    val location = resolveLocation(
                        shiftedStart,
                        shiftedEnd,
                        result.chapterIndex,
                        result.chapterTitle
                    )
                    result.copy(
                        chapterIndex = location.chapterIndex,
                        chapterTitle = location.title,
                        context = context.text,
                        matchText = sourceText.substring(shiftedStart, shiftedEnd),
                        contextMatchStart = context.matchStart,
                        contextMatchEnd = context.matchEnd,
                        sourceStart = shiftedStart,
                        sourceEnd = shiftedEnd
                    )
                }
            }
        }
    }
}

internal fun buildReplacementPreviewMatches(
    sources: List<SearchSource>,
    rule: ParsedReplacementRule,
    caseSensitive: Boolean,
    idPrefix: String,
    resolveLocation: (Int, Int, Int, String) -> TextSearchResultLocation,
    maxMatches: Int = Int.MAX_VALUE
): List<ReplacementPreviewMatch> {
    if (maxMatches <= 0) return emptyList()
    val results = mutableListOf<ReplacementPreviewMatch>()
    val options = if (caseSensitive) emptySet<RegexOption>() else setOf(RegexOption.IGNORE_CASE)
    fun addMatch(
        source: SearchSource,
        matchIndex: Int,
        start: Int,
        end: Int,
        replacementText: String
    ) {
        if (results.size >= maxMatches) return
        val context = searchContext(source.text, start, end)
        val absoluteStart = source.sourceOffset + start
        val absoluteEnd = source.sourceOffset + end
        val location = resolveLocation(
            absoluteStart,
            absoluteEnd,
            source.chapterIndex,
            source.title
        )
        results += ReplacementPreviewMatch(
            id = "$idPrefix-${source.chapterIndex}-${source.sourceOffset}-$matchIndex-$start",
            chapterIndex = location.chapterIndex,
            chapterTitle = location.title,
            fileName = source.fileName,
            context = context.text,
            matchText = source.text.substring(start, end),
            contextMatchStart = context.matchStart,
            contextMatchEnd = context.matchEnd,
            sourceStart = absoluteStart,
            sourceEnd = absoluteEnd,
            replacementText = replacementText
        )
    }
    for (source in sources) {
        if (results.size >= maxMatches) break
        if (rule.regex) {
            val matches = Regex(rule.pattern, options).findAll(source.text).iterator()
            var matchIndex = 0
            while (matches.hasNext() && results.size < maxMatches) {
                val match = matches.next()
                val start = match.range.first
                val end = match.range.last + 1
                if (end > start) {
                    addMatch(
                        source = source,
                        matchIndex = matchIndex,
                        start = start,
                        end = end,
                        replacementText = expandRegexReplacement(match, rule.replacement)
                    )
                    matchIndex += 1
                }
            }
        } else {
            if (rule.pattern.isEmpty()) continue
            var index = 0
            var matchIndex = 0
            while (index <= source.text.length - rule.pattern.length && results.size < maxMatches) {
                val start = source.text.indexOf(rule.pattern, startIndex = index, ignoreCase = !caseSensitive)
                if (start < 0) break
                val end = start + rule.pattern.length
                addMatch(
                    source = source,
                    matchIndex = matchIndex,
                    start = start,
                    end = end,
                    replacementText = rule.replacement
                )
                matchIndex += 1
                index = end
            }
        }
    }
    return results
}

internal fun regexReplacementMatchPlans(
    source: String,
    chapterIndex: Int,
    pattern: Regex,
    replacement: String
): List<ReplacementMatchPlan> {
    val plans = mutableListOf<ReplacementMatchPlan>()
    val matches = pattern.findAll(source).iterator()
    while (matches.hasNext()) {
        val match = matches.next()
        val start = match.range.first
        val end = match.range.last + 1
        if (end > start) {
            plans += ReplacementMatchPlan(
                chapterIndex = chapterIndex,
                sourceStart = start,
                sourceEnd = end,
                replacementText = expandRegexReplacement(match, replacement)
            )
        }
    }
    return plans
}

internal fun visibleTextSearchSources(source: SearchSource): List<SearchSource> {
    val segments = mutableListOf<SearchSource>()
    var cursor = 0
    Regex("""<[^>]+>""").findAll(source.text).forEach { tag ->
        if (tag.range.first > cursor) {
            val text = source.text.substring(cursor, tag.range.first)
            if (text.isNotBlank()) {
                segments += source.copy(
                    text = text,
                    sourceOffset = source.sourceOffset + cursor
                )
            }
        }
        cursor = tag.range.last + 1
    }
    if (cursor < source.text.length) {
        val text = source.text.substring(cursor)
        if (text.isNotBlank()) {
            segments += source.copy(
                text = text,
                sourceOffset = source.sourceOffset + cursor
            )
        }
    }
    return segments
}

internal fun epubChapterBodySearchSources(book: EpubBook): List<SearchSource> {
    return book.chapters.mapIndexedNotNull { index, chapter ->
        val bodyParts = htmlBodyContentParts(chapter.html)
        SearchSource(
            chapterIndex = index,
            title = chapter.title.ifBlank { "无标题" },
            fileName = chapter.path.substringAfterLast('/'),
            text = bodyParts.body,
            sourceOffset = bodyParts.visibleBodySourceStart
        )
    }
}

internal fun txtSearchSources(
    document: TxtDocument,
    scope: String,
    currentChapterIndex: Int,
    prefaceEndIndex: Int?
): List<SearchSource> {
    if (document.chapters.isEmpty()) {
        return listOf(SearchSource(0, "TXT 正文预览", document.originalName, document.text))
            .filterTxtSearchSourcesForScope(scope, currentChapterIndex)
    }
    if (scope != TOOL_SCOPE_CURRENT) {
        return listOf(
            SearchSource(
                chapterIndex = 0,
                title = "全文",
                fileName = document.originalName,
                text = document.text,
                sourceOffset = 0
            )
        )
    }
    val sources = buildList {
        prefaceEndIndex?.let { prefaceEnd ->
            add(
                SearchSource(
                    chapterIndex = TXT_PREFACE_CHAPTER_INDEX,
                    title = "前言",
                    fileName = "第 1 行",
                    text = document.text.substring(0, prefaceEnd),
                    sourceOffset = 0
                )
            )
        }
        document.chapters.forEachIndexed { index, chapter ->
            val start = chapter.startIndex.coerceIn(0, document.text.length)
            val end = chapter.endIndex.coerceIn(start, document.text.length)
            add(
                SearchSource(
                    chapterIndex = index,
                    title = chapter.title.ifBlank { "无标题" },
                    fileName = "第 ${chapter.lineIndex + 1} 行",
                    text = document.text.substring(start, end),
                    sourceOffset = start
                )
            )
        }
    }
    return sources.filterTxtSearchSourcesForScope(scope, currentChapterIndex)
}

internal fun txtSearchSourcesForPreview(
    document: TxtDocument,
    scope: String,
    previewChapterIndex: Int,
    prefaceEndIndex: Int?
): List<SearchSource> {
    return txtSearchSources(
        document = document,
        scope = scope,
        currentChapterIndex = previewChapterIndex.takeIf { it >= 0 } ?: TXT_PREFACE_CHAPTER_INDEX,
        prefaceEndIndex = prefaceEndIndex
    )
}

internal fun replaceInTxtDocumentText(
    document: TxtDocument,
    parameters: TextReplaceParameters,
    currentChapterIndex: Int,
    prefaceEndIndex: Int?,
    rules: List<TextReplaceRule>,
    ensureActive: () -> Unit = {}
): TxtTextReplaceApplyResult {
    val activeRules = rules.filter { it.enabled && it.find.isNotEmpty() }
    val sources = txtSearchSources(
        document = document,
        scope = parameters.scope,
        currentChapterIndex = currentChapterIndex,
        prefaceEndIndex = prefaceEndIndex
    )
    var nextText = document.text
    var changedSources = 0
    var total = 0
    sources.sortedByDescending { it.sourceOffset }.forEach { source ->
        ensureActive()
        val start = source.sourceOffset.coerceIn(0, nextText.length)
        val end = (source.sourceOffset + source.text.length).coerceIn(start, nextText.length)
        var sourceText = nextText.substring(start, end)
        var changed = 0
        activeRules.forEach { rule ->
            ensureActive()
            val replaced = replaceInString(sourceText, rule, false)
            sourceText = replaced.first
            changed += replaced.second
        }
        if (changed > 0) {
            nextText = nextText.replaceRange(start, end, sourceText)
            changedSources += 1
            total += changed
        }
    }
    return TxtTextReplaceApplyResult(
        text = nextText,
        changedSources = changedSources,
        replacements = total
    )
}

private fun List<SearchSource>.filterTxtSearchSourcesForScope(
    scope: String,
    currentChapterIndex: Int
): List<SearchSource> {
    return when (scope) {
        TOOL_SCOPE_CURRENT -> filter { it.chapterIndex == currentChapterIndex }
        else -> this
    }
}

internal fun searchContext(
    source: String,
    start: Int,
    end: Int,
    contextChars: Int = DEFAULT_SEARCH_CONTEXT_CHARS
): SearchContext {
    val beforeStart = (start - contextChars).coerceAtLeast(0)
    val afterEnd = (end + contextChars).coerceAtMost(source.length)
    val prefix = if (beforeStart > 0) "..." else ""
    val suffix = if (afterEnd < source.length) "..." else ""
    val text = prefix + source.substring(beforeStart, afterEnd) + suffix
    val matchStart = prefix.length + start - beforeStart
    val matchEnd = prefix.length + end - beforeStart
    return SearchContext(text, matchStart, matchEnd)
}

internal fun applyReplacementPlansToText(
    source: String,
    plans: List<ReplacementMatchPlan>
): Pair<String, Int> {
    var next = source
    var changed = 0
    var upperBound = Int.MAX_VALUE
    plans.sortedByDescending { it.sourceStart }.forEach { plan ->
        if (plan.sourceStart < 0 || plan.sourceEnd <= plan.sourceStart) return@forEach
        if (plan.sourceEnd > next.length || plan.sourceEnd > upperBound) return@forEach
        next = next.replaceRange(plan.sourceStart, plan.sourceEnd, plan.replacementText)
        upperBound = plan.sourceStart
        changed += 1
    }
    return next to changed
}

internal fun singleMatchReplacement(
    matchText: String,
    rule: TextReplaceRule,
    caseSensitive: Boolean
): String {
    if (!rule.regex) return rule.replacement
    val options = if (caseSensitive) emptySet<RegexOption>() else setOf(RegexOption.IGNORE_CASE)
    val pattern = Regex(rule.find, options)
    val match = pattern.find(matchText)
    if (match == null || match.range.first != 0 || match.range.last + 1 != matchText.length) {
        return rule.replacement
    }
    return expandRegexReplacement(match, rule.replacement)
}
