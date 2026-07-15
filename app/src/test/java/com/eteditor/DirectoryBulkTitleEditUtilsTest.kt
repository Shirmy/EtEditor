package com.eteditor

import com.eteditor.core.ChapterInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryBulkTitleEditUtilsTest {
    private val chapters = listOf(
        info(1, "封面", isVolume = false),
        info(2, "第一卷", isVolume = true),
        info(3, "第1章 开始", isVolume = false),
        info(4, "第2章 中间", isVolume = false),
        info(5, "第二卷", isVolume = true),
        info(6, "第3章 后续", isVolume = false),
        info(7, "第三卷", isVolume = true)
    )

    @Test
    fun classifySelectionKinds() {
        assertEquals(BulkTitleEditSelectionKind.Empty, classifyBulkTitleEditSelection(chapters, emptySet()))
        assertEquals(BulkTitleEditSelectionKind.Chapters, classifyBulkTitleEditSelection(chapters, setOf(2, 3)))
        assertEquals(BulkTitleEditSelectionKind.SingleVolume, classifyBulkTitleEditSelection(chapters, setOf(1)))
        assertEquals(BulkTitleEditSelectionKind.MultiVolumes, classifyBulkTitleEditSelection(chapters, setOf(1, 4)))
        assertEquals(BulkTitleEditSelectionKind.Mixed, classifyBulkTitleEditSelection(chapters, setOf(1, 2)))
    }

    @Test
    fun toggleRejectsVolumeAndChapterMix() {
        assertEquals(
            "已选章节，不能再选卷",
            canToggleBulkTitleEditSelection(chapters, setOf(2), 1)
        )
        assertEquals(
            "已选卷，不能再选章节",
            canToggleBulkTitleEditSelection(chapters, setOf(1), 2)
        )
        assertNull(canToggleBulkTitleEditSelection(chapters, setOf(1), 4))
        assertNull(canToggleBulkTitleEditSelection(chapters, setOf(2), 3))
        assertNull(canToggleBulkTitleEditSelection(chapters, setOf(1), 1))
    }

    @Test
    fun resolveSingleVolumeExpandsChildrenWithoutVolume() {
        val result = resolveBulkTitleEditTargets(chapters, setOf(1))
        assertEquals(BulkTitleEditSelectionKind.SingleVolume, result.kind)
        assertEquals(listOf(2, 3), result.targetIndexes)
        assertTrue(result.scopeLabel.contains("2 章"))
        assertEquals("", result.message)
    }

    @Test
    fun resolveMultiVolumesTargetsVolumesThemselves() {
        val result = resolveBulkTitleEditTargets(chapters, setOf(1, 4))
        assertEquals(BulkTitleEditSelectionKind.MultiVolumes, result.kind)
        assertEquals(listOf(1, 4), result.targetIndexes)
        assertTrue(result.scopeLabel.contains("2 个卷名"))
    }

    @Test
    fun resolveEmptyVolumeReportsMessage() {
        val result = resolveBulkTitleEditTargets(chapters, setOf(6))
        assertEquals(BulkTitleEditSelectionKind.SingleVolume, result.kind)
        assertTrue(result.targetIndexes.isEmpty())
        assertTrue(result.message.contains("没有可改章节"))
    }

    @Test
    fun volumeChildrenStopAtNextVolumeAndRespectEditableFilter() {
        assertEquals(listOf(2, 3), directoryVolumeChildChapterIndexes(chapters, 1))
        assertEquals(
            listOf(3),
            directoryVolumeChildChapterIndexes(chapters, 1) { index -> index != 2 }
        )
    }

    @Test
    fun findReplacePlainAndRegex() {
        assertEquals(
            "番外 第1章 开始",
            applyDirectoryTitleFindReplace("第1章 开始", "第", "番外 第", regex = false).getOrThrow()
        )
        assertEquals(
            "番外1 开始",
            applyDirectoryTitleFindReplace("第1章 开始", """第(\d+)章""", "番外$1", regex = true).getOrThrow()
        )
        assertTrue(applyDirectoryTitleFindReplace("标题", "", "x", regex = false).isFailure)
        assertTrue(applyDirectoryTitleFindReplace("标题", "(", "x", regex = true).isFailure)
    }

    @Test
    fun buildPlanSkipsEmptyResultsAndCountsChanges() {
        val plan = buildBulkTitleEditPlan(
            chapters = chapters,
            targetIndexes = listOf(2, 3),
            find = "第",
            replace = "番外",
            regex = false
        )
        assertEquals(2, plan.items.size)
        assertEquals(2, plan.changedCount)
        assertEquals("番外1章 开始", plan.items.first().newTitle)
        assertEquals("番外2章 中间", plan.items[1].newTitle)
        assertTrue(plan.items.all { it.changed })

        val emptyPlan = buildBulkTitleEditPlan(
            chapters = chapters,
            targetIndexes = listOf(2),
            find = "第1章 开始",
            replace = "   ",
            regex = false
        )
        assertTrue(emptyPlan.items.isEmpty())
        assertEquals(1, emptyPlan.skippedEmptyCount)
        assertTrue(emptyPlan.message.contains("空"))
    }

    @Test
    fun buildPlanReportsNoMatch() {
        val plan = buildBulkTitleEditPlan(
            chapters = chapters,
            targetIndexes = listOf(2),
            find = "不存在",
            replace = "x",
            regex = false
        )
        assertEquals(1, plan.items.size)
        assertEquals(0, plan.changedCount)
        assertFalse(plan.items.single().changed)
    }

    @Test
    fun buildPlanWritePairsOnlyIncludeChangedNonEmptyTitles() {
        val plan = buildBulkTitleEditPlan(
            chapters = chapters,
            targetIndexes = listOf(2, 3, 5),
            find = "第",
            replace = "番外",
            regex = false
        )
        val writePairs = plan.items.filter { it.changed }.map { it.chapterIndex to it.newTitle }
        assertEquals(3, plan.changedCount)
        assertEquals(3, writePairs.size)
        assertTrue(writePairs.all { it.second.isNotBlank() })
        assertEquals(listOf(2, 3, 5), writePairs.map { it.first })
    }

    private fun info(index: Int, title: String, isVolume: Boolean): ChapterInfo {
        return ChapterInfo(
            index = index,
            title = title,
            wordCount = 1,
            source = "path$index",
            fileName = "file$index",
            isVolume = isVolume
        )
    }
}
