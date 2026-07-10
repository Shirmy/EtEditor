package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.TxtChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TxtMoveDeleteUtilsTest {
    @Test
    fun moveTxtChapterListMovesSingleChapterToBookEndAndReindexes() {
        val chapters = detect("第1章\n正文1\n第2章\n正文2\n第3章\n正文3")

        val result = moveTxtChapterList(chapters, sourceIndex = 0, targetIndex = MOVE_TARGET_BOOK_END)

        assertEquals(listOf("第2章", "第3章", "第1章"), result?.first?.map { it.title })
        assertEquals(listOf(1, 2, 3), result?.first?.map { it.index })
        assertEquals(2, result?.second)
        assertNull(moveTxtChapterList(chapters, sourceIndex = 9, targetIndex = 0))
    }

    @Test
    fun moveTxtChapterListMovesSelectedBlockAfterTarget() {
        val chapters = detect("第1章\n正文1\n第2章\n正文2\n第3章\n正文3\n第4章\n正文4")

        val result = moveTxtChapterList(chapters, sourceIndices = setOf(0, 2), targetIndex = 3)

        assertEquals(listOf("第2章", "第4章", "第1章", "第3章"), result?.first?.map { it.title })
        assertEquals(2, result?.second)
        assertNull(moveTxtChapterList(chapters, sourceIndices = setOf(0, 2), targetIndex = 2))
    }

    @Test
    fun moveTxtChapterListMovesValidSelectedBlockToBookStartAndIgnoresStaleIndices() {
        val chapters = detect("第1章\n正文1\n第2章\n正文2\n第3章\n正文3\n第4章\n正文4")

        val result = moveTxtChapterList(
            chapters = chapters,
            sourceIndices = setOf(1, 3, 99),
            targetIndex = MOVE_TARGET_BOOK_START
        )

        assertEquals(listOf("第2章", "第4章", "第1章", "第3章"), result?.first?.map { it.title })
        assertEquals(listOf(1, 2, 3, 4), result?.first?.map { it.index })
        assertEquals(0, result?.second)
    }

    @Test
    fun buildMovedTxtChapterTextRebuildsTextFromDesiredChapterOrder() {
        val text = "前言\n第1章\n正文1\n第2章\n正文2\n第3章\n正文3"
        val chapters = detect(text)
        val desired = listOf(chapters[1], chapters[0], chapters[2])

        val result = buildMovedTxtChapterText(
            text = text,
            desiredChapters = desired,
            firstStart = chapters.first().startIndex,
            insertIndex = 0,
            config = TxtChapterDetectionConfig("", 100, 10000, emptySet()),
            autoKeys = emptySet(),
            detectChapters = { nextText, _, _ -> detect(nextText) }
        )

        assertEquals("前言\n第2章\n正文2\n第1章\n正文1\n第3章\n正文3", result.text.trimEnd())
        assertEquals(listOf("第2章", "第1章", "第3章"), result.chapters.map { it.title })
    }

    @Test
    fun buildMovedTxtChapterTextAddsSeparatorWhenLastOriginalSegmentMovesBeforeAnotherChapter() {
        val text = "第1章\n正文1\n第2章\n正文2\n第3章\n正文3"
        val chapters = detect(text)
        val desired = listOf(chapters[2], chapters[0], chapters[1])

        val result = buildMovedTxtChapterText(
            text = text,
            desiredChapters = desired,
            firstStart = chapters.first().startIndex,
            insertIndex = 0,
            config = TxtChapterDetectionConfig("", 100, 10000, emptySet()),
            autoKeys = emptySet(),
            detectChapters = { nextText, _, _ -> detect(nextText) }
        )

        assertEquals("第3章\n正文3\n第1章\n正文1\n第2章\n正文2\n", result.text)
        assertEquals(listOf("第3章", "第1章", "第2章"), result.chapters.map { it.title })
    }

    @Test
    fun deleteTxtChapterBlockTextRemapsHiddenAndSupplementedLines() {
        val text = "第1章\n正文1\n第2章\n正文2\n第3章\n正文3"
        val chapters = detect(text)

        val result = deleteTxtChapterBlockText(
            sourceText = text,
            chapters = chapters,
            index = 1,
            hiddenCatalogLineIndices = setOf(1, 3, 5),
            supplementedCatalogLines = listOf(TxtSupplementedCatalogLine(4, "第3章", "第3章 补充"))
        )

        assertEquals("第1章\n正文1\n第3章\n正文3", result?.text)
        assertEquals(setOf(1, 3), result?.hiddenCatalogLineIndices)
        assertEquals(listOf(2), result?.supplementedCatalogLines?.map { it.lineIndex })
        assertEquals(setOf(1), result?.deletedIndices)
        assertEquals(1, result?.deletedCount)
        assertNull(deleteTxtChapterBlockText(text, chapters, 99, emptySet(), emptyList()))
    }

    @Test
    fun deleteTxtChapterBlockTextRemapsCrOnlyLines() {
        val text = "第1章\r正文1\r第2章\r正文2\r第3章\r正文3"
        val chapters = detect(text)

        val result = deleteTxtChapterBlockText(
            sourceText = text,
            chapters = chapters,
            index = 1,
            hiddenCatalogLineIndices = setOf(1, 3, 5),
            supplementedCatalogLines = listOf(TxtSupplementedCatalogLine(4, "第3章", "第3章 补充"))
        )

        assertEquals("第1章\r正文1\r第3章\r正文3", result?.text)
        assertEquals(setOf(1, 3), result?.hiddenCatalogLineIndices)
        assertEquals(listOf(2), result?.supplementedCatalogLines?.map { it.lineIndex })
    }

    @Test
    fun deleteTxtChapterBlocksTextRemovesMultipleRangesAndTracksDeletedIndices() {
        val text = "第1章\n正文1\n第2章\n正文2\n第3章\n正文3"
        val chapters = detect(text)

        val result = deleteTxtChapterBlocksText(
            sourceText = text,
            chapters = chapters,
            selectedIndices = listOf(0, 2),
            hiddenCatalogLineIndices = setOf(1, 3, 5),
            supplementedCatalogLines = emptyList()
        )

        assertEquals("第2章\n正文2\n", result?.text)
        assertEquals(setOf(1), result?.hiddenCatalogLineIndices)
        assertEquals(setOf(0, 2), result?.deletedIndices)
        assertEquals(2, result?.deletedCount)
    }

    @Test
    fun deleteTxtChapterBlocksTextRemapsSupplementedLinesAcrossMultipleRanges() {
        val text = "第1章\n正文1\n第2章\n正文2\n第3章\n正文3\n第4章\n正文4"
        val chapters = detect(text)

        val result = deleteTxtChapterBlocksText(
            sourceText = text,
            chapters = chapters,
            selectedIndices = listOf(1, 3, 99),
            hiddenCatalogLineIndices = setOf(1, 3, 5, 7),
            supplementedCatalogLines = listOf(
                TxtSupplementedCatalogLine(4, "第3章", "第3章 补"),
                TxtSupplementedCatalogLine(6, "第4章", "第4章 补")
            )
        )

        assertEquals("第1章\n正文1\n第3章\n正文3\n", result?.text)
        assertEquals(setOf(1, 3), result?.hiddenCatalogLineIndices)
        assertEquals(listOf(TxtSupplementedCatalogLine(2, "第3章", "第3章 补")), result?.supplementedCatalogLines)
        assertEquals(setOf(1, 3), result?.deletedIndices)
        assertEquals(2, result?.deletedCount)
    }

    @Test
    fun shiftTxtChaptersAfterTextChangeMovesFollowingChaptersAndExtendsOverlappingChapter() {
        val text = "第1章\n正文1\n第2章\n正文2"
        val chapters = detect(text)
        val sourceStart = text.indexOf("正文1")
        val sourceEnd = sourceStart + "正文1".length

        val shifted = shiftTxtChaptersAfterTextChange(
            chapters = chapters,
            sourceText = text,
            sourceStart = sourceStart,
            sourceEnd = sourceEnd,
            replacementText = "正文一\n新增"
        )

        assertEquals(chapters[0].endIndex + 3, shifted[0].endIndex)
        assertEquals(chapters[1].lineIndex + 1, shifted[1].lineIndex)
        assertEquals(chapters[1].startIndex + 3, shifted[1].startIndex)
        assertEquals(listOf(0, 2, 4, 5), txtLineOffsets("a\nb\nc"))
        assertEquals(0, txtPreviewIndexAfterChapterDeletion(0, 0, 0))
        assertEquals(1, txtPreviewIndexAfterChapterDeletion(2, 1, 2))
        assertEquals(1, txtPreviewIndexAfterChapterBlocksDeletion(3, setOf(0, 2), 2))
    }

    @Test
    fun shiftTxtChaptersAfterTextChangeMovesAllChaptersAfterPrefaceEdit() {
        val text = "前言\n第1章\n正文1\n第2章\n正文2"
        val chapters = detect(text)
        val originalText = "前言\n"
        val replacementText = "新前言\n补充\n"
        val textDelta = replacementText.length - originalText.length

        val shifted = shiftTxtChaptersAfterTextChange(
            chapters = chapters,
            sourceText = text,
            sourceStart = 0,
            sourceEnd = originalText.length,
            replacementText = replacementText
        )

        assertEquals(listOf(2, 4), shifted.map { it.lineIndex })
        assertEquals(chapters.map { it.startIndex + textDelta }, shifted.map { it.startIndex })
        assertEquals(chapters.map { it.bodyStartIndex + textDelta }, shifted.map { it.bodyStartIndex })
    }

    @Test
    fun shiftTxtChaptersAfterTextChangeCountsAllLineEndingShapes() {
        val sourceText = "旧\r文\r\n后"
        val chapters = listOf(
            chapter(lineIndex = 3, endLineIndex = 5, startIndex = 20, bodyStartIndex = 25, endIndex = 40)
        )

        val shifted = shiftTxtChaptersAfterTextChange(
            chapters = chapters,
            sourceText = sourceText,
            sourceStart = 0,
            sourceEnd = 3,
            replacementText = "a\r\nb\nc"
        ).single()

        assertEquals(4, shifted.lineIndex)
        assertEquals(6, shifted.endLineIndex)
        assertEquals(listOf(0, 2, 5, 7, 8), txtLineOffsets("a\rb\r\nc\nd"))
        assertEquals(3, txtLineBreakCount("a\rb\r\nc\nd"))
        assertEquals(2, countLineBreaksBefore("a\r\nb\r", 5))
    }

    @Test
    fun selectionDeletionRemapsCrAndMixedHiddenAndSupplementedLines() {
        val text = "头\r删1\r\n删2\n保留\r尾"
        val start = text.indexOf("删1")
        val end = text.indexOf("保留")

        assertEquals(
            setOf(1, 2),
            remapTxtLineIndicesAfterSelectionDeletion(
                lineIndices = setOf(3, 4),
                sourceText = text,
                start = start,
                end = end
            )
        )
        assertEquals(
            listOf(1, 2),
            remapTxtSupplementedLinesAfterSelectionDeletion(
                records = listOf(
                    TxtSupplementedCatalogLine(3, "保留", "第3章 保留"),
                    TxtSupplementedCatalogLine(4, "尾", "第4章 尾")
                ),
                sourceText = text,
                start = start,
                end = end
            ).map { it.lineIndex }
        )
    }

    @Test
    fun deletingOneHalfOfCrLfDoesNotMoveFollowingLineRecords() {
        val text = "标题\r\n正文\r尾"
        val crIndex = text.indexOf('\r')
        val lfIndex = text.indexOf('\n')

        assertEquals(
            setOf(1, 2),
            remapTxtLineIndicesAfterSelectionDeletion(
                lineIndices = setOf(1, 2),
                sourceText = text,
                start = crIndex,
                end = crIndex + 1
            )
        )
        assertEquals(
            setOf(1, 2),
            remapTxtLineIndicesAfterSelectionDeletion(
                lineIndices = setOf(1, 2),
                sourceText = text,
                start = lfIndex,
                end = lfIndex + 1
            )
        )
    }

    @Test
    fun deletingWholeStartingLineDropsItsRecordsButMidLineDeletionKeepsThem() {
        val text = "标题\r正文\r尾"
        val nextLineStart = text.indexOf("正文")
        val records = listOf(
            TxtSupplementedCatalogLine(0, "标题", "第1章 标题"),
            TxtSupplementedCatalogLine(1, "正文", "第2章 正文")
        )

        assertEquals(
            listOf(TxtSupplementedCatalogLine(0, "正文", "第2章 正文")),
            remapTxtSupplementedLinesAfterSelectionDeletion(
                records = records,
                sourceText = text,
                start = 0,
                end = nextLineStart
            )
        )
        assertEquals(
            listOf(
                TxtSupplementedCatalogLine(0, "标题", "第1章 标题"),
                TxtSupplementedCatalogLine(0, "正文", "第2章 正文")
            ),
            remapTxtSupplementedLinesAfterSelectionDeletion(
                records = records,
                sourceText = text,
                start = 1,
                end = nextLineStart
            )
        )
    }

    @Test
    fun replacementOfOneHalfOfCrLfKeepsFollowingChapterLineNumbers() {
        val text = "前言\r\n第1章\r正文"
        val chapter = chapter(
            lineIndex = 1,
            endLineIndex = 3,
            startIndex = text.indexOf("第1章"),
            bodyStartIndex = text.indexOf("正文"),
            endIndex = text.length
        )
        val crIndex = text.indexOf('\r')

        val shifted = shiftTxtChaptersAfterTextChange(
            chapters = listOf(chapter),
            sourceText = text,
            sourceStart = crIndex,
            sourceEnd = crIndex + 1,
            replacementText = ""
        ).single()

        assertEquals(1, shifted.lineIndex)
        assertEquals(3, shifted.endLineIndex)
        assertEquals(chapter.startIndex - 1, shifted.startIndex)

        val replaced = shiftTxtChaptersAfterTextChange(
            chapters = listOf(chapter),
            sourceText = text,
            sourceStart = crIndex,
            sourceEnd = crIndex + 1,
            replacementText = "x"
        ).single()
        val lfIndex = text.indexOf('\n')
        val deletedLf = shiftTxtChaptersAfterTextChange(
            chapters = listOf(chapter),
            sourceText = text,
            sourceStart = lfIndex,
            sourceEnd = lfIndex + 1,
            replacementText = ""
        ).single()

        assertEquals(chapter.lineIndex, replaced.lineIndex)
        assertEquals(chapter.lineIndex, deletedLf.lineIndex)
    }

    @Test
    fun txtPreviewIndexAfterChapterBlocksDeletionKeepsDeletedPreviewAtNextSurvivingSlot() {
        assertEquals(
            1,
            txtPreviewIndexAfterChapterBlocksDeletion(
                previousPreviewIndex = 2,
                deletedIndices = setOf(0, 2),
                remainingChapterCount = 2
            )
        )
    }

    private fun detect(text: String): List<TxtChapter> {
        return ChapterDetector.detectTxtChapters(
            text = text,
            customPatterns = listOf("""^第\s*(\d+)\s*章.*$""")
        )
    }

    private fun chapter(
        lineIndex: Int,
        endLineIndex: Int,
        startIndex: Int,
        bodyStartIndex: Int,
        endIndex: Int
    ): TxtChapter {
        return TxtChapter(
            index = 1,
            lineIndex = lineIndex,
            endLineIndex = endLineIndex,
            title = "章节",
            wordCount = 0,
            startIndex = startIndex,
            bodyStartIndex = bodyStartIndex,
            endIndex = endIndex
        )
    }
}
