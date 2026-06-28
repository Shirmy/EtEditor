package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.DocumentKind
import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.TxtChapter
import com.eteditor.core.TxtDocument
import com.eteditor.core.updateEpubChapterHtmlEntry

internal data class TitleFormatParts(
    val prefix: String?,
    val suffix: String
)

internal data class TitleFormatAutoDecision(
    val style: String,
    val reason: String
)

internal data class TitleFormatRendered(
    val plainTitle: String,
    val headingHtml: String,
    val styleCode: String
)

data class TitleFormatPlanItem(
    val chapterIndex: Int,
    val sequenceNumber: Int,
    val oldTitle: String,
    val prefix: String,
    val suffix: String,
    val styleCode: String,
    val styleName: String,
    val reason: String,
    val newTitle: String,
    val formatChanged: Boolean = false,
    val autoGroupName: String = ""
) {
    val changed: Boolean get() = formatChanged || ChapterDetector.cleanTitle(oldTitle) != ChapterDetector.cleanTitle(newTitle)
}

internal data class TitleFormatPlanBuildResult(
    val plan: List<TitleFormatPlanItem>,
    val message: String = ""
)

private data class TitleFormatVolumeGroup(
    val name: String,
    val indices: List<Int>
)

internal const val DEFAULT_TITLE_FORMAT_SHORT_THRESHOLD = 6
internal const val TITLE_FORMAT_STYLE_LEFT = "01"
internal const val TITLE_FORMAT_STYLE_DOUBLE = "02"
internal const val TITLE_FORMAT_STYLE_NONE = "03"

internal val TITLE_FORMAT_STYLE_OPTIONS = listOf(
    TITLE_FORMAT_STYLE_DOUBLE to "双横线",
    TITLE_FORMAT_STYLE_LEFT to "左竖线",
    TITLE_FORMAT_STYLE_NONE to "无横线"
)

internal fun titleFormatSelectableChapterOptionsModel(
    kind: DocumentKind,
    epubChapters: List<EpubChapter>?,
    txtDocument: TxtDocument?
): List<Pair<String, String>> {
    return when (kind) {
        DocumentKind.Epub -> epubChapters
            ?.mapIndexed { index, chapter -> index to chapter }
            ?.filterNot { (_, chapter) -> chapter.isVolumeChapter() || chapter.isCoverSection0001Or0002() }
            ?.map { (index, chapter) ->
                index.toString() to chapter.title.ifBlank { chapter.path.substringAfterLast('/') }
            }
            .orEmpty()
        DocumentKind.Txt -> txtDocument?.chapters
            ?.mapIndexed { index, chapter -> index.toString() to chapter.title.ifBlank { "\u7b2c ${index + 1} \u9875" } }
            .orEmpty()
        DocumentKind.None -> emptyList()
    }
}

private val TITLE_FORMAT_STYLE_CODES = TITLE_FORMAT_STYLE_OPTIONS.map { it.first }.toSet()

