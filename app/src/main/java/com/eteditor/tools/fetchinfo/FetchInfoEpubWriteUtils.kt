package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.ManifestItem
import com.eteditor.core.decodeEpubHtmlBytes
import com.eteditor.core.encodeEpubHtmlUtf8
import com.eteditor.core.normalizeEpubHtmlUtf8Declaration

internal data class FetchInfoCatalogWriteResult(
    val changed: Int,
    val touchedCurrentChapter: Boolean
)

internal fun applyFetchedCatalogToEpub(
    book: EpubBook,
    parameters: FetchInfoParameters,
    catalog: List<FetchedCatalogItem>,
    currentChapterIndex: Int,
    renames: Map<Int, String> = emptyMap(),
    deletes: Set<Int> = emptySet(),
    onError: (String) -> Unit = {}
): FetchInfoCatalogWriteResult {
    val targetChapters = fetchInfoCatalogTargetChapters(book).map { it.second }
    val currentChapter = book.chapters.getOrNull(currentChapterIndex)
    val autoStyle = fetchInfoCatalogAutoTitleStyle(parameters, targetChapters, catalog)
    val usedVolumePaths = mutableSetOf<String>()
    var targetCursor = 0
    var changed = 0
    var touchedCurrent = false
    catalog.forEach { item ->
        if (item.isVolume) {
            // 目标章节已用尽后出现的卷不再写回，与预览一致。
            if (targetCursor >= targetChapters.size) return@forEach
            // 抓取来的卷标题保持原样，仅去首尾空白。
            val title = item.title.trim()
            if (title.isBlank()) return@forEach
            val insertPosition = fetchInfoVolumeInsertPosition(book, targetChapters, targetCursor, currentChapterIndex)
            val existingVolume = findFetchInfoAdjacentVolume(book, insertPosition, usedVolumePaths)
            val volumeChapter = existingVolume
                ?: insertEpubVolumeChapter(
                    book,
                    VOLUME_KIND_NORMAL,
                    title,
                    insertPosition,
                    onError = onError
                )?.second
            if (volumeChapter != null) {
                usedVolumePaths += volumeChapter.path
                if (volumeChapter.title != title) {
                    updateFetchedCatalogChapterTitle(volumeChapter, title)
                    if (volumeChapter === currentChapter) touchedCurrent = true
                }
                changed += 1
            }
        } else {
            val position = targetCursor
            val chapter = targetChapters.getOrNull(targetCursor) ?: return@forEach
            targetCursor += 1
            if (position in deletes) return@forEach
            val renamed = renames[position]
            if (renamed != null) {
                // 用户手动输入的重命名保持原样，仅去首尾空白。
                val cleanRenamed = renamed.trim()
                if (cleanRenamed.isBlank()) return@forEach
                updateFetchedCatalogChapterTitle(chapter, cleanRenamed)
                if (chapter === currentChapter) touchedCurrent = true
                changed += 1
                return@forEach
            }
            val rendered = fetchInfoCatalogWriteBackRenderedTitle(chapter.title, item, autoStyle)
            val title = rendered.plainTitle
            if (title.isBlank()) return@forEach
            if (autoStyle != null && fetchInfoChapterNumberPrefix(chapter.title).isNotBlank()) {
                updateFetchedCatalogChapterFormattedTitle(chapter, rendered)
            } else {
                updateFetchedCatalogChapterTitle(chapter, title)
            }
            if (chapter === currentChapter) touchedCurrent = true
            changed += 1
        }
    }
    resequenceEpubVolumeFileNames(book, VOLUME_KIND_NORMAL)
    applyVolumeTocLevels(book)
    return FetchInfoCatalogWriteResult(changed = changed, touchedCurrentChapter = touchedCurrent)
}

private fun updateFetchedCatalogChapterTitle(chapter: EpubChapter, title: String) {
    chapter.title = title
    chapter.html = if (chapter.isVolumeChapter()) {
        ChapterDetector.updateHtmlTitleWithLineBreaks(chapter.html, title)
    } else {
        ChapterDetector.updateHtmlTitle(chapter.html, title)
    }
    chapter.wordCount = ChapterDetector.countHtmlChars(chapter.html)
}

private fun updateFetchedCatalogChapterFormattedTitle(chapter: EpubChapter, rendered: TitleFormatRendered) {
    chapter.title = rendered.plainTitle
    chapter.html = updateHtmlTitleForFormat(
        html = chapter.html,
        plainTitle = rendered.plainTitle,
        headingHtml = rendered.headingHtml,
        styleCode = rendered.styleCode
    )
    chapter.wordCount = ChapterDetector.countHtmlChars(chapter.html)
}

internal fun writeFetchInfoIntroFileToEpub(book: EpubBook, targetPath: String, info: FetchedInfo, source: String) {
    val path = resolveFetchInfoIntroTarget(targetPath, book)
    val html = if (book.entries.containsKey(path)) {
        val current = decodeEpubHtmlBytes(book.entries[path] ?: ByteArray(0))
        val body = fetchedIntroBodyHtml(info.intro, source)
        replaceIntroHtmlPreservingStructure(current, body)
    } else {
        introHtml(info.title.ifBlank { "\u7b80\u4ecb" }, info.intro, source)
    }
    val normalizedHtml = normalizeEpubHtmlUtf8Declaration(html)
    book.entries[path] = encodeEpubHtmlUtf8(normalizedHtml)
    book.chapters.firstOrNull { chapter ->
        normalizeEpubPath(chapter.path).equals(path, ignoreCase = true) ||
            normalizeEpubPath(chapter.originalPath).equals(path, ignoreCase = true) ||
            chapter.pathAliases.any { alias -> normalizeEpubPath(alias).equals(path, ignoreCase = true) }
    }?.let { chapter ->
        chapter.html = normalizedHtml
        chapter.wordCount = ChapterDetector.countHtmlChars(normalizedHtml)
    }
    if (book.manifest.values.none { it.path == path }) {
        val opfDir = book.opfPath.substringBeforeLast('/', missingDelimiterValue = "").let {
            if (it.isBlank()) "" else "$it/"
        }
        val id = uniqueManifestId(book, path.substringAfterLast('/').substringBeforeLast('.').ifBlank { "intro" })
        book.manifest[id] = ManifestItem(
            id = id,
            href = relativeEpubHref(opfDir, path),
            mediaType = if (path.endsWith(".html", ignoreCase = true) ||
                path.endsWith(".htm", ignoreCase = true)
            ) {
                "text/html"
            } else {
                "application/xhtml+xml"
            },
            path = path
        )
    }
}
