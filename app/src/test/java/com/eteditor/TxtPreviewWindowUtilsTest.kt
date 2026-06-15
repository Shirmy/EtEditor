package com.eteditor

import com.eteditor.core.TxtChapter
import com.eteditor.core.TxtDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtPreviewWindowUtilsTest {
    @Test
    fun buildTxtFullPreviewAndEditWindowsReturnWholeSmallSource() {
        val source = "a\nb\nc"

        val previewWindow = buildTxtFullPreviewWindow(source, anchorOffset = 99)
        val editSeed = buildTxtFullEditWindowSeed(source, anchorOffset = 3)

        assertEquals(source, previewWindow.text)
        assertEquals(0, previewWindow.startOffset)
        assertEquals(source.length, previewWindow.endOffset)
        assertEquals(0, previewWindow.startLineIndex)
        assertFalse(editSeed.windowed)
        assertEquals(source, editSeed.sourceText)
        assertEquals(3, editSeed.targetOffset)
        assertEquals(1, editSeed.targetLineIndex)
    }

    @Test
    fun buildTxtFullPreviewAndEditWindowsAlignLargeSourcesToLineBoundaries() {
        val source = buildString {
            repeat(70_000) { append("x\n") }
        }

        val previewWindow = buildTxtFullPreviewWindow(source, anchorOffset = 110_000)
        val editSeed = buildTxtFullEditWindowSeed(source, anchorOffset = 110_000)

        assertTrue(txtRequiresFullEditWindow(source.length))
        assertEquals(40_000, previewWindow.startOffset)
        assertEquals(source.length, previewWindow.endOffset)
        assertEquals(20_000, previewWindow.startLineIndex)
        assertEquals(100_000, previewWindow.text.length)
        assertTrue(editSeed.windowed)
        assertEquals(40_000, editSeed.startOffset)
        assertEquals(source.length, editSeed.endOffset)
        assertEquals(70_000, editSeed.targetOffset)
        assertEquals(35_000, editSeed.targetLineIndex)
    }

    @Test
    fun txtFullPreviewAnchorOffsetPrefersSelectionThenCacheThenChapter() {
        val text = "前言\n第1章\n正文\n第2章\n正文"
        val firstStart = text.indexOf("第1章")
        val secondStart = text.indexOf("第2章")
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = listOf(
                chapter(1, 1, "第1章", firstStart, secondStart),
                chapter(2, 3, "第2章", secondStart, text.length)
            )
        )

        assertEquals(
            2,
            txtFullPreviewAnchorOffset(
                document,
                selectedLocation = TextSourceLocation(0, sourceStart = 2, sourceEnd = 4),
                cachedAnchor = TxtFullPreviewAnchor(8, 1),
                previewChapterIndex = 1
            )
        )
        assertEquals(
            8,
            txtFullPreviewAnchorOffset(document, null, TxtFullPreviewAnchor(8, 1), 1)
        )
        assertEquals(
            secondStart,
            txtFullPreviewAnchorOffset(document, null, null, 1)
        )
        assertEquals(0, txtFullPreviewAnchorOffset(document, null, null, -1))
    }

    @Test
    fun txtFullPreviewAnchorOffsetClampsSelectedAndCachedOffsetsToDocumentLength() {
        val text = "第1章\n正文"
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = listOf(chapter(1, 0, "第1章", 0, text.length))
        )

        assertEquals(
            text.length,
            txtFullPreviewAnchorOffset(
                document = document,
                selectedLocation = TextSourceLocation(0, sourceStart = text.length + 99, sourceEnd = text.length + 120),
                cachedAnchor = TxtFullPreviewAnchor(offset = 0, lineIndex = 0),
                previewChapterIndex = 0
            )
        )
        assertEquals(
            0,
            txtFullPreviewAnchorOffset(
                document = document,
                selectedLocation = null,
                cachedAnchor = TxtFullPreviewAnchor(offset = -50, lineIndex = 0),
                previewChapterIndex = 0
            )
        )
    }

    @Test
    fun txtFullPreviewHighlightRangeAndStateClipToCurrentWindow() {
        val window = TxtFullPreviewWindow(
            text = "0123456789",
            startOffset = 100,
            endOffset = 110,
            startLineIndex = 5
        )

        assertEquals(
            0 to 4,
            txtFullPreviewHighlightRange(window, TextSourceLocation(0, sourceStart = 95, sourceEnd = 104))
        )
        assertNull(
            txtFullPreviewHighlightRange(window, TextSourceLocation(0, sourceStart = 90, sourceEnd = 100))
        )

        val scrollState = buildTxtFullPreviewState(
            sourceLength = 120,
            window = window,
            anchor = TxtFullPreviewAnchor(offset = 105, lineIndex = 7),
            highlightRange = null
        )
        val highlightedState = buildTxtFullPreviewState(
            sourceLength = 120,
            window = window,
            anchor = TxtFullPreviewAnchor(offset = 105, lineIndex = 7),
            highlightRange = 0 to 4
        )

        assertEquals("100:110", scrollState.windowKey)
        assertEquals(5, scrollState.scrollTargetOffset)
        assertEquals(2, scrollState.scrollTargetLineIndex)
        assertNull(highlightedState.scrollTargetOffset)
        assertNull(highlightedState.scrollTargetLineIndex)
    }

    @Test
    fun txtFullPreviewHighlightRangeClipsSelectionAtWindowEnd() {
        val window = TxtFullPreviewWindow(
            text = "0123456789",
            startOffset = 100,
            endOffset = 110,
            startLineIndex = 5
        )

        assertEquals(
            8 to 10,
            txtFullPreviewHighlightRange(window, TextSourceLocation(0, sourceStart = 108, sourceEnd = 120))
        )
    }

    @Test
    fun txtPreviewSelectedLineHighlightMapsSelectionToVisibleLine() {
        val text = "前言A\n第1章\n正文第一行\n正文第二行\n第2章\n正文"
        val firstStart = text.indexOf("第1章")
        val secondStart = text.indexOf("第2章")
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = listOf(
                chapter(1, 1, "第1章", firstStart, secondStart),
                chapter(2, 4, "第2章", secondStart, text.length)
            )
        )
        val selectionStart = text.indexOf("第二")

        val highlight = txtPreviewSelectedLineHighlight(
            document = document,
            selectedLocation = TextSourceLocation(0, selectionStart, selectionStart + "第二".length),
            fullPreviewMode = false,
            prefaceEndIndex = firstStart
        )

        assertEquals(TxtPreviewLineHighlight(lineIndex = 3, start = 2, end = 4), highlight)
        assertEquals(
            TxtPreviewLineHighlight(lineIndex = 0, start = 0, end = 2),
            txtPreviewSelectedLineHighlight(
                document = document,
                selectedLocation = TextSourceLocation(0, 0, 2),
                fullPreviewMode = false,
                prefaceEndIndex = firstStart
            )
        )
    }

    @Test
    fun txtPreviewSelectedLineHighlightUsesWholeTextWhenNoChaptersExist() {
        val document = TxtDocument(
            originalName = "book.txt",
            text = "第一行\n第二行\n第三行",
            encoding = "UTF-8",
            chapters = emptyList()
        )

        assertEquals(
            TxtPreviewLineHighlight(lineIndex = 1, start = 0, end = 2),
            txtPreviewSelectedLineHighlight(
                document = document,
                selectedLocation = TextSourceLocation(0, sourceStart = 4, sourceEnd = 6),
                fullPreviewMode = false,
                prefaceEndIndex = null
            )
        )
    }

    @Test
    fun txtPreviewSelectedLineHighlightFallsBackToSourceOffsetWhenChapterIndexIsStale() {
        val text = "第1章\n正文1\n第2章\n正文2\n正文3"
        val secondStart = text.indexOf("第2章")
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = listOf(
                chapter(1, 0, "第1章", 0, secondStart),
                chapter(2, 2, "第2章", secondStart, text.length)
            )
        )
        val selectionStart = text.indexOf("正文3")

        val highlight = txtPreviewSelectedLineHighlight(
            document = document,
            selectedLocation = TextSourceLocation(
                chapterIndex = 0,
                sourceStart = selectionStart,
                sourceEnd = selectionStart + "正文".length
            ),
            fullPreviewMode = false,
            prefaceEndIndex = null
        )

        assertEquals(TxtPreviewLineHighlight(lineIndex = 4, start = 0, end = 2), highlight)
    }

    @Test
    fun txtLineHighlightForSourceClipsSelectionToAnchoredLine() {
        val source = "alpha\nbeta\ngamma"

        assertEquals(
            TxtPreviewLineHighlight(lineIndex = 11, start = 1, end = 4),
            txtLineHighlightForSource(
                source = source,
                sourceStart = source.indexOf("eta"),
                sourceEnd = source.indexOf("gamma") + 2,
                baseLineIndex = 10
            )
        )
        assertEquals(
            TxtPreviewLineHighlight(lineIndex = 10, start = 5, end = 5),
            txtLineHighlightForSource(
                source = source,
                sourceStart = source.indexOf('\n'),
                sourceEnd = source.indexOf('\n') + 1,
                baseLineIndex = 10
            )
        )
    }

    @Test
    fun txtLineHighlightForSourceAnchorsCrSelectionToPreviousLine() {
        val source = "alpha\rbeta"

        assertEquals(
            TxtPreviewLineHighlight(lineIndex = 3, start = 5, end = 6),
            txtLineHighlightForSource(
                source = source,
                sourceStart = source.indexOf('\r'),
                sourceEnd = source.indexOf('\r') + 1,
                baseLineIndex = 3
            )
        )
    }

    @Test
    fun mappedTxtChapterPreviewOffsetToSourceOffsetAccountsForMappedTitleLength() {
        assertEquals(
            10,
            mappedTxtChapterPreviewOffsetToSourceOffset(
                source = "第100章 旧标题\n正文",
                mappedTitle = "第1章 旧标题",
                mappedOffset = 8
            )
        )
        assertEquals(
            2,
            mappedTxtChapterPreviewOffsetToSourceOffset(
                source = "第1章 旧标题\n正文",
                mappedTitle = "第1章 旧标题",
                mappedOffset = 2
            )
        )
    }

    @Test
    fun mappedTxtChapterPreviewOffsetToSourceOffsetHandlesLongerAndBlankMappedTitles() {
        val source = "Old\nBody"

        assertEquals(
            3,
            mappedTxtChapterPreviewOffsetToSourceOffset(
                source = source,
                mappedTitle = "Longer Old",
                mappedOffset = 6
            )
        )
        assertEquals(
            5,
            mappedTxtChapterPreviewOffsetToSourceOffset(
                source = source,
                mappedTitle = "Longer Old",
                mappedOffset = 12
            )
        )
        assertEquals(
            source.length,
            mappedTxtChapterPreviewOffsetToSourceOffset(
                source = source,
                mappedTitle = "",
                mappedOffset = 99
            )
        )
    }

    private fun chapter(
        index: Int,
        lineIndex: Int,
        title: String,
        startIndex: Int,
        endIndex: Int
    ): TxtChapter {
        return TxtChapter(
            index = index,
            lineIndex = lineIndex,
            endLineIndex = lineIndex + 1,
            title = title,
            wordCount = 0,
            startIndex = startIndex,
            bodyStartIndex = startIndex,
            endIndex = endIndex
        )
    }
}
