package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.TxtChapter
import com.eteditor.core.TxtDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtPurifyRuleUtilsTest {
    @Test
    fun parseSerializeAndNormalizeTxtPurifyRules() {
        val rules = parseTxtPurifyRuleItems(
            """
                catalog	标题	true	旧=>新
                plain=>replacement
            """.trimIndent()
        )

        assertEquals(2, rules.size)
        assertEquals(TXT_PURIFY_TARGET_CATALOG, rules[0].target)
        assertEquals("标题", rules[0].name)
        assertEquals("旧", rules[0].pattern)
        assertEquals(TXT_PURIFY_TARGET_BODY, rules[1].target)
        assertEquals("plain", rules[1].pattern)
        assertEquals(TXT_PURIFY_TARGET_CATALOG, normalizeTxtPurifyTarget("目录"))
        assertEquals(TXT_PURIFY_TARGET_BODY, normalizeTxtPurifyTarget("unknown"))
        assertEquals(
            "catalog\t标题\ttrue\t旧=>新\nbody\t\ttrue\tplain=>replacement",
            serializeTxtPurifyRuleItems(rules)
        )
    }

    @Test
    fun normalizeTxtPurifyTargetTreatsTitleAliasesAsCatalogAndSerializesDisabledRules() {
        val item = TxtPurifyRuleItem(
            index = 0,
            target = "titles",
            name = "  名\t称\nA  ",
            pattern = "  旧\t名\n(.+)  ",
            replacement = "  新\t名\n${'$'}1",
            regex = true,
            enabled = false
        )

        assertEquals(TXT_PURIFY_TARGET_CATALOG, normalizeTxtPurifyTarget("title"))
        assertEquals(TXT_PURIFY_TARGET_CATALOG, normalizeTxtPurifyTarget("titles"))
        assertEquals(TXT_PURIFY_TARGET_CATALOG, normalizeTxtPurifyTarget("toc"))
        assertEquals(TXT_PURIFY_TARGET_CATALOG, normalizeTxtPurifyTarget("标题"))
        assertEquals("# catalog\t名 称 A\ttrue\t旧 名 (.+)=>  新 名 ${'$'}1", serializeTxtPurifyRuleItems(listOf(item)))
    }

    @Test
    fun parseLegacyDisabledRulesAndEnabledRulesKeepExecutablePatternsOnly() {
        val rulesText = """
            # body	禁用	false	广告=>
            body	空模式	false	=>replacement
            body	启用	false	水印=>
        """.trimIndent()

        val rules = parseTxtPurifyRuleItems(rulesText)
        val enabledRules = enabledTxtPurifyRules(rulesText)

        assertEquals(3, rules.size)
        assertFalse(rules[0].enabled)
        assertEquals("广告", rules[0].pattern)
        assertEquals("", rules[1].pattern)
        assertEquals(listOf(2), enabledRules.map { it.index })
        assertEquals(listOf("启用"), enabledRules.map { it.name })
    }

    @Test
    fun countTxtPurifyRuleMatchesCatalogAndBodySeparately() {
        val text = "第1章 旧标题\n广告\n正文\n第2章 第二章\n广告"
        val document = TxtDocument("book.txt", text, "UTF-8", detectTxtChapters(text))

        assertEquals(
            1,
            countTxtPurifyRuleMatches(
                document,
                TxtPurifyRuleItem(0, TXT_PURIFY_TARGET_CATALOG, "标题", "旧", "", regex = false, enabled = true)
            )
        )
        assertEquals(
            2,
            countTxtPurifyRuleMatches(
                document,
                TxtPurifyRuleItem(1, TXT_PURIFY_TARGET_BODY, "正文", "广告", "", regex = false, enabled = true)
            )
        )
    }

    @Test
    fun countTxtPurifyRuleMatchesReturnsZeroForMissingDocumentBlankPatternAndInvalidRegex() {
        val document = TxtDocument("book.txt", "第1章 标题\n正文", "UTF-8", detectTxtChapters("第1章 标题\n正文"))

        assertEquals(
            0,
            countTxtPurifyRuleMatches(
                null,
                TxtPurifyRuleItem(0, TXT_PURIFY_TARGET_BODY, "正文", "正文", "", regex = false, enabled = true)
            )
        )
        assertEquals(
            0,
            countTxtPurifyRuleMatches(
                document,
                TxtPurifyRuleItem(1, TXT_PURIFY_TARGET_BODY, "空", "", "", regex = true, enabled = true)
            )
        )
        assertEquals(
            0,
            countTxtPurifyRuleMatches(
                document,
                TxtPurifyRuleItem(2, TXT_PURIFY_TARGET_BODY, "坏", "(", "", regex = true, enabled = true)
            )
        )
    }

    @Test
    fun applyTxtPurifyTargetsToDocumentUpdatesBodyAndCatalogThenRedetects() {
        val text = "第1章 旧标题\n广告\n正文\n第2章 第二章\n广告"
        val document = TxtDocument("book.txt", text, "UTF-8", detectTxtChapters(text))
        val rulesText = """
            body	正文	false	广告=>
            catalog	标题	false	旧=>新
        """.trimIndent()

        val result = applyTxtPurifyTargetsToDocument(
            document = document,
            rulesText = rulesText,
            applyBody = true,
            applyCatalog = true,
            detectChapters = ::detectTxtChapters
        )

        assertEquals(TxtPurifyApplyResult(hasRules = true, bodyCount = 2, catalogCount = 1), result)
        assertEquals("第1章 新标题\n\n正文\n第2章 第二章\n", document.text)
        assertEquals(listOf("第1章 新标题", "第2章 第二章"), document.chapters.map { it.title })
    }

    @Test
    fun applyTxtPurifyRulesToSegmentSupportsPlainAndRegexReplacements() {
        val plainRule = TxtPurifyRuleItem(0, TXT_PURIFY_TARGET_BODY, "plain", ".", "。", regex = false, enabled = true)
        val regexRule = TxtPurifyRuleItem(1, TXT_PURIFY_TARGET_BODY, "regex", """第(\d+)章""", "Chapter \$1", regex = true, enabled = true)
        val rules = compileTxtPurifyRules(listOf(plainRule, regexRule)).orEmpty()

        val result = applyTxtPurifyRulesToSegment("第12章 a.b", rules)

        assertEquals("Chapter 12 a。b", result.first)
        assertEquals(2, result.second)
    }

    @Test
    fun txtBodyRangesIncludePrefaceAndExcludeCatalogTitles() {
        val text = "前言广告\n第1章 标题广告\n正文广告\n第2章 标题\n正文"
        val chapters = detectTxtChapters(text)
        val ranges = txtBodyRanges(text, chapters)

        assertEquals(
            listOf("前言广告\n", "正文广告\n", "正文"),
            ranges.map { (start, end) -> text.substring(start, end) }
        )
    }

    @Test
    fun txtBodyRangesUseTextOrderWhenChaptersAreOutOfOrder() {
        val text = "前言广告\n第1章 标题广告\n正文广告\n第2章 标题\n正文"
        val reversedChapters = detectTxtChapters(text).asReversed()
        val ranges = txtBodyRanges(text, reversedChapters)

        assertEquals(
            listOf("前言广告\n", "正文广告\n", "正文"),
            ranges.map { (start, end) -> text.substring(start, end) }
        )
    }

    @Test
    fun txtPurifyRegexCostWarningUsesOnlyEnabledApplyTargets() {
        val bodyText = "第1章 标题\n${"正文".repeat(700)}"
        val bodyDocument = TxtDocument("book.txt", bodyText, "UTF-8", detectTxtChapters(bodyText))
        val bodyRule = TxtPurifyRuleItem(
            index = 0,
            target = TXT_PURIFY_TARGET_BODY,
            name = "正文",
            pattern = "(foo.*)+bar",
            replacement = "",
            regex = true,
            enabled = true
        )
        val disabledBodyRule = bodyRule.copy(enabled = false)

        val catalogTitle = "第1章 ${"标题".repeat(700)}"
        val catalogText = "$catalogTitle\n正文"
        val catalogDocument = TxtDocument(
            "book.txt",
            catalogText,
            "UTF-8",
            listOf(
                TxtChapter(
                    index = 1,
                    lineIndex = 0,
                    endLineIndex = 2,
                    title = catalogTitle,
                    wordCount = 2
                )
            )
        )
        val catalogRule = bodyRule.copy(target = TXT_PURIFY_TARGET_CATALOG, name = "目录")

        assertEquals(
            "净化规则正则过于复杂，可能在长 TXT 正文上卡住；请拆成更简单的规则，或改用普通文本匹配",
            txtPurifyRegexCostWarningForDocument(
                document = bodyDocument,
                rules = listOf(bodyRule),
                applyBody = true,
                applyCatalog = false
            )
        )
        assertNull(
            txtPurifyRegexCostWarningForDocument(
                document = bodyDocument,
                rules = listOf(bodyRule),
                applyBody = false,
                applyCatalog = true
            )
        )
        assertNull(
            txtPurifyRegexCostWarningForDocument(
                document = bodyDocument,
                rules = listOf(disabledBodyRule),
                applyBody = true,
                applyCatalog = false
            )
        )
        assertEquals(
            "净化规则正则过于复杂，可能在长 TXT 正文上卡住；请拆成更简单的规则，或改用普通文本匹配",
            txtPurifyRegexCostWarningForDocument(
                document = catalogDocument,
                rules = listOf(catalogRule),
                applyBody = false,
                applyCatalog = true
            )
        )
    }

    @Test
    fun applyTxtPurifyTargetsToDocumentReturnsNoRulesWhenTargetsDisabled() {
        val text = "第1章 标题\n广告\n正文"
        val document = TxtDocument("book.txt", text, "UTF-8", detectTxtChapters(text))

        val result = applyTxtPurifyTargetsToDocument(
            document = document,
            rulesText = "body	广告	false	广告=>",
            applyBody = false,
            applyCatalog = false,
            detectChapters = ::detectTxtChapters
        )

        assertEquals(TxtPurifyApplyResult(hasRules = false, bodyCount = 0, catalogCount = 0), result)
        assertEquals(text, document.text)
        assertEquals(listOf("第1章 标题"), document.chapters.map { it.title })
    }

    @Test
    fun applyTxtPurifyTargetsToDocumentSkipsCatalogReplacementThatWouldBlankTitle() {
        val text = "第1章 标题\n正文"
        val document = TxtDocument("book.txt", text, "UTF-8", detectTxtChapters(text))

        val result = applyTxtPurifyTargetsToDocument(
            document = document,
            rulesText = "catalog\t清空标题\tfalse\t第1章 标题=>",
            applyBody = false,
            applyCatalog = true,
            detectChapters = ::detectTxtChapters
        )

        assertEquals(TxtPurifyApplyResult(hasRules = true, bodyCount = 0, catalogCount = 0), result)
        assertEquals(text, document.text)
        assertEquals(listOf("第1章 标题"), document.chapters.map { it.title })
    }

    @Test
    fun applyTxtPurifyTargetsToDocumentRejectsInvalidRuleWithoutChangingDocument() {
        val text = "第1章 标题\n广告\n正文"
        val document = TxtDocument("book.txt", text, "UTF-8", detectTxtChapters(text))
        val invalidRules = mutableListOf<TxtPurifyRuleItem>()

        val result = applyTxtPurifyTargetsToDocument(
            document = document,
            rulesText = "body	坏规则	true	(=>",
            applyBody = true,
            applyCatalog = false,
            detectChapters = ::detectTxtChapters,
            onInvalidRule = { invalidRules += it }
        )

        assertNull(result)
        assertEquals(listOf("坏规则"), invalidRules.map { it.name })
        assertEquals(text, document.text)
        assertEquals(listOf("第1章 标题"), document.chapters.map { it.title })
    }

    @Test
    fun autoSelectTxtPurifyRulesAfterOpenEnablesOnlyMatchingRules() {
        val text = "第1章 标题\n广告\n正文"
        val document = TxtDocument("book.txt", text, "UTF-8", detectTxtChapters(text))
        val rulesText = """
            body	广告	false	广告=>
            body	无匹配	false	无匹配=>
        """.trimIndent()

        val result = autoSelectTxtPurifyRulesAfterOpenModel(document, rulesText)

        assertTrue(result.success)
        assertEquals(listOf(true, false), parseTxtPurifyRuleItems(result.rulesText).map { it.enabled })
        assertNull(applyTxtPurifyRuleEditState(TxtPurifyRuleEditResult(success = false)))
    }

    @Test
    fun autoSelectTxtPurifyRulesAfterOpenReturnsFailureWhenSelectionDoesNotChange() {
        val text = "第1章 标题\n广告\n正文"
        val document = TxtDocument("book.txt", text, "UTF-8", detectTxtChapters(text))
        val rulesText = """
            body	广告	false	广告=>
            # body	无匹配	false	无匹配=>
        """.trimIndent()

        val result = autoSelectTxtPurifyRulesAfterOpenModel(document, rulesText)

        assertFalse(result.success)
        assertNull(autoSelectedTxtPurifyRuleItemsAfterOpen(document, ""))
    }

    @Test
    fun compileTxtPurifyRulesReturnsNullForInvalidRegex() {
        val invalid = TxtPurifyRuleItem(0, TXT_PURIFY_TARGET_BODY, "bad", "(", "", regex = true, enabled = true)
        val invalidRules = mutableListOf<TxtPurifyRuleItem>()

        assertNull(compileTxtPurifyRules(listOf(invalid)) { rule, _ -> invalidRules += rule })
        assertEquals(listOf(invalid), invalidRules)
        assertFalse(deleteTxtPurifyRuleModel("", 0).success)
    }

    @Test
    fun legacyDisabledRuleLinesAndRegexErrorMessagesAreNormalized() {
        assertTrue(isLegacyDisabledRuleLine("# 旧规则=>新规则"))
        assertFalse(isLegacyDisabledRuleLine("#旧规则=>新规则"))
        assertEquals("旧规则=>新规则", stripLegacyDisabledRulePrefix("#  旧规则=>新规则"))
        assertNull(txtRuleRegexErrorMessage("正文", "(", regex = false))
        assertNull(txtRuleRegexErrorMessage("正文", "", regex = true))
        assertTrue(txtRuleRegexErrorMessage("正文", "(", regex = true)?.startsWith("正文 正则错误：") == true)
    }

    @Test
    fun editTxtPurifyRuleModelsRejectInvalidIndicesAndApplyEditStateForSuccess() {
        val rulesText = addTxtPurifyRuleModel("", TXT_PURIFY_TARGET_BODY, "广告", false, "广告", "").rulesText
        val updated = updateTxtPurifyRuleModel(
            rulesText = rulesText,
            index = 0,
            target = "目录",
            name = "标题",
            regex = false,
            pattern = "旧",
            replacement = "新"
        )

        assertTrue(updated.success)
        assertEquals(TXT_PURIFY_TARGET_CATALOG, updated.changedRule?.target)
        assertEquals(updated.rulesText, applyTxtPurifyRuleEditState(updated)?.rulesText)
        assertFalse(updateTxtPurifyRuleTargetModel(rulesText, 9, TXT_PURIFY_TARGET_CATALOG).success)
        assertFalse(updateTxtPurifyRuleNameModel(rulesText, 9, "新名").success)
        assertFalse(updateTxtPurifyRulePatternModel(rulesText, 9, "新").success)
        assertFalse(updateTxtPurifyRuleReplacementModel(rulesText, 9, "新").success)
        assertFalse(updateTxtPurifyRuleRegexModel(rulesText, 9, true).success)
        assertFalse(updateTxtPurifyRuleEnabledModel(rulesText, 9, false).success)
        assertFalse(deleteTxtPurifyRuleModel(rulesText, 9).success)
        assertFalse(moveTxtPurifyRuleModel(rulesText, 0, 0).success)
        assertFalse(moveTxtPurifyRuleModel(rulesText, 0, 9).success)
    }

    private fun detectTxtChapters(text: String): List<TxtChapter> {
        return ChapterDetector.detectTxtChapters(
            text = text,
            customPatterns = listOf("""^第\s*(\d+)\s*章.*$""")
        )
    }
}