internal fun parseTitleFormatParts(title: String): TitleFormatParts {
    val normalized = title
        .replace('\u3000', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim()
    val match = Regex("""^(第\s*[零〇一二两三四五六七八九十百千万亿\d]+\s*章)([\s\S]*)${'$'}""")
        .find(normalized)
    if (match == null) {
        return TitleFormatParts(prefix = null, suffix = "")
    }
    val prefix = match.groupValues[1].replace(Regex("""\s+"""), "")
    val suffix = match.groupValues[2]
        .replace(Regex("""^[\s|─—–-]+"""), "")
        .trim()
    return TitleFormatParts(prefix = prefix, suffix = suffix)
}

internal fun titleFormatStyleLabel(style: String): String {
    return TITLE_FORMAT_STYLE_OPTIONS.firstOrNull { it.first == style }?.second ?: "双横线"
}

internal fun titleFormatShortStyleLabel(style: String): String {
    return when (style) {
        TITLE_FORMAT_STYLE_DOUBLE -> "双横线"
        TITLE_FORMAT_STYLE_LEFT -> "左竖线"
        TITLE_FORMAT_STYLE_NONE -> "无横线"
        else -> titleFormatStyleLabel(style)
    }
}

internal fun titleFormatAutoDecision(
    parts: Collection<TitleFormatParts>,
    shortThreshold: Int
): TitleFormatAutoDecision {
    val total = parts.size.coerceAtLeast(1)
    val threshold = shortThreshold.coerceAtLeast(1)
    val emptyCount = parts.count { it.suffix.isBlank() }
    val longCount = parts.count { it.suffix.isNotBlank() && titleSuffixLength(it.suffix) > threshold }
    return when {
        emptyCount * 100 >= total * 80 -> TitleFormatAutoDecision(
            style = TITLE_FORMAT_STYLE_NONE,
            reason = "自动：判断为无横线"
        )
        longCount * 100 >= total * 60 -> TitleFormatAutoDecision(
            style = TITLE_FORMAT_STYLE_LEFT,
            reason = "自动：判断为左竖线"
        )
        else -> TitleFormatAutoDecision(
            style = TITLE_FORMAT_STYLE_DOUBLE,
            reason = "自动：判断为双横线"
        )
    }
}

private fun titleSuffixLength(text: String): Int {
    return text.codePointCount(0, text.length)
}

internal fun renderTitleFormat(
    prefix: String,
    suffix: String,
    style: String
): TitleFormatRendered {
    val cleanPrefix = ChapterDetector.cleanTitle(prefix).ifBlank { prefix.trim() }
    val cleanSuffix = suffix.trim()
    val plainTitle = when {
        style == TITLE_FORMAT_STYLE_NONE || cleanSuffix.isBlank() -> cleanPrefix
        else -> "$cleanPrefix $cleanSuffix"
    }
    val headingHtml = when {
        style == TITLE_FORMAT_STYLE_LEFT && cleanSuffix.isNotBlank() -> {
            "${cleanPrefix.escapeXmlText()}<br/>${cleanSuffix.escapeXmlText()}"
        }
        else -> plainTitle.escapeXmlText()
    }
    return TitleFormatRendered(
        plainTitle = plainTitle,
        headingHtml = headingHtml,
        styleCode = style
    )
}

internal fun renderInsertedChapterTitle(title: String, style: String): TitleFormatRendered {
    val parts = parseTitleFormatParts(title)
    val prefix = parts.prefix ?: ChapterDetector.cleanTitle(title).ifBlank { "第1章" }
    return renderTitleFormat(prefix, parts.suffix, style)
}

internal fun buildTitleFormatPlanItems(
    parameters: TitleFormatParameters,
    targetIndices: List<Int>,
    titles: List<String>,
    formatChanged: (Int, TitleFormatRendered) -> Boolean = { _, _ -> false },
    autoDecisionByIndex: Map<Int, TitleFormatAutoDecision>? = null,
    autoGroupNameByIndex: Map<Int, String> = emptyMap()
): List<TitleFormatPlanItem> {
    if (targetIndices.isEmpty()) return emptyList()
    val partsByIndex = targetIndices.associateWith { index ->
        parseTitleFormatParts(titles.getOrNull(index).orEmpty())
    }
    val fallbackAutoDecision = if (autoDecisionByIndex == null && parameters.mode == TITLE_FORMAT_MODE_PER_CHAPTER) {
        titleFormatAutoDecision(partsByIndex.values, DEFAULT_TITLE_FORMAT_SHORT_THRESHOLD)
    } else {
        null
    }
    val numberByIndex = titleFormatNumbers(targetIndices)
    return targetIndices.mapNotNull { index ->
        val parts = partsByIndex[index] ?: return@mapNotNull null
        val number = numberByIndex[index] ?: return@mapNotNull null
        val prefix = parts.prefix ?: ChapterDetector.cleanTitle(titles.getOrNull(index).orEmpty())
        val autoDecision = autoDecisionByIndex?.get(index) ?: fallbackAutoDecision
        val style = when (parameters.mode) {
            TITLE_FORMAT_MODE_PER_CHAPTER -> autoDecision?.style ?: TITLE_FORMAT_STYLE_DOUBLE
            TITLE_FORMAT_MODE_UNIFORM -> parameters.style
            else -> autoDecision?.style ?: TITLE_FORMAT_STYLE_DOUBLE
        }
        val reason = titleFormatReason(style, parameters, autoDecision)
        val rendered = renderTitleFormat(prefix, parts.suffix, style)
        TitleFormatPlanItem(
            chapterIndex = index,
            sequenceNumber = number,
            oldTitle = titles.getOrNull(index).orEmpty(),
            prefix = prefix,
            suffix = parts.suffix,
            styleCode = style,
            styleName = titleFormatStyleLabel(style),
            reason = reason,
            newTitle = rendered.plainTitle,
            formatChanged = formatChanged(index, rendered),
            autoGroupName = autoGroupNameByIndex[index].orEmpty()
        )
    }
}

internal fun buildTitleFormatPlanModel(
    kind: DocumentKind,
    epubChapters: List<EpubChapter>?,
    txtDocument: TxtDocument?,
    parameters: TitleFormatParameters
): TitleFormatPlanBuildResult {
    val targetIndices = titleFormatTargetIndicesModel(
        kind = kind,
        epubChapters = epubChapters,
        txtDocument = txtDocument,
        parameters = parameters
    )
    if (targetIndices.isEmpty()) {
        val message = if (parameters.scope == TITLE_FORMAT_SCOPE_SELECTED) {
            "请选择HTML章节"
        } else {
            "处理范围内没有普通章节"
        }
        return TitleFormatPlanBuildResult(plan = emptyList(), message = message)
    }
    val titles = titleFormatSourceTitlesModel(kind, epubChapters, txtDocument)
    val formatChanged: (Int, TitleFormatRendered) -> Boolean = { index, rendered ->
        epubChapters?.getOrNull(index)?.let { chapter ->
            kind == DocumentKind.Epub && epubTitleFormatWillChange(chapter, rendered)
        } ?: false
    }
    val groups = epubTitleFormatVolumeGroups(kind, epubChapters, parameters, targetIndices)
    val plan = if (groups != null) {
        val autoDecisionByIndex = mutableMapOf<Int, TitleFormatAutoDecision>()
        val autoGroupNameByIndex = mutableMapOf<Int, String>()
        for (group in groups) {
            val groupParts = group.indices.mapNotNull { index ->
                parseTitleFormatParts(titles.getOrNull(index).orEmpty())
            }
            val decision = titleFormatAutoDecision(groupParts, DEFAULT_TITLE_FORMAT_SHORT_THRESHOLD)
            group.indices.forEach { index ->
                autoDecisionByIndex[index] = decision
                autoGroupNameByIndex[index] = group.name
            }
        }
        buildTitleFormatPlanItems(
            parameters = parameters,
            targetIndices = targetIndices,
            titles = titles,
            formatChanged = formatChanged,
            autoDecisionByIndex = autoDecisionByIndex,
            autoGroupNameByIndex = autoGroupNameByIndex
        )
    } else {
        buildTitleFormatPlanItems(
            parameters = parameters,
            targetIndices = targetIndices,
            titles = titles,
            formatChanged = formatChanged
        )
    }
    return TitleFormatPlanBuildResult(plan = plan)
}

private fun epubTitleFormatVolumeGroups(
    kind: DocumentKind,
    epubChapters: List<EpubChapter>?,
    parameters: TitleFormatParameters,
    targetIndices: List<Int>
): List<TitleFormatVolumeGroup>? {
    if (kind != DocumentKind.Epub || parameters.scope == TITLE_FORMAT_SCOPE_SELECTED || parameters.mode == TITLE_FORMAT_MODE_UNIFORM) return null
    val chapters = epubChapters ?: return null

    val hasExtraVolume = chapters.any { it.isExtraVolumeChapter() }
    if (!hasExtraVolume) return null

    val hasNormalVolume = chapters.any { it.isNormalVolumeChapter() }

    val extraGroup = mutableListOf<Int>()
    val normalGroup = mutableListOf<Int>()
    val targetSet = targetIndices.toSet()

    if (hasNormalVolume) {
        // 有正文卷有番外卷：按所属卷类型分
        var currentIsExtra = false
        for (index in chapters.indices) {
            val chapter = chapters[index]
            if (chapter.isVolumeChapter()) {
                currentIsExtra = chapter.isExtraVolumeChapter()
            } else if (index in targetSet) {
                if (currentIsExtra) extraGroup += index else normalGroup += index
            }
        }
    } else {
        // 无正文卷有番外卷：番外卷之前都算正文
        val firstExtraVolumeIndex = chapters.indexOfFirst { it.isExtraVolumeChapter() }
        for (index in targetIndices) {
            if (index < firstExtraVolumeIndex) normalGroup += index else extraGroup += index
        }
    }

    if (normalGroup.isEmpty() && extraGroup.isEmpty()) return null
    val groups = mutableListOf<TitleFormatVolumeGroup>()
    if (normalGroup.isNotEmpty()) groups += TitleFormatVolumeGroup("正文", normalGroup)
    if (extraGroup.isNotEmpty()) groups += TitleFormatVolumeGroup("番外", extraGroup)
    return groups
}

private fun EpubChapter.isExtraVolumeChapter(): Boolean {
    if (!isVolumeChapter()) return false
    return (pathAliases + path + originalPath).any { itemPath ->
        val stem = itemPath.substringAfterLast('/').substringBeforeLast('.').lowercase()
        stem == "vol00" || Regex("""^volf\d+$""").matches(stem)
    }
}

private fun EpubChapter.isNormalVolumeChapter(): Boolean {
    if (!isVolumeChapter()) return false
    return (pathAliases + path + originalPath).any { itemPath ->
        val stem = itemPath.substringAfterLast('/').substringBeforeLast('.').lowercase()
        stem != "vol00" && Regex("""^vol\d+$""").matches(stem)
    }
}

private fun epubTitleFormatWillChange(chapter: EpubChapter, rendered: TitleFormatRendered): Boolean {
    val nextHtml = updateHtmlTitleForFormat(
        html = chapter.html,
        plainTitle = rendered.plainTitle,
        headingHtml = rendered.headingHtml,
        styleCode = rendered.styleCode
    )
    return chapter.title != rendered.plainTitle || chapter.html != nextHtml
}

private fun titleFormatSourceTitlesModel(
    kind: DocumentKind,
    epubChapters: List<EpubChapter>?,
    txtDocument: TxtDocument?
): List<String> {
    return when (kind) {
        DocumentKind.Epub -> epubChapters?.map { it.title }.orEmpty()
        DocumentKind.Txt -> txtDocument?.chapters?.map { it.title }.orEmpty()
        DocumentKind.None -> emptyList()
    }
}

private fun titleFormatTargetIndicesModel(
    kind: DocumentKind,
    epubChapters: List<EpubChapter>?,
    txtDocument: TxtDocument?,
    parameters: TitleFormatParameters
): List<Int> {
    return when (kind) {
        DocumentKind.Epub -> {
            val chapters = epubChapters ?: return emptyList()
            val allChapterIndices = chapters.indices.filterNot { index ->
                chapters[index].isVolumeChapter() || chapters[index].isCoverSection0001Or0002()
            }
            when (parameters.scope) {
                TITLE_FORMAT_SCOPE_SELECTED -> {
                    allChapterIndices.filter { it in parameters.selectedChapterIndices }
                }
                else -> allChapterIndices
            }
        }
        DocumentKind.Txt -> {
            val document = txtDocument ?: return emptyList()
            when (parameters.scope) {
                TITLE_FORMAT_SCOPE_SELECTED -> {
                    document.chapters.indices.filter { it in parameters.selectedChapterIndices }
                }
                else -> document.chapters.indices.toList()
            }
        }
        DocumentKind.None -> emptyList()
    }
}

internal fun titleFormatNoChangeMessage(plan: List<TitleFormatPlanItem>): String {
    val reason = titleFormatPlanSummaryReason(plan)
    return "判断原因：${reason}，所以无需修改（检查 ${plan.size} 章，修改 0 章）"
}

internal fun titleFormatCompletionMessage(plan: List<TitleFormatPlanItem>, changed: Int): String {
    val groupedReason = titleFormatGroupedSummaryReason(plan)
    return if (groupedReason != null) {
        "标题格式完成：$groupedReason；处理 ${plan.size} 章，修改 $changed 章"
    } else {
        "标题格式完成：处理 ${plan.size} 章，修改 $changed 章"
    }
}

internal fun titleFormatLogicText(
    parameters: TitleFormatParameters,
    plan: List<TitleFormatPlanItem>
): String {
    return when (parameters.mode) {
        TITLE_FORMAT_MODE_UNIFORM -> "统一：${titleFormatShortStyleLabel(parameters.style)}"
        else -> {
            titleFormatPlanSummaryReason(plan)
        }
    }
}

private fun titleFormatPlanSummaryReason(plan: List<TitleFormatPlanItem>): String {
    if (plan.isEmpty()) return "自动：判断为${titleFormatShortStyleLabel(TITLE_FORMAT_STYLE_DOUBLE)}"
    titleFormatGroupedSummaryReason(plan)?.let { return it }
    return plan.firstOrNull()?.reason ?: "自动：判断为${titleFormatShortStyleLabel(TITLE_FORMAT_STYLE_DOUBLE)}"
}

private fun titleFormatGroupedSummaryReason(plan: List<TitleFormatPlanItem>): String? {
    val grouped = plan
        .filter { item -> item.autoGroupName.isNotBlank() }
        .groupBy { item -> item.autoGroupName }
    if (grouped.isNotEmpty()) {
        val parts = listOf("正文", "番外").mapNotNull { groupName ->
            val items = grouped[groupName].orEmpty()
            if (items.isEmpty()) return@mapNotNull null
            val styleNames = items
                .map { item -> titleFormatShortStyleLabel(item.styleCode) }
                .distinct()
            val styleText = if (styleNames.size == 1) styleNames.single() else styleNames.joinToString("/")
            "$groupName$styleText"
        }
        if (parts.isNotEmpty()) return "自动：${parts.joinToString("，")}"
    }
    return null
}

private fun titleFormatNumbers(targetIndices: List<Int>): Map<Int, Int> {
    if (targetIndices.isEmpty()) return emptyMap()
    return targetIndices.mapIndexed { offset, index ->
        index to offset + 1
    }.toMap()
}

private fun titleFormatReason(
    style: String,
    parameters: TitleFormatParameters,
    autoDecision: TitleFormatAutoDecision?
): String {
    if (parameters.mode == TITLE_FORMAT_MODE_UNIFORM) {
        return "统一：${titleFormatShortStyleLabel(style)}"
    }
    return autoDecision?.reason ?: "自动：判断为${titleFormatShortStyleLabel(style)}"
}

internal fun epubChapterTitleStyle(chapter: EpubChapter): String {
    Regex("""chapter-title_(\d+)""")
        .find(chapter.html)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { style -> style in TITLE_FORMAT_STYLE_CODES }
        ?.let { return it }

    val heading = Regex(
        """<h[1-3][^>]*>(.*?)</h[1-3]>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).find(chapter.html)?.groupValues?.getOrNull(1).orEmpty()
    if (Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE).containsMatchIn(heading)) {
        return TITLE_FORMAT_STYLE_LEFT
    }

    val parts = parseTitleFormatParts(chapter.title)
    return if (parts.suffix.isBlank()) TITLE_FORMAT_STYLE_NONE else TITLE_FORMAT_STYLE_DOUBLE
}

internal fun epubInsertReferenceTitleStyle(book: EpubBook, insertPosition: Int): String {
    val before = book.chapters
        .take(insertPosition)
        .asReversed()
        .firstOrNull { chapter -> !chapter.isVolumeChapter() && !chapter.isCoverSection0001Or0002() }
    val reference = before ?: book.chapters
        .drop(insertPosition)
        .firstOrNull { chapter -> !chapter.isVolumeChapter() && !chapter.isCoverSection0001Or0002() }
    return reference?.let(::epubChapterTitleStyle) ?: TITLE_FORMAT_STYLE_DOUBLE
}

internal fun txtInsertReferenceTitleStyle(document: TxtDocument, insertPosition: Int): String {
    val before = document.chapters
        .take(insertPosition)
        .asReversed()
        .firstOrNull()
    val reference = before ?: document.chapters.drop(insertPosition).firstOrNull()
    val parts = parseTitleFormatParts(reference?.title.orEmpty())
    return if (parts.suffix.isBlank()) TITLE_FORMAT_STYLE_NONE else TITLE_FORMAT_STYLE_DOUBLE
}

// 插入参照点的章节是否本就是"第X章"样式(决定要不要给插入章续编号)。
internal fun epubInsertReferenceIsNumbered(book: EpubBook, insertPosition: Int): Boolean {
    val before = book.chapters
        .take(insertPosition)
        .asReversed()
        .firstOrNull { chapter -> !chapter.isVolumeChapter() && !chapter.isCoverSection0001Or0002() }
    val reference = before ?: book.chapters
        .drop(insertPosition)
        .firstOrNull { chapter -> !chapter.isVolumeChapter() && !chapter.isCoverSection0001Or0002() }
    return reference != null && parseTitleFormatParts(reference.title).prefix != null
}

internal fun txtInsertReferenceIsNumbered(document: TxtDocument, insertPosition: Int): Boolean {
    val before = document.chapters.take(insertPosition).asReversed().firstOrNull()
    val reference = before ?: document.chapters.drop(insertPosition).firstOrNull()
    return reference != null && parseTitleFormatParts(reference.title).prefix != null
}

internal fun updateHtmlTitleForFormat(
    html: String,
    plainTitle: String,
    headingHtml: String,
    styleCode: String
): String {
    val escapedPlainTitle = plainTitle.escapeXmlText()
    val titleRegex = Regex("""<title[^>]*>.*?</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val headingRegex = Regex("""<((?:h1|h2|h3))([^>]*)>.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    var updated = if (titleRegex.containsMatchIn(html)) {
        html.replace(titleRegex, "<title>$escapedPlainTitle</title>")
    } else {
        html
    }

    val headingMatch = headingRegex.find(updated)
    if (headingMatch != null) {
        val tag = headingMatch.groupValues[1]
        val attrs = headingMatch.groupValues[2]
        val nextAttrs = updateTitleHeadingClass(attrs, styleCode)
        return updated.replaceRange(
            headingMatch.range,
            "<$tag$nextAttrs>$headingHtml</$tag>"
        )
    }

    val bodyRegex = Regex("""<body([^>]*)>""", RegexOption.IGNORE_CASE)
    return bodyRegex.find(updated)?.let { match ->
        updated.replaceRange(
            match.range,
            "${match.value}\n<h1 class=\"chapter-title_$styleCode\">$headingHtml</h1>"
        )
    } ?: "<h1 class=\"chapter-title_$styleCode\">$headingHtml</h1>\n$updated"
}

internal fun applyEpubTitleFormatsToBook(
    book: EpubBook,
    renderedByIndex: List<Pair<Int, TitleFormatRendered>>
): Int {
    var changed = 0
    renderedByIndex.forEach { (index, rendered) ->
        val chapter = book.chapters.getOrNull(index) ?: return@forEach
        val nextHtml = updateHtmlTitleForFormat(
            html = chapter.html,
            plainTitle = rendered.plainTitle,
            headingHtml = rendered.headingHtml,
            styleCode = rendered.styleCode
        )
        if (chapter.title != rendered.plainTitle || chapter.html != nextHtml) {
            chapter.title = rendered.plainTitle
            chapter.html = nextHtml
            chapter.wordCount = ChapterDetector.countHtmlChars(chapter.html)
            updateEpubChapterHtmlEntry(book, chapter)
            changed += 1
        }
    }
    return changed
}

internal fun applyTxtTitleFormatsToDocument(
    document: TxtDocument,
    renderedByIndex: List<Pair<Int, TitleFormatRendered>>,
    detectChapters: (String) -> List<TxtChapter>
): Int {
    var text = document.text
    var changed = 0
    renderedByIndex
        .sortedByDescending { it.first }
        .forEach { (index, rendered) ->
            val chapter = document.chapters.getOrNull(index) ?: return@forEach
            if (chapter.title != rendered.plainTitle) {
                text = ChapterDetector.updateTxtTitle(text, chapter.lineIndex, rendered.plainTitle)
                changed += 1
            }
        }
    if (changed > 0) {
        document.text = text
        document.chapters = detectChapters(text)
    }
    return changed
}

private fun updateTitleHeadingClass(attrs: String, styleCode: String): String {
    val classRegex = Regex("""\sclass\s*=\s*(['"])(.*?)\1""", RegexOption.IGNORE_CASE)
    val classMatch = classRegex.find(attrs)
    if (classMatch != null) {
        val quote = classMatch.groupValues[1]
        val classes = classMatch.groupValues[2]
            .split(Regex("""\s+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() && !Regex("""^chapter-title_\d+${'$'}""").matches(it) }
            .toMutableList()
        classes += "chapter-title_$styleCode"
        val nextClass = " class=$quote${classes.joinToString(" ")}$quote"
        return attrs.replaceRange(classMatch.range, nextClass)
    }
    return "$attrs class=\"chapter-title_$styleCode\""
}

internal fun inheritedTitleHeadingFormat(html: String): EpubTitleHeadingFormat? {
    val headingRegex = Regex("""<((?:h1|h2|h3))([^>]*)>.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val classRegex = Regex("""\sclass\s*=\s*(['"])(.*?)\1""", RegexOption.IGNORE_CASE)
    val headingMatch = headingRegex.find(html) ?: return null
    val classValue = classRegex.find(headingMatch.groupValues[2])
        ?.groupValues
        ?.getOrNull(2)
        .orEmpty()
    return EpubTitleHeadingFormat(
        tag = headingMatch.groupValues[1],
        classValue = classValue
    )
}

internal fun updateHtmlTitleWithInheritedFormat(
    html: String,
    newTitle: String,
    inheritedFormat: EpubTitleHeadingFormat?
): String {
    val escapedTitle = ChapterDetector.cleanTitle(newTitle).escapeXmlText()
    val titleRegex = Regex("""<title[^>]*>.*?</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val headingRegex = Regex("""<((?:h1|h2|h3))([^>]*)>.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    var updated = if (titleRegex.containsMatchIn(html)) {
        html.replace(titleRegex, "<title>$escapedTitle</title>")
    } else {
        html
    }

    val headingMatch = headingRegex.find(updated)
    if (headingMatch != null) {
        val tag = inheritedFormat?.tag ?: headingMatch.groupValues[1]
        val attrs = applyInheritedHeadingClass(
            attrs = headingMatch.groupValues[2],
            classValue = inheritedFormat?.classValue.orEmpty()
        )
        return updated.replaceRange(
            headingMatch.range,
            "<$tag$attrs>$escapedTitle</$tag>\n"
        )
    }

    val tag = inheritedFormat?.tag?.ifBlank { "h1" } ?: "h1"
    val attrs = inheritedFormat
        ?.classValue
        ?.takeIf { it.isNotBlank() }
        ?.let { " class=\"${it.escapeXmlAttribute("\"")}\"" }
        .orEmpty()
    val heading = "<$tag$attrs>$escapedTitle</$tag>"
    val bodyRegex = Regex("""<body([^>]*)>""", RegexOption.IGNORE_CASE)
    return bodyRegex.find(updated)?.let { match ->
        updated.replaceRange(match.range, "${match.value}\n$heading\n")
    } ?: "$heading\n$updated"
}

private fun applyInheritedHeadingClass(attrs: String, classValue: String): String {
    if (classValue.isBlank()) return attrs
    val classRegex = Regex("""\sclass\s*=\s*(['"])(.*?)\1""", RegexOption.IGNORE_CASE)
    val classAttr = " class=\"${classValue.escapeXmlAttribute("\"")}\""
    val classMatch = classRegex.find(attrs)
    return if (classMatch != null) {
        attrs.replaceRange(classMatch.range, classAttr)
    } else {
        "$attrs$classAttr"
    }
}
