package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.EpubChapter

internal fun addUniqueLine(lines: MutableList<String>, value: String) {
    val clean = value.compactLines()
    if (clean.isNotBlank() && clean !in lines) lines += clean
}

internal fun String.compactLines(): String {
    return replace('\u00A0', ' ')
        .replace("\r", "\n")
        .split(Regex("""\n+"""))
        .map { line -> line.replace(Regex("""\s+"""), " ").trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

internal fun String.trimPresaleBlock(): String {
    var lines = compactLines().lines()
    val cutIndex = lines.withIndex().firstOrNull { (index, line) -> index >= 3 && line.contains("预收") }?.index ?: -1
    if (cutIndex >= 0) lines = lines.take(cutIndex)
    return lines.joinToString("\n")
}

internal fun String.cleanIntroBracketLines(): String {
    return compactLines().lines()
        .mapIndexed { index, line ->
            if (index >= 3) {
                line
            } else {
                line.replace(Regex("""【[^【】]*(?:更新|预收|请假)[^【】]*】"""), "")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
            }
        }
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

internal fun String.trimJjwxcIntroBoundary(): String {
    val boundaryPrefixes = listOf(
        "内容标签：",
        "内容标签:",
        "搜索关键字：",
        "搜索关键字:",
        "一句话简介：",
        "一句话简介:",
        "立意：",
        "立意:",
        "作品视角：",
        "作品视角:",
        "作品风格：",
        "作品风格:",
        "所属系列：",
        "所属系列:",
        "文章进度：",
        "文章进度:",
        "全文字数：",
        "全文字数:",
        "签约状态：",
        "签约状态:",
        "作品积分：",
        "作品积分:"
    )
    val boundaryMarkers = listOf(
        "霸王排行",
        "章节列表",
        "最新章节",
        "非v章节章均点击数",
        "总书评数",
        "当前被收藏数",
        "营养液数",
        "文章收藏"
    )
    val uiLines = setOf("展开", "收起", "展开全部", "点击展开全文")
    val lines = compactLines().lines()
        .filterNot { it.trim() in uiLines }
    val boundaryTerms = boundaryPrefixes + boundaryMarkers
    val cutIndex = lines.indexOfFirst { rawLine ->
        val line = rawLine.trim()
        boundaryPrefixes.any { line.startsWith(it) } ||
            boundaryMarkers.any { line.contains(it) }
    }
    val scoped = if (cutIndex >= 0) {
        val cutLine = lines[cutIndex]
        val markerIndex = boundaryTerms
            .map { cutLine.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull()
            ?: 0
        val keptHead = cutLine.substring(0, markerIndex).trim()
        if (keptHead.isBlank()) lines.take(cutIndex) else lines.take(cutIndex) + keptHead
    } else {
        lines
    }
    return scoped.joinToString("\n").trim()
}

internal fun String.cleanGongzicpIntro(): String {
    var value = cleanHtmlBlock().compactLines()
    val startIndex = value.indexOf("作品简介")
    if (startIndex >= 0) value = value.substring(startIndex + "作品简介".length)

    val endIndex = listOf("作品目录", "收起", "作者声明")
        .map { value.indexOf(it) }
        .filter { it >= 0 }
        .minOrNull()
    if (endIndex != null) value = value.substring(0, endIndex)

    return value.compactLines()
        .lines()
        .filterNot { it == "展开" || it == "收起" }
        .joinToString("\n")
        .trimPresaleBlock()
}

internal fun String.cleanHtmlBlock(): String {
    return replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""</p\s*>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""</div\s*>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""</li\s*>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""</tr\s*>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""</section\s*>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""</article\s*>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""</h[1-3]\s*>""", RegexOption.IGNORE_CASE), "\n")
        .cleanHtmlText()
        .replace(Regex("""[ \t]+\n"""), "\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}

internal fun String.cleanHtmlText(): String {
    return replace(Regex("""<script\b.*?</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("""<style\b.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("""<[^>]+>"""), "")
        .decodeHtmlEntities()
        .replace('\u00A0', ' ')
        .replace(Regex("""[ \t]{2,}"""), " ")
        .trim()
}

internal fun String.decodeHtmlEntities(): String {
    return replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("""&#(\d+);""")) { match ->
            match.groupValues[1].toIntOrNull()?.let { code -> runCatching { code.toChar().toString() }.getOrNull() }.orEmpty()
        }
        .replace(Regex("""&#x([0-9A-Fa-f]+);""")) { match ->
            match.groupValues[1].toIntOrNull(16)?.let { code -> runCatching { code.toChar().toString() }.getOrNull() }.orEmpty()
        }
}

private val FETCH_INFO_CHAPTER_PREFIX_REGEX = Regex(
    """^\s*(第\s*[0-9０-９一二三四五六七八九十百千万萬零〇○两兩壹贰叁肆伍陆柒捌玖拾佰仟]+\s*(?:章|节|回|话|話))\s*"""
)

internal fun fetchInfoCatalogAutoTitleStyle(
    parameters: FetchInfoParameters,
    targetChapters: List<EpubChapter>,
    catalog: List<FetchedCatalogItem>
): String? {
    if (!parameters.autoTitleFormat) return null
    val parts = mutableListOf<TitleFormatParts>()
    var targetCursor = 0
    catalog.forEach { item ->
        if (item.isVolume) return@forEach
        val chapter = targetChapters.getOrNull(targetCursor) ?: return@forEach
        targetCursor += 1
        val prefix = fetchInfoChapterNumberPrefix(chapter.title).takeIf { it.isNotBlank() }
        val suffix = fetchInfoFetchedTitleName(item)
        if (prefix != null || suffix.isNotBlank()) {
            parts += TitleFormatParts(prefix = prefix, suffix = suffix)
        }
    }
    if (parts.isEmpty()) return null
    return titleFormatAutoDecision(parts, DEFAULT_TITLE_FORMAT_SHORT_THRESHOLD).style
}

internal fun fetchInfoCatalogWriteBackTitle(
    originalTitle: String,
    item: FetchedCatalogItem,
    autoStyle: String? = null
): String {
    return fetchInfoCatalogWriteBackRenderedTitle(originalTitle, item, autoStyle).plainTitle
}

internal fun fetchInfoCatalogWriteBackRenderedTitle(
    originalTitle: String,
    item: FetchedCatalogItem,
    autoStyle: String?
): TitleFormatRendered {
    val originalPrefix = fetchInfoChapterNumberPrefix(originalTitle)
    val fetchedName = fetchInfoFetchedTitleName(item)
    if (autoStyle != null && originalPrefix.isNotBlank()) {
        return renderTitleFormat(originalPrefix, fetchedName, autoStyle)
    }
    val plainTitle = if (originalPrefix.isBlank()) {
        fetchedName.ifBlank { item.title.trim() }
    } else {
        listOf(originalPrefix, fetchedName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
    return TitleFormatRendered(
        plainTitle = plainTitle,
        headingHtml = plainTitle.escapeXmlText(),
        styleCode = TITLE_FORMAT_STYLE_DOUBLE
    )
}

internal fun fetchInfoChapterNumberPrefix(title: String): String {
    val clean = ChapterDetector.cleanTitle(title)
    return FETCH_INFO_CHAPTER_PREFIX_REGEX
        .find(clean)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        .orEmpty()
}

private fun fetchInfoFetchedTitleName(item: FetchedCatalogItem): String {
    // 抓取来的标题不做内置规范化（不合并空格/不转全角/不去标记），仅去首尾空白，保持原站原样。
    return item.title.ifBlank { item.chapterTitle }.trim()
}
