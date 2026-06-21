package com.eteditor

import com.eteditor.core.TxtChapter
import com.eteditor.core.TxtDocument
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtSaveUtilsTest {
    @Test
    fun mapTxtChapterTitlesForSaveUpdatesOnlyChapterTitleLines() {
        val text = "第9章 旧标题\r\n正文一\n第10章 旧标题二\n正文二"
        val firstStart = text.indexOf("第9章")
        val secondStart = text.indexOf("第10章")

        val result = mapTxtChapterTitlesForSave(
            text = text,
            chapters = listOf(
                chapter(index = 1, lineIndex = 0, title = "第9章 新标题", startIndex = firstStart),
                chapter(index = 2, lineIndex = 2, title = "第10章 新标题二", startIndex = secondStart)
            ),
            renumberTitles = false
        )

        assertEquals("第9章 新标题\r\n正文一\n第10章 新标题二\n正文二", result.text)
        assertEquals(2, result.changedCount)
    }

    @Test
    fun mapTxtChapterTitlesForSaveRenumbersDetectedChapterTitles() {
        val text = "第12章 开始\n正文一\n第20章 继续\n正文二"
        val firstStart = text.indexOf("第12章")
        val secondStart = text.indexOf("第20章")

        val result = mapTxtChapterTitlesForSave(
            text = text,
            chapters = listOf(
                chapter(index = 1, lineIndex = 0, title = "第12章 开始", startIndex = firstStart),
                chapter(index = 2, lineIndex = 2, title = "第20章 继续", startIndex = secondStart)
            ),
            renumberTitles = true
        )

        assertEquals("第1章 开始\n正文一\n第2章 继续\n正文二", result.text)
        assertEquals(2, result.changedCount)
    }

    @Test
    fun mapTxtChapterTitlesForSaveRenumbersChineseTitlesAndStripsSeparators() {
        val text = "第十二章：开始\n正文一\n第二十章、继续\n正文二"

        val result = mapTxtChapterTitlesForSave(
            text = text,
            chapters = listOf(
                chapter(index = 1, lineIndex = 0, title = "第十二章：开始", startIndex = text.indexOf("第十二章")),
                chapter(index = 2, lineIndex = 2, title = "第二十章、继续", startIndex = text.indexOf("第二十章"))
            ),
            renumberTitles = true
        )

        assertEquals("第1章 开始\n正文一\n第2章 继续\n正文二", result.text)
        assertEquals(2, result.changedCount)
    }

    @Test
    fun mapTxtChapterTitlesForSaveRenumbersFromZeroAndSkipsNonNumberedTitles() {
        val text = "第0章 序\n正文一\n番外 特典\n正文二\n第9章 正文\n正文三"

        val result = mapTxtChapterTitlesForSave(
            text = text,
            chapters = listOf(
                chapter(index = 1, lineIndex = 0, title = "第0章 序", startIndex = text.indexOf("第0章")),
                chapter(index = 2, lineIndex = 2, title = "番外 特典", startIndex = text.indexOf("番外")),
                chapter(index = 3, lineIndex = 4, title = "第9章 正文", startIndex = text.indexOf("第9章"))
            ),
            renumberTitles = true,
            numberStartAtOne = false
        )

        assertEquals("第0章 序\n正文一\n番外 特典\n正文二\n第1章 正文\n正文三", result.text)
        assertEquals(1, result.changedCount)
    }

    @Test
    fun mapTxtChapterTitlesForSaveLeavesAllNonNumberedTitlesUntouchedWhenRenumbering() {
        val text = "序章\n正文一\n番外\n正文二"

        val result = mapTxtChapterTitlesForSave(
            text = text,
            chapters = listOf(
                chapter(index = 1, lineIndex = 0, title = "序章", startIndex = text.indexOf("序章")),
                chapter(index = 2, lineIndex = 2, title = "番外", startIndex = text.indexOf("番外"))
            ),
            renumberTitles = true
        )

        assertEquals(text, result.text)
        assertEquals(0, result.changedCount)
    }

    @Test
    fun mapTxtChapterTitlesForSaveIgnoresInvalidChapterOffsets() {
        val text = "正文一\n正文二"

        val result = mapTxtChapterTitlesForSave(
            text = text,
            chapters = listOf(
                chapter(index = 1, lineIndex = 0, title = "第1章 标题", startIndex = text.length + 10)
            ),
            renumberTitles = false
        )

        assertEquals(text, result.text)
        assertEquals(0, result.changedCount)
    }

    @Test
    fun mapTxtChapterTitlesForSaveHandlesCrOnlyLineEndings() {
        val text = "第1章 旧\r正文"

        val result = mapTxtChapterTitlesForSave(
            text = text,
            chapters = listOf(chapter(index = 1, lineIndex = 0, title = "第1章 新", startIndex = 0)),
            renumberTitles = false
        )

        assertEquals("第1章 新\r正文", result.text)
        assertEquals(1, result.changedCount)
    }

    @Test
    fun prepareTxtDocumentSaveAppliesTitleMappingAndEncoding() {
        val text = "第9章 旧标题\n正文"
        val document = TxtDocument(
            originalName = "book",
            text = text,
            encoding = "UTF-8",
            chapters = listOf(
                chapter(
                    index = 1,
                    lineIndex = 0,
                    title = "第9章 新标题",
                    startIndex = text.indexOf("第9章")
                )
            )
        )

        val result = prepareTxtDocumentSave(document, renumberTitles = false)

        assertEquals("第9章 新标题\r\n正文", result.mapping.text)
        assertEquals(1, result.mapping.changedCount)
        assertEquals("UTF-8", result.encodingLabel)
        assertEquals(
            "第9章 新标题\r\n正文",
            String(result.bytes, StandardCharsets.UTF_8)
        )
        assertTrue(result.keepMappedCatalog)
    }

    @Test
    fun prepareTxtDocumentSaveKeepsMappedCatalogFalseWhenTitlesDoNotChange() {
        val text = "第1章 标题\n正文"
        val document = TxtDocument(
            originalName = "book",
            text = text,
            encoding = "UTF-8",
            chapters = listOf(
                chapter(
                    index = 1,
                    lineIndex = 0,
                    title = "第1章 标题",
                    startIndex = 0
                )
            )
        )

        val result = prepareTxtDocumentSave(document, renumberTitles = false)

        assertEquals("第1章 标题\r\n正文", result.mapping.text)
        assertEquals(0, result.mapping.changedCount)
        assertEquals("第1章 标题\r\n正文", String(result.bytes, StandardCharsets.UTF_8))
        assertFalse(result.keepMappedCatalog)
    }

    @Test
    fun prepareTxtDocumentSaveNormalizesMixedLineEndingsToCrLf() {
        val text = "A\nB\rC\r\nD"
        val document = TxtDocument(
            originalName = "book",
            text = text,
            encoding = "UTF-8",
            chapters = emptyList()
        )

        val result = prepareTxtDocumentSave(document, renumberTitles = false)

        assertEquals("A\r\nB\r\nC\r\nD", result.mapping.text)
        assertEquals("A\r\nB\r\nC\r\nD", String(result.bytes, StandardCharsets.UTF_8))
        assertEquals(0, result.mapping.changedCount)
        assertFalse(result.keepMappedCatalog)
    }

    @Test
    fun rebuildTxtChaptersFromSavedLinesRefreshesOffsetsCountsAndStatuses() {
        val text = "第1章 A\n短\n第3章 C\n正文正文\n第3章 C\n正文"
        val config = TxtChapterDetectionConfig(
            rulesText = "",
            shortThreshold = 2,
            longThreshold = 3,
            hiddenLineIndices = emptySet()
        )
        val chapters = listOf(
            chapter(index = 9, lineIndex = 0, title = "旧1", startIndex = 99),
            chapter(index = 9, lineIndex = 2, title = "旧2", startIndex = 99),
            chapter(index = 9, lineIndex = 4, title = "旧3", startIndex = 99)
        )

        val rebuilt = rebuildTxtChaptersFromSavedLines(text, chapters, config)

        assertEquals(listOf(1, 2, 3), rebuilt.map { it.index })
        assertEquals(listOf("第1章 A", "第3章 C", "第3章 C"), rebuilt.map { it.title })
        assertEquals(listOf(0, 2, 4), rebuilt.map { it.lineIndex })
        assertEquals(listOf(1, 4, 2), rebuilt.map { it.wordCount })
        assertEquals(listOf(listOf("短章"), listOf("超长章", "疑似缺章"), listOf("重名", "重复序号")), rebuilt.map { it.status })
    }

    @Test
    fun rebuildTxtChaptersFromSavedLinesFlagsFirstNumberedChapterStartingAfterOneAsMissing() {
        val text = "前言\n正文\n第25章 A\n正文\n第26章 B\n正文"
        val config = TxtChapterDetectionConfig(
            rulesText = "",
            shortThreshold = 1,
            longThreshold = 0,
            hiddenLineIndices = emptySet()
        )
        val chapters = listOf(
            chapter(index = 1, lineIndex = 0, title = "前言", startIndex = 0),
            chapter(index = 2, lineIndex = 2, title = "第25章 A", startIndex = text.indexOf("第25章")),
            chapter(index = 3, lineIndex = 4, title = "第26章 B", startIndex = text.indexOf("第26章"))
        )

        val rebuilt = rebuildTxtChaptersFromSavedLines(text, chapters, config)

        assertEquals(listOf(emptyList(), listOf("疑似缺章"), emptyList()), rebuilt.map { it.status })
    }

    @Test
    fun rebuildTxtChaptersFromSavedLinesSkipsChaptersWhoseLineNoLongerExists() {
        val text = "第1章 A\n正文"
        val config = TxtChapterDetectionConfig(
            rulesText = "",
            shortThreshold = 1,
            longThreshold = 0,
            hiddenLineIndices = emptySet()
        )

        val rebuilt = rebuildTxtChaptersFromSavedLines(
            text = text,
            chapters = listOf(
                chapter(index = 1, lineIndex = 0, title = "旧1", startIndex = 99),
                chapter(index = 2, lineIndex = 8, title = "旧2", startIndex = 99)
            ),
            config = config
        )

        assertEquals(1, rebuilt.size)
        assertEquals("第1章 A", rebuilt.single().title)
        assertEquals(0, rebuilt.single().startIndex)
        assertEquals(text.length, rebuilt.single().endIndex)
    }

    @Test
    fun txtLinePositionsAndTextHandleEmptyCrLfAndInvalidOffsets() {
        assertEquals(listOf(TxtLinePosition(0, 0)), txtLinePositions(""))
        assertEquals(
            listOf(TxtLinePosition(0, 3), TxtLinePosition(3, 5), TxtLinePosition(5, 6)),
            txtLinePositions("一\r\n二\r三")
        )
        assertEquals("二", txtLineText("一\r\n二\r三", 3))
        assertEquals("", txtLineText("正文", -1))
        assertEquals("", txtLineText("正文", 99))
    }

    private fun chapter(
        index: Int,
        lineIndex: Int,
        title: String,
        startIndex: Int
    ): TxtChapter {
        return TxtChapter(
            index = index,
            lineIndex = lineIndex,
            endLineIndex = lineIndex + 1,
            title = title,
            wordCount = 0,
            startIndex = startIndex
        )
    }
}
