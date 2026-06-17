package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Test

class TextReplacePreviewFieldsTest {
    @Test
    fun displayLineBreakEscapesShowsAllLineEndingKinds() {
        assertEquals(
            "一\\r\\n二\\n三\\r四",
            displayLineBreakEscapes("一\r\n二\n三\r四")
        )
    }

    @Test
    fun displaySearchContextEscapesInvisibleCharactersAndAdjustsHighlight() {
        val context = displaySearchContext(
            text = "前文\t匹配\r\n后文",
            highlightStart = 3,
            highlightEnd = 5
        )

        assertEquals("前文\\t匹配\\r\\n后文", context.text)
        assertEquals(4, context.highlightStart)
        assertEquals(6, context.highlightEnd)
        assertEquals("匹配", context.text.substring(context.highlightStart, context.highlightEnd))
    }

    @Test
    fun displaySearchContextHighlightCanSpanEscapedLineBreaksAndTabs() {
        val context = displaySearchContext(
            text = "A\r\nB\tC",
            highlightStart = 1,
            highlightEnd = 5
        )

        assertEquals("A\\r\\nB\\tC", context.text)
        assertEquals(1, context.highlightStart)
        assertEquals(8, context.highlightEnd)
        assertEquals("\\r\\nB\\t", context.text.substring(context.highlightStart, context.highlightEnd))
    }

    @Test
    fun displaySearchContextClipsLongContextAroundMatch() {
        val context = displaySearchContext(
            text = "0123456789ABCDEFGHIJ-MATCH-abcdefghijklmnopqrstuvwxyz",
            highlightStart = 21,
            highlightEnd = 26
        )

        assertEquals("...789ABCDEFGHIJ-MATCH-abcdefghijklmnopqrstuvwxyz", context.text)
        assertEquals(17, context.highlightStart)
        assertEquals(22, context.highlightEnd)
    }

    @Test
    fun displaySearchContextAddsSuffixWhenLongTextContinuesAfterMatch() {
        val context = displaySearchContext(
            text = "aaaaaMATCH" + "b".repeat(40),
            highlightStart = 5,
            highlightEnd = 10
        )

        assertEquals("aaaaaMATCH" + "b".repeat(28) + "...", context.text)
        assertEquals(5, context.highlightStart)
        assertEquals(10, context.highlightEnd)
    }

    @Test
    fun displaySearchContextHandlesEmptyAndEndCursorHighlights() {
        assertEquals(SearchContextDisplay("", -1, -1), displaySearchContext("", 0, 0))

        val context = displaySearchContext(
            text = "正文\n",
            highlightStart = 3,
            highlightEnd = 3
        )

        assertEquals("正文\\n", context.text)
        assertEquals(4, context.highlightStart)
        assertEquals(4, context.highlightEnd)
    }

    @Test
    fun displaySearchContextCoercesOutOfBoundsHighlights() {
        val context = displaySearchContext(
            text = "正文",
            highlightStart = -10,
            highlightEnd = 99
        )

        assertEquals("正文", context.text)
        assertEquals(0, context.highlightStart)
        assertEquals(2, context.highlightEnd)
    }

    @Test
    fun replacementInvalidRulesMessageListsSkippedRulesOrEmptyState() {
        assertEquals("没有无效规则", replacementInvalidRulesMessage(emptyList()))
        assertEquals(
            "第 2 行：缺少 #-># 分隔符\nbad\n\n第 4 行：查找内容为空\n#->#empty",
            replacementInvalidRulesMessage(
                listOf(
                    ReplacementSkippedRule(lineNo = 2, reason = "缺少 #-># 分隔符", text = "bad"),
                    ReplacementSkippedRule(lineNo = 4, reason = "查找内容为空", text = "#->#empty")
                )
            )
        )
    }

    @Test
    fun replacementInvalidRulesMessagePreservesMultilineRuleTextAndBlankFields() {
        assertEquals(
            "第 0 行：\n第一行\n第二行\n\n第 9 行：格式错误\n",
            replacementInvalidRulesMessage(
                listOf(
                    ReplacementSkippedRule(lineNo = 0, reason = "", text = "第一行\n第二行"),
                    ReplacementSkippedRule(lineNo = 9, reason = "格式错误", text = "")
                )
            )
        )
    }

    @Test
    fun shouldKeepTextSearchPreviewOpenAfterSelectedApplyOnlyForManualRemainingResults() {
        assertEquals(
            true,
            shouldKeepTextSearchPreviewOpenAfterSelectedApply(
                hasAutomationStep = false,
                hasAppliedCallback = false,
                remainingResults = 2
            )
        )
        assertEquals(
            false,
            shouldKeepTextSearchPreviewOpenAfterSelectedApply(
                hasAutomationStep = true,
                hasAppliedCallback = false,
                remainingResults = 2
            )
        )
        assertEquals(
            false,
            shouldKeepTextSearchPreviewOpenAfterSelectedApply(
                hasAutomationStep = false,
                hasAppliedCallback = true,
                remainingResults = 2
            )
        )
        assertEquals(
            false,
            shouldKeepTextSearchPreviewOpenAfterSelectedApply(
                hasAutomationStep = false,
                hasAppliedCallback = false,
                remainingResults = 0
            )
        )
    }
}
