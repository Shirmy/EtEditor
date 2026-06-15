package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtChapterRuleUtilsTest {
    @Test
    fun parseAndSerializeTxtChapterRulesPreserveNamePatternAndReplacement() {
        val items = parseTxtChapterRuleItems("章节\t^第(\\d+)章\t第{index}章 ${'$'}1\n^番外.*$")

        assertEquals(2, items.size)
        assertEquals("章节", items[0].name)
        assertEquals("""^第(\d+)章""", items[0].pattern)
        assertEquals("第{index}章 ${'$'}1", items[0].replacement)
        assertEquals("规则 2", items[1].name)
        assertEquals("^番外.*$", items[1].pattern)
        assertEquals(
            "章节\t^第(\\d+)章\t第{index}章 ${'$'}1\n规则 2\t^番外.*$\t",
            serializeTxtChapterRuleItems(items)
        )
    }

    @Test
    fun parseTxtChapterRulesKeepsTabsInsideReplacementTemplate() {
        val items = parseTxtChapterRuleItems("章节\t^第(\\d+)章\t第{index}章\t${'$'}1")

        assertEquals(1, items.size)
        assertEquals("""^第(\d+)章""", items[0].pattern)
        assertEquals("第{index}章\t${'$'}1", items[0].replacement)
        assertEquals(
            "章节\t^第(\\d+)章\t第{index}章 ${'$'}1",
            serializeTxtChapterRuleItems(items)
        )
    }

    @Test
    fun parseLegacyDisabledTxtChapterRuleAndUpdateEnabledKey() {
        val disabled = parseTxtChapterRuleItems("# 章节\t^第(\\d+)章\t").single()
        val rulesText = serializeTxtChapterRuleItems(listOf(disabled))
        val enabledKeys = setOf(txtChapterRuleKey(disabled))
        val updated = updateTxtChapterRuleItemModel(
            rulesText = rulesText,
            enabledKeys = enabledKeys,
            index = 0,
            name = "新章节",
            pattern = "^第\\s*(\\d+)\\s*章",
            replacement = "第{index}章"
        )
        val state = applyTxtChapterRuleEditState(
            currentEnabledKeys = emptySet(),
            result = updated,
            updateEnabledKeys = true
        )

        assertFalse(disabled.enabled)
        assertTrue(updated.success)
        assertEquals(setOf(txtChapterRuleKey(parseTxtChapterRuleItems(updated.rulesText).single())), updated.enabledKeys)
        assertEquals(updated.enabledKeys, state?.enabledKeys)
    }

    @Test
    fun activeTxtChapterPatternRulesUsesEnabledKeysOnly() {
        val items = parseTxtChapterRuleItems("章节\t^第(\\d+)章\t\n番外\t^番外.*$\t")
        val enabledKey = txtChapterRuleKey(items[1])
        val rules = activeTxtChapterPatternRules(
            config = TxtChapterDetectionConfig(
                rulesText = serializeTxtChapterRuleItems(items),
                shortThreshold = 100,
                longThreshold = 10000,
                hiddenLineIndices = emptySet()
            ),
            enabledKeys = setOf(enabledKey)
        )

        assertEquals(1, rules.size)
        assertEquals("^番外.*$", rules.single().pattern)
    }

    @Test
    fun updateTxtChapterRuleEnabledKeysAndRenameReplaceKeys() {
        val added = addTxtChapterRuleModel("", emptySet(), "章节", "^第(\\d+)章", "")
        val item = parseTxtChapterRuleItems(added.rulesText).single()
        val disabled = updateTxtChapterRuleEnabledKeys(added.rulesText, added.enabledKeys, 0, enabled = false)
        val renamed = renameTxtChapterRuleModel(added.rulesText, added.enabledKeys, 0, "新章节")

        assertTrue(added.success)
        assertTrue(txtChapterRuleKey(item) in added.enabledKeys)
        assertTrue(disabled.success)
        assertEquals(emptySet<String>(), disabled.enabledKeys)
        assertTrue(txtChapterRuleKey(parseTxtChapterRuleItems(renamed.rulesText).single()) in renamed.enabledKeys)
    }

    @Test
    fun updateDisabledTxtChapterRuleDoesNotEnableIt() {
        val rulesText = "章节\t^第(\\d+)章\t"
        val updated = updateTxtChapterRuleItemModel(
            rulesText = rulesText,
            enabledKeys = emptySet(),
            index = 0,
            name = "新章节",
            pattern = "^第\\s*(\\d+)\\s*章",
            replacement = "第{index}章"
        )

        assertTrue(updated.success)
        assertEquals(emptySet<String>(), updated.enabledKeys)
        assertEquals(
            listOf(false),
            effectiveTxtChapterRuleItems(updated.rulesText, updated.enabledKeys).map { it.enabled }
        )
    }

    @Test
    fun applyTxtChapterRuleEditStateCanKeepCurrentEnabledKeys() {
        val currentEnabledKeys = setOf("existing-key")
        val result = addTxtChapterRuleModel("", emptySet(), "章节", "^第(\\d+)章", "")
        val state = applyTxtChapterRuleEditState(
            currentEnabledKeys = currentEnabledKeys,
            result = result,
            updateEnabledKeys = false
        )

        assertEquals(result.rulesText, state?.rulesText)
        assertEquals(currentEnabledKeys, state?.enabledKeys)
        assertNull(
            applyTxtChapterRuleEditState(
                currentEnabledKeys = currentEnabledKeys,
                result = TxtChapterRuleEditResult(success = false)
            )
        )
    }

    @Test
    fun countTxtChapterRuleMatchesOnlyCatalogLikeLinesFromStart() {
        val text = "第1章 开始\n正文第2章\n这是一行很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长的内容\n第2章 继续"

        assertEquals(2, countTxtChapterRuleMatches(text, """^第\d+章"""))
        assertTrue(txtChapterRuleHasMatch(text, """^第\d+章"""))
        assertFalse(txtChapterRuleHasMatch(text, "("))
    }

    @Test
    fun deleteAndMoveTxtChapterRulesRejectInvalidIndices() {
        val rulesText = "A\t^A\t\nB\t^B\t"

        assertFalse(deleteTxtChapterRuleModel(rulesText, emptySet(), 9).success)
        assertFalse(moveTxtChapterRuleModel(rulesText, 0, 0).success)
        assertEquals(
            listOf("B", "A"),
            parseTxtChapterRuleItems(moveTxtChapterRuleModel(rulesText, 1, 0).rulesText).map { it.name }
        )
    }
}
