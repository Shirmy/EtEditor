package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.EpubBook
import com.eteditor.core.ReplaceResult
import com.eteditor.core.decodeEpubHtmlBytes
import com.eteditor.core.encodeEpubHtmlUtf8
import com.eteditor.core.normalizeEpubHtmlUtf8Declaration
import com.eteditor.core.parseNavEntries
import com.eteditor.core.parseNcxEntries
import com.eteditor.core.syncEpubDirectoryTitleFromHtml

private const val TEXT_REPLACE_HTML_SOURCE_BASE = -2000
private const val TEXT_REPLACE_INTRO_SOURCE_INDEX = -12000

internal data class EpubPackageTextTarget(
    val sourceIndex: Int,
    val title: String,
    val path: String
)

internal fun isEpubHtmlTextReplaceScope(scope: String): Boolean {
    return scope == TOOL_SCOPE_ALL ||
        scope == TOOL_SCOPE_CURRENT ||
        scope == TEXT_REPLACE_SCOPE_INTRO
}

internal fun epubPackageTextReplaceTargets(
    book: EpubBook,
    scope: String,
    currentPath: String?,
    introPath: String
): List<EpubPackageTextTarget> {
    return when (scope) {
        TOOL_SCOPE_ALL -> epubHtmlTextReplaceTargets(book)
        TOOL_SCOPE_CURRENT -> {
            val normalizedCurrentPath = currentPath
                ?.let(::normalizeEpubPath)
                ?.lowercase()
                ?: return emptyList()
            epubHtmlTextReplaceTargets(book)
                .filter { target -> normalizeEpubPath(target.path).lowercase() == normalizedCurrentPath }
        }
        TEXT_REPLACE_SCOPE_INTRO -> listOfNotNull(epubIntroTextReplaceTarget(book, introPath))
        else -> emptyList()
    }
}

internal fun epubPackageTextReplaceTarget(
    book: EpubBook,
    sourceIndex: Int,
    introPath: String
): EpubPackageTextTarget? {
    return (epubHtmlTextReplaceTargets(book) + listOfNotNull(epubIntroTextReplaceTarget(book, introPath)))
        .firstOrNull { it.sourceIndex == sourceIndex }
}

internal fun epubHtmlBodySearchSource(
    book: EpubBook,
    target: EpubPackageTextTarget
): SearchSource? {
    val text = epubPackageText(book, target.path) ?: return null
    val bodyParts = htmlBodyContentParts(text)
    return SearchSource(
        chapterIndex = target.sourceIndex,
        title = target.title,
        fileName = target.path,
        text = bodyParts.body,
        sourceOffset = bodyParts.visibleBodySourceStart
    )
}

internal fun epubPackageTextSearchSources(
    book: EpubBook,
    parameters: TextReplaceParameters,
    currentPath: String?,
    introPath: String
): List<SearchSource> {
    if (!isEpubHtmlTextReplaceScope(parameters.scope)) return emptyList()
    return epubPackageTextReplaceTargets(
        book = book,
        scope = parameters.scope,
        currentPath = currentPath,
        introPath = introPath
    ).mapNotNull { target -> epubHtmlBodySearchSource(book, target) }
}

internal fun replaceInEpubPackageText(
    book: EpubBook,
    parameters: TextReplaceParameters,
    currentPath: String?,
    introPath: String,
    rules: List<TextReplaceRule>,
    ensureActive: () -> Unit = {}
): ReplaceResult {
    var files = 0
    var total = 0
    // Compile each rule's regex once up front, then reuse it across every chapter file and markup
    // segment (previously the same regex was recompiled per file, and per paragraph in text-only mode).
    val activeRules = rules
        .filter { it.enabled && it.find.isNotEmpty() }
        .map { rule -> rule to compileTextReplaceRulePattern(rule, rule.caseSensitive) }
    epubPackageTextReplaceTargets(
        book = book,
        scope = parameters.scope,
        currentPath = currentPath,
        introPath = introPath
    ).forEach { target ->
        ensureActive()
        val original = epubPackageText(book, target.path) ?: return@forEach
        val bodyRange = htmlBodyContentRangeOrNull(original) ?: return@forEach
        var nextBody = original.substring(bodyRange.first, bodyRange.second)
        var changed = 0
        activeRules.forEach { (rule, pattern) ->
            ensureActive()
            val replaced = if (rule.textOnly) {
                replaceVisibleTextInMarkupWithPattern(nextBody, rule, pattern, rule.caseSensitive)
            } else {
                replaceInStringWithPattern(nextBody, rule, pattern, rule.caseSensitive)
            }
            nextBody = replaced.first
            changed += replaced.second
        }
        if (changed > 0) {
            val next = original.replaceRange(bodyRange.first, bodyRange.second, nextBody)
            updateEpubPackageText(book, target.path, next)
            files += 1
            total += changed
        }
    }
    return ReplaceResult(files, total)
}

internal fun applyReplacementMatchPlansToEpubPackageText(
    book: EpubBook,
    introPath: String,
    plans: List<ReplacementMatchPlan>
): Int {
    var total = 0
    plans.groupBy { it.chapterIndex }.forEach { (chapterIndex, chapterPlans) ->
        val target = epubPackageTextReplaceTarget(book, chapterIndex, introPath) ?: return@forEach
        val source = epubPackageText(book, target.path) ?: return@forEach
        val bodyRange = htmlBodyContentRangeOrNull(source) ?: return@forEach
        val bodyPlans = chapterPlans.filter { plan ->
            plan.sourceStart >= bodyRange.first && plan.sourceEnd <= bodyRange.second
        }
        val (nextSource, changed) = applyReplacementPlansToText(source, bodyPlans)
        if (changed > 0) {
            updateEpubPackageText(book, target.path, nextSource)
            total += changed
        }
    }
    return total
}

