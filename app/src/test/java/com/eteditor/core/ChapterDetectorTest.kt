package com.eteditor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterDetectorTest {
    @Test
    fun parseNumberHandlesArabicFullWidthAndChineseUnits() {
        assertEquals(12, ChapterDetector.parseNumber("１２"))
        assertEquals(21, ChapterDetector.parseNumber("二十一"))
        assertEquals(10003, ChapterDetector.parseNumber("一万零三"))
        assertNull(ChapterDetector.parseNumber("序章"))
    }

    @Test
    fun detectTxtChaptersFlagsDuplicateShortAndMissingNumbers() {
        val chapters = ChapterDetector.detectTxtChapters(
            text = """
                第1章 开始
                短
                第1章 开始
                内容
                第3章 跳过
                尾声
            """.trimIndent(),
            shortThreshold = 3,
            customPatterns = listOf("""^第\s*(\d+|[一二三四五六七八九十]+)\s*章.*$""")
        )

        assertEquals(listOf("第1章 开始", "第1章 开始", "第3章 跳过"), chapters.map { it.title })
        assertEquals(listOf(1, 1, 3), chapters.map { it.number })
        assertTrue(chapters[0].status.contains("短章"))
        assertTrue(chapters[1].status.contains("重名"))
        assertTrue(chapters[1].status.contains("重复序号"))
        assertTrue(chapters[2].status.contains("疑似缺章"))
    }

    @Test
    fun detectTxtChaptersFlagsFirstNumberedChapterStartingAfterOneAsMissing() {
        val chapters = ChapterDetector.detectTxtChapters(
            text = """
                前言
                引子
                第25章 开始
                正文
                第26章 继续
                正文
            """.trimIndent(),
            customPatterns = listOf("""^(前言|第\s*(\d+)\s*章.*)$""")
        )

        assertEquals(listOf("前言", "第25章 开始", "第26章 继续"), chapters.map { it.title })
        assertTrue(chapters[1].status.contains("疑似缺章"))
    }

    @Test
    fun detectTxtChaptersRespectsHiddenAndForcedLines() {
        val chapters = ChapterDetector.detectTxtChapters(
            text = """
                手动章节
                正文
                第1章 自动章节
                正文
            """.trimIndent(),
            customPatterns = listOf("""^第\s*(\d+)\s*章.*$"""),
            hiddenLineIndices = setOf(2),
            forcedLineIndices = setOf(0)
        )

        assertEquals(1, chapters.size)
        assertEquals("手动章节", chapters.single().title)
        assertEquals(0, chapters.single().lineIndex)
    }

    @Test
    fun detectTxtChaptersNormalizesZeroWidthCharactersBeforeMatching() {
        val chapters = ChapterDetector.detectTxtChapters(
            text = "第\u200B1章 开始\n正文",
            customPatterns = listOf("""^第\s*(\d+)\s*章.*$""")
        )

        assertEquals(1, chapters.size)
        assertEquals(1, chapters.single().number)
        assertEquals(0, chapters.single().lineIndex)
    }

    @Test
    fun detectTxtChaptersAllowsForcedLongLinesThatExceedAutomaticLimit() {
        val longTitle = "这是一条很长的手动章节标题".repeat(8)

        val chapters = ChapterDetector.detectTxtChapters(
            text = "$longTitle\n正文",
            forcedLineIndices = setOf(0)
        )

        assertEquals(1, chapters.size)
        assertEquals(longTitle, chapters.single().title)
        assertEquals(0, chapters.single().lineIndex)
    }

    @Test
    fun formatTxtLayoutRemovesBlankLinesAndIndentsBodyLines() {
        val result = ChapterDetector.formatTxtLayout(
            text = "第1章 开始\n\n正文一\n  正文二  \n\n第2章 继续",
            customPatterns = listOf("""^第\s*(\d+)\s*章.*$""")
        )

        assertEquals(
            "第1章 开始\n　　正文一\n　　正文二\n\n\n第2章 继续",
            result.text
        )
        assertEquals(2, result.removedBlankCount)
        assertEquals(2, result.contentLineCount)
        assertEquals(2, result.chapterLineCount)
    }

    @Test
    fun htmlTitleExtractionAndUpdateHandleLineBreaksAndEscaping() {
        val html = """
            <html>
                <head><title>旧标题</title></head>
                <body><h1>第一行<br/>第二行</h1><p>正文</p></body>
            </html>
        """.trimIndent()

        assertEquals("第一行<br/>第二行", ChapterDetector.extractTitleFromHtmlWithBreaks(html, "fallback.xhtml"))

        val updated = ChapterDetector.updateHtmlTitleWithLineBreaks(html, "新章<br/>副标题 & 备注")

        assertTrue(updated.contains("<title>新章 副标题 &amp; 备注</title>"))
        assertTrue(updated.contains("<h1>新章<br/>副标题 &amp; 备注</h1>"))
    }
}
