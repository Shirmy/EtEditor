package com.eteditor

import com.eteditor.core.DocumentKind
import org.junit.Assert.assertEquals
import org.junit.Test

class TextReplaceMessageUtilsTest {
    @Test
    fun regexErrorMessageUsesFirstDetailLineOrFallback() {
        assertEquals(
            "查找正则错误：Dangling meta character",
            textReplaceRegexErrorMessage(
                IllegalArgumentException("Dangling meta character\nnear index 1")
            )
        )
        assertEquals("查找正则错误", textReplaceRegexErrorMessage(IllegalArgumentException()))
    }

    @Test
    fun searchFoundMessageReportsMatchCount() {
        assertEquals("找到 3 处", textSearchFoundMessage(3))
    }

    @Test
    fun noMatchMessageKeepsScopeOrMatchRuleStatus() {
        assertEquals(
            "匹配规则无效",
            textReplaceNoMatchMessage(
                documentKind = DocumentKind.Txt,
                currentStatusMessage = "匹配规则无效",
                parameters = parameters(findText = "needle")
            )
        )
        assertEquals(
            "作用范围内没有可处理章节",
            textReplaceNoMatchMessage(
                documentKind = DocumentKind.Epub,
                currentStatusMessage = "作用范围内没有可处理章节",
                parameters = parameters(
                    target = TEXT_REPLACE_TARGET_SOURCE,
                    findText = "<section>"
                )
            )
        )
    }

    @Test
    fun noMatchMessageExplainsSourceHtmlTagSearch() {
        assertEquals(
            "无匹配内容",
            textReplaceNoMatchMessage(
                documentKind = DocumentKind.Epub,
                currentStatusMessage = "",
                parameters = parameters(
                    target = TEXT_REPLACE_TARGET_SOURCE,
                    findText = "<section>"
                )
            )
        )
        assertEquals(
            "无匹配内容",
            textReplaceNoMatchMessage(
                documentKind = DocumentKind.Epub,
                currentStatusMessage = "",
                parameters = parameters(
                    target = TEXT_REPLACE_TARGET_VISIBLE,
                    findText = "<section>"
                )
            )
        )
    }

    @Test
    fun noMatchMessageFallsBackToGenericForPlainLineBreakSearch() {
        assertEquals(
            "无匹配内容",
            textReplaceNoMatchMessage(
                documentKind = DocumentKind.Txt,
                currentStatusMessage = "",
                parameters = parameters(findText = "第一行\\n第二行")
            )
        )
        assertEquals(
            "无匹配内容",
            textReplaceNoMatchMessage(
                documentKind = DocumentKind.Txt,
                currentStatusMessage = "",
                parameters = parameters(findText = "第一行\\r\\n第二行")
            )
        )
        assertEquals(
            "无匹配内容",
            textReplaceNoMatchMessage(
                documentKind = DocumentKind.Txt,
                currentStatusMessage = "",
                parameters = parameters(findText = "第一行\n第二行")
            )
        )
        assertEquals(
            "无匹配内容",
            textReplaceNoMatchMessage(
                documentKind = DocumentKind.Txt,
                currentStatusMessage = "",
                parameters = parameters(findText = "第一行\r\n第二行")
            )
        )
    }

    @Test
    fun noMatchMessageFallsBackToGenericWhenRegexIsAlreadyEnabledForLineBreakSearch() {
        assertEquals(
            "无匹配内容",
            textReplaceNoMatchMessage(
                documentKind = DocumentKind.Txt,
                currentStatusMessage = "",
                parameters = parameters(
                    findText = "第一行\\n第二行",
                    findRegexEnabled = true
                )
            )
        )
        assertEquals(
            "无匹配内容",
            textReplaceNoMatchMessage(
                documentKind = DocumentKind.Txt,
                currentStatusMessage = "",
                parameters = parameters(
                    findText = "第一行\n第二行",
                    findRegexEnabled = true
                )
            )
        )
    }

    @Test
    fun noMatchMessageDoesNotHintRegexWhenLineBreakRuleAlreadyUsesRegex() {
        assertEquals(
            "无匹配内容",
            textReplaceNoMatchMessage(
                documentKind = DocumentKind.Txt,
                currentStatusMessage = "",
                parameters = parameters(
                    findText = "第一行\\n第二行",
                    findRegexEnabled = false
                ),
                rules = listOf(
                    TextReplaceRule(
                        find = "第一行\\n第二行",
                        replacement = "",
                        regex = true
                    )
                )
            )
        )
    }

    @Test
    fun noMatchMessageUsesRuleLineBreakSearchBeforeParameterTextWithoutRegexHint() {
        assertEquals(
            "无匹配内容",
            textReplaceNoMatchMessage(
                documentKind = DocumentKind.Txt,
                currentStatusMessage = "",
                parameters = parameters(findText = "plain"),
                rules = listOf(
                    TextReplaceRule(find = "", replacement = "", regex = false),
                    TextReplaceRule(find = "第一行\r\n第二行", replacement = "", regex = false)
                )
            )
        )
    }

    @Test
    fun noMatchMessageUsesFirstNonBlankRuleBeforeParameterText() {
        assertEquals(
            "无匹配内容",
            textReplaceNoMatchMessage(
                documentKind = DocumentKind.Epub,
                currentStatusMessage = "",
                parameters = parameters(
                    target = TEXT_REPLACE_TARGET_SOURCE,
                    findText = "plain text",
                    findRegexEnabled = false
                ),
                rules = listOf(
                    TextReplaceRule(find = "", replacement = "", regex = false),
                    TextReplaceRule(find = "<p>", replacement = "", regex = false)
                )
            )
        )
        assertEquals(
            "无匹配内容",
            textReplaceNoMatchMessage(
                documentKind = DocumentKind.Txt,
                currentStatusMessage = "",
                parameters = parameters(findText = "第一行\\n第二行"),
                rules = listOf(TextReplaceRule(find = "plain", replacement = "", regex = false))
            )
        )
    }

    private fun parameters(
        target: String = TEXT_REPLACE_TARGET_SOURCE,
        scope: String = TOOL_SCOPE_ALL,
        findText: String = "",
        findRegexEnabled: Boolean = false
    ): TextReplaceParameters {
        return TextReplaceParameters(
            mode = TEXT_REPLACE_MODE_SINGLE,
            target = target,
            scope = scope,
            selectedHtmlSourceIndices = emptySet(),
            matchPattern = "",
            matchRegexEnabled = true,
            findText = findText,
            replaceText = "",
            findRegexEnabled = findRegexEnabled,
            batchSource = "",
            batchText = "",
            batchFile = "",
            preview = true
        )
    }
}
