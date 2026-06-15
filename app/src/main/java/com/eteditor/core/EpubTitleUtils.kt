package com.eteditor.core

internal const val EPUB_COVER_DIRECTORY_TITLE = "封面"

internal fun epubDirectoryTitle(path: String, html: String): String {
    if (isEpubCoverDirectoryCandidate(path, html)) return EPUB_COVER_DIRECTORY_TITLE
    return epubOwnFileDirectoryTitle(path, html)
}

internal fun epubOwnFileDirectoryTitle(path: String, html: String): String {
    return htmlTocTitleFromHtml(html)
        .let(ChapterDetector::cleanTitleLineBreaksAsSpace)
        .ifBlank { fallbackEpubDirectoryTitle(path) }
}

internal fun EpubChapter.epubDirectoryTitle(useOwnSection0001Title: Boolean = false): String {
    return if (useOwnSection0001Title && isSection0001Chapter()) {
        epubOwnFileDirectoryTitle(path, html)
    } else {
        epubDirectoryTitle(path, html)
    }
}

internal fun EpubChapter.syncEpubDirectoryTitleFromHtml(useOwnSection0001Title: Boolean = false): Boolean {
    val nextTitle = epubDirectoryTitle(useOwnSection0001Title)
    if (title == nextTitle) return false
    title = nextTitle
    return true
}

internal fun EpubChapter.isEpubCoverDirectoryCandidate(): Boolean {
    return isEpubCoverDirectoryCandidate(path, html)
}

internal fun isEpubCoverDirectoryCandidate(path: String, html: String): Boolean {
    val stem = path.substringAfterLast('/').substringBeforeLast('.').lowercase()
    if (stem in setOf("cover", "coverpage", "cover-page", "titlepage", "title-page", "section0001")) {
        return true
    }
    return Regex("""(?i)<body\b[\s\S]*?<img\b""").containsMatchIn(html) &&
        Regex("""(?i)(cover|titlepage|title-page)""").containsMatchIn(path)
}

private fun htmlTocTitleFromHtml(html: String): String {
    listOf("h1", "h2", "title").forEach { tag ->
        val title = htmlTagTextRegex(tag).find(html)?.groupValues?.getOrNull(1)
            ?.let(ChapterDetector::stripHtml)
            .orEmpty()
        if (title.isNotBlank()) return title
    }
    return ""
}

private fun htmlTagTextRegex(tag: String): Regex {
    val escapedTag = Regex.escape(tag)
    return Regex(
        """<(?:(?:[A-Za-z_][\w.-]*):)?$escapedTag\b[^>]*>(.*?)</(?:(?:[A-Za-z_][\w.-]*):)?$escapedTag\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
}

private fun fallbackEpubDirectoryTitle(path: String): String {
    return path.substringAfterLast('/').substringBeforeLast('.').ifBlank { "未命名章节" }
}
