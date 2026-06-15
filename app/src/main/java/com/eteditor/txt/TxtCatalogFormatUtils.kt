package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.TxtChapter

internal fun formatTxtLayoutFromCurrentCatalog(
    text: String,
    chapters: List<TxtChapter>,
    config: TxtChapterDetectionConfig
): TxtCatalogFormatResult {
    val chapterLineIndices = chapters.map { it.lineIndex }.toSet()
    val output = mutableListOf<String>()
    val formattedLineBySourceLine = mutableMapOf<Int, Int>()
    var removedBlank = 0
    var contentLines = 0
    var chapterLines = 0

    text.lineSequence().map { it.removeSuffix("\r") }.forEachIndexed { lineIndex, raw ->
        val stripped = raw.trim()
        if (stripped.isBlank()) {
            removedBlank += 1
            return@forEachIndexed
        }

        if (lineIndex in chapterLineIndices) {
            if (output.isNotEmpty()) {
                var existingBlank = 0
                for (index in output.lastIndex downTo 0) {
                    if (output[index].isNotBlank()) break
                    existingBlank += 1
                }
                repeat((2 - existingBlank).coerceAtLeast(0)) { output += "" }
            }
            formattedLineBySourceLine[lineIndex] = output.size
            output += stripped
            chapterLines += 1
        } else {
            output += "\u3000\u3000$stripped"
            contentLines += 1
        }
    }

    val formattedText = output.joinToString("\n").toCrlfLineEndings()
    val formattedChapters = rebuildTxtChaptersFromFormattedLines(
        text = formattedText,
        chapters = chapters,
        formattedLineBySourceLine = formattedLineBySourceLine,
        config = config
    )
    return TxtCatalogFormatResult(
        text = formattedText,
        chapters = formattedChapters,
        removedBlankCount = removedBlank,
        contentLineCount = contentLines,
        chapterLineCount = chapterLines
    )
}

private fun rebuildTxtChaptersFromFormattedLines(
    text: String,
    chapters: List<TxtChapter>,
    formattedLineBySourceLine: Map<Int, Int>,
    config: TxtChapterDetectionConfig
): List<TxtChapter> {
    val mappedChapters = chapters.mapNotNull { chapter ->
        val lineIndex = formattedLineBySourceLine[chapter.lineIndex] ?: return@mapNotNull null
        chapter to lineIndex
    }
    return rebuildTxtChaptersAtLineIndices(text, mappedChapters, config)
}

private fun rebuildTxtChaptersAtLineIndices(
    text: String,
    chaptersWithLineIndices: List<Pair<TxtChapter, Int>>,
    config: TxtChapterDetectionConfig
): List<TxtChapter> {
    if (chaptersWithLineIndices.isEmpty()) return emptyList()
    val lines = txtLinePositions(text)
    val rebuilt = chaptersWithLineIndices.mapIndexedNotNull { index, (chapter, lineIndex) ->
        val position = lines.getOrNull(lineIndex) ?: return@mapIndexedNotNull null
        val nextLineIndex = chaptersWithLineIndices.getOrNull(index + 1)?.second
        val nextChapterPosition = nextLineIndex?.let { lines.getOrNull(it) }
        val endIndex = nextChapterPosition?.startIndex ?: text.length
        val bodyStart = position.nextIndex.coerceIn(0, text.length)
        val bodyEnd = endIndex.coerceIn(bodyStart, text.length)
        val title = chapter.title.trim().ifBlank { txtLineText(text, position.startIndex) }
        chapter.copy(
            index = index + 1,
            lineIndex = lineIndex,
            title = title,
            wordCount = ChapterDetector.countVisibleChars(text.substring(bodyStart, bodyEnd)),
            startIndex = position.startIndex,
            bodyStartIndex = bodyStart,
            endIndex = bodyEnd,
            endLineIndex = nextLineIndex?.takeIf { nextChapterPosition != null } ?: lines.size,
            number = ChapterDetector.txtChapterNumberFromTitle(title)
        )
    }
    return refreshTxtChapterStatuses(rebuilt, config)
}
