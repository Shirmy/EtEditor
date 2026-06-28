package com.eteditor

import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.ManifestItem
import com.eteditor.core.decodeEpubHtmlBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchInfoEpubWriteUtilsTest {
    @Test
    fun applyFetchedCatalogToEpubRenamesTargetChaptersAndReportsCurrentTouch() {
        val book = sampleBook(
            listOf(
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 旧标题"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章 旧标题")
            )
        )

        val result = applyFetchedCatalogToEpub(
            book = book,
            parameters = fetchParameters(autoTitleFormat = false),
            catalog = listOf(FetchedCatalogItem(index = 1, title = "新标题", sequence = "001")),
            currentChapterIndex = 0
        )

        assertEquals(FetchInfoCatalogWriteResult(changed = 1, touchedCurrentChapter = true), result)
        assertEquals("第1章 新标题", book.chapters[0].title)
        assertTrue(book.chapters[0].html.contains("<h1>第1章 新标题</h1>"))
        assertEquals("第2章 旧标题", book.chapters[1].title)
    }

    @Test
    fun applyFetchedCatalogToEpubSyncsRenamedTitleIntoEntryBytes() {
        val book = sampleBook(
            listOf(chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 旧标题"))
        )

        applyFetchedCatalogToEpub(
            book = book,
            parameters = fetchParameters(autoTitleFormat = false),
            catalog = listOf(FetchedCatalogItem(index = 1, title = "新标题", sequence = "001")),
            currentChapterIndex = 0
        )

        // 回归：目录标题必须同步进 book.entries。否则执行链里后续读取 entries 的文本替换/批量替换
        // 会基于旧标题原始字节重写章节，把目录标题清空。
        val entryHtml = decodeEpubHtmlBytes(book.entries.getValue("OEBPS/Text/Chapter0001.xhtml"))
        assertTrue(entryHtml.contains("<h1>第1章 新标题</h1>"))
        assertTrue(!entryHtml.contains("旧标题"))
    }

    @Test
    fun applyFetchedCatalogToEpubHonorsRenameAndDeleteByChapterPosition() {
        val book = sampleBook(
            listOf(
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 旧标题"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章 旧标题")
            )
        )

        val result = applyFetchedCatalogToEpub(
            book = book,
            parameters = fetchParameters(autoTitleFormat = false),
            catalog = listOf(
                FetchedCatalogItem(index = 1, title = "新一", sequence = "1"),
                FetchedCatalogItem(index = 2, title = "新二", sequence = "2")
            ),
            currentChapterIndex = 0,
            renames = mapOf(0 to "第1章 手改"),
            deletes = setOf(1)
        )

        assertEquals(FetchInfoCatalogWriteResult(changed = 1, touchedCurrentChapter = true), result)
        assertEquals("第1章 手改", book.chapters[0].title)
        assertEquals("第2章 旧标题", book.chapters[1].title)
    }

    @Test
    fun applyFetchedCatalogToEpubCreatesVolumeChaptersAndTocLevels() {
        val book = sampleBook(
            listOf(
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 旧标题"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章 旧标题")
            )
        )

        val result = applyFetchedCatalogToEpub(
            book = book,
            parameters = fetchParameters(autoTitleFormat = false),
            catalog = listOf(
                FetchedCatalogItem(index = 1, title = "第一卷", isVolume = true),
                FetchedCatalogItem(index = 2, title = "新一"),
                FetchedCatalogItem(index = 3, title = "第二卷", isVolume = true),
                FetchedCatalogItem(index = 4, title = "新二")
            ),
            currentChapterIndex = 1
        )

        assertEquals(FetchInfoCatalogWriteResult(changed = 4, touchedCurrentChapter = true), result)
        assertEquals(listOf("第一卷", "第1章 新一", "第二卷", "第2章 新二"), book.chapters.map { it.title })
        assertEquals(listOf(0, 1, 0, 1), book.chapters.map { it.tocLevel })
        assertEquals(listOf("Vol01.xhtml", "Chapter0001.xhtml", "Vol02.xhtml", "Chapter0002.xhtml"), book.chapters.map { it.path.substringAfterLast('/') })
        assertEquals(book.chapters.map { it.id }, book.spineIds)
        assertTrue(book.manifest.values.any { it.path == "OEBPS/Text/Vol01.xhtml" })
        assertTrue(book.entries.containsKey("OEBPS/Text/Vol02.xhtml"))
    }

    @Test
    fun applyFetchedCatalogToEpubReusesAdjacentVolumeBeforeTargetChapter() {
        val book = sampleBook(
            listOf(
                chapter("v1", "OEBPS/Text/Vol01.xhtml", "旧卷"),
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 旧标题")
            )
        )

        val result = applyFetchedCatalogToEpub(
            book = book,
            parameters = fetchParameters(autoTitleFormat = false),
            catalog = listOf(
                FetchedCatalogItem(index = 1, title = "新卷", isVolume = true),
                FetchedCatalogItem(index = 2, title = "新一")
            ),
            currentChapterIndex = 0
        )

        assertEquals(FetchInfoCatalogWriteResult(changed = 2, touchedCurrentChapter = true), result)
        assertEquals(listOf("新卷", "第1章 新一"), book.chapters.map { it.title })
        assertEquals(listOf("OEBPS/Text/Vol01.xhtml", "OEBPS/Text/Chapter0001.xhtml"), book.chapters.map { it.path })
        assertEquals(2, book.entries.size)
        assertTrue(book.chapters[0].html.contains("<h1>新卷</h1>"))
    }

    @Test
    fun applyFetchedCatalogToEpubUsesAutoTitleFormatWhenWritingChapterHtml() {
        val book = sampleBook(
            listOf(
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 旧标题"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章 旧标题")
            )
        )

        val result = applyFetchedCatalogToEpub(
            book = book,
            parameters = fetchParameters(autoTitleFormat = true),
            catalog = listOf(
                FetchedCatalogItem(index = 1, title = "非常非常长的新标题", sequence = "1"),
                FetchedCatalogItem(index = 2, title = "另一段非常长的新标题", sequence = "2")
            ),
            currentChapterIndex = 1
        )

        assertEquals(FetchInfoCatalogWriteResult(changed = 2, touchedCurrentChapter = true), result)
        assertEquals(listOf("第1章 非常非常长的新标题", "第2章 另一段非常长的新标题"), book.chapters.map { it.title })
        assertTrue(book.chapters[0].html.contains("""<h1 class="chapter-title_01">第1章<br/>非常非常长的新标题</h1>"""))
        assertTrue(book.chapters[1].html.contains("""<h1 class="chapter-title_01">第2章<br/>另一段非常长的新标题</h1>"""))
    }

    @Test
    fun applyFetchedCatalogToEpubSkipsCoverVolumeTargetsBlankVolumesAndExtraCatalogItems() {
        val book = sampleBook(
            listOf(
                chapter("cover", "OEBPS/Text/Section0001.xhtml", "封面"),
                chapter("v1", "OEBPS/Text/Vol01.xhtml", "第一卷"),
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 旧标题"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章 旧标题")
            )
        )
        val originalPaths = book.chapters.map { it.path }

        val result = applyFetchedCatalogToEpub(
            book = book,
            parameters = fetchParameters(autoTitleFormat = false),
            catalog = listOf(
                FetchedCatalogItem(index = 1, title = " ", isVolume = true),
                FetchedCatalogItem(index = 2, title = "新一"),
                FetchedCatalogItem(index = 3, title = "新二"),
                FetchedCatalogItem(index = 4, title = "第3章 超出")
            ),
            currentChapterIndex = 1
        )

        assertEquals(FetchInfoCatalogWriteResult(changed = 2, touchedCurrentChapter = false), result)
        assertEquals(originalPaths, book.chapters.map { it.path })
        assertEquals(listOf("封面", "第一卷", "第1章 新一", "第2章 新二"), book.chapters.map { it.title })
        assertTrue(book.chapters[0].html.contains("<h1>封面</h1>"))
        assertTrue(book.chapters[1].html.contains("<h1>第一卷</h1>"))
    }

    @Test
    fun writeFetchInfoIntroFileToEpubCreatesEntryAndManifestItem() {
        val book = sampleBook(
            listOf(chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章"))
        )

        writeFetchInfoIntroFileToEpub(
            book = book,
            targetPath = "OEBPS/Text/intro.xhtml",
            info = FetchedInfo(
                source = FETCH_INFO_SOURCE_JJWXC,
                query = "Book",
                resolvedUrl = "https://example.test/book",
                title = "简介 & 书名",
                author = "作者",
                intro = "第一段\n内容标签：幻想",
                coverUrl = "",
                catalog = emptyList()
            ),
            source = FETCH_INFO_SOURCE_JJWXC
        )

        val html = decodeEpubHtmlBytes(book.entries.getValue("OEBPS/Text/intro.xhtml"))
        val manifestItem = book.manifest.values.single { it.path == "OEBPS/Text/intro.xhtml" }

        assertTrue(!html.contains("<title>"))
        assertTrue(html.contains("<p>第一段</p>"))
        assertTrue(html.contains("<hr/>"))
        assertEquals("Text/intro.xhtml", manifestItem.href)
        assertEquals("application/xhtml+xml", manifestItem.mediaType)
    }

    @Test
    fun writeFetchInfoIntroFileToEpubReplacesExistingBodyAndUpdatesChapter() {
        val currentHtml = """
            <html><head><title>旧简介</title></head><body>
            <div class="intro"><h2>文案</h2><p>旧正文</p></div>
            </body></html>
        """.trimIndent()
        val intro = EpubChapter(
            id = "intro",
            href = "Text/intro.html",
            path = "OEBPS/Text/intro.html",
            originalPath = "OEBPS/Text/intro.html",
            pathAliases = mutableSetOf("OEBPS/Text/intro.html"),
            title = "旧简介",
            html = currentHtml,
            wordCount = 3
        )
        val book = sampleBook(listOf(intro))
        val manifestCount = book.manifest.size

        writeFetchInfoIntroFileToEpub(
            book = book,
            targetPath = "oebps/text/INTRO.HTML",
            info = FetchedInfo(
                source = FETCH_INFO_SOURCE_SOSAD,
                query = "Book",
                resolvedUrl = "https://example.test/book",
                title = "新简介",
                author = "作者",
                intro = "标签一、标签二\n新正文",
                coverUrl = "",
                catalog = emptyList()
            ),
            source = FETCH_INFO_SOURCE_SOSAD
        )

        val html = decodeEpubHtmlBytes(book.entries.getValue("OEBPS/Text/intro.html"))
        assertEquals(manifestCount, book.manifest.size)
        assertEquals(html, book.chapters.single().html)
        assertTrue(html.contains("""<div class="intro"><h2>文案</h2>"""))
        assertTrue(html.contains("<p>标签一、标签二</p>"))
        assertTrue(html.contains("<hr/>"))
        assertTrue(html.contains("<p>新正文</p>"))
    }

    private fun sampleBook(chapters: List<EpubChapter>): EpubBook {
        val entries = linkedMapOf<String, ByteArray>()
        val manifest = mutableMapOf<String, ManifestItem>()
        chapters.forEach { chapter ->
            entries[chapter.path] = chapter.html.toByteArray(Charsets.UTF_8)
            manifest[chapter.id] = ManifestItem(
                id = chapter.id,
                href = chapter.href,
                mediaType = "application/xhtml+xml",
                path = chapter.path
            )
        }
        return EpubBook(
            originalName = "book.epub",
            metadataTitle = "Book",
            metadataAuthor = "",
            entries = entries,
            opfPath = "OEBPS/content.opf",
            tocPath = "OEBPS/toc.ncx",
            manifest = manifest,
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
            html = "<html><head><title>$title</title></head><body><h1>$title</h1><p>旧正文</p></body></html>",
            wordCount = title.length
        )
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
}
