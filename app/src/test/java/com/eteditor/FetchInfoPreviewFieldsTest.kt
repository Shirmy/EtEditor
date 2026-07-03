package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchInfoPreviewFieldsTest {
    @Test
    fun compactCatalogPreviewRowsShiftsRemainingTitlesForwardAfterMiddleDelete() {
        val rows = listOf(
            row("Chapter0001.xhtml", "第1章 旧一", "第1章 新一", position = 0),
            row("Chapter0002.xhtml", "第2章 旧二", "第2章 新二", position = 1),
            row("Chapter0003.xhtml", "第3章 旧三", "第3章 新三", position = 2)
        )

        val compacted = compactCatalogPreviewRows(rows, deletes = setOf(1))

        assertEquals(3, compacted.size)
        assertEquals("第1章 新一", compacted[0].fetchedTitle)
        assertEquals(0, compacted[0].chapterPosition)
        assertEquals("第2章 新三", compacted[1].fetchedTitle)
        assertEquals(2, compacted[1].chapterPosition)
        assertTrue(compacted[2].missingFetch)
        assertFalse(compacted[2].chapterPosition >= 0)
    }

    @Test
    fun compactCatalogPreviewRowsShiftsAllTitlesForwardWhenDeletingFirstItem() {
        val rows = listOf(
            row("Chapter0001.xhtml", "第1章 旧一", "第1章 新一", position = 0),
            row("Chapter0002.xhtml", "第2章 旧二", "第2章 新二", position = 1),
            row("Chapter0003.xhtml", "第3章 旧三", "第3章 新三", position = 2)
        )

        val compacted = compactCatalogPreviewRows(rows, deletes = setOf(0))

        assertEquals(3, compacted.size)
        assertEquals("第1章 新二", compacted[0].fetchedTitle)
        assertEquals(1, compacted[0].chapterPosition)
        assertEquals("第2章 新三", compacted[1].fetchedTitle)
        assertEquals(2, compacted[1].chapterPosition)
        assertTrue(compacted[2].missingFetch)
    }

    @Test
    fun compactCatalogPreviewRowsShiftsRemainingTitlesForwardAfterMultipleDeletes() {
        val rows = listOf(
            row("Chapter0001.xhtml", "第1章 旧一", "第1章 新一", position = 0),
            row("Chapter0002.xhtml", "第2章 旧二", "第2章 新二", position = 1),
            row("Chapter0003.xhtml", "第3章 旧三", "第3章 新三", position = 2)
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
            row("Chapter0001.xhtml", "第1章 旧一", "第1章 新一", position = 0),
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

    private fun row(
        fileName: String,
        originalTitle: String,
        fetchedTitle: String,
        position: Int = -1,
        missingFetch: Boolean = false
    ): FetchInfoCatalogPreviewRow {
        return FetchInfoCatalogPreviewRow(
            fileName = fileName,
            originalTitle = originalTitle,
            fetchedTitle = fetchedTitle,
            isVolume = false,
            missingFetch = missingFetch,
            chapterPosition = position
        )
    }
}
