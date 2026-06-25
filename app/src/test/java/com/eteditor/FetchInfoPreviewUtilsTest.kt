package com.eteditor

import com.eteditor.core.DocumentKind
import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.ManifestItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchInfoPreviewUtilsTest {
    @Test
    fun defaultFetchInfoQueryAndCoverTitleComeFromDocumentKind() {
        assertEquals(
            "元数据书名",
            defaultFetchInfoQueryForDocument(
                kind = DocumentKind.Epub,
                searchMode = FETCH_INFO_SEARCH_TITLE,
                epubMetadataTitle = "元数据书名",
                epubMetadataAuthor = "作者",
                title = "book.epub",
                authorSearchMode = FETCH_INFO_SEARCH_AUTHOR,
                keywordSearchMode = FETCH_INFO_SEARCH_KEYWORD
            )
        )
        assertEquals(
            "作者",
            defaultFetchInfoQueryForDocument(
                kind = DocumentKind.Epub,
                searchMode = FETCH_INFO_SEARCH_AUTHOR,
                epubMetadataTitle = "元数据书名",
                epubMetadataAuthor = "作者",
                title = "book.epub",
                authorSearchMode = FETCH_INFO_SEARCH_AUTHOR,
                keywordSearchMode = FETCH_INFO_SEARCH_KEYWORD
            )
        )
        assertEquals("book", defaultCoverTitleForDocument(DocumentKind.Txt, "", "book.txt"))
    }

    @Test
    fun defaultFetchInfoQueryUsesKeywordBlankAndFileNameFallbacks() {
        assertEquals(
            "",
            defaultFetchInfoQueryForDocument(
                kind = DocumentKind.Epub,
                searchMode = FETCH_INFO_SEARCH_KEYWORD,
                epubMetadataTitle = "元数据书名",
                epubMetadataAuthor = "作者",
                title = "book.epub",
                authorSearchMode = FETCH_INFO_SEARCH_AUTHOR,
                keywordSearchMode = FETCH_INFO_SEARCH_KEYWORD
            )
        )
        assertEquals(
            "book",
            defaultFetchInfoQueryForDocument(
                kind = DocumentKind.Epub,
                searchMode = FETCH_INFO_SEARCH_TITLE,
                epubMetadataTitle = "",
                epubMetadataAuthor = "",
                title = "dir/book.epub",
                authorSearchMode = FETCH_INFO_SEARCH_AUTHOR,
                keywordSearchMode = FETCH_INFO_SEARCH_KEYWORD
            )
        )
        assertEquals("", defaultFetchInfoQueryForDocument(DocumentKind.None, "", "", "", "", "", ""))
        assertEquals("", defaultCoverTitleForDocument(DocumentKind.None, "Book", "book.txt"))
    }

    @Test
    fun defaultFetchInfoQueryAndCoverTitleHandleTxtAndBlankEpubMetadataEdges() {
        assertEquals(
            "book",
            defaultFetchInfoQueryForDocument(
                kind = DocumentKind.Txt,
                searchMode = FETCH_INFO_SEARCH_AUTHOR,
                epubMetadataTitle = "",
                epubMetadataAuthor = "",
                title = "dir/book.txt",
                authorSearchMode = FETCH_INFO_SEARCH_AUTHOR,
                keywordSearchMode = FETCH_INFO_SEARCH_KEYWORD
            )
        )
        assertEquals(
            ".txt",
            defaultFetchInfoQueryForDocument(
                kind = DocumentKind.Txt,
                searchMode = FETCH_INFO_SEARCH_TITLE,
                epubMetadataTitle = "",
                epubMetadataAuthor = "",
                title = ".txt",
                authorSearchMode = FETCH_INFO_SEARCH_AUTHOR,
                keywordSearchMode = FETCH_INFO_SEARCH_KEYWORD
            )
        )
        assertEquals("", defaultCoverTitleForDocument(DocumentKind.Epub, "", "fallback.epub"))
    }

    @Test
    fun buildFetchInfoCatalogSummaryReportsMoreLessOrEqualCounts() {
        assertEquals(
            "原章节 1 章  抓取章节 2 章",
            buildFetchInfoCatalogSummary(1, previewWithCatalog(nonVolumeCount = 2))
        )
        assertEquals(
            "原章节 3 章  抓取章节 1 章",
            buildFetchInfoCatalogSummary(3, previewWithCatalog(nonVolumeCount = 1))
        )
        assertEquals(
            "原章节 2 章  抓取章节 2 章",
            buildFetchInfoCatalogSummary(2, previewWithCatalog(nonVolumeCount = 2))
        )
    }

    @Test
    fun fetchInfoParametersForSourceModelResetsWriteFlagsAndSosadCookie() {
        val base = fetchParameters(
            source = FETCH_INFO_SOURCE_JJWXC,
            content = FETCH_INFO_CONTENT_INTRO,
            authCookie = ""
        )

        val sosad = fetchInfoParametersForSourceModel(
            base = base,
            source = FETCH_INFO_SOURCE_SOSAD,
            defaultTitleQuery = "Book",
            sosadLoginCookie = "cookie"
        )
        val jjwxc = fetchInfoParametersForSourceModel(
            base = base,
            source = FETCH_INFO_SOURCE_JJWXC,
            defaultTitleQuery = "Book",
            sosadLoginCookie = "cookie"
        )

        assertEquals(FETCH_INFO_SOURCE_SOSAD, sosad.source)
        assertEquals("cookie", sosad.authCookie)
        assertTrue(sosad.fetchIntro)
        assertTrue(sosad.writeIntro)
        assertEquals("", jjwxc.authCookie)
    }

    @Test
    fun fetchInfoParametersForSourceModelPrefersExistingSosadCookieAndCatalogFlags() {
        val base = fetchParameters(
            source = FETCH_INFO_SOURCE_SOSAD,
            content = FETCH_INFO_CONTENT_CATALOG,
            authCookie = "existing-cookie"
        )

        val parameters = fetchInfoParametersForSourceModel(
            base = base,
            source = FETCH_INFO_SOURCE_SOSAD,
            defaultTitleQuery = "Book",
            sosadLoginCookie = "login-cookie"
        )

        assertEquals("existing-cookie", parameters.authCookie)
        assertTrue(parameters.fetchCatalog)
        assertTrue(parameters.writeCatalog)
        assertFalse(parameters.fetchIntro)
        assertFalse(parameters.writeIntro)
        assertFalse(parameters.fetchCover)
        assertFalse(parameters.writeCover)
    }

    @Test
    fun fetchInfoParametersForSourceModelResetsSearchModeAndQueryForSosadSource() {
        val base = fetchParameters(
            source = FETCH_INFO_SOURCE_JJWXC,
            content = FETCH_INFO_CONTENT_CATALOG,
            authCookie = ""
        ).copy(
            searchMode = FETCH_INFO_SEARCH_KEYWORD,
            query = "https://example.com/retry"
        )

        val parameters = fetchInfoParametersForSourceModel(
            base = base,
            source = FETCH_INFO_SOURCE_SOSAD,
            defaultTitleQuery = "默认书名",
            sosadLoginCookie = "login-cookie"
        )

        assertEquals(FETCH_INFO_SOURCE_SOSAD, parameters.source)
        assertEquals(FETCH_INFO_SEARCH_TITLE, parameters.searchMode)
        assertEquals("默认书名", parameters.query)
        assertEquals("login-cookie", parameters.authCookie)
        assertTrue(parameters.fetchCatalog)
        assertTrue(parameters.writeCatalog)
    }

    @Test
    fun fetchInfoParametersForSourceModelUsesCoverFlagsAndClearsNonSosadCookies() {
        val base = fetchParameters(
            source = FETCH_INFO_SOURCE_SOSAD,
            content = FETCH_INFO_CONTENT_COVER,
            authCookie = "old-cookie"
        )

        val parameters = fetchInfoParametersForSourceModel(
            base = base,
            source = FETCH_INFO_SOURCE_GONGZICP,
            defaultTitleQuery = "Default Book",
            sosadLoginCookie = "login-cookie"
        )

        assertEquals(FETCH_INFO_SOURCE_GONGZICP, parameters.source)
        assertEquals(FETCH_INFO_SEARCH_TITLE, parameters.searchMode)
        assertEquals("Default Book", parameters.query)
        assertEquals("", parameters.authCookie)
        assertFalse(parameters.fetchCatalog)
        assertFalse(parameters.writeCatalog)
        assertFalse(parameters.fetchIntro)
        assertFalse(parameters.writeIntro)
        assertTrue(parameters.fetchCover)
        assertTrue(parameters.writeCover)
    }

    @Test
    fun fetchedInfoHasRequestedContentChecksSelectedContentOnly() {
        val info = FetchedInfo(
            source = FETCH_INFO_SOURCE_JJWXC,
            query = "Book",
            resolvedUrl = "",
            title = "Book",
            author = "",
            intro = "简介",
            coverUrl = "https://example.com/cover.jpg",
            catalog = listOf(
                FetchedCatalogItem(1, "第1章"),
                FetchedCatalogItem(2, "第2章")
            )
        )

        assertTrue(fetchedInfoHasRequestedContent(info, fetchParameters(content = FETCH_INFO_CONTENT_INTRO)))
        assertTrue(fetchedInfoHasRequestedContent(info, fetchParameters(content = FETCH_INFO_CONTENT_COVER)))
        assertTrue(fetchedInfoHasRequestedContent(info, fetchParameters(content = FETCH_INFO_CONTENT_CATALOG)))
        assertFalse(fetchedInfoHasRequestedContent(info.copy(intro = ""), fetchParameters(content = FETCH_INFO_CONTENT_INTRO)))
        assertFalse(fetchedInfoHasRequestedContent(info.copy(coverUrl = " "), fetchParameters(content = FETCH_INFO_CONTENT_COVER)))
        assertFalse(fetchedInfoHasRequestedContent(info.copy(catalog = emptyList()), fetchParameters(content = FETCH_INFO_CONTENT_CATALOG)))
        assertFalse(fetchedInfoHasRequestedContent(info, fetchParameters(content = "unknown")))
    }

    @Test
    fun buildFetchInfoCatalogPreviewRowsMapsVolumesTargetsAndSkippedExtraChapters() {
        val book = sampleBook(
            listOf(
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 旧"),
                chapter("v1", "OEBPS/Text/Vol01.xhtml", "第一卷"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章 旧"),
                chapter("c3", "OEBPS/Text/Chapter0003.xhtml", "第3章 旧")
            )
        )
        val catalog = listOf(
            FetchedCatalogItem(index = 1, title = "第1章 新", sequence = "1"),
            FetchedCatalogItem(index = 2, title = "第一卷", isVolume = true),
            FetchedCatalogItem(index = 3, title = "第2章 新", sequence = "2"),
            FetchedCatalogItem(index = 4, title = "第二卷", isVolume = true),
            FetchedCatalogItem(index = 5, title = "第3章 新", sequence = "3"),
            FetchedCatalogItem(index = 6, title = "第4章 超出", sequence = "4")
        )
        val raw = FetchedInfo(
            source = FETCH_INFO_SOURCE_JJWXC,
            query = "Book",
            resolvedUrl = "",
            title = "Book",
            author = "",
            intro = "",
            coverUrl = "",
            catalog = catalog
        )
        val preview = FetchInfoPreview(
            toolId = "fetch",
            parameters = fetchParameters(content = FETCH_INFO_CONTENT_CATALOG),
            raw = raw,
            filtered = raw,
            filterIssues = emptyList()
        )

        val rows = buildFetchInfoCatalogPreviewRows(book, preview, filtered = true, fallbackChapterIndex = 0)

        assertEquals(
            listOf(
                FetchInfoCatalogPreviewRow("Chapter0001.xhtml", "第1章 旧", "第1章 第1章 新", isVolume = false, chapterPosition = 0),
                FetchInfoCatalogPreviewRow("Vol01.xhtml", "第一卷", "第一卷", isVolume = true, willCreateVolume = false),
                FetchInfoCatalogPreviewRow("Chapter0002.xhtml", "第2章 旧", "第2章 第2章 新", isVolume = false, chapterPosition = 1),
                FetchInfoCatalogPreviewRow("", "", "第二卷", isVolume = true, willCreateVolume = true),
                FetchInfoCatalogPreviewRow("Chapter0003.xhtml", "第3章 旧", "第3章 第3章 新", isVolume = false, chapterPosition = 2),
                FetchInfoCatalogPreviewRow("", "", "4", isVolume = false, skipped = true, chapterPosition = 3)
            ),
            rows
        )
    }

    @Test
    fun buildFetchInfoCatalogPreviewRowsAppliesRenameAndDeleteByChapterPosition() {
        val book = sampleBook(
            listOf(
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 旧"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章 旧"),
                chapter("c3", "OEBPS/Text/Chapter0003.xhtml", "第3章 旧")
            )
        )
        val raw = FetchedInfo(
            source = FETCH_INFO_SOURCE_JJWXC,
            query = "Book",
            resolvedUrl = "",
            title = "Book",
            author = "",
            intro = "",
            coverUrl = "",
            catalog = listOf(
                FetchedCatalogItem(index = 1, title = "新一", sequence = "1"),
                FetchedCatalogItem(index = 2, title = "新二", sequence = "2"),
                FetchedCatalogItem(index = 3, title = "新三", sequence = "3")
            )
        )
        val preview = FetchInfoPreview(
            toolId = "fetch",
            parameters = fetchParameters(content = FETCH_INFO_CONTENT_CATALOG),
            raw = raw,
            filtered = raw,
            filterIssues = emptyList()
        )

        val rows = buildFetchInfoCatalogPreviewRows(
            book = book,
            preview = preview,
            filtered = true,
            fallbackChapterIndex = 0,
            renames = mapOf(1 to "手改标题"),
            deletes = setOf(0)
        )

        // 第 0 章被删除：deleted 标记、抓取标题清空、原标题保留
        assertTrue(rows[0].deleted)
        assertEquals("第1章 旧", rows[0].originalTitle)
        assertEquals("", rows[0].fetchedTitle)
        // 第 1 章被重命名：使用手改值
        assertEquals("手改标题", rows[1].fetchedTitle)
        // 第 2 章正常拼装：原章号 + 抓取名字
        assertEquals("第3章 新三", rows[2].fetchedTitle)
    }

    @Test
    fun buildFetchInfoCatalogPreviewRowsDoesNotReuseMatchedVolumeForConsecutiveVolumeItems() {
        val book = sampleBook(
            listOf(
                chapter("v1", "OEBPS/Text/Vol01.xhtml", "第一卷 旧"),
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 旧")
            )
        )
        val raw = FetchedInfo(
            source = FETCH_INFO_SOURCE_JJWXC,
            query = "Book",
            resolvedUrl = "",
            title = "Book",
            author = "",
            intro = "",
            coverUrl = "",
            catalog = listOf(
                FetchedCatalogItem(index = 1, title = "第一卷", isVolume = true),
                FetchedCatalogItem(index = 2, title = "第二卷", isVolume = true),
                FetchedCatalogItem(index = 3, title = "第1章 新", sequence = "1")
            )
        )
        val preview = FetchInfoPreview(
            toolId = "fetch",
            parameters = fetchParameters(content = FETCH_INFO_CONTENT_CATALOG),
            raw = raw,
            filtered = raw,
            filterIssues = emptyList()
        )

        val rows = buildFetchInfoCatalogPreviewRows(book, preview, filtered = true, fallbackChapterIndex = 0)

        assertEquals(
            listOf(
                FetchInfoCatalogPreviewRow("Vol01.xhtml", "第一卷 旧", "第一卷", isVolume = true, willCreateVolume = false),
                FetchInfoCatalogPreviewRow("", "", "第二卷", isVolume = true, willCreateVolume = true),
                FetchInfoCatalogPreviewRow("Chapter0001.xhtml", "第1章 旧", "第1章 第1章 新", isVolume = false, chapterPosition = 0)
            ),
            rows
        )
    }

    @Test
    fun buildFetchInfoCatalogPreviewRowsCanUseRawCatalogInsteadOfFilteredCatalog() {
        val book = sampleBook(
            listOf(
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 旧")
            )
        )
        val raw = FetchedInfo(
            source = FETCH_INFO_SOURCE_JJWXC,
            query = "Book",
            resolvedUrl = "",
            title = "Book",
            author = "",
            intro = "",
            coverUrl = "",
            catalog = listOf(
                FetchedCatalogItem(index = 1, title = "第1章 原始", sequence = "1"),
                FetchedCatalogItem(index = 2, title = "第2章 原始", sequence = "2")
            )
        )
        val filtered = raw.copy(
            catalog = listOf(
                FetchedCatalogItem(index = 1, title = "第1章 过滤", sequence = "1")
            )
        )
        val preview = FetchInfoPreview(
            toolId = "fetch",
            parameters = fetchParameters(content = FETCH_INFO_CONTENT_CATALOG),
            raw = raw,
            filtered = filtered,
            filterIssues = emptyList()
        )

        val rows = buildFetchInfoCatalogPreviewRows(book, preview, filtered = false, fallbackChapterIndex = 0)

        assertEquals(
            listOf(
                FetchInfoCatalogPreviewRow("Chapter0001.xhtml", "第1章 旧", "第1章 第1章 原始", isVolume = false, chapterPosition = 0),
                FetchInfoCatalogPreviewRow("", "", "2", isVolume = false, skipped = true, chapterPosition = 1)
            ),
            rows
        )
    }

    @Test
    fun buildFetchInfoCatalogPreviewRowsAppendsEmptyRowsForRemainingTargets() {
        val book = sampleBook(
            listOf(
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 旧"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章 旧")
            )
        )
        val raw = FetchedInfo(
            source = FETCH_INFO_SOURCE_JJWXC,
            query = "Book",
            resolvedUrl = "",
            title = "Book",
            author = "",
            intro = "",
            coverUrl = "",
            catalog = listOf(
                FetchedCatalogItem(index = 1, title = "第1章 新", sequence = "1")
            )
        )
        val preview = FetchInfoPreview(
            toolId = "fetch",
            parameters = fetchParameters(content = FETCH_INFO_CONTENT_CATALOG),
            raw = raw,
            filtered = raw,
            filterIssues = emptyList()
        )

        val rows = buildFetchInfoCatalogPreviewRows(book, preview, filtered = true, fallbackChapterIndex = 0)

        assertEquals(
            listOf(
                FetchInfoCatalogPreviewRow("Chapter0001.xhtml", "第1章 旧", "第1章 第1章 新", isVolume = false, chapterPosition = 0),
                FetchInfoCatalogPreviewRow("Chapter0002.xhtml", "第2章 旧", "", isVolume = false, missingFetch = true)
            ),
            rows
        )
    }

    private fun previewWithCatalog(nonVolumeCount: Int): FetchInfoPreview {
        val catalog = (1..nonVolumeCount).map { index -> FetchedCatalogItem(index, "第${index}章") } +
            FetchedCatalogItem(nonVolumeCount + 1, "第一卷", isVolume = true)
        val raw = FetchedInfo(
            source = FETCH_INFO_SOURCE_JJWXC,
            query = "Book",
            resolvedUrl = "",
            title = "Book",
            author = "",
            intro = "",
            coverUrl = "",
            catalog = catalog
        )
        return FetchInfoPreview(
            toolId = "fetch",
            parameters = fetchParameters(content = FETCH_INFO_CONTENT_CATALOG),
            raw = raw,
            filtered = raw,
            filterIssues = emptyList()
        )
    }

    private fun fetchParameters(
        source: String = FETCH_INFO_SOURCE_JJWXC,
        content: String = FETCH_INFO_CONTENT_CATALOG,
        authCookie: String = ""
    ): FetchInfoParameters {
        return FetchInfoParameters(
            source = source,
            searchMode = FETCH_INFO_SEARCH_TITLE,
            query = "Book",
            content = content,
            fetchCatalog = content == FETCH_INFO_CONTENT_CATALOG,
            fetchIntro = content == FETCH_INFO_CONTENT_INTRO,
            fetchCover = content == FETCH_INFO_CONTENT_COVER,
            authCookie = authCookie,
            bodyRangeStart = 1,
            bodyRangeEnd = 0,
            catalogFilter = "",
            autoTitleFormat = false,
            introFilter = "",
            writeCatalog = content == FETCH_INFO_CONTENT_CATALOG,
            writeIntro = content == FETCH_INFO_CONTENT_INTRO,
            introTargetPath = DEFAULT_FETCH_INFO_INTRO_TARGET,
            writeCover = content == FETCH_INFO_CONTENT_COVER
        )
    }

    private fun sampleBook(chapters: List<EpubChapter>): EpubBook {
        return EpubBook(
            originalName = "book.epub",
            metadataTitle = "Book",
            metadataAuthor = "",
            metadataItems = mutableListOf(),
            entries = linkedMapOf(),
            opfPath = "OEBPS/content.opf",
            tocPath = null,
            manifest = chapters.associate { chapter ->
                chapter.id to ManifestItem(chapter.id, chapter.href, "application/xhtml+xml", chapter.path)
            }.toMutableMap(),
            spineIds = chapters.map { it.id }.toMutableList(),
            chapters = chapters.toMutableList()
        )
    }

    private fun chapter(id: String, path: String, title: String): EpubChapter {
        return EpubChapter(
            id = id,
            href = path.removePrefix("OEBPS/"),
            path = path,
            originalPath = path,
            pathAliases = mutableSetOf(path),
            title = title,
            html = "<html><body><h1>$title</h1></body></html>",
            wordCount = title.length
        )
    }
}
