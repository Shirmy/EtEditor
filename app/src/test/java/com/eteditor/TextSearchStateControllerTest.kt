package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TextSearchStateControllerTest {
    @Test
    fun replacementPreviewAfterBodyTextChangeUsesRebuiltPreview() {
        val previous = replacementPreview(
            toolId = "tool-1",
            multiRules = listOf(previewRule("multi", lineNo = 2)),
            singleRules = listOf(previewRule("single", lineNo = 1)),
            zeroRules = listOf(previewRule("zero", lineNo = 3))
        )
        val rebuilt = replacementPreview(toolId = "tool-1", singleRules = listOf(previewRule("rebuilt", lineNo = 1)))

        val result = replacementPreviewAfterBodyTextChange(previous) { preview ->
            assertSame(previous, preview)
            rebuilt
        }

        assertSame(rebuilt, result)
    }

    @Test
    fun replacementPreviewSourceRulesKeepOriginalLineOrderAcrossSections() {
        val preview = replacementPreview(
            toolId = "tool-1",
            multiRules = listOf(previewRule("multi", lineNo = 30, pattern = "c")),
            singleRules = listOf(previewRule("single", lineNo = 10, pattern = "a")),
            zeroRules = listOf(previewRule("zero", lineNo = 20, pattern = "b"))
        )

        val rules = replacementPreviewSourceRules(preview)

        assertEquals(listOf(10, 20, 30), rules.map { it.lineNo })
        assertEquals(listOf("a", "b", "c"), rules.map { it.pattern })
    }

    private fun replacementPreview(
        toolId: String,
        multiRules: List<ReplacementPreviewRule> = emptyList(),
        singleRules: List<ReplacementPreviewRule> = emptyList(),
        zeroRules: List<ReplacementPreviewRule> = emptyList()
    ): ReplacementFilePreview {
        return ReplacementFilePreview(
            toolId = toolId,
            totalRules = multiRules.size + singleRules.size + zeroRules.size,
            multiRules = multiRules,
            singleRules = singleRules,
            zeroRules = zeroRules,
            skippedRules = emptyList()
        )
    }

    private fun previewRule(
        id: String,
        lineNo: Int,
        pattern: String = id
    ): ReplacementPreviewRule {
        return ReplacementPreviewRule(
            id = id,
            lineNo = lineNo,
            pattern = pattern,
            replacement = "$pattern-replacement",
            regex = false,
            matches = emptyList()
        )
    }
}
