package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.TxtChapter
import com.eteditor.core.TxtDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtChapterLineUtilsTest {
    @Test
    fun txtPrefaceEndIndexUsesFirstChapterByTextOrder() {
        val document = txtDocument("前言\n第1章\n正文\n第2章\n正文")
        val reversed = document.copy(chapters = document.chapters.asReversed())

        assertEquals(document.text.indexOf("第1章"), txtPrefaceEndIndex(reversed))
        assertEquals("第1章", txtFirstChapterByTextOrder(reversed)?.title)
        assertNull(txtPrefaceEndIndex(txtDocument("第1章\n正文")))
        assertNull(txtPrefaceEndIndex(txtDocument(" \n\n第1章\n正文")))
    }

    @Test
    fun updateTxtChapterTitleTextUpdatesLineAndRejectsInvalidTitles() {
        val document = txtDocument("第1章\n正文\n第2章\n正文")

        val result = updateTxtChapterTitleText(document, chapterIndex = 1, chapterTitle = " 第2章 新标题 ")
        val blank = updateTxtChapterTitleText(document, chapterIndex = 0, chapterTitle = " ")
        val missing = updateTxtChapterTitleText(document, chapterIndex = 9, chapterTitle = "标题")

        assertTrue(result.success)
        assertEquals("第1章\n正文\n第2章 新标题\n正文", result.text)
        assertFalse(blank.success)
        assertEquals("章节标题不能为空", blank.message)
        assertFalse(missing.success)
    }

    @Test
    fun suggestTxtSupplementChapterNumberUsesManualCurrentPreviousAndNextNumbers() {
        val document = txtDocument("第1章\n正文\n插曲\n正文\n第4章\n正文")
        val onlyNext = txtDocument("插曲\n正文\n第3章\n正文")

        assertEquals("12", suggestTxtSupplementChapterNumberForLine(2, "第12章 手动", document.chapters))
        assertEquals("1", suggestTxtSupplementChapterNumberForLine(0, "第1章", document.chapters))
        assertEquals("2", suggestTxtSupplementChapterNumberForLine(2, "插曲", document.chapters))
        assertEquals("2", suggestTxtSupplementChapterNumberForLine(3, "正文", document.chapters))
        assertEquals("5", suggestTxtSupplementChapterNumberForLine(99, "尾声", document.chapters))
        assertEquals("2", suggestTxtSupplementChapterNumberForLine(0, "插曲", onlyNext.chapters))
        assertEquals("1", suggestTxtSupplementChapterNumberForLine(0, "无章节", emptyList()))
    }

    @Test
    fun suggestTxtSupplementChapterNumberUsesChapterIndexWhenCurrentChapterHasNoNumber() {
        val chapters = listOf(
            TxtChapter(
                index = 7,
                lineIndex = 3,
                endLineIndex = 4,
                title = "番外",
                wordCount = 2,
                number = null
            )
        )

        assertEquals("7", suggestTxtSupplementChapterNumberForLine(3, "番外", chapters))
    }

    @Test
    fun manualChapterNumberHelpersNormalizeAndDetectChapterPrefixes() {
        assertEquals("12", normalizeManualChapterNumber(" 第 12 章 "))
        assertEquals("１２", normalizeManualChapterNumber("第１２章"))
        assertEquals("十二", normalizeManualChapterNumber("第十二章"))
        assertEquals("", normalizeManualChapterNumber("第A章"))
        assertTrue(hasManualChapterPrefix("  第十二回 旧事"))
        assertTrue(hasManualChapterPrefix("第 三 節 舊事"))
        assertFalse(hasManualChapterPrefix("正文第十二章"))
        assertEquals("十二", manualChapterNumberInLine("第十二话 标题"))
        assertEquals("１２", manualChapterNumberInLine("第１２話 標題"))
        assertNull(manualChapterNumberInLine("番外 标题"))
    }

    private fun txtDocument(text: String): TxtDocument {
        return TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = detect(text)
        )
    }

    private fun detect(text: String): List<TxtChapter> {
        return ChapterDetector.detectTxtChapters(
            text = text,
            customPatterns = listOf("""^第\s*([0-9一二三四五六七八九十百千万]+)\s*章.*$"""),
            shortThreshold = 0
        )
    }
}
