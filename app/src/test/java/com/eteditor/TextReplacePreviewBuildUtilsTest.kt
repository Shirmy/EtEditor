package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextReplacePreviewBuildUtilsTest {
    @Test
    fun buildTextReplaceSearchResultsForRulesSwitchesSourceTargetForTextOnlyRules() {
        val requestedTargets = mutableListOf<String>()
        val results = buildTextReplaceSearchResultsForRules(
            rules = listOf(
                TextReplaceRule(find = "foo", replacement = "bar", regex = false, textOnly = true),
                TextReplaceRule(find = "bar", replacement = "baz", regex = false, textOnly = false)
            ),
            parameters = parameters(),
            sourceResolver = { params ->
                requestedTargets += params.target
                listOf(SearchSource(0, "章节", "chapter.xhtml", "foo bar", 10))
            },
            resolveLocation = { _, _, chapterIndex, title -> TextSearchResultLocation(chapterIndex, title) }
        )

        assertEquals(listOf(TEXT_REPLACE_TARGET_VISIBLE, TEXT_REPLACE_TARGET_SOURCE), requestedTargets)
        assertEquals(listOf("foo", "bar"), results.map { it.matchText })
        assertEquals(listOf(0, 1), results.map { it.ruleIndex })
    }

    @Test
    fun buildTextReplaceSearchResultsForRulesSkipsRulesWithNoSources() {
        val results = buildTextReplaceSearchResultsForRules(
            rules = listOf(
                TextReplaceRule(find = "foo", replacement = "bar", regex = false, textOnly = true),
                TextReplaceRule(find = "bar", replacement = "baz", regex = false, textOnly = false)
            ),
            parameters = parameters(),
            sourceResolver = { params ->
                if (params.target == TEXT_REPLACE_TARGET_VISIBLE) {
                    emptyList()
                } else {
                    listOf(SearchSource(0, "章节", "chapter.xhtml", "foo bar", 10))
                }
            },
            resolveLocation = { _, _, chapterIndex, title -> TextSearchResultLocation(chapterIndex, title) }
        )

        assertEquals(listOf("bar"), results.map { it.matchText })
        assertEquals(listOf(1), results.map { it.ruleIndex })
    }

    @Test
    fun buildReplacementFilePreviewModelGroupsRulesByMatchCountAndKeepsSkippedRules() {
        val skipped = ReplacementSkippedRule(lineNo = 4, reason = "无效", text = "bad")
        val preview = buildReplacementFilePreviewModel(
            toolId = "tool-1",
            parsedRules = listOf(
                ParsedReplacementRule(lineNo = 1, pattern = "foo", replacement = "bar", regex = false),
                ParsedReplacementRule(lineNo = 2, pattern = "bar", replacement = "baz", regex = false),
                ParsedReplacementRule(lineNo = 3, pattern = "none", replacement = "x", regex = false)
            ),
            skippedRules = listOf(skipped),
            sources = listOf(SearchSource(0, "章节", "chapter.xhtml", "foo bar foo", 0)),
            resolveLocation = { _, _, chapterIndex, title -> TextSearchResultLocation(chapterIndex, title) }
        )

        assertEquals("tool-1", preview.toolId)
        assertEquals(4, preview.totalRules)
        assertEquals(listOf("foo"), preview.multiRules.map { it.pattern })
        assertEquals(listOf("bar"), preview.singleRules.map { it.pattern })
        assertEquals(listOf("none"), preview.zeroRules.map { it.pattern })
        assertEquals(listOf(skipped), preview.skippedRules)
    }

    @Test
    fun buildReplacementFilePreviewForParametersParsesInputAndGroupsSkippedRules() {
        val preview = buildReplacementFilePreviewForParameters(
            toolId = "tool-2",
            parameters = parameters(),
            input = "foo#->#bar\nbad line\nnone#->#x",
            sourceResolver = {
                listOf(SearchSource(0, "章节", "chapter.xhtml", "foo foo", 0))
            },
            resolveLocation = { _, _, chapterIndex, title -> TextSearchResultLocation(chapterIndex, title) }
        )

        assertEquals("tool-2", preview.toolId)
        assertEquals(3, preview.totalRules)
        assertEquals(listOf("foo"), preview.multiRules.map { it.pattern })
        assertEquals(listOf("none"), preview.zeroRules.map { it.pattern })
        assertEquals(listOf("缺少 #-># 分隔符"), preview.skippedRules.map { it.reason })
    }

    @Test
    fun buildReplacementFilePreviewModelCapsMatchesAndMarksPreviewLimitReached() {
        val preview = buildReplacementFilePreviewModel(
            toolId = "tool-3",
            parsedRules = listOf(
                ParsedReplacementRule(lineNo = 1, pattern = "foo", replacement = "bar", regex = false)
            ),
            skippedRules = emptyList(),
            sources = listOf(SearchSource(0, "章节", "chapter.xhtml", "foo ".repeat(250))),
            resolveLocation = { _, _, chapterIndex, title -> TextSearchResultLocation(chapterIndex, title) }
        )

        assertTrue(preview.previewLimitReached)
        assertEquals(1, preview.multiRules.size)
        assertEquals(REPLACEMENT_PREVIEW_MAX_MATCHES_PER_RULE, preview.multiRules.single().matches.size)
        assertEquals(emptyList<ReplacementPreviewRule>(), preview.singleRules)
        assertEquals(emptyList<ReplacementPreviewRule>(), preview.zeroRules)
    }

    @Test
    fun visibleTextRegexCostWarningOnlyWarnsForEnabledTextOnlyHighCostRegexOnLongSegments() {
        val riskyRule = TextReplaceRule(
            find = "(a|b|c|d|e|f|g|h|i).*baz",
            replacement = "",
            regex = true,
            textOnly = true
        )
        val disabledRule = riskyRule.copy(enabled = false)
        val plainRule = riskyRule.copy(regex = false)

        assertNull(visibleTextRegexCostWarning(listOf(disabledRule, plainRule), longestSegmentLength = 2000))
        assertEquals(
            "仅文本正则过于复杂，可能在长正文段落上卡住；请切换为“正文源码 + 正则”，或把这条正则拆成多条更简单的规则",
            visibleTextRegexCostWarning(listOf(riskyRule), longestSegmentLength = 2000)
        )
        assertNull(visibleTextRegexCostWarning(listOf(riskyRule), longestSegmentLength = 20))
    }

    @Test
    fun visibleTextRegexCostWarningIgnoresHighCostSourceRegexRules() {
        val sourceRegexRule = TextReplaceRule(
            find = "(a|b|c|d|e|f|g|h|i).*baz",
            replacement = "",
            regex = true,
            textOnly = false
        )

        assertNull(
            visibleTextRegexCostWarning(
                rules = listOf(sourceRegexRule),
                longestSegmentLength = 2000
            )
        )
    }

    @Test
    fun highCostVisibleTextRegexDetectsRepeatedGroupsAndWideAlternation() {
        val wideAlternation = (1..17).joinToString("|") { "term$it" } +
            "x".repeat(240)

        assertTrue(isHighCostVisibleTextRegex("(foo.*)+bar"))
        assertTrue(isHighCostVisibleTextRegex(wideAlternation))
        assertEquals(false, isVisibleTextRegexRiskyForVisibleSegments(wideAlternation, longestSegmentLength = 0))
        assertEquals(false, isVisibleTextRegexRiskyForVisibleSegments("foo.*bar", longestSegmentLength = 3000))
    }

    @Test
    fun visibleTextRegexRiskRequiresLongSegmentOrLongPatternAfterHighCostDetection() {
        val shortHighCostPattern = "(foo.*)+bar"
        val longHighCostPattern = shortHighCostPattern + "x".repeat(240)

        assertEquals(false, isVisibleTextRegexRiskyForVisibleSegments(shortHighCostPattern, longestSegmentLength = 1199))
        assertEquals(true, isVisibleTextRegexRiskyForVisibleSegments(shortHighCostPattern, longestSegmentLength = 1200))
        assertEquals(true, isVisibleTextRegexRiskyForVisibleSegments(longHighCostPattern, longestSegmentLength = 1))
    }

    @Test
    fun visibleTextRegexCostWarningForSourcesUsesLongestVisibleSegment() {
        val riskyRule = TextReplaceRule(
            find = "(foo.*)+bar",
            replacement = "",
            regex = true,
            textOnly = true
        )
        val sources = listOf(
            SearchSource(0, "短章", "a.xhtml", "<p>短</p>"),
            SearchSource(1, "长章", "b.xhtml", "<p>${"正文".repeat(700)}</p>")
        )

        assertEquals(
            "仅文本正则过于复杂，可能在长正文段落上卡住；请切换为“正文源码 + 正则”，或把这条正则拆成多条更简单的规则",
            visibleTextRegexCostWarningForSources(listOf(riskyRule), sources)
        )
    }

    @Test
    fun textRegexCostWarningForSourcesUsesLongestSourceText() {
        val riskyRule = TextReplaceRule(
            find = "(foo.*)+bar",
            replacement = "",
            regex = true,
            textOnly = false
        )
        val shortSource = listOf(SearchSource(0, "短章", "a.xhtml", "短正文"))
        val longSource = listOf(SearchSource(1, "长章", "b.xhtml", "正文".repeat(700)))

        assertNull(textRegexCostWarningForSources(listOf(riskyRule.copy(enabled = false)), longSource))
        assertNull(textRegexCostWarningForSources(listOf(riskyRule), shortSource))
        assertEquals(
            "正则过于复杂，可能在长正文上卡住；请拆成更简单的规则，或改用普通文本匹配",
            textRegexCostWarningForSources(listOf(riskyRule), longSource)
        )
    }

    @Test
    fun longestVisibleTextSegmentLengthIgnoresTagsAndBlankSegments() {
        val longest = longestVisibleTextSegmentLength(
            listOf(
                SearchSource(0, "章节", "a.xhtml", "<p>短</p><p>很长的正文段落</p>"),
                SearchSource(1, "章节", "b.xhtml", "<p>   </p>")
            )
        )

        assertEquals("很长的正文段落".length, longest)
        assertTrue(isHighCostVisibleTextRegex("(?=.*foo).*bar"))
    }

    @Test
    fun visibleSegmentLengthIfTextSkipsInvalidAndWhitespaceOnlyRanges() {
        val text = "  \n正文  "

        assertEquals(0, visibleSegmentLengthIfText(text, start = 3, end = 3))
        assertEquals(0, visibleSegmentLengthIfText(text, start = 0, end = 3))
        assertEquals(4, visibleSegmentLengthIfText(text, start = 3, end = 7))
    }

    private fun parameters(): TextReplaceParameters {
        return TextReplaceParameters(
            mode = TEXT_REPLACE_MODE_SINGLE,
            target = TEXT_REPLACE_TARGET_SOURCE,
            scope = TOOL_SCOPE_ALL,
            selectedHtmlSourceIndices = emptySet(),
            matchPattern = "",
            matchRegexEnabled = true,
            findText = "",
            replaceText = "",
            findRegexEnabled = false,
            batchSource = "",
            batchText = "",
            batchFile = "",
            preview = true
        )
    }
}
