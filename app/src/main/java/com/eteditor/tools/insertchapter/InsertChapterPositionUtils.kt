package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.EpubBook
import com.eteditor.core.TxtDocument

internal data class InsertTxtChaptersResult(
    val text: String,
    val insertPosition: Int,
    val insertedCount: Int
)

internal fun renumberInsertedChapterTitle(title: String, number: Int): String {
    val cleaned = ChapterDetector.cleanTitle(title)
    val suffix = Regex("""^第\s*[\d零〇一二两三四五六七八九十百千万亿]+\s*[章节回卷部集]\s*(.*)${'$'}""")
        .find(cleaned)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?: cleaned
    return if (suffix.isBlank()) "第${number}章" else "第${number}章$suffix"
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

internal fun resolveTxtInsertChapterPosition(
    document: TxtDocument,
    positionMode: String,
    targetChapterIndex: Int?,
    currentChapterIndex: Int
): Int {
    if (document.chapters.isEmpty()) {
        return if (positionMode == INSERT_CHAPTER_POSITION_START) 0 else 1
    }
    val current = currentChapterIndex.coerceIn(0, document.chapters.lastIndex)
    return when (positionMode) {
        INSERT_CHAPTER_POSITION_START -> 0
        INSERT_CHAPTER_POSITION_END,
        INSERT_CHAPTER_POSITION_VOLUME_END -> document.chapters.size
        INSERT_CHAPTER_POSITION_CURRENT_BEFORE -> current
        INSERT_CHAPTER_POSITION_CURRENT_AFTER -> current + 1
        INSERT_CHAPTER_POSITION_TARGET_BEFORE -> targetChapterIndex
            ?.coerceIn(0, document.chapters.lastIndex)
            ?: current
        INSERT_CHAPTER_POSITION_TARGET_AFTER -> targetChapterIndex
            ?.coerceIn(0, document.chapters.lastIndex)
            ?.plus(1)
            ?: (current + 1)
        else -> document.chapters.size
    }.coerceIn(0, document.chapters.size)
}

internal fun txtChapterInsertOffset(document: TxtDocument, insertPosition: Int): Int {
    if (document.chapters.isEmpty()) {
        return if (insertPosition <= 0) 0 else document.text.length
    }
    val offsets = txtLineOffsets(document.text)
    return if (insertPosition >= document.chapters.size) {
        document.text.length
    } else {
        val chapter = document.chapters[insertPosition.coerceIn(0, document.chapters.lastIndex)]
        offsets.getOrNull(chapter.lineIndex) ?: document.text.length
    }
}

internal fun insertChaptersIntoTxtDocumentText(
    document: TxtDocument,
    selected: List<InsertableChapter>,
    positionMode: String,
    targetChapterIndex: Int?,
    currentChapterIndex: Int,
    onProgress: (Int, Int) -> Unit = { _, _ -> }
): InsertTxtChaptersResult? {
    val insertPosition = resolveTxtInsertChapterPosition(document, positionMode, targetChapterIndex, currentChapterIndex)
    var number = document.chapters.take(insertPosition).size + 1
    val referenceStyle = txtInsertReferenceTitleStyle(document, insertPosition)
    var completed = 0
    val chunks = selected
        .map { chapter ->
            val numberedTitle = if (!chapter.isVolume) {
                renumberInsertedChapterTitle(chapter.title, number++)
            } else {
                chapter.title
            }
            val title = if (chapter.isVolume) {
                numberedTitle
            } else {
                renderInsertedChapterTitle(numberedTitle, referenceStyle).plainTitle
            }
            buildString {
                append(title.trim())
                val body = chapter.text.trim().toCrlfLineEndings()
                if (body.isNotBlank()) {
                    append("\r\n")
                    append(body)
                }
            }.trim().toCrlfLineEndings()
                .also {
                    completed += 1
                    onProgress(completed, selected.size)
                }
        }
        .filter { it.isNotBlank() }
    if (chunks.isEmpty()) return null

    val offset = txtChapterInsertOffset(document, insertPosition)
    val before = document.text.substring(0, offset.coerceIn(0, document.text.length)).trimEnd()
    val after = document.text.substring(offset.coerceIn(0, document.text.length)).trimStart()
    val insertion = chunks.joinToString("\r\n\r\n")
    val text = buildString {
        if (before.isNotBlank()) {
            append(before)
            append("\r\n\r\n")
        }
        append(insertion)
        if (after.isNotBlank()) {
            append("\r\n\r\n")
            append(after)
        }
    }.toCrlfLineEndings()
    return InsertTxtChaptersResult(
        text = text,
        insertPosition = insertPosition,
        insertedCount = chunks.size
    )
}
