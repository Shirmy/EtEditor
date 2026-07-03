package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchInfoPreviewFieldsTest {
    @Test
    fun compactCatalogPreviewRowsShiftsRemainingTitlesForwardAfterMiddleDelete() {
        val rows = listOf(
            row("Chapter0001.xhtml", "第1章 旧一", "新一", position = 0),
            row("Chapter0002.xhtml", "第2章 旧二", "新二", position = 1),
            row("Chapter0003.xhtml", "第3章 旧三", "新三", position = 2)
        )

        val compacted = compactCatalogPreviewRows(rows, deletes = setOf(1))

        assertEquals(3, compacted.size)
        assertEquals("第1章 新一", compacted[0].fetchedTitle)
        assertEquals(0, compacted[0].chapterPosition)
        // 新三顶到第2章，序号跟着左列那一章重算为“第2章”。
        assertEquals("第2章 新三", compacted[1].fetchedTitle)
        assertEquals(2, compacted[1].chapterPosition)
        assertTrue(compacted[2].missingFetch)
        assertFalse(compacted[2].chapterPosition >= 0)
    }

    @Test
    fun compactCatalogPreviewRowsShiftsAllTitlesForwardWhenDeletingFirstItem() {
        val rows = listOf(
            row("Chapter0001.xhtml", "第1章 旧一", "新一", position = 0),
            row("Chapter0002.xhtml", "第2章 旧二", "新二", position = 1),
            row("Chapter0003.xhtml", "第3章 旧三", "新三", position = 2)
        )

        val compacted = compactCatalogPreviewRows(rows, deletes = setOf(0))

        assertEquals(3, compacted.size)
        // 新二顶到第1章，序号跟着左列那一章重算为“第1章”。
        assertEquals("第1章 新二", compacted[0].fetchedTitle)
        assertEquals(1, compacted[0].chapterPosition)
        assertEquals("第2章 新三", compacted[1].fetchedTitle)
        assertEquals(2, compacted[1].chapterPosition)
        assertTrue(compacted[2].missingFetch)
    }

    @Test
    fun compactCatalogPreviewRowsShiftsRemainingTitlesForwardAfterMultipleDeletes() {
        val rows = listOf(
            row("Chapter0001.xhtml", "第1章 旧一", "新一", position = 0),
            row("Chapter0002.xhtml", "第2章 旧二", "新二", position = 1),
            row("Chapter0003.xhtml", "第3章 旧三", "新三", position = 2)
        )

        val compacted = compactCatalogPreviewRows(rows, deletes = setOf(0, 2))

        assertEquals(3, compacted.size)
        assertEquals("第1章 新二", compacted[0].fetchedTitle)
        assertEquals(1, compacted[0].chapterPosition)
        assertTrue(compacted[1].missingFetch)
        assertTrue(compacted[2].missingFetch)
    }

    @Test
    fun compactCatalogPreviewRowsKeepsMissingFetchRowsWhenNoSurvivingTitles() {
        val rows = listOf(
            row("Chapter0001.xhtml", "第1章 旧一", "新一", position = 0),
            row("Chapter0002.xhtml", "第2章 旧二", "", missingFetch = true),
            row("Chapter0003.xhtml", "第3章 旧三", "", missingFetch = true)
        )

        val compacted = compactCatalogPreviewRows(rows, deletes = setOf(0))

        // 唯一的抓取项被删，三行 epub 章节都没有抓取内容覆盖，全部保持原样。
        assertEquals(3, compacted.size)
        assertEquals("第1章 旧一", compacted[0].originalTitle)
        assertEquals("第2章 旧二", compacted[1].originalTitle)
        assertEquals("第3章 旧三", compacted[2].originalTitle)
        assertTrue(compacted.all { it.missingFetch })
    }

    @Test
    fun compactCatalogPreviewRowsKeepsRenamedTitleAsIsWhenShifting() {
        val rows = listOf(
            row("Chapter0001.xhtml", "第1章 旧一", "新一", position = 0),
            row("Chapter0002.xhtml", "第2章 旧二", "新二", position = 1, renamedTitle = "手改标题")
        )

        val compacted = compactCatalogPreviewRows(rows, deletes = setOf(0))

        assertEquals(2, compacted.size)
        // 手改项顶到第1章，但手改值保持用户输入原样，不重算序号。
        assertEquals("手改标题", compacted[0].fetchedTitle)
        assertEquals(1, compacted[0].chapterPosition)
        assertTrue(compacted[1].missingFetch)
    }

    @Test
    fun compactCatalogPreviewRowsRepacksTitleWithoutPrefixWhenLeftChapterHasNoPrefix() {
        val rows = listOf(
            row("Chapter0001.xhtml", "楔子", "新一", position = 0),
            row("Chapter0002.xhtml", "第2章 旧二", "新二", position = 1)
        )

        val compacted = compactCatalogPreviewRows(rows, deletes = setOf(0))

        // 新二顶到“楔子”那一章，左列没有章号前缀，右列只显示抓取名字。
        assertEquals("新二", compacted[0].fetchedTitle)
        assertEquals(1, compacted[0].chapterPosition)
        assertTrue(compacted[1].missingFetch)
    }

    @Test
    fun fetchCatalogRenameInitialValueUsesCompactedVisibleTitleAfterDelete() {
        val rows = listOf(
            row("Chapter0001.xhtml", "第1章 旧一", "新一", position = 0),
            row("Chapter0002.xhtml", "第2章 旧二", "新二", position = 1)
        )
        val compacted = compactCatalogPreviewRows(rows, deletes = setOf(0))

        val initial = fetchCatalogRenameInitialValue(
            position = 1,
            renames = emptyMap(),
            visibleRows = compacted,
            displayRows = rows
        )

        assertEquals("第1章 新二", initial)
    }

    private fun row(
        fileName: String,
        originalTitle: String,
        fetchedName: String,
        position: Int = -1,
        missingFetch: Boolean = false,
        renamedTitle: String? = null
    ): FetchInfoCatalogPreviewRow {
        val isRenamed = renamedTitle != null
        return FetchInfoCatalogPreviewRow(
            fileName = fileName,
            originalTitle = originalTitle,
            fetchedTitle = if (isRenamed) renamedTitle else fetchedName,
            isVolume = false,
            missingFetch = missingFetch,
            chapterPosition = position,
            fetchedItem = FetchedCatalogItem(index = position + 1, title = fetchedName),
            renamed = isRenamed
        )
    }
}
