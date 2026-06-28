package com.eteditor

import org.junit.Assert.assertEquals
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
