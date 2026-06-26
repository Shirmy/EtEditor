package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.ChapterInfo
import com.eteditor.core.DocumentKind
import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.TxtChapter
import com.eteditor.core.TxtDocument
import com.eteditor.core.updateEpubChapterHtmlEntry
import kotlinx.coroutines.yield

data class TitleRenamePlanItem(
    val chapterIndex: Int,
    val sequenceNumber: Int,
    val oldTitle: String,
    val suffix: String,
    val newTitle: String
) {
    val changed: Boolean get() = ChapterDetector.cleanTitle(oldTitle) != ChapterDetector.cleanTitle(newTitle)
}

internal data class TitleRenamePlanBuildResult(
    val plan: List<TitleRenamePlanItem>,
    val message: String = ""
)

internal data class TitleRenameApplyResult(
    val count: Int,
    val attempted: Boolean
)

internal fun titleRenameScopeLabel(
    scope: String,
    options: List<Pair<String, String>>
): String {
    return options.firstOrNull { it.first == scope }?.second
        ?: options.firstOrNull()?.second.orEmpty()
}

internal fun buildTitleRenamePlanItems(
    parameters: TitleRenameParameters,
    targetIndices: List<Int>,
    sourceTitle: (Int) -> String,
    onError: (String) -> Unit = {}
): List<TitleRenamePlanItem> {
    if (targetIndices.isEmpty()) return emptyList()
    val pattern = parameters.pattern
    if (pattern.isBlank()) {
        onError("请输入标题模板")
        return emptyList()
    }
    val containsTitle = pattern.contains("{title}")
    return targetIndices.mapIndexed { offset, chapterIndex ->
        val oldTitle = sourceTitle(chapterIndex)
        val suffix = titleRenameSuffix(oldTitle, null)
        val prefix = applyTemplatePlaceholders(
            pattern = pattern,
            index = offset,
            total = targetIndices.size,
            title = suffix,
            sequenceIndex = offset,
            templateStart = 1
        )
        val nextTitle = if (!containsTitle && suffix.isNotBlank()) {
            "$prefix $suffix"
        } else {
            prefix
        }
        TitleRenamePlanItem(
            chapterIndex = chapterIndex,
            sequenceNumber = offset + 1,
            oldTitle = oldTitle,
            suffix = suffix,
            newTitle = nextTitle
        )
    }
}

internal fun buildTitleRenamePlanModel(
    kind: DocumentKind,
    epubChapters: List<EpubChapter>?,
    txtDocument: TxtDocument?,
    chapters: List<ChapterInfo>,
    currentIndex: Int,
    parameters: TitleRenameParameters
): TitleRenamePlanBuildResult {
    var message = ""
    val targetIndices = titleRenameTargetIndicesModel(
        kind = kind,
        epubChapters = epubChapters,
        txtDocument = txtDocument,
        chapters = chapters,
        currentIndex = currentIndex,
        parameters = parameters,
        onMessage = { message = it }
    )
    if (targetIndices.isEmpty()) {
        return TitleRenamePlanBuildResult(
            plan = emptyList(),
            message = message.ifBlank { "没有可重命名的普通章节" }
        )
    }
    val plan = buildTitleRenamePlanItems(
        parameters = parameters,
        targetIndices = targetIndices,
        sourceTitle = { index -> titleRenameSourceTitleModel(kind, epubChapters, txtDocument, index) },
        onError = { message = it }
    )
    return TitleRenamePlanBuildResult(plan = plan, message = message)
}

private fun titleRenameTargetIndicesModel(
    kind: DocumentKind,
    epubChapters: List<EpubChapter>?,
    txtDocument: TxtDocument?,
    chapters: List<ChapterInfo>,
    currentIndex: Int,
    parameters: TitleRenameParameters,
    onMessage: (String) -> Unit
): List<Int> {
    val scopedIndices = if (parameters.scope == TOOL_SCOPE_FILE_REGEX) {
        val matcher = toolScopeFileNameMatcher(parameters.matchPattern, parameters.matchRegexEnabled, onMessage)
            ?: return emptyList()
        val matches = chapters.mapIndexedNotNull { index, _ ->
            val titles = titleRenameMatchTitlesModel(kind, epubChapters, txtDocument, index)
            index.takeIf { titles.any(matcher) }
        }
        if (matches.isEmpty()) onMessage("标题匹配未命中章节")
        matches
    } else {
        toolScopeTargetChapterIndices(
            scope = parameters.scope,
            size = chapters.size,
            currentIndex = currentIndex,
            chapters = chapters,
            matchPattern = parameters.matchPattern,
            matchRegexEnabled = parameters.matchRegexEnabled,
            onError = onMessage
        )
    }
    return when (kind) {
        DocumentKind.Epub -> {
            val sourceChapters = epubChapters ?: return emptyList()
            scopedIndices.filter { index ->
                index in sourceChapters.indices && !sourceChapters[index].isVolumeChapter()
            }
        }
        DocumentKind.Txt -> {
            val document = txtDocument ?: return emptyList()
            scopedIndices.filter { it in document.chapters.indices }
        }
        DocumentKind.None -> emptyList()
    }
}

