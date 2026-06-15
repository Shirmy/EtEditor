package com.eteditor

import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.EpubExportOptions

internal fun fetchInfoCatalogTargetChapters(book: EpubBook): List<Pair<Int, EpubChapter>> {
    return book.chapters
        .mapIndexed { index, chapter -> index to chapter }
        .filterNot { (_, chapter) -> chapter.isVolumeChapter() }
        .filterNot { (_, chapter) -> chapter.isCoverSection0001Or0002() }
}

internal fun fetchInfoVolumeInsertPosition(
    book: EpubBook,
    targets: List<EpubChapter>,
    targetCursor: Int,
    fallbackChapterIndex: Int
): Int {
    val nextTarget = targets.getOrNull(targetCursor)
    if (nextTarget != null) {
        val index = book.chapters.indexOfFirst { it === nextTarget }
        if (index >= 0) return index
    }
    val previousTarget = targets.getOrNull(targetCursor - 1) ?: targets.lastOrNull()
    if (previousTarget != null) {
        val index = book.chapters.indexOfFirst { it === previousTarget }
        if (index >= 0) return (index + 1).coerceIn(0, book.chapters.size)
    }
    return fallbackChapterIndex.coerceIn(0, book.chapters.size)
}

internal fun findFetchInfoAdjacentVolume(
    book: EpubBook,
    insertPosition: Int,
    usedVolumePaths: Set<String>
): EpubChapter? {
    return listOf(insertPosition - 1, insertPosition)
        .asSequence()
        .filter { it in book.chapters.indices }
        .map { book.chapters[it] }
        .firstOrNull { chapter -> chapter.isVolumeChapter() && chapter.path !in usedVolumePaths }
}

internal fun isSection0001Path(pathOrFileName: String): Boolean {
    val file = pathOrFileName.substringBefore('#').substringAfterLast('/')
    return file.equals("Section0001.xhtml", ignoreCase = true) ||
        file.equals("Section0001.html", ignoreCase = true)
}

internal fun epubExportOptions(hideSection0001FromNcx: Boolean): EpubExportOptions {
    return EpubExportOptions(
        ncxFollowHtmlFileTitle = true,
        rebuildNcxHierarchyFromHtmlHeadings = true,
        hideSection0001FromNcx = hideSection0001FromNcx
    )
}