internal fun applySingleEpubPackageTextReplacement(
    book: EpubBook,
    introPath: String,
    sourceIndex: Int,
    sourceStart: Int,
    sourceEnd: Int,
    rule: TextReplaceRule,
    caseSensitive: Boolean
): Int? {
    if (sourceStart < 0 || sourceEnd <= sourceStart) return null
    val target = epubPackageTextReplaceTarget(book, sourceIndex, introPath) ?: return null
    val source = epubPackageText(book, target.path) ?: return null
    val bodyRange = htmlBodyContentRangeOrNull(source) ?: return null
    if (sourceStart < bodyRange.first || sourceEnd > bodyRange.second) return null
    if (sourceEnd > source.length) return null
    val replacement = singleMatchReplacement(source.substring(sourceStart, sourceEnd), rule, caseSensitive)
    updateEpubPackageText(book, target.path, source.replaceRange(sourceStart, sourceEnd, replacement))
    return replacement.length - (sourceEnd - sourceStart)
}

internal fun epubPackageText(book: EpubBook, path: String): String? {
    return book.entries[path]?.let(::decodeEpubHtmlBytes)
}

internal fun updateEpubPackageText(book: EpubBook, path: String, text: String) {
    val normalizedText = normalizeEpubHtmlUtf8Declaration(text)
    book.entries[path] = encodeEpubHtmlUtf8(normalizedText)
    val normalized = normalizeEpubPath(path)
    book.chapters.firstOrNull { chapter ->
        normalizeEpubPath(chapter.path).equals(normalized, ignoreCase = true)
    }?.let { chapter ->
        chapter.html = normalizedText
        chapter.syncEpubDirectoryTitleFromHtml()
        chapter.wordCount = ChapterDetector.countHtmlChars(normalizedText)
    }
}

internal fun epubHtmlTextReplaceTargets(book: EpubBook): List<EpubPackageTextTarget> {
    val tocTitleByPath = epubTextReplaceTocTitleByPath(book)
    val chapterTitleByPath = epubTextReplaceChapterTitleByPath(book)
    val paths = (book.chapters.map { it.path } +
        book.manifest.values.map { it.path } +
        book.entries.keys)
        .filter { path ->
            path.endsWith(".xhtml", ignoreCase = true) ||
                path.endsWith(".html", ignoreCase = true) ||
                path.endsWith(".htm", ignoreCase = true)
        }
        .filter { path -> book.entries.containsKey(path) }
        .filterNot(::isExcludedEpubTextReplaceHtmlPath)
        .distinctBy { path -> normalizeEpubPath(path).lowercase() }
    return paths.mapIndexed { index, path ->
        EpubPackageTextTarget(
            sourceIndex = TEXT_REPLACE_HTML_SOURCE_BASE - index,
            title = epubTextReplaceDisplayTitle(path, tocTitleByPath, chapterTitleByPath),
            path = path
        )
    }
}

private fun epubTextReplaceDisplayTitle(
    path: String,
    tocTitleByPath: Map<String, String>,
    chapterTitleByPath: Map<String, String>
): String {
    val normalized = normalizeEpubPath(path).lowercase()
    return tocTitleByPath[normalized]?.takeIf { it.isNotBlank() }
        ?: chapterTitleByPath[normalized]?.takeIf { it.isNotBlank() }
        ?: path.substringAfterLast('/').ifBlank { path }
}

private fun epubTextReplaceTocTitleByPath(book: EpubBook): Map<String, String> {
    val tocEntries = book.tocPath
        ?.let { path -> book.entries[path]?.let { bytes -> parseNcxEntries(bytes, path) } }
        .orEmpty()
        .ifEmpty { parseNavEntries(book.entries, book.manifest) }
    return tocEntries.mapKeys { (path, _) -> normalizeEpubPath(path).lowercase() }
        .mapValues { (_, entry) -> entry.title }
}

private fun epubTextReplaceChapterTitleByPath(book: EpubBook): Map<String, String> {
    val titles = linkedMapOf<String, String>()
    book.chapters.forEach { chapter ->
        val title = chapter.title
        (chapter.pathAliases + chapter.path + chapter.originalPath).forEach { path ->
            titles.putIfAbsent(normalizeEpubPath(path).lowercase(), title)
        }
    }
    return titles
}

private fun epubIntroTextReplaceTarget(
    book: EpubBook,
    introPath: String
): EpubPackageTextTarget? {
    val path = introPath.takeIf { book.entries.containsKey(it) } ?: return null
    return EpubPackageTextTarget(
        sourceIndex = TEXT_REPLACE_INTRO_SOURCE_INDEX,
        title = "简介",
        path = path
    )
}

private fun isExcludedEpubTextReplaceHtmlPath(path: String): Boolean {
    val fileName = path.substringAfterLast('/').ifBlank { path }
    val stem = fileName.substringBeforeLast('.', fileName).lowercase()
    return stem == "cover" || stem == "section0001" || stem == "section0002"
}