private fun titleRenameMatchTitlesModel(
    kind: DocumentKind,
    epubChapters: List<EpubChapter>?,
    txtDocument: TxtDocument?,
    chapterIndex: Int
): List<String> {
    return when (kind) {
        DocumentKind.Epub -> epubChapters?.getOrNull(chapterIndex)
            ?.html
            ?.let(::extractH1H2Titles)
            .orEmpty()
        DocumentKind.Txt -> txtDocument?.chapters?.getOrNull(chapterIndex)
            ?.title
            ?.let { listOf(it) }
            .orEmpty()
        DocumentKind.None -> emptyList()
    }
}

private fun titleRenameSourceTitleModel(
    kind: DocumentKind,
    epubChapters: List<EpubChapter>?,
    txtDocument: TxtDocument?,
    chapterIndex: Int
): String {
    return when (kind) {
        DocumentKind.Epub -> epubChapters?.getOrNull(chapterIndex)?.title.orEmpty()
        DocumentKind.Txt -> txtDocument?.chapters?.getOrNull(chapterIndex)?.title.orEmpty()
        DocumentKind.None -> ""
    }
}

internal fun extractH1H2Titles(html: String): List<String> {
    return Regex("""<(h[12])\b[^>]*>(.*?)</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(html)
        .map { match -> ChapterDetector.cleanTitle(ChapterDetector.stripHtml(match.groupValues[2])) }
        .filter { it.isNotBlank() }
        .toList()
}

internal fun applyRenamedTitlesToEpub(
    book: EpubBook,
    newTitles: List<Pair<Int, String>>
): TitleRenameApplyResult {
    val cleaned = cleanTitleRenamePairs(newTitles)
    if (cleaned.isEmpty()) return TitleRenameApplyResult(count = 0, attempted = false)
    var count = 0
    cleaned.forEach { (index, title) ->
        if (applyRenamedTitleToEpubChapter(book, index, title)) {
            count += 1
        }
    }
    return TitleRenameApplyResult(count = count, attempted = true)
}

internal suspend fun applyRenamedTitlesToEpubWithProgress(
    book: EpubBook,
    newTitles: List<Pair<Int, String>>,
    onProgress: (completed: Int, total: Int) -> Unit
): Int {
    val cleaned = cleanTitleRenamePairs(newTitles)
    if (cleaned.isEmpty()) return 0
    var count = 0
    cleaned.forEachIndexed { index, (chapterIndex, title) ->
        if (applyRenamedTitleToEpubChapter(book, chapterIndex, title)) {
            count += 1
        }
        onProgress(index + 1, cleaned.size)
        yield()
    }
    return count
}

private fun applyRenamedTitleToEpubChapter(book: EpubBook, index: Int, title: String): Boolean {
    val chapter = book.chapters.getOrNull(index) ?: return false
    chapter.title = title
    chapter.html = ChapterDetector.updateHtmlTitle(chapter.html, title)
    chapter.wordCount = ChapterDetector.countHtmlChars(chapter.html)
    // 标题改写后必须同步回 book.entries：否则后续读取 entries 的步骤（如执行链里的文本替换/批量替换）
    // 会用旧标题的原始字节重写章节，把这里写入的标题清空。
    updateEpubChapterHtmlEntry(book, chapter)
    return true
}

internal suspend fun applyRenamedTitlesToTxtWithProgress(
    document: TxtDocument,
    newTitles: List<Pair<Int, String>>,
    detectChapters: (String) -> List<TxtChapter>,
    onProgress: (completed: Int, total: Int) -> Unit
): Int {
    val cleaned = cleanTitleRenamePairs(newTitles)
    if (cleaned.isEmpty()) return 0
    var text = document.text
    var count = 0
    val sortedTitles = cleaned.sortedByDescending { it.first }
    sortedTitles.forEachIndexed { index, (chapterIndex, title) ->
        val chapter = document.chapters.getOrNull(chapterIndex)
        if (chapter != null) {
            text = ChapterDetector.updateTxtTitle(text, chapter.lineIndex, title)
            count += 1
        }
        onProgress(index + 1, sortedTitles.size)
        yield()
    }
    document.text = text
    document.chapters = detectChapters(text)
    return count
}

internal fun applyRenamedTitlesToTxt(
    document: TxtDocument,
    newTitles: List<Pair<Int, String>>,
    detectChapters: (String) -> List<TxtChapter>
): TitleRenameApplyResult {
    val cleaned = cleanTitleRenamePairs(newTitles)
    if (cleaned.isEmpty()) return TitleRenameApplyResult(count = 0, attempted = false)
    var text = document.text
    var count = 0
    cleaned
        .sortedByDescending { it.first }
        .forEach { (index, title) ->
            val chapter = document.chapters.getOrNull(index) ?: return@forEach
            text = ChapterDetector.updateTxtTitle(text, chapter.lineIndex, title)
            count += 1
        }
    document.text = text
    document.chapters = detectChapters(text)
    return TitleRenameApplyResult(count = count, attempted = true)
}

private fun cleanTitleRenamePairs(newTitles: List<Pair<Int, String>>): List<Pair<Int, String>> {
    return newTitles.mapNotNull { (index, title) ->
        ChapterDetector.cleanTitle(title).takeIf { it.isNotBlank() }?.let { index to it }
    }
}

internal fun titleRenameSuffix(sourceTitle: String, extractor: Regex?): String {
    val cleanSource = ChapterDetector.cleanTitle(sourceTitle)
    if (extractor == null) return cleanSource
    val match = extractor.find(cleanSource) ?: return cleanSource
    return if (match.groups.size > 1) {
        match.groups[1]?.value?.let(ChapterDetector::cleanTitle) ?: cleanSource
    } else {
        cleanSource
    }
}
