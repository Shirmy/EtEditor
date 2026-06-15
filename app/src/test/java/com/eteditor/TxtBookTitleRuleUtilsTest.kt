package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtBookTitleRuleUtilsTest {
    @Test
    fun parseAndSerializeTxtBookTitleRulesNormalizeLegacyRows() {
        val rules = parseTxtBookTitleRuleItems(
            """
                旧名=>(默认书名)
                命名	true	《(.+)》=>${'$'}1
            """.trimIndent()
        )

        assertEquals(2, rules.size)
        assertEquals("旧名", rules[0].pattern)
        assertEquals("(默认书名)", rules[0].replacement)
        assertEquals("命名", rules[1].name)
        assertTrue(rules.all { it.regex && it.enabled })
        assertEquals(
            "\ttrue\t旧名=>(默认书名)\n命名\ttrue\t《(.+)》=>${'$'}1",
            serializeTxtBookTitleRuleItems(rules)
        )
    }

    @Test
    fun parseTxtBookTitleRulesKeepsArrowTextInsideReplacement() {
        val rules = parseTxtBookTitleRuleItems("命名\ttrue\t旧名=>新名=>附注")

        assertEquals(1, rules.size)
        assertEquals("旧名", rules[0].pattern)
        assertEquals("新名=>附注", rules[0].replacement)
        assertEquals(
            "命名\ttrue\t旧名=>新名=>附注",
            serializeTxtBookTitleRuleItems(rules)
        )
    }

    @Test
    fun parseTxtBookTitleRulesNormalizesLegacyDisabledAndOldBooleanRows() {
        val rules = parseTxtBookTitleRuleItems(
            """
                # 旧规则=>旧书名
                false	原名=>新名
            """.trimIndent()
        )

        assertEquals(listOf("旧规则", "原名"), rules.map { it.pattern })
        assertEquals(listOf("旧书名", "新名"), rules.map { it.replacement })
        assertTrue(rules.all { it.regex && it.enabled })
        assertEquals(
            "\ttrue\t旧规则=>旧书名\n\ttrue\t原名=>新名",
            serializeTxtBookTitleRuleItems(rules)
        )
    }

    @Test
    fun resolveTxtBookTitleFilterUsesFirstMatchingRuleAndCleansTxtExtension() {
        val rules = listOf(
            TxtBookTitleRuleItem(0, "跳过", "无匹配", "跳过", regex = false, enabled = true),
            TxtBookTitleRuleItem(1, "提取", """《(.+)》\.txt""", "${'$'}1", regex = true, enabled = true)
        )

        val result = resolveTxtBookTitleFilterWithRules("《书名》.txt", rules)

        assertEquals("《书名》.txt", result.sourceTitle)
        assertEquals("书名", result.filteredTitle)
        assertEquals(1, result.ruleIndex)
    }

    @Test
    fun resolveTxtBookTitleFilterSkipsInvalidRegexAndUsesLaterRule() {
        val errors = mutableListOf<String>()
        val rules = listOf(
            TxtBookTitleRuleItem(0, "坏规则", "(", "坏", regex = true, enabled = true),
            TxtBookTitleRuleItem(1, "纯文本", "书名.txt", "新书名", regex = false, enabled = true)
        )

        val result = resolveTxtBookTitleFilterWithRules("书名.txt", rules) { _, pattern ->
            errors += pattern
        }

        assertEquals(listOf("("), errors)
        assertEquals("新书名", result.filteredTitle)
        assertEquals(1, result.ruleIndex)
    }

    @Test
    fun resolveTxtBookTitleFilterUsesFirstMatchingSourceCandidate() {
        val rules = listOf(
            TxtBookTitleRuleItem(0, "提取", """《(.+)》\.txt""", "${'$'}1", regex = true, enabled = true)
        )

        val result = resolveTxtBookTitleFilterWithRules(
            sourceTitles = listOf(" ", "plain.txt", "《候选书名》.txt"),
            rules = rules
        )

        assertEquals("《候选书名》.txt", result.sourceTitle)
        assertEquals("候选书名", result.filteredTitle)
        assertEquals(0, result.ruleIndex)
    }

    @Test
    fun resolveTxtBookTitleFilterFallsBackToFirstCandidateWhenNoRuleMatches() {
        val rules = listOf(
            TxtBookTitleRuleItem(0, "提取", """《(.+)》\.txt""", "${'$'}1", regex = true, enabled = true)
        )

        val result = resolveTxtBookTitleFilterWithRules(
            sourceTitles = listOf(" plain.txt ", "《候选书名》.txt"),
            rules = rules.drop(1)
        )

        assertEquals(" plain.txt ", result.sourceTitle)
        assertEquals("plain", result.filteredTitle)
        assertNull(result.ruleIndex)
    }

    @Test
    fun resolveTxtBookTitleFilterHandlesBlankSingleAndEmptyCandidateList() {
        val blankSingle = resolveTxtBookTitleFilterWithRules(" ", emptyList())
        val emptyList = resolveTxtBookTitleFilterWithRules(emptyList(), emptyList())

        assertEquals(" ", blankSingle.sourceTitle)
        assertEquals(" ", blankSingle.filteredTitle)
        assertEquals("", emptyList.sourceTitle)
        assertEquals("", emptyList.filteredTitle)
    }

    @Test
    fun resolveTxtBookTitleFilterMatchesFullWidthRegexParenthesesLeniently() {
        val rules = listOf(
            TxtBookTitleRuleItem(0, "提取", """《（.+）》\.txt""", "${'$'}1", regex = true, enabled = true)
        )

        val result = resolveTxtBookTitleFilterWithRules("《书名》.txt", rules)

        assertEquals("书名", result.filteredTitle)
        assertEquals(0, result.ruleIndex)
    }

    @Test
    fun resolveTxtBookTitleFilterFallsBackToSourceTitleWhenRegexReplacementIsBlank() {
        val rules = listOf(
            TxtBookTitleRuleItem(0, "缺失分组", """(.+)\.txt""", "${'$'}9", regex = true, enabled = true)
        )

        val result = resolveTxtBookTitleFilterWithRules("书名.txt", rules)

        assertEquals("书名.txt", result.sourceTitle)
        assertEquals("书名", result.filteredTitle)
        assertEquals(0, result.ruleIndex)
    }

    @Test
    fun enabledTxtBookTitleRulesKeepsOnlyRulesWithPatternAndReplacement() {
        val rules = enabledTxtBookTitleRules(
            """
                A	true	旧=>新
                B	true	=>
                C	true	空=>
            """.trimIndent()
        )

        assertEquals(listOf("A"), rules.map { it.name })
    }

    @Test
    fun serializeTxtBookTitleRuleItemsCleansTabsAndLineBreaks() {
        val text = serializeTxtBookTitleRuleItems(
            listOf(
                TxtBookTitleRuleItem(
                    index = 0,
                    name = "  名\t称\nA  ",
                    pattern = "  旧\t名\n(.+)  ",
                    replacement = "  新\t名\n${'$'}1  ",
                    regex = true,
                    enabled = true
                )
            )
        )

        assertEquals("名 称 A\ttrue\t旧 名 (.+)=>新 名 ${'$'}1", text)
    }

    @Test
    fun buildTxtBookTitleFilterSourcesIncludesTxtVariantsAndFallback() {
        assertEquals(
            listOf("book.txt", "book", "显示名", "显示名.txt"),
            buildTxtBookTitleFilterSources("book.txt", "显示名", "fallback")
        )
        assertEquals(
            listOf("fallback", "fallback.txt"),
            buildTxtBookTitleFilterSources(null, "", "fallback")
        )
    }

    @Test
    fun buildTxtBookTitleFilterSourcesKeepsOrderedDistinctTxtVariants() {
        assertEquals(
            listOf("book", "book.txt"),
            buildTxtBookTitleFilterSources(" book ", "book.txt", "fallback")
        )
        assertEquals(
            listOf("TXT"),
            buildTxtBookTitleFilterSources(null, "", "")
        )
    }

    @Test
    fun updateTxtBookTitleModelRejectsBlankAndNoopButAcceptsNewTitle() {
        assertFalse(updateTxtBookTitleModel("旧书名", " ").success)
        assertEquals("书名无需修改", updateTxtBookTitleModel("旧书名", "旧书名.txt").message)

        val result = updateTxtBookTitleModel("旧书名", "新书名.txt")

        assertTrue(result.success)
        assertEquals("新书名", result.title)
        assertEquals("书名已修改：新书名", result.message)
    }

    @Test
    fun editTxtBookTitleRuleModelsAddUpdateMoveAndDeleteRules() {
        val added = addTxtBookTitleRuleModel("", "规则A", "旧", "新")
        val updated = updateTxtBookTitleRuleModel(added.rulesText, 0, "规则B", "旧(.+)", "新${'$'}1")
        val second = addTxtBookTitleRuleModel(updated.rulesText, "规则C", "C", "D")
        val moved = moveTxtBookTitleRuleModel(second.rulesText, 1, 0)
        val deleted = deleteTxtBookTitleRuleModel(moved.rulesText, 1)

        assertTrue(added.success)
        assertTrue(updated.success)
        assertTrue(moved.success)
        assertEquals(listOf("规则C", "规则B"), parseTxtBookTitleRuleItems(moved.rulesText).map { it.name })
        assertEquals(listOf("规则C"), parseTxtBookTitleRuleItems(deleted.rulesText).map { it.name })
        assertNull(applyTxtBookTitleRuleEditText(TxtBookTitleRuleEditResult(success = false)))
    }

    @Test
    fun editTxtBookTitleRuleModelsRejectInvalidDeleteMoveAndUpdateIndices() {
        val rulesText = addTxtBookTitleRuleModel("", "规则A", "旧", "新").rulesText

        assertFalse(updateTxtBookTitleRuleNameModel(rulesText, -1, "新名").success)
        assertFalse(updateTxtBookTitleRulePatternModel(rulesText, 9, "新").success)
        assertFalse(updateTxtBookTitleRuleReplacementModel(rulesText, 9, "新").success)
        assertFalse(updateTxtBookTitleRuleRegexModel(rulesText, 9).success)
        assertFalse(deleteTxtBookTitleRuleModel(rulesText, 9).success)
        assertFalse(moveTxtBookTitleRuleModel(rulesText, 0, 0).success)
        assertFalse(moveTxtBookTitleRuleModel(rulesText, 0, 9).success)
    }
}
