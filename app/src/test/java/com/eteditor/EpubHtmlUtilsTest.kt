package com.eteditor

import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.ManifestItem
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubHtmlUtilsTest {
    @Test
    fun escapeXmlTextAndAttributeEscapeSpecialCharacters() {
        assertEquals("A&amp;B&lt;C&gt;", "A&B<C>".escapeXmlText())
        assertEquals("A&amp;&quot;B&quot;", "A&\"B\"".escapeXmlAttribute("\""))
        assertEquals("A&amp;&apos;B&apos;", "A&'B'".escapeXmlAttribute("'"))
    }

    @Test
    fun htmlBodyContentRangeFindsBodyOrFallsBackToWholeDocument() {
        val html = "<html><body class=\"x\">正文</body></html>"
        val range = htmlBodyContentRange(html)

        assertEquals("正文", html.substring(range.first, range.second))
        assertEquals(0 to "plain text".length, htmlBodyContentRange("plain text"))
        assertNull(htmlBodyContentRangeOrNull("plain text"))
    }

    @Test
    fun htmlBodyContentRangeOrNullUsesDocumentEndWhenBodyCloseIsMissing() {
        val html = "<html><body><p>正文</p>"
        val range = htmlBodyContentRangeOrNull(html)

        assertEquals("<p>正文</p>", html.substring(range!!.first, range.second))
        assertEquals(range, htmlBodyContentRange(html))
    }

    @Test
    fun htmlDirectoryTitlePrefersH1ThenH2ThenTitleAndStripsHtml() {
        assertEquals(
            "第一章 标题",
            htmlDirectoryTitle("<title>书名</title><h2>副标题</h2><h1><span>第一章&nbsp;标题</span></h1>")
        )
        assertEquals("副标题", htmlDirectoryTitle("<title>书名</title><h2>副标题</h2>"))
        assertEquals("书名", htmlDirectoryTitle("<title>书名</title>"))
    }

    @Test
    fun replaceIntroHtmlPreservingStructureKeepsHeadingAndContainer() {
        val html = """
            <html><body>
            <div class="intro"><h2>文案</h2><p>旧简介</p></div>
            </body></html>
        """.trimIndent()

        val updated = replaceIntroHtmlPreservingStructure(html, "<p>新简介</p>")

        assertTrue(updated.contains("""<div class="intro"><h2>文案</h2>"""))
        assertTrue(updated.contains("<p>新简介</p>"))
        assertTrue(!updated.contains("旧简介"))
    }

    @Test
    fun replaceIntroHtmlPreservingStructureKeepsHeadingWhenNoContainerExists() {
        val html = "<html><body><h2>文案</h2>\n<p>旧简介</p>\n<p>旧尾巴</p></body></html>"

        val updated = replaceIntroHtmlPreservingStructure(html, "\n<p>新简介</p>\n")

        assertTrue(updated.contains("<h2>文案</h2>\n<p>新简介</p>"))
        assertTrue(!updated.contains("旧简介"))
        assertTrue(!updated.contains("旧尾巴"))
    }

    @Test
    fun replaceIntroHtmlPreservingStructureReplacesFirstNonBlankDivWithoutHeading() {
        val html = "<html><body><div><p>旧简介</p></div><p>保留</p></body></html>"

        val updated = replaceIntroHtmlPreservingStructure(html, "<p>新简介</p>")

        assertTrue(updated.contains("<div>\n<p>新简介</p>\n</div>"))
        assertTrue(updated.contains("<p>保留</p>"))
        assertTrue(!updated.contains("旧简介"))
    }

    @Test
    fun normalizeEpubPathAndRelativeHrefResolveDotSegments() {
        assertEquals(
            "OEBPS/Images/cover.jpg",
            normalizeEpubPath("""OEBPS\Text\..\Images\.\cover.jpg""")
        )
        assertEquals(
            "../Images/cover.jpg",
            relativeEpubHref("OEBPS/Text/", "OEBPS/Images/cover.jpg")
        )
        assertEquals(
            "chapter2.xhtml",
            relativeEpubHref("OEBPS/Text/", "OEBPS/Text/chapter2.xhtml")
        )
    }

    @Test
    fun uniqueManifestIdAndEntryPathAvoidExistingBookPaths() {
        val book = sampleBook()
        book.manifest["item_1cover"] = ManifestItem(
            id = "item_1cover",
            href = "Text/cover.xhtml",
            mediaType = "application/xhtml+xml",
            path = "OEBPS/Text/cover.xhtml"
        )
        book.entries["OEBPS/Text/image.jpg"] = ByteArray(0)
        book.entries["OEBPS/Text/image_1.jpg"] = ByteArray(0)

        assertEquals("item_1cover_1", uniqueManifestId(book, "1cover"))
        assertEquals("OEBPS/Text/image_2.jpg", uniqueEpubEntryPath(book, "OEBPS/Text/image.jpg"))
    }

    @Test
    fun guessMediaTypeAndSetXmlAttributeHandleCommonEpubAssets() {
        assertEquals("application/xhtml+xml", guessMediaType("Text/chapter.xhtml"))
        assertEquals("image/webp", guessMediaType("Images/cover.webp"))
        assertEquals("application/octet-stream", guessMediaType("data.bin"))
        assertEquals("<item id=\"c1\" href=\"Text/2.xhtml\">", setXmlAttribute("<item id=\"c1\">", "href", "Text/2.xhtml"))
        assertEquals("<item id=\"c1\" href=\"A&amp;B\"/>", setXmlAttribute("<item id=\"c1\"/>", "href", "A&B"))
        assertEquals("<item href=\"Text/new.xhtml\">", setXmlAttribute("<item HREF='old.xhtml'>", "href", "Text/new.xhtml"))
    }

    @Test
    fun normalizeEpubChapterLineEndingsToCrlfUpdatesChapterAndEntry() {
        val book = sampleBook(html = "<html><body><p>一</p>\n<p>二</p></body></html>")
        val chapter = book.chapters.single()

        normalizeEpubChapterLineEndingsToCrlf(book, chapter)

        assertEquals("<html><body><p>一</p>\r\n<p>二</p></body></html>", chapter.html)
        assertEquals(chapter.html, String(book.entries.getValue(chapter.path), StandardCharsets.UTF_8))
    }

    @Test
    fun editEpubBodyAndSyncEntryReflectsNewContentInSavedBytes() {
        // 复现 EPUB 正文编辑保存路径的核心：改正文 -> rebuildHtmlWithBodyContent -> 同步 entry。
        // 守护此前的缺陷：仅改 chapter.html 不同步 entries 会导致保存/导出仍是旧内容。
        val book = sampleBook(html = "<html><body><p>旧正文</p></body></html>")
        val chapter = book.chapters.single()

        val parts = htmlBodyContentParts(chapter.html)
        chapter.html = rebuildHtmlWithBodyContent(parts.prefix, "<p>新正文</p>", parts.suffix)
        normalizeEpubChapterLineEndingsToCrlf(book, chapter)

        val savedBytes = String(book.entries.getValue(chapter.path), StandardCharsets.UTF_8)
        assertTrue(savedBytes.contains("新正文"))
        assertTrue(!savedBytes.contains("旧正文"))
        assertEquals(chapter.html, savedBytes)
    }

    @Test
    fun toCrlfLineEndingsNormalizesMixedLineEndings() {
        assertEquals("一\r\n二\r\n三\r\n四", "一\r\n二\n三\r四".toCrlfLineEndings())
    }

    @Test
    fun epubSplitTitleLineCandidateAndBodyLinesUseVisibleBodyText() {
        val chapter = sampleBook(
            html = "<html><body>\r\n<p>&nbsp;</p>\r\n<h2>第二行</h2>\r\n<p>正文</p>\r\n</body></html>"
        ).chapters.single()

        assertEquals(1 to "第二行", epubSplitTitleLineCandidate(epubChapterBodyLines(chapter), 0))
        assertEquals(listOf("<p>&nbsp;</p>", "<h2>第二行</h2>", "<p>正文</p>"), epubChapterBodyLines(chapter))
    }

    @Test
    fun epubIntroTemplateAddsSourceSpecificSeparators() {
        assertEquals(
            "<p>第一段</p>\n<hr/>\n<p>内容标签：幻想</p>",
            fetchedIntroBodyHtml("第一段\n内容标签：幻想", FETCH_INFO_SOURCE_JJWXC)
        )
        assertEquals(
            "<p>幻想、剧情</p>\n<hr/>\n<p>简介正文</p>",
            fetchedIntroBodyHtml("幻想、剧情\n简介正文", FETCH_INFO_SOURCE_SOSAD)
        )
    }

    @Test
    fun epubIntroTemplateAddsGongzicpSeparatorBeforeTagLine() {
        assertEquals(
            "<p>第一段</p>\n<hr/>\n<p>标签：幻想</p>",
            fetchedIntroBodyHtml("第一段\n标签：幻想", FETCH_INFO_SOURCE_GONGZICP)
        )
    }

    @Test
    fun introHtmlEscapesTitleAndUsesEmptyParagraphFallback() {
        val html = introHtml(
            title = "书名 & <副题>",
            intro = " ",
            source = FETCH_INFO_SOURCE_JJWXC
        )

        assertTrue(!html.contains("<title>"))
        assertTrue(html.contains("<h1 class=\"centered-text_01\">书名 &amp; &lt;副题&gt;</h1>"))
        assertTrue(html.contains("<p></p>"))
    }

    @Test
    fun volumeHtmlNormalizesRawLineBreakHeadingAndOmitsDocumentTitle() {
        val html = volumeHtml("第一卷\n副标题 & <x>")

        assertTrue(!html.contains("<title>"))
        assertTrue(html.contains("<h1 class=\"centered-text_01\">第一卷 副标题 &amp; &lt;x&gt;</h1>"))
    }

    @Test
    fun volumeHtmlWithParagraphsFiltersBlankLinesAndEscapesBodyText() {
        val html = volumeHtml(
            title = "第一卷 & <标题>",
            paragraphs = listOf("  副题 & 说明  ", " ", "<原文标签>")
        )

        assertTrue(!html.contains("<title>"))
        assertTrue(html.contains("<h1 class=\"centered-text_01\">第一卷 &amp; &lt;标题&gt;</h1>"))
        assertTrue(html.contains("<p>副题 &amp; 说明</p>"))
        assertTrue(html.contains("<p>&lt;原文标签&gt;</p>"))
        assertTrue(!html.contains("<p> </p>"))
    }

    @Test
    fun chapterHtmlEscapesTitleBodyAndFallsBackForBlankContent() {
        val html = chapterHtml(" 第1章 & <标题> ", " 第一段 & <x> \n\n 第二段 ")
        val blank = chapterHtml(" ", " ")

        assertTrue(html.contains("<h2>第1章 &amp; &lt;标题&gt;</h2>"))
        assertTrue(html.contains("<p>第一段 &amp; &lt;x&gt;</p>"))
        assertTrue(html.contains("<p>第二段</p>"))
        assertTrue(blank.contains("<h2>未命名章节</h2>"))
        assertTrue(blank.contains("  <p></p>"))
    }

    @Test
    fun chapterHtmlFromBlocksGroupsSysParagraphsAndEscapesImages() {
        val html = chapterHtml(
            title = "第1章 <标题>",
            blocks = listOf(
                InsertChapterBodyBlock(text = "系统一\n系统二", cssClass = "sys"),
                InsertChapterBodyBlock(imageFileName = "a&b.jpg"),
                InsertChapterBodyBlock(text = "正文 & <x>")
            ),
            imageHrefForFileName = { "../Images/$it" }
        )

        assertTrue(html.contains("<h2>第1章 &lt;标题&gt;</h2>"))
        assertTrue(html.contains("<div class=\"sys\">\n    <p>系统一</p>\n    <p>系统二</p>\n  </div>"))
        assertTrue(html.contains("src=\"../Images/a&amp;b.jpg\""))
        assertTrue(html.contains("<p>正文 &amp; &lt;x&gt;</p>"))
    }

    private fun sampleBook(
        path: String = "OEBPS/Text/chapter1.xhtml",
        html: String = "<html><body><h1>第一章</h1><p>正文</p></body></html>"
    ): EpubBook {
        val chapter = EpubChapter(
            id = "c1",
            href = path.removePrefix("OEBPS/"),
            path = path,
            originalPath = path,
            pathAliases = mutableSetOf(path),
            title = "第一章",
            html = html,
            wordCount = 2
        )
        return EpubBook(
            originalName = "book.epub",
            metadataTitle = "Book",
            metadataAuthor = "",
            metadataItems = mutableListOf(),
            entries = linkedMapOf(path to html.toByteArray(StandardCharsets.UTF_8)),
            opfPath = "OEBPS/content.opf",
            tocPath = "OEBPS/toc.ncx",
            manifest = mutableMapOf(
                "c1" to ManifestItem(
                    id = "c1",
                    href = chapter.href,
                    mediaType = "application/xhtml+xml",
                    path = path
                )
            ),
            spineIds = mutableListOf("c1"),
            chapters = mutableListOf(chapter)
        )
    }
}
