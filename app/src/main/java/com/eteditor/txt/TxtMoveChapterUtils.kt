package com.eteditor

import com.eteditor.core.TxtChapter

internal fun buildMovedTxtChapterText(
    text: String,
    desiredChapters: List<TxtChapter>,
    firstStart: Int,
    insertIndex: Int,
    config: TxtChapterDetectionConfig,
    autoKeys: Set<String>,
    detectChapters: (String, TxtChapterDetectionConfig, Set<String>) -> List<TxtChapter>
): TxtMoveChapterResult {
    if (desiredChapters.isEmpty()) {
        return TxtMoveChapterResult(text, emptyList(), 0)
    }
    val orderedByText = desiredChapters.sortedBy { it.startIndex }
    val starts = orderedByText.map { it.startIndex }
    if (
        firstStart !in 0..text.length ||
        starts.any { it !in 0..text.length } ||
        starts.distinct().size != starts.size ||
        starts.zipWithNext().any { (left, right) -> left >= right }
    ) {
        error("目录位置已过期，请先刷新目录")
    }

    val segmentsByStart = orderedByText.mapIndexed { index, chapter ->
        val start = chapter.startIndex
        val end = orderedByText.getOrNull(index + 1)?.startIndex ?: text.length
        if (end < start) error("目录位置已过期，请先刷新目录")
        start to text.substring(start, end)
    }.toMap()
    val segments = desiredChapters.map { chapter ->
        segmentsByStart[chapter.startIndex] ?: error("目录位置已过期，请先刷新目录")
    }.toMutableList()
    for (index in 0 until segments.lastIndex) {
        if (segments[index].isNotEmpty() && !segments[index].endsWith("\n")) {
            segments[index] = segments[index] + "\n"
        }
    }
    val nextText = text.substring(0, firstStart) + segments.joinToString("")
    return TxtMoveChapterResult(
        text = nextText,
        chapters = detectChapters(nextText, config, autoKeys),
        insertIndex = insertIndex
    )
}

internal fun moveTxtChapterList(
    chapters: List<TxtChapter>,
    sourceIndex: Int,
    targetIndex: Int
): Pair<List<TxtChapter>, Int>? {
    if (sourceIndex !in chapters.indices) return null
    if (targetIndex !in setOf(MOVE_TARGET_BOOK_START, MOVE_TARGET_BOOK_END) && targetIndex !in chapters.indices) return null
    val reordered = chapters.toMutableList()
    val moved = reordered.removeAt(sourceIndex)
    val clampedTarget = targetIndex.coerceIn(0, chapters.lastIndex)
    val insertIndex = when (targetIndex) {
        MOVE_TARGET_BOOK_START -> 0
        MOVE_TARGET_BOOK_END -> reordered.size
        else -> {
            val adjustedTarget = if (clampedTarget > sourceIndex) clampedTarget - 1 else clampedTarget
            (adjustedTarget + 1).coerceIn(0, reordered.size)
        }
    }
    reordered.add(insertIndex, moved)
    return reordered.mapIndexed { index, chapter -> chapter.copy(index = index + 1) } to insertIndex
}

internal fun moveTxtChapterList(
    chapters: List<TxtChapter>,
    sourceIndices: Set<Int>,
    targetIndex: Int
): Pair<List<TxtChapter>, Int>? {
    val selectedIndices = sourceIndices
        .filter { index -> index in chapters.indices }
        .toSet()
    if (selectedIndices.isEmpty()) return null
    if (targetIndex !in setOf(MOVE_TARGET_BOOK_START, MOVE_TARGET_BOOK_END) && targetIndex !in chapters.indices) return null
    if (targetIndex in selectedIndices) return null

    val moving = chapters.filterIndexed { index, _ -> index in selectedIndices }
    val remaining = chapters.filterIndexed { index, _ -> index !in selectedIndices }.toMutableList()
    val insertIndex = when (targetIndex) {
        MOVE_TARGET_BOOK_START -> 0
        MOVE_TARGET_BOOK_END -> remaining.size
        else -> {
            val target = chapters[targetIndex]
            val remainingTargetIndex = remaining.indexOfFirst { chapter ->
                chapter.startIndex == target.startIndex && chapter.lineIndex == target.lineIndex
            }
            if (remainingTargetIndex < 0) return null
            remainingTargetIndex + 1
        }
    }.coerceIn(0, remaining.size)

    remaining.addAll(insertIndex, moving)
    return remaining.mapIndexed { index, chapter -> chapter.copy(index = index + 1) } to insertIndex
}

internal fun txtPreviewIndexAfterChapterDeletion(
    previousPreviewIndex: Int,
    deletedIndex: Int,
    remainingChapterCount: Int
): Int {
    if (remainingChapterCount <= 0) return 0
    if (previousPreviewIndex < 0) return previousPreviewIndex
    return when {
        previousPreviewIndex < deletedIndex -> previousPreviewIndex
        previousPreviewIndex == deletedIndex -> deletedIndex.coerceAtMost(remainingChapterCount - 1)
        else -> (previousPreviewIndex - 1).coerceIn(0, remainingChapterCount - 1)
    }
}

internal fun txtPreviewIndexAfterChapterBlocksDeletion(
    previousPreviewIndex: Int,
    deletedIndices: Set<Int>,
    remainingChapterCount: Int
): Int {
    if (remainingChapterCount <= 0) return 0
    if (previousPreviewIndex < 0) return previousPreviewIndex
    val deletedBefore = deletedIndices.count { index -> index < previousPreviewIndex }
    val nextIndex = previousPreviewIndex - deletedBefore
    return nextIndex.coerceIn(0, remainingChapterCount - 1)
}

internal fun txtLineOffsets(text: String): List<Int> {
    val offsets = mutableListOf(0)
    text.forEachIndexed { index, char ->
        if (char == '\n') offsets += index + 1
    }
    offsets += text.length
    return offsets
}

internal fun shiftTxtChaptersAfterTextChange(
    chapters: List<TxtChapter>,
    sourceStart: Int,
    sourceEnd: Int,
    originalText: String,
    replacementText: String
): List<TxtChapter> {
    val textDelta = replacementText.length - originalText.length
    val lineDelta = replacementText.count { it == '\n' } - originalText.count { it == '\n' }
    if (textDelta == 0 && lineDelta == 0) return chapters
    return chapters.map { chapter ->
        when {
            chapter.endIndex <= sourceStart -> chapter
            chapter.startIndex >= sourceEnd -> chapter.copy(
                lineIndex = chapter.lineIndex + lineDelta,
                endLineIndex = chapter.endLineIndex + lineDelta,
                startIndex = chapter.startIndex + textDelta,
                bodyStartIndex = chapter.bodyStartIndex + textDelta,
                endIndex = chapter.endIndex + textDelta
            )
            else -> chapter.copy(
                bodyStartIndex = if (sourceStart < chapter.bodyStartIndex) {
                    chapter.bodyStartIndex + textDelta
                } else {
                    chapter.bodyStartIndex
                },
                endLineIndex = chapter.endLineIndex + lineDelta,
                endIndex = chapter.endIndex + textDelta
            )
        }
    }
}
