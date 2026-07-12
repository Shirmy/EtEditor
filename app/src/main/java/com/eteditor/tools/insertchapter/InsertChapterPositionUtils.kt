package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.EpubBook

internal fun renumberInsertedChapterTitle(title: String, number: Int): String {
    val cleaned = ChapterDetector.cleanTitle(title)
    val match = Regex("""^第\s*[\d零〇一二两三四五六七八九十百千万亿]+\s*[章节回卷部集]\s*(.*)${'$'}""")
        .find(cleaned)
        ?: return if (cleaned.isBlank()) "第${number}章" else "第${number}章 $cleaned"
    val suffix = match.groupValues.getOrNull(1)?.trim().orEmpty()
    return if (suffix.isBlank()) "第${number}章" else "第${number}章$suffix"
}

internal fun nextInsertedChapterNumber(book: EpubBook, insertPosition: Int): Int? {
    return book.chapters
        .take(insertPosition.coerceIn(0, book.chapters.size))
        .asReversed()
        .asSequence()
        .filterNot { chapter -> chapter.isVolumeChapter() || chapter.isCoverSection0001Or0002() }
        .mapNotNull { chapter -> ChapterDetector.txtChapterNumberFromTitle(chapter.title) }
        .firstOrNull()
        ?.plus(1)
}

internal fun resolveEpubInsertChapterPosition(
    book: EpubBook,
    positionMode: String,
    targetChapterIndex: Int?,
    currentChapterIndex: Int
): Int {
    val current = currentChapterIndex.coerceIn(0, book.chapters.lastIndex.coerceAtLeast(0))
    return when (positionMode) {
        INSERT_CHAPTER_POSITION_START -> 0
        INSERT_CHAPTER_POSITION_END -> book.chapters.size
        INSERT_CHAPTER_POSITION_CURRENT_BEFORE -> current
        INSERT_CHAPTER_POSITION_CURRENT_AFTER -> current + 1
        INSERT_CHAPTER_POSITION_VOLUME_END -> {
            val nextVolume = ((current + 1) until book.chapters.size).firstOrNull { index ->
                book.chapters[index].isVolumeChapter()
            }
            nextVolume ?: book.chapters.size
        }
        INSERT_CHAPTER_POSITION_TARGET_BEFORE -> targetChapterIndex
            ?.coerceIn(0, book.chapters.lastIndex.coerceAtLeast(0))
            ?: current
        INSERT_CHAPTER_POSITION_TARGET_AFTER -> targetChapterIndex
            ?.coerceIn(0, book.chapters.lastIndex.coerceAtLeast(0))
            ?.plus(1)
            ?: (current + 1)
        else -> book.chapters.size
    }.coerceIn(0, book.chapters.size)
}
