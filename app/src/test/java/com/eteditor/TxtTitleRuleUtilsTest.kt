package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.TxtChapterPatternRule
import org.junit.Assert.assertEquals
import org.junit.Test

class TxtTitleRuleUtilsTest {
    @Test
    fun customTxtChapterRuleReplacementUsesIndexAndCaptureGroups() {
        val chapters = ChapterDetector.detectTxtChapters(
            text = "第9章 新标题\n正文\n第10章 继续\n正文",
            shortThreshold = 0,
            longThreshold = 10000,
            customRules = listOf(
                TxtChapterPatternRule(
                    pattern = """^第\d+章\s+(.+)$""",
                    replacement = "第{index}章 ${'$'}1"
                )
            )
        )

        assertEquals(
            listOf("第1章 新标题", "第2章 继续"),
            chapters.map { it.title }
        )
        assertEquals(listOf(1, 2), chapters.map { it.number })
    }

    @Test
    fun customTxtChapterRuleReplacementCanEscapeDollarGroups() {
        val chapters = ChapterDetector.detectTxtChapters(
            text = "第九章 原标题\n正文",
            shortThreshold = 0,
            longThreshold = 10000,
            customRules = listOf(
                TxtChapterPatternRule(
                    pattern = """^第九章\s+(.+)$""",
                    replacement = "第{index}章 ${'$'}1 / \\${'$'}1"
                )
            )
        )

        assertEquals(
            listOf("第1章 原标题 / ${'$'}1"),
            chapters.map { it.title }
        )
    }

    @Test
    fun customTxtChapterRuleReplacementFallsBackToOriginalWhenReplacementBlank() {
        val chapters = ChapterDetector.detectTxtChapters(
            text = "序章\n正文",
            shortThreshold = 0,
            longThreshold = 10000,
            customRules = listOf(
                TxtChapterPatternRule(
                    pattern = """^序章$""",
                    replacement = " "
                )
            )
        )

        assertEquals(
            listOf("序章"),
            chapters.map { it.title }
        )
    }
}
