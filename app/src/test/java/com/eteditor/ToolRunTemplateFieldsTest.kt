package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolRunTemplateFieldsTest {
    @Test
    fun fetchProgressDisplayTextKeepsOnlyBracketedProgressMessages() {
        assertEquals("【目录】正在抓取", fetchProgressDisplayText("  【目录】正在抓取  "))
        assertEquals("【正文】1/2", fetchProgressDisplayText("\n【正文】1/2\t"))
        assertEquals("", fetchProgressDisplayText("正在抓取"))
        assertEquals("", fetchProgressDisplayText("进度：【目录】正在抓取"))
        assertEquals("", fetchProgressDisplayText(""))
    }

    @Test
    fun nextWorkingProgressAdvancesButCapsBeforeComplete() {
        assertEquals(-0.055f, nextWorkingProgress(-0.1f), 0.0001f)
        assertEquals(0.045f, nextWorkingProgress(0f), 0.0001f)
        assertEquals(0.145f, nextWorkingProgress(0.1f), 0.0001f)
        assertEquals(0.9f, nextWorkingProgress(0.89f), 0.0001f)
        assertEquals(0.9f, nextWorkingProgress(1f), 0.0001f)
    }

    @Test
    fun countProgressFractionAndLabelClampCompletedAndTotal() {
        assertEquals(0f, countProgressFraction(completed = -2, total = 4), 0.0001f)
        assertEquals(0.5f, countProgressFraction(completed = 2, total = 4), 0.0001f)
        assertEquals(1f, countProgressFraction(completed = 9, total = 4), 0.0001f)
        assertEquals(1f, countProgressFraction(completed = 3, total = 0), 0.0001f)

        assertEquals("准备", countProgressLabel(phase = "准备", completed = -2, total = 4))
        assertEquals("准备 2/4", countProgressLabel(phase = "准备", completed = 2, total = 4))
        assertEquals("准备 4/4", countProgressLabel(phase = "准备", completed = 9, total = 4))
        assertEquals("准备 1/1", countProgressLabel(phase = "准备", completed = 3, total = 0))
        assertEquals("准备", countProgressLabel(phase = "准备", completed = 0, total = 0))
    }
}
