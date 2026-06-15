package com.eteditor

import com.eteditor.core.ChapterInfo
import com.eteditor.core.TxtChapter
import com.eteditor.core.TxtDocument
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayAndToolIdUtilsTest {
    @Test
    fun assignUniqueEditorToolIdsKeepsUniqueIdsAndAllocatesConflicts() {
        val existing = listOf(tool("tool-1", "Existing"))
        val imported = listOf(
            tool("tool-1", "Conflict"),
            tool("", "Blank"),
            tool("custom", "Custom"),
            tool("drop", "Drop")
        )

        val assigned = assignUniqueEditorToolIds(
            importedTools = imported,
            existingTools = existing,
            idPrefix = "tool-",
            normalize = { tool -> tool.takeUnless { it.name == "Drop" } }
        )

        assertEquals(listOf("tool-2", "tool-3", "custom"), assigned.map { it.id })
        assertEquals(4, nextEditorToolNumberForPrefix(existing + assigned, "tool-"))
    }

    @Test
    fun nextEditorToolNumberForPrefixIgnoresOtherPrefixesAndNonNumericSuffixes() {
        val tools = listOf(
            tool("tool-9", "Nine"),
            tool("tool-x", "Text"),
            tool("other-20", "Other")
        )

        assertEquals(10, nextEditorToolNumberForPrefix(tools, "tool-"))
        assertEquals(21, nextEditorToolNumberForPrefix(tools, "other-"))
        assertEquals(1, nextEditorToolNumberForPrefix(tools, "missing-"))
    }

    @Test
    fun assignUniqueEditorToolIdsAllocatesConflictsWithinImportedToolsInOrder() {
        val assigned = assignUniqueEditorToolIds(
            importedTools = listOf(
                tool("custom", "First"),
                tool("custom", "Second"),
                tool("", "Blank")
            ),
            existingTools = listOf(tool("tool-2", "Existing")),
            idPrefix = "tool-"
        )

        assertEquals(listOf("custom", "tool-3", "tool-4"), assigned.map { it.id })
        assertEquals(listOf("First", "Second", "Blank"), assigned.map { it.name })
    }

    @Test
    fun nextEditorToolNumberForPrefixTreatsEmptyPrefixAsNumericWholeId() {
        val tools = listOf(
            tool("7", "Numeric"),
            tool("tool-9", "Prefixed"),
            tool("abc", "Text")
        )

        assertEquals(8, nextEditorToolNumberForPrefix(tools, ""))
    }

    @Test
    fun compactByteSizeRoundsToReadableUnits() {
        assertEquals("0B", compactByteSize(-1))
        assertEquals("512B", compactByteSize(512))
        assertEquals("1023B", compactByteSize(1023))
        assertEquals("1KB", compactByteSize(1024))
        assertEquals("1.5KB", compactByteSize(1536))
        assertEquals("1024KB", compactByteSize(1024L * 1024L - 1))
        assertEquals("1MB", compactByteSize(1024L * 1024L))
        assertEquals("1.5GB", compactByteSize(1024L * 1024L * 1024L + 512L * 1024L * 1024L))
    }

    @Test
    fun compactCountLabelRoundsToThousandsAndTenThousands() {
        assertEquals("-1", compactCountLabel(-1))
        assertEquals("999", compactCountLabel(999))
        assertEquals("1k", compactCountLabel(1_000))
        assertEquals("1.5k", compactCountLabel(1_499))
        assertEquals("10k", compactCountLabel(9_999))
        assertEquals("1w", compactCountLabel(10_000))
        assertEquals("1.3w", compactCountLabel(12_600))
    }

    @Test
    fun txtSubtitleIncludesEncodingLineEndingSizeChapterAndIssueCounts() {
        val document = TxtDocument(
            originalName = "book.txt",
            text = "第1章\r\n正文",
            encoding = "UTF-8",
            chapters = listOf(
                TxtChapter(
                    index = 1,
                    lineIndex = 0,
                    endLineIndex = 2,
                    title = "第1章",
                    wordCount = 2,
                    status = listOf("短章", "疑似缺章")
                )
            )
        )

        assertEquals("UTF-8 | CRLF | 5 | 15B | 章节 1 | 问题 2", txtSubtitle(document))
    }

    @Test
    fun txtSubtitleOmitsIssueCountWhenChaptersAreClean() {
        val document = TxtDocument(
            originalName = "book.txt",
            text = "正文\n第二行",
            encoding = "UTF-8",
            chapters = emptyList()
        )

        assertEquals("UTF-8 | LF | 5 | 16B | 章节 0", txtSubtitle(document))
    }

    @Test
    fun txtSubtitleUsesFallbackBytesWhenPreferredEncodingCannotRepresentText() {
        val document = TxtDocument(
            originalName = "book.txt",
            text = "汉A",
            encoding = "US-ASCII",
            chapters = listOf(
                TxtChapter(
                    index = 1,
                    lineIndex = 0,
                    endLineIndex = 1,
                    title = "汉A",
                    wordCount = 2
                )
            )
        )

        assertEquals("US-ASCII | 无 | 2 | 4B | 章节 1", txtSubtitle(document))
    }

    @Test
    fun txtChapterIssueSummarySeparatesLengthAndOtherStatuses() {
        val short = chapter("短章", listOf("短章"))
        val missing = chapter("缺章", listOf("疑似缺章"))
        val both = chapter("混合", listOf("超长章", "重复序号"))
        val clean = chapter("正常", emptyList())

        val summary = txtChapterIssueSummary(listOf(short, missing, both, clean))

        assertEquals(listOf(short, both), summary.lengthChapters)
        assertEquals(listOf(missing, both), summary.otherChapters)
        assertEquals(summary.lengthChapters, summary.chaptersFor(TxtChapterIssueType.Length))
        assertEquals(summary.otherChapters, summary.chaptersFor(TxtChapterIssueType.Other))
    }

    private fun tool(id: String, name: String): EditorTool {
        return EditorTool(id = id, name = name, toolId = "text_replace")
    }

    private fun chapter(title: String, status: List<String>): ChapterInfo {
        return ChapterInfo(
            index = 1,
            title = title,
            wordCount = 0,
            source = "",
            fileName = "",
            status = status
        )
    }
}
