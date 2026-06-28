package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Test

class TxtRuleApplicationUtilsTest {
    @Test
    fun encodeTxtRuleArrowSeparatorReplacesArrowWithPlaceholder() {
        assertEquals("查找\uE000替换", encodeTxtRuleArrowSeparator("查找=>替换"))
    }

    @Test
    fun encodeTxtRuleArrowSeparatorStripsExistingPlaceholderFirst() {
        assertEquals("ab", encodeTxtRuleArrowSeparator("a\uE000b"))
        assertEquals("a\uE000b", encodeTxtRuleArrowSeparator("a=>\uE000b"))
    }

    @Test
    fun decodeTxtRuleArrowSeparatorRestoresArrow() {
        assertEquals("查找=>替换", decodeTxtRuleArrowSeparator("查找\uE000替换"))
    }

    @Test
    fun encodeDecodeRoundTripPreservesArrowContent() {
        val original = "名称含=>分隔=>符号"
        assertEquals(original, decodeTxtRuleArrowSeparator(encodeTxtRuleArrowSeparator(original)))
    }

    @Test
    fun encodeDecodeLeaveValuesWithoutArrowUnchanged() {
        val original = "普通文本"
        assertEquals(original, encodeTxtRuleArrowSeparator(original))
        assertEquals(original, decodeTxtRuleArrowSeparator(original))
    }

    @Test
    fun encodeDecodeHandleEmptyStrings() {
        assertEquals("", encodeTxtRuleArrowSeparator(""))
        assertEquals("", decodeTxtRuleArrowSeparator(""))
    }
}
