package com.eteditor

import com.eteditor.core.EpubChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FetchInfoTextUtilsTest {
    @Test
    fun compactAndCleanIntroTextRemoveNoiseBoundariesAndHtml() {
        assertEquals("第一行\n第二 行", " 第一行 \r\n\r\n 第二   行 ".compactLines())
        assertEquals(
            "正文一\n正文二",
            "正文一\n正文二\n内容标签：幻想\n搜索关键字：旧".trimJjwxcIntroBoundary()
        )
        assertEquals(
            "简介第一段\n简介第二段",
            "<div>作品简介</div><p>简介第一段</p><p>简介第二段</p><div>作品目录</div>".cleanGongzicpIntro()
        )
        assertEquals("A & <B> \"C\" 'D'", "A&nbsp;&amp;&nbsp;&lt;B&gt;&nbsp;&quot;C&quot;&nbsp;&#39;D&#39;".decodeHtmlEntities())
    }

    @Test
    fun addUniqueLineAndPresaleTrimKeepOnlyUsefulIntroLines() {
        val lines = mutableListOf<String>()

        addUniqueLine(lines, " 第一行 \n\n 第二行 ")
        addUniqueLine(lines, "第一行\n第二行")
        addUniqueLine(lines, " ")

        assertEquals(listOf("第一行\n第二行"), lines)
        assertEquals(
            "第一段\n第二段\n第三段",
            "第一段\n第二段\n第三段\n预收《新文》\n后续噪音".trimPresaleBlock()
        )
        assertEquals(
            "第一段\n预收正文\n第二段",
            "第一段\n预收正文\n第二段".trimPresaleBlock()
        )
    }

    @Test
    fun cleanIntroBracketLinesOnlyRemovesLeadingNoiseTags() {
        val cleaned = "【更新通知】第一段\n第二段\n第三段\n【预收】正文内保留".cleanIntroBracketLines()

        assertEquals("第一段\n第二段\n第三段\n【预收】正文内保留", cleaned)
    }

    @Test
    fun cleanHtmlBlockRemovesHiddenBlocksAndDecodesNumericEntities() {
        val cleaned = "<script>bad()</script><style>.x{}</style><p>A&#65;</p><div>B&#x4E2D;</div><li>C&nbsp;D</li>"
            .cleanHtmlBlock()

        assertEquals("AA\nB中\nC D", cleaned)
    }

    @Test
    fun trimJjwxcIntroBoundaryOnlyCutsMetadataPrefixesAtLineStart() {
        val cleaned = "第一段\n第二段 内容标签：幻想 搜索关键字：旧\n第三段".trimJjwxcIntroBoundary()

        assertEquals("第一段\n第二段 内容标签：幻想 搜索关键字：旧\n第三段", cleaned)
    }

    @Test
    fun trimJjwxcIntroBoundaryDropsUiLinesBeforeFindingBoundaryMarkers() {
        val cleaned = "展开\n第一段\n点击展开全文\n第二段 霸王排行 123\n第三段".trimJjwxcIntroBoundary()

        assertEquals("第一段\n第二段", cleaned)
    }

    @Test
    fun cleanGongzicpIntroCutsAuthorStatementAndLatePresaleBlock() {
        val cleaned = """
            <div>作品简介</div>
            <p>第一段</p>
            <p>第二段</p>
            <p>第三段</p>
            <p>预收新文</p>
            <p>作者声明</p>
            <p>噪音</p>
        """.trimIndent().cleanGongzicpIntro()

        assertEquals("第一段\n第二段\n第三段", cleaned)
    }

    @Test
    fun writeBackKeepsOriginalPrefixAndAppendsFetchedTitleVerbatim() {
        val item = FetchedCatalogItem(
            index = 1,
            title = "第01章 01aa",
            sequence = "1",
            chapterTitle = "第01章 01aa"
        )

        // 无规则清理时：原章号「第01章」+ 抓取标题原样「第01章 01aa」（不再自动砍前缀）
        assertEquals("第01章 第01章 01aa", fetchInfoCatalogWriteBackTitle("第01章 bb", item))
    }

    @Test
    fun writeBackUsesCleanedFetchedNameWhenAlreadyFiltered() {
        val item = FetchedCatalogItem(index = 1, title = "aa", sequence = "1", chapterTitle = "aa")

        assertEquals("第01章 aa", fetchInfoCatalogWriteBackTitle("第01章 bb", item))
    }

    @Test
    fun writeBackUsesFetchedNameWhenOriginalHasNoPrefix() {
        val item = FetchedCatalogItem(index = 1, title = "aa", sequence = "1", chapterTitle = "aa")

        assertEquals("aa", fetchInfoCatalogWriteBackTitle("楔子", item))
    }

    @Test
    fun writeBackRenderedKeepsPrefixAndUsesFetchedNameForAutoStyle() {
        val item = FetchedCatalogItem(index = 1, title = "新标题", sequence = "1", chapterTitle = "新标题")

        val rendered = fetchInfoCatalogWriteBackRenderedTitle(
            originalTitle = "第9章 旧标题",
            item = item,
            autoStyle = TITLE_FORMAT_STYLE_LEFT
        )

        assertEquals("第9章 新标题", rendered.plainTitle)
        assertEquals("第9章<br/>新标题", rendered.headingHtml)
        assertEquals(TITLE_FORMAT_STYLE_LEFT, rendered.styleCode)
    }

    @Test
    fun writeBackUsesChapterTitleFallbackWhenTitleBlank() {
        val chapterTitleItem = FetchedCatalogItem(
            index = 1,
            title = "",
            sequence = "",
            chapterTitle = "新标题"
        )

        assertEquals("新标题", fetchInfoCatalogWriteBackTitle("旧标题", chapterTitleItem))
        assertEquals("第3章 新标题", fetchInfoCatalogWriteBackTitle("第3章 旧标题", chapterTitleItem))
    }

    @Test
    fun fetchInfoCatalogAutoTitleStyleReturnsNullWhenDisabledOrNoUsableParts() {
        val target = listOf(chapter("第1章 旧标题"))
        val catalog = listOf(FetchedCatalogItem(1, title = "第1章 新标题"))

        assertNull(
            fetchInfoCatalogAutoTitleStyle(
                parameters = fetchParameters(autoTitleFormat = false),
                targetChapters = target,
                catalog = catalog
            )
        )
        assertEquals(
            TITLE_FORMAT_STYLE_LEFT,
            fetchInfoCatalogAutoTitleStyle(
                parameters = fetchParameters(autoTitleFormat = true),
                targetChapters = target,
                catalog = catalog
            )
        )
    }

    @Test
    fun fetchInfoCatalogAutoTitleStyleSkipsVolumeItemsBeforeConsumingTargetChapters() {
        val target = listOf(chapter("第1章 旧标题"), chapter("旧标题"))
        val catalog = listOf(
            FetchedCatalogItem(index = 1, title = "第一卷", isVolume = true),
            FetchedCatalogItem(index = 2, title = "002", sequence = "002"),
            FetchedCatalogItem(index = 3, title = "003", sequence = "003")
        )

        assertEquals(
            TITLE_FORMAT_STYLE_DOUBLE,
            fetchInfoCatalogAutoTitleStyle(
                parameters = fetchParameters(autoTitleFormat = true),
                targetChapters = target,
                catalog = catalog
            )
        )
    }

    @Test
    fun fetchInfoChapterNumberPrefixHandlesChineseAndArabicNumbers() {
        assertEquals("第 十二 章", fetchInfoChapterNumberPrefix("第 十二 章 旧标题"))
        assertEquals("第9章", fetchInfoChapterNumberPrefix("第9章 旧标题"))
        assertEquals("", fetchInfoChapterNumberPrefix("序章"))
    }

    private fun fetchParameters(autoTitleFormat: Boolean): FetchInfoParameters {
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
            catalogFilter = "",
            autoTitleFormat = autoTitleFormat,
            introFilter = "",
            writeCatalog = true,
            writeIntro = false,
            introTargetPath = DEFAULT_FETCH_INFO_INTRO_TARGET,
            writeCover = false
        )
    }

    private fun chapter(title: String): EpubChapter {
        return EpubChapter(
            id = "c1",
            href = "Text/chapter1.xhtml",
            path = "OEBPS/Text/chapter1.xhtml",
            originalPath = "OEBPS/Text/chapter1.xhtml",
            pathAliases = mutableSetOf("OEBPS/Text/chapter1.xhtml"),
            title = title,
            html = "<html><body><h1>$title</h1></body></html>",
            wordCount = title.length
        )
    }
}
