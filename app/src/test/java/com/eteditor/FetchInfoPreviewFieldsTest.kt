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
    fun compactCatalogPreviewRowsUsesSkippedExtraFetchedTitleAfterEarlierDelete() {
        val rows = listOf(
            row("Chapter0001.xhtml", "第1章 旧一", "新一", position = 0),
            row("Chapter0002.xhtml", "第2章 旧二", "新二", position = 1),
            row("Chapter0003.xhtml", "第3章 旧三", "新三", position = 2),
            row("", "", "新四", position = 3, skipped = true)
        )

        val compacted = compactCatalogPreviewRows(rows, deletes = setOf(0))

        assertEquals(3, compacted.size)
        assertEquals("第1章 新二", compacted[0].fetchedTitle)
        assertEquals(1, compacted[0].chapterPosition)
        assertEquals("第2章 新三", compacted[1].fetchedTitle)
        assertEquals(2, compacted[1].chapterPosition)
        assertEquals("第3章 新四", compacted[2].fetchedTitle)
        assertEquals(3, compacted[2].chapterPosition)
        assertFalse(compacted[2].missingFetch)
        assertTrue(effectiveSkippedCatalogRows(rows, compacted).isEmpty())
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

    @Test
    fun effectiveSkippedCatalogRowsRetainsOverflowWhenNoDelete() {
        val rows = listOf(
            row("Chapter0001.xhtml", "第1章 旧一", "新一", position = 0),
            row("Chapter0002.xhtml", "第2章 旧二", "新二", position = 1),
            row("Chapter0003.xhtml", "第3章 旧三", "新三", position = 2),
            row("", "", "新四", position = 3, skipped = true)
        )

        val compacted = compactCatalogPreviewRows(rows, deletes = emptySet())

        // 没有删除就没有空位，超出的新四仍留在“不写入章节”对话框里。
        val skipped = effectiveSkippedCatalogRows(rows, compacted)
        assertEquals(1, skipped.size)
        assertEquals(3, skipped[0].chapterPosition)
        assertTrue(skipped[0].skipped)
    }

    @Test
    fun fetchCatalogRenameInitialValuePrefersStoredRenameOverVisibleTitle() {
        val rows = listOf(
            row("Chapter0001.xhtml", "第1章 旧一", "新一", position = 0),
            row("Chapter0002.xhtml", "第2章 旧二", "新二", position = 1)
        )
        val compacted = compactCatalogPreviewRows(rows, deletes = setOf(0))

        val initial = fetchCatalogRenameInitialValue(
            position = 1,
            renames = mapOf(1 to "我的标题"),
            visibleRows = compacted,
            displayRows = rows
        )

        // 已有手改值时优先用它，不被压实后的显示标题覆盖。
        assertEquals("我的标题", initial)
    }

    @Test
    fun moveCatalogItemMovesItemToTargetPositionInDefaultOrder() {
        // 默认顺序 [0,1,2]，把第0项移到 position=1 后面
        val rows = listOf(
            row("Chapter0001.xhtml", "第1章 旧一", "新一", position = 0),
            row("Chapter0002.xhtml", "第2章 旧二", "新二", position = 1),
            row("Chapter0003.xhtml", "第3章 旧三", "新三", position = 2)
        )
        val moved = moveCatalogItem(null, currentPosition = 0, targetPosition = 1, movableRows = rows, defaultCount = 3)
        assertEquals(listOf(1, 0, 2), moved)
    }

    @Test
    fun moveCatalogItemMovesItemToTargetPositionInExistingOrder() {
        // 已有顺序 [1,2,0]，把 position=2 移到 position=1 后面
        val rows = listOf(
            row("Chapter0001.xhtml", "第1章 旧二", "新二", position = 1),
            row("Chapter0002.xhtml", "第2章 新三", "新三", position = 2),
            row("Chapter0003.xhtml", "第3章 新一", "新一", position = 0)
        )
        val moved = moveCatalogItem(listOf(1, 2, 0), currentPosition = 2, targetPosition = 1, movableRows = rows, defaultCount = 3)
        assertEquals(listOf(1, 2, 0), moved)
    }

    @Test
    fun moveCatalogItemNoOpWhenTargetIsSelf() {
        val rows = listOf(
            row("Chapter0001.xhtml", "第1章 旧一", "新一", position = 0),
            row("Chapter0002.xhtml", "第2章 旧二", "新二", position = 1)
        )
        // 把第0项移到第0项后面 = 不动
        val moved = moveCatalogItem(null, currentPosition = 0, targetPosition = 0, movableRows = rows, defaultCount = 2)
        assertEquals(listOf(0, 1), moved)
    }

    @Test
    fun moveCatalogItemUsesVisibleRowsWhenDeletedItemsAreHidden() {
        val rows = listOf(
            row("Chapter0001.xhtml", "第1章 旧一", "新二", position = 1),
            row("Chapter0002.xhtml", "第2章 旧二", "新三", position = 2)
        )

        // position=0 已删除且不可见；确认当前第一项仍应不动。
        val unchanged = moveCatalogItem(null, currentPosition = 1, targetPosition = 1, movableRows = rows, defaultCount = 3)
        assertEquals(listOf(0, 1, 2), unchanged)

        // 把当前第一个可见项移到第二个可见项后面，隐藏的已删除项仍留在原处。
        val moved = moveCatalogItem(null, currentPosition = 1, targetPosition = 2, movableRows = rows, defaultCount = 3)
        assertEquals(listOf(0, 2, 1), moved)
    }

    private fun row(
        fileName: String,
        originalTitle: String,
        fetchedName: String,
        position: Int = -1,
        missingFetch: Boolean = false,
        renamedTitle: String? = null,
        skipped: Boolean = false
    ): FetchInfoCatalogPreviewRow {
        val isRenamed = renamedTitle != null
        return FetchInfoCatalogPreviewRow(
            fileName = fileName,
            originalTitle = originalTitle,
            fetchedTitle = if (isRenamed) renamedTitle else fetchedName,
            isVolume = false,
            skipped = skipped,
            missingFetch = missingFetch,
            chapterPosition = position,
            fetchedItem = FetchedCatalogItem(index = position + 1, title = fetchedName),
            renamed = isRenamed
        )
    }
}
