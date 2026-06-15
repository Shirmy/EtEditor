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
            sourceStart = sourceStart,
            sourceEnd = sourceEnd,
            originalText = "正文1",
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
            sourceStart = 0,
            sourceEnd = originalText.length,
            originalText = originalText,
            replacementText = replacementText
        )

        assertEquals(listOf(2, 4), shifted.map { it.lineIndex })
        assertEquals(chapters.map { it.startIndex + textDelta }, shifted.map { it.startIndex })
        assertEquals(chapters.map { it.bodyStartIndex + textDelta }, shifted.map { it.bodyStartIndex })
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
}
