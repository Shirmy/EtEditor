package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextReplaceStringUtilsTest {
    @Test
    fun buildTextReplaceRulesSingleModeDecodesLineBreaksAndVisibleTarget() {
        val result = buildTextReplaceRules(
            parameters = parameters(
                mode = TEXT_REPLACE_MODE_SINGLE,
                target = TEXT_REPLACE_TARGET_VISIBLE,
                findText = "第一行\\n第二行",
                replaceText = "替换\\r\\n结果",
                findRegexEnabled = true
            ),
            singleMode = TEXT_REPLACE_MODE_SINGLE,
            replacementMode = TEXT_REPLACE_MODE_REPLACEMENT,
            visibleTextTarget = TEXT_REPLACE_TARGET_VISIBLE
        )

        assertEquals(
            listOf(
                TextReplaceRule(
                    find = "第一行\n第二行",
                    replacement = "替换\r\n结果",
                    regex = true,
                    textOnly = true
                )
            ),
            result.rules
        )
    }

    @Test
    fun buildTextReplaceRulesSingleModeDropsEmptyFindText() {
        val result = buildTextReplaceRules(
            parameters = parameters(
                mode = TEXT_REPLACE_MODE_SINGLE,
                findText = "",
                replaceText = "替换",
                findRegexEnabled = false
            ),
            singleMode = TEXT_REPLACE_MODE_SINGLE,
            replacementMode = TEXT_REPLACE_MODE_REPLACEMENT,
            visibleTextTarget = TEXT_REPLACE_TARGET_VISIBLE
        )

        assertEquals(emptyList<TextReplaceRule>(), result.rules)
        assertEquals("", result.message)
    }

    @Test
    fun buildTextReplaceRulesReadsStructuredBatchJson() {
        val result = buildTextReplaceRules(
            parameters = parameters(
                mode = TEXT_REPLACE_MODE_BATCH,
                batchText = """[{"search":"foo","replacement":"bar","regex":false,"textOnly":true},{"search":""}]"""
            ),
            singleMode = TEXT_REPLACE_MODE_SINGLE,
            replacementMode = TEXT_REPLACE_MODE_REPLACEMENT,
            visibleTextTarget = TEXT_REPLACE_TARGET_VISIBLE
        )

        assertEquals(
            listOf(
                TextReplaceRule(
                    find = "foo",
                    replacement = "bar",
                    regex = false,
                    textOnly = true
                )
            ),
            result.rules
        )
        assertEquals("", result.message)
    }

    @Test
    fun buildTextReplaceRulesReportsEmptyStructuredBatchWhenNoSearchTextExists() {
        val result = buildTextReplaceRules(
            parameters = parameters(
                mode = TEXT_REPLACE_MODE_BATCH,
                batchText = """[{"replacement":"bar"},{"search":""},null]"""
            ),
            singleMode = TEXT_REPLACE_MODE_SINGLE,
            replacementMode = TEXT_REPLACE_MODE_REPLACEMENT,
            visibleTextTarget = TEXT_REPLACE_TARGET_VISIBLE
        )

        assertEquals(emptyList<TextReplaceRule>(), result.rules)
        assertEquals("请输入批量文本", result.message)
    }

    @Test
    fun buildTextReplaceRulesRejectsMalformedBatchText() {
        val result = buildTextReplaceRules(
            parameters = parameters(
                mode = TEXT_REPLACE_MODE_BATCH,
                batchText = "foo -> bar"
            ),
            singleMode = TEXT_REPLACE_MODE_SINGLE,
            replacementMode = TEXT_REPLACE_MODE_REPLACEMENT,
            visibleTextTarget = TEXT_REPLACE_TARGET_VISIBLE
        )

        assertNull(result.rules)
        assertEquals("批量规则格式错误", result.message)
    }

    @Test
    fun buildTextReplaceRulesRejectsMalformedStructuredBatchJson() {
        val result = buildTextReplaceRules(
            parameters = parameters(
                mode = TEXT_REPLACE_MODE_BATCH,
                batchText = """[{"search":"foo","replacement":"bar"}"""
            ),
            singleMode = TEXT_REPLACE_MODE_SINGLE,
            replacementMode = TEXT_REPLACE_MODE_REPLACEMENT,
            visibleTextTarget = TEXT_REPLACE_TARGET_VISIBLE
        )

        assertNull(result.rules)
        assertEquals("批量规则格式错误", result.message)
    }

    @Test
    fun buildTextReplaceRulesForParametersWrapsLineBatchAndReplacementModes() {
        val batch = buildTextReplaceRulesForParameters(
            parameters(
                mode = TEXT_REPLACE_MODE_BATCH,
                target = TEXT_REPLACE_TARGET_VISIBLE,
                findRegexEnabled = true,
                batchText = "foo=>bar\nbaz=>qux"
            )
        )
        val replacement = buildTextReplaceRulesForParameters(
            parameters(mode = TEXT_REPLACE_MODE_REPLACEMENT)
        )

        assertEquals(
            listOf(
                TextReplaceRule(find = "foo", replacement = "bar", regex = true, textOnly = true),
                TextReplaceRule(find = "baz", replacement = "qux", regex = true, textOnly = true)
            ),
            batch.rules
        )
        assertNull(replacement.rules)
        assertEquals("请先生成 .replacement 分组预览", replacement.message)
    }

    @Test
    fun textReplaceParametersDetectReplacementModeAndLegacyBatchFileMode() {
        assertTrue(parameters(mode = TEXT_REPLACE_MODE_REPLACEMENT).isReplacementMode())
        assertTrue(
            parameters(
                mode = TEXT_REPLACE_MODE_BATCH,
                batchSource = TEXT_REPLACE_BATCH_FILE
            ).isReplacementMode()
        )
        assertEquals(false, parameters(mode = TEXT_REPLACE_MODE_BATCH).isReplacementMode())
    }

    @Test
    fun replaceInStringSupportsPlainCaseInsensitiveNonOverlappingMatches() {
        val (text, count) = replaceInString(
            source = "Aa aa aaa",
            rule = TextReplaceRule(find = "aa", replacement = "X", regex = false),
            caseSensitive = false
        )

        assertEquals("X X Xa", text)
        assertEquals(3, count)
    }

    @Test
    fun replaceInStringExpandsRegexGroupsAndPaddedNumberTokens() {
        val (text, count) = replaceInString(
            source = "第十二章 第3章",
            rule = TextReplaceRule(
                find = """第([一二三四五六七八九十\d]+)章""",
                replacement = "Chapter{z3:1}-\$1",
                regex = true
            ),
            caseSensitive = true
        )

        assertEquals("Chapter012-十二 Chapter003-3", text)
        assertEquals(2, count)
    }

    @Test
    fun replaceInStringRegexReplacementHandlesEscapedDollarAndMissingGroups() {
        val (text, count) = replaceInString(
            source = "A12",
            rule = TextReplaceRule(
                find = """([A-Z])(\d+)""",
                replacement = "\\$-\$1-\$3-{z2:2}",
                regex = true
            ),
            caseSensitive = true
        )

        assertEquals("$-A--12", text)
        assertEquals(1, count)
    }

    @Test
    fun replaceVisibleTextInMarkupDoesNotReplaceTagAttributes() {
        val (text, count) = replaceVisibleTextInMarkup(
            source = """<p title="foo">foo<span>foo</span></p>""",
            rule = TextReplaceRule(find = "foo", replacement = "bar", regex = false),
            caseSensitive = true
        )

        assertEquals("""<p title="foo">bar<span>bar</span></p>""", text)
        assertEquals(2, count)
    }

    @Test
    fun replaceVisibleTextInMarkupReturnsOriginalWhenNoVisibleMatch() {
        val source = """<p title="foo">bar<span>baz</span></p>"""

        val (text, count) = replaceVisibleTextInMarkup(
            source = source,
            rule = TextReplaceRule(find = "foo", replacement = "qux", regex = false),
            caseSensitive = true
        )

        assertEquals(source, text)
        assertEquals(0, count)
    }

    @Test
    fun parseReplacementRulesReadsRegexPlainAndSkippedRules() {
        val (rules, skipped) = parseReplacementRules(
            "\uFEFFfoo#->#bar\n*literal\\n#->#line\\n2\nbad line\n#->#empty"
        )

        assertEquals(
            listOf(
                ParsedReplacementRule(lineNo = 1, pattern = "foo", replacement = "bar", regex = true),
                ParsedReplacementRule(lineNo = 2, pattern = "literal\n", replacement = "line\n2", regex = false)
            ),
            rules
        )
        assertEquals(2, skipped.size)
        assertEquals("缺少 #-># 分隔符", skipped[0].reason)
        assertEquals("查找内容为空", skipped[1].reason)
    }

    @Test
    fun parseReplacementRulesSkipsLinesWithMultipleSeparators() {
        val (rules, skipped) = parseReplacementRules("foo#->#bar#->#baz")

        assertEquals(emptyList<ParsedReplacementRule>(), rules)
        assertEquals(1, skipped.size)
        assertEquals("包含多个 #-># 分隔符", skipped.single().reason)
    }

    @Test
    fun parseReplacementRulesIgnoreBlankLinesButKeepSourceLineNumbers() {
        val (rules, skipped) = parseReplacementRules("\nfoo#->#bar\n   \nbad line\n*baz#->#qux")

        assertEquals(
            listOf(
                ParsedReplacementRule(lineNo = 2, pattern = "foo", replacement = "bar", regex = true),
                ParsedReplacementRule(lineNo = 5, pattern = "baz", replacement = "qux", regex = false)
            ),
            rules
        )
        assertEquals(
            listOf(ReplacementSkippedRule(lineNo = 4, reason = "缺少 #-># 分隔符", text = "bad line")),
            skipped
        )
    }

    @Test
    fun buildTextReplaceRulesFromReplacementFileReportsWhenNoValidRules() {
        val result = buildTextReplaceRulesFromReplacementFileText("bad line\n#->#empty")

        assertNull(result.rules)
        assertTrue(result.message.startsWith("没有有效规则"))
    }

    @Test
    fun buildTextReplaceRulesFromReplacementFileKeepsValidRulesWhenRegexLineIsInvalid() {
        val result = buildTextReplaceRulesFromReplacementFileText(
            "foo#->#bar\n[bad#->#ignored\n*baz#->#qux"
        )

        assertEquals(
            listOf(
                TextReplaceRule(find = "foo", replacement = "bar", regex = true, textOnly = false, caseSensitive = true),
                TextReplaceRule(find = "baz", replacement = "qux", regex = false, textOnly = false, caseSensitive = true)
            ),
            result.rules
        )
        assertEquals("", result.message)
    }

    @Test
    fun buildTextReplaceRulesFromReplacementFileReportsWhenRuleCountExceedsLimit() {
        val tooManyRules = (1..(REPLACEMENT_RULE_MAX_COUNT + 1))
            .joinToString("\n") { index -> "*foo$index#->#bar" }

        val result = buildTextReplaceRulesFromReplacementFileText(tooManyRules)

        assertNull(result.rules)
        assertEquals("规则数量过多，最多支持 $REPLACEMENT_RULE_MAX_COUNT 条", result.message)
    }

    @Test
    fun parsedReplacementRulesBecomeSourceReplacementRules() {
        val rules = textReplaceRulesFromParsedReplacementRules(
            listOf(
                ParsedReplacementRule(lineNo = 1, pattern = "foo", replacement = "bar", regex = false),
                ParsedReplacementRule(lineNo = 2, pattern = "第(\\d+)章", replacement = "第\$1节", regex = true)
            )
        )

        assertEquals(
            listOf(
                TextReplaceRule(find = "foo", replacement = "bar", regex = false, textOnly = false, caseSensitive = true),
                TextReplaceRule(find = "第(\\d+)章", replacement = "第\$1节", regex = true, textOnly = false, caseSensitive = true)
            ),
            rules
        )
    }

    private fun parameters(
        mode: String = TEXT_REPLACE_MODE_SINGLE,
        target: String = TEXT_REPLACE_TARGET_SOURCE,
        scope: String = TOOL_SCOPE_ALL,
        findText: String = "",
        replaceText: String = "",
        findRegexEnabled: Boolean = false,
        batchText: String = "",
        batchSource: String = ""
    ): TextReplaceParameters {
        return TextReplaceParameters(
            mode = mode,
            target = target,
            scope = scope,
            selectedHtmlSourceIndices = emptySet(),
            matchPattern = "",
            matchRegexEnabled = true,
            findText = findText,
            replaceText = replaceText,
            findRegexEnabled = findRegexEnabled,
            batchSource = batchSource,
            batchText = batchText,
            batchFile = "",
            preview = true
        )
    }
}
