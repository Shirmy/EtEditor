package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Test

class FetchInfoJsonUtilsTest {
    @Test
    fun findJsonStringValueDecodesEscapesAndCleansHtml() {
        val text = """{"intro":"<p>第一行\n第二&nbsp;&amp;&nbsp;三行</p>"}"""

        assertEquals(
            "第一行\n第二 & 三行",
            findJsonStringValue(text, "intro")
        )
    }

    @Test
    fun findJsonStringValueTriesKeysInOrderAndIgnoresBlankValues() {
        val text = """{"summary":"","intro":"正文"}"""

        assertEquals("正文", findJsonStringValue(text, "summary", "intro"))
    }

    @Test
    fun findJsonStringValueFallsBackToRecursiveValueWhenDirectMatchIsBlank() {
        val text = """{"intro":"","data":{"intro":"<p>嵌套&nbsp;正文</p>"}}"""

        assertEquals("嵌套 正文", findJsonStringValue(text, "intro"))
    }

    @Test
    fun findJsonStringValueMatchesDirectKeysCaseInsensitively() {
        val text = """{"Title":"<b>作品&nbsp;名</b>"}"""

        assertEquals("作品 名", findJsonStringValue(text, "title"))
    }

    @Test
    fun findJsonStringValueDecodesUnicodeEscapedQuotesAndSkipsBlankArrayItems() {
        val html = """
            <script type="application/ld+json">
              {"items":[{"name":""},{"Name":"\u4f5c\u54c1 \"A\" &amp; B"}]}
            </script>
        """.trimIndent()

        assertEquals("作品 \"A\" & B", findJsonStringValue(html, "name"))
    }

    @Test
    fun findJsonRawStringValueKeepsHtmlMarkupFromDirectJsonMatch() {
        val text = """{"raw":"<p>第一段&nbsp;</p>"}"""

        assertEquals("<p>第一段&nbsp;</p>", findJsonRawStringValue(text, "raw"))
    }

    @Test
    fun findJsonStringValueReadsNestedScriptJsonObjects() {
        val html = """
            <html>
              <script id="__NEXT_DATA__" type="application/json">
                {"props":{"pageProps":{"book":{"intro":"<div>嵌套简介&nbsp;&amp;&nbsp;正文</div>"}}}}
              </script>
            </html>
        """.trimIndent()

        assertEquals("嵌套简介 & 正文", findJsonStringValue(html, "intro"))
    }

    @Test
    fun findJsonStringValueReadsLdJsonAndSkipsMalformedScriptJson() {
        val html = """
            <html>
              <script id="__NEXT_DATA__" type="application/json">{bad json</script>
              <script type="application/ld+json">
                {"book":{"description":"<p>结构化简介&nbsp;&amp;&nbsp;正文</p>"}}
              </script>
            </html>
        """.trimIndent()

        assertEquals("结构化简介 & 正文", findJsonStringValue(html, "description"))
    }

    @Test
    fun findJsonRawStringValueKeepsRawMarkupFromScriptDirectMatch() {
        val html = """
            <script type="application/ld+json">
              {"book":{"raw":"<p>递归&nbsp;正文</p>"}}
            </script>
        """.trimIndent()

        assertEquals("<p>递归&nbsp;正文</p>", findJsonRawStringValue(html, "raw"))
    }

    @Test
    fun findJsonRawStringValueFallsBackToCleanedValueWhenDirectMatchIsBlank() {
        val text = """{"raw":"","data":{"raw":"<p>递归&nbsp;正文</p>"}}"""

        assertEquals("递归 正文", findJsonRawStringValue(text, "raw"))
    }

    @Test
    fun parseMetaReadsNameOrPropertyAndCleansContent() {
        assertEquals(
            "作品名",
            parseMeta("""<meta name="book:title" content="作品名">""", "book:title")
        )
        assertEquals(
            "书名 & 作者",
            parseMeta(
                """<meta content="书名&nbsp;&amp;&nbsp;作者" property="og:title">""",
                "og:title"
            )
        )
    }

    @Test
    fun parseMetaReadsSingleQuotedCaseInsensitiveAttributes() {
        assertEquals(
            "单引号简介",
            parseMeta("""<META PROPERTY='og:description' CONTENT='单引号简介'>""", "og:description")
        )
    }

    @Test
    fun parseMetaCleansHtmlMarkupAndEntitiesFromDirectMatch() {
        assertEquals(
            "书名 & 作者",
            parseMeta(
                """<meta property="og:title" content="<b>书名&nbsp;&amp;&nbsp;作者</b>">""",
                "og:title"
            )
        )
    }

    @Test
    fun parseMetaMatchesAttributesInAnyOrderAndIgnoresMissingContent() {
        assertEquals(
            "简介 & 文案",
            parseMeta(
                """<meta content="简介&nbsp;&amp;&nbsp;文案" data-x="1" name="description">""",
                "description"
            )
        )
        assertEquals("", parseMeta("""<meta name="description">""", "description"))
    }

    @Test
    fun parseMetaUsesLaterMatchingTagWhenEarlierContentIsMissing() {
        val html = """
            <meta name="description">
            <meta name="description" content="后续简介">
        """.trimIndent()

        assertEquals("后续简介", parseMeta(html, "description"))
    }
}
