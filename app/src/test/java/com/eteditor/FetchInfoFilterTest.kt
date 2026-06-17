package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchInfoFilterTest {
    @Test
    fun applyCatalogFiltersDropsAndReplacesTitlesThenReindexesCatalog() {
        val raw = fetchedInfo(
            catalog = listOf(
                FetchedCatalogItem(10, "第1章 旧标题"),
                FetchedCatalogItem(11, "番外 不写入"),
                FetchedCatalogItem(12, "第2章 旧标题")
            )
        )
        val parameters = fetchParameters(
            catalogFilter = """
                dropContains:番外
                replace:旧=>新
                replaceRegex:第(\d+)章=>Chapter${'$'}1
            """.trimIndent()
        )

        val (filtered, issues) = FetchInfoFilter.apply(raw, parameters)

        assertEquals(listOf("Chapter1 新标题", "Chapter2 新标题"), filtered.catalog.map { it.title })
        assertEquals(listOf(1, 2), filtered.catalog.map { it.index })
        assertEquals(emptyList<FetchInfoFilterIssue>(), issues)
    }

    @Test
    fun applyIntroFiltersTrimsCompressesTruncatesAndReplaces() {
        val raw = fetchedInfo(intro = "  头部\n\n\n正文 旧\n尾部  ")
        val parameters = fetchParameters(
            introFilter = """
                trim
                compressBlankLines
                truncateBefore:正文
                replace:旧=>新
            """.trimIndent()
        )

        val (filtered, issues) = FetchInfoFilter.apply(raw, parameters)

        assertEquals(" 新\n尾部", filtered.intro)
        assertEquals(emptyList<FetchInfoFilterIssue>(), issues)
    }

    @Test
    fun applyStructuredReplacementFiltersCollectsInvalidRules() {
        val raw = fetchedInfo(intro = "旧简介")
        val parameters = fetchParameters(
            introFilter = """[{"name":"替换","search":"旧","replacement":"新"},{"name":"空","search":""}]"""
        )

        val (filtered, issues) = FetchInfoFilter.apply(raw, parameters)

        assertEquals("新简介", filtered.intro)
        assertEquals(1, issues.size)
        assertEquals("查找内容为空", issues.single().reason)
        assertTrue(issues.single().text.contains("空"))
    }

    @Test
    fun applyStructuredCatalogFiltersUseRegexGroupsAndEscapedLineBreaks() {
        val raw = fetchedInfo(
            catalog = listOf(FetchedCatalogItem(9, "第12章 旧\n标题"))
        )
        val parameters = fetchParameters(
            catalogFilter = """[{"name":"序号","search":"第(\\d+)章","replacement":"Chapter${'$'}1","regex":true},{"name":"换行","search":"旧\\n标题","replacement":"新标题"}]"""
        )

        val (filtered, issues) = FetchInfoFilter.apply(raw, parameters)

        assertEquals(listOf("Chapter12 新标题"), filtered.catalog.map { it.title })
        assertEquals(listOf(1), filtered.catalog.map { it.index })
        assertEquals(emptyList<FetchInfoFilterIssue>(), issues)
    }

    @Test
    fun applyUnsupportedFilterRulesReportIssues() {
        val raw = fetchedInfo(catalog = listOf(FetchedCatalogItem(1, "第1章")))
        val parameters = fetchParameters(
            catalogFilter = "unknown:rule",
            introFilter = "unknown:rule"
        )

        val (_, issues) = FetchInfoFilter.apply(raw, parameters)

        assertEquals(listOf("目录过滤规则不支持", "简介过滤规则不支持"), issues.map { it.reason })
    }

    @Test
    fun applyLineCatalogFiltersReportInvalidRulesAndKeepLaterValidDrops() {
        val raw = fetchedInfo(
            catalog = listOf(
                FetchedCatalogItem(10, "第1章 保留"),
                FetchedCatalogItem(11, "番外 删除"),
                FetchedCatalogItem(12, "第2章 保留")
            )
        )
        val parameters = fetchParameters(
            catalogFilter = """
                dropContains:
                dropRegex:(
                replace:=>空
                replaceRegex:第(\d+)章
                dropRegex:番外
            """.trimIndent()
        )

        val (filtered, issues) = FetchInfoFilter.apply(raw, parameters)

        assertEquals(listOf("第1章 保留", "第2章 保留"), filtered.catalog.map { it.title })
        assertEquals(listOf(1, 2), filtered.catalog.map { it.index })
        assertEquals(
            listOf("dropContains 内容为空", "查找内容为空", "替换规则缺少 =>"),
            listOf(issues[0].reason, issues[2].reason, issues[3].reason)
        )
        assertTrue(issues[1].reason.startsWith("正则错误："))
    }

    @Test
    fun applyLineIntroFiltersReportInvalidRulesAndKeepLaterValidReplacements() {
        val raw = fetchedInfo(intro = "头部 旧 尾部")
        val parameters = fetchParameters(
            introFilter = """
                truncateBefore:
                replace:=>空
                replaceRegex:(=>坏
                replace:旧=>新
            """.trimIndent()
        )

        val (filtered, issues) = FetchInfoFilter.apply(raw, parameters)

        assertEquals("头部 新 尾部", filtered.intro)
        assertEquals(3, issues.size)
        assertEquals("truncateBefore 内容为空", issues[0].reason)
        assertEquals("查找内容为空", issues[1].reason)
        assertTrue(issues[2].reason.startsWith("正则错误："))
    }

    @Test
    fun applyStructuredIntroFiltersReportNonObjectAndInvalidRegexButKeepValidRules() {
        val raw = fetchedInfo(intro = "旧简介")
        val parameters = fetchParameters(
            introFilter = """["bad",{"name":"坏正则","search":"(","replacement":"x","regex":true},{"name":"替换","search":"旧","replacement":"新"}]"""
        )

        val (filtered, issues) = FetchInfoFilter.apply(raw, parameters)

        assertEquals("新简介", filtered.intro)
        assertEquals(2, issues.size)
        assertEquals("过滤规则格式错误", issues[0].reason)
        assertTrue(issues[1].reason.startsWith("正则错误："))
        assertEquals("坏正则", issues[1].text)
    }

    @Test
    fun applyMalformedStructuredFiltersKeepOriginalContentAndReportFormatIssue() {
        val raw = fetchedInfo(
            intro = "旧简介",
            catalog = listOf(FetchedCatalogItem(9, "旧标题"))
        )
        val parameters = fetchParameters(
            catalogFilter = """[{"search":"旧","replacement":"新"}""",
            introFilter = """[{"search":"旧","replacement":"新"}"""
        )

        val (filtered, issues) = FetchInfoFilter.apply(raw, parameters)

        assertEquals(listOf("旧标题"), filtered.catalog.map { it.title })
        assertEquals("旧简介", filtered.intro)
        assertEquals(listOf(1), filtered.catalog.map { it.index })
        assertEquals(2, issues.size)
        assertTrue(issues.all { it.reason.startsWith("过滤规则格式错误：") })
    }

    @Test
    fun structuredRulesApplyChapterCategoryBeforePurifyAndSupportDropAndEnabled() {
        val filter = """
            [
              {"name":"去序号","search":"^\\d+\\s+","replacement":"","regex":true,"category":"净化"},
              {"name":"滤单章","search":"单章","action":"drop","category":"章节"},
              {"name":"停用","search":"aa","replacement":"ZZ","regex":false,"category":"净化","enabled":false}
            ]
        """.trimIndent()
        val raw = fetchedInfo(
            catalog = listOf(
                FetchedCatalogItem(1, "01 aa"),
                FetchedCatalogItem(2, "单章 番外")
            )
        )

        val (filtered, issues) = FetchInfoFilter.apply(raw, fetchParameters(catalogFilter = filter))

        // 章节类(drop 单章)先生效移除"单章 番外"；净化类(去前导序号)把"01 aa"清成"aa"；停用规则不生效。
        assertEquals(listOf("aa"), filtered.catalog.map { it.title })
        assertTrue(issues.isEmpty())
    }

    private fun fetchParameters(
        catalogFilter: String = "",
        introFilter: String = ""
    ): FetchInfoParameters {
        return FetchInfoParameters(
            source = FETCH_INFO_SOURCE_JJWXC,
            searchMode = FETCH_INFO_SEARCH_TITLE,
            query = "Book",
            content = FETCH_INFO_CONTENT_CATALOG,
            fetchCatalog = true,
            fetchIntro = false,
            fetchCover = false,
            authCookie = "",
            bodyRangeStart = 1,
            bodyRangeEnd = 0,
            catalogFilter = catalogFilter,
            autoTitleFormat = false,
            introFilter = introFilter,
            writeCatalog = true,
            writeIntro = false,
            introTargetPath = DEFAULT_FETCH_INFO_INTRO_TARGET,
            writeCover = false
        )
    }

    private fun fetchedInfo(
        intro: String = "",
        catalog: List<FetchedCatalogItem> = emptyList()
    ): FetchedInfo {
        return FetchedInfo(
            source = FETCH_INFO_SOURCE_JJWXC,
            query = "Book",
            resolvedUrl = "https://example.com/book",
            title = "Book",
            author = "Author",
            intro = intro,
            coverUrl = "",
            catalog = catalog
        )
    }
}
