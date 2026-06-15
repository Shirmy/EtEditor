package com.eteditor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubTitleUtilsTest {
    @Test
    fun epubDirectoryTitlePrefersBodyHeadingThenTitleThenFileName() {
        assertEquals(
            "第一章 开始",
            epubDirectoryTitle(
                path = "OEBPS/Text/Chapter0001.xhtml",
                html = "<html><head><title>书名</title></head><body><h1>第一章 开始</h1></body></html>"
            )
        )
        assertEquals(
            "副标题",
            epubDirectoryTitle(
                path = "OEBPS/Text/Chapter0002.xhtml",
                html = "<html><body><h2>副标题</h2></body></html>"
            )
        )
        assertEquals(
            "Head Title",
            epubDirectoryTitle(
                path = "OEBPS/Text/Chapter0003.xhtml",
                html = "<html><head><title>Head Title</title></head><body><p>正文</p></body></html>"
            )
        )
        assertEquals(
            "Chapter0004",
            epubDirectoryTitle(
                path = "OEBPS/Text/Chapter0004.xhtml",
                html = "<html><body><p>正文</p></body></html>"
            )
        )
    }

    @Test
    fun epubDirectoryTitleReadsNamespacedHeadingsAndFallsBackToUnnamedChapter() {
        assertEquals(
            "命名空间 标题",
            epubDirectoryTitle(
                path = "OEBPS/Text/Chapter0005.xhtml",
                html = "<html><body><xhtml:h1>命名空间<br/>标题</xhtml:h1></body></html>"
            )
        )
        assertEquals(
            "未命名章节",
            epubDirectoryTitle(
                path = "OEBPS/Text/.xhtml",
                html = "<html><body><p>正文</p></body></html>"
            )
        )
    }

    @Test
    fun epubDirectoryTitleCleansNestedHeadingMarkupAndPrefersH1OverEarlierH2() {
        assertEquals(
            "第一章 开始",
            epubDirectoryTitle(
                path = "OEBPS/Text/Chapter0006.xhtml",
                html = "<html><body><h2>副标题</h2><H1 class=\"title\">第 一 章 <span>开始</span></H1></body></html>"
            )
        )
    }

    @Test
    fun epubDirectoryTitleSkipsBlankHeadingAndFallsBackToNextTitleSource() {
        assertEquals(
            "副标题",
            epubDirectoryTitle(
                path = "OEBPS/Text/Chapter0007.xhtml",
                html = "<html><head><title>Head Title</title></head><body><h1> </h1><h2>副标题</h2></body></html>"
            )
        )
        assertEquals(
            "Head Title",
            epubDirectoryTitle(
                path = "OEBPS/Text/Chapter0008.xhtml",
                html = "<html><head><title>Head Title</title></head><body><h1> </h1><p>正文</p></body></html>"
            )
        )
    }

    @Test
    fun epubDirectoryTitleDetectsCoverPagesBeforeHtmlTitles() {
        val html = "<html><head><title>封面标题</title></head><body><img src=\"cover.jpg\" /></body></html>"

        assertEquals(EPUB_COVER_DIRECTORY_TITLE, epubDirectoryTitle("OEBPS/Text/Section0001.xhtml", html))
        assertEquals(EPUB_COVER_DIRECTORY_TITLE, epubDirectoryTitle("OEBPS/Images/title-page.xhtml", html))
        assertTrue(isEpubCoverDirectoryCandidate("OEBPS/Text/coverpage.xhtml", "<html><body></body></html>"))
        assertFalse(isEpubCoverDirectoryCandidate("OEBPS/Text/Chapter0001.xhtml", html))
    }

    @Test
    fun epubCoverDirectoryCandidateUsesImageBodyAndCoverLikePath() {
        val imageHtml = "<html><body><p>封面</p><img src=\"cover.jpg\" /></body></html>"
        val textHtml = "<html><body><p>封面</p></body></html>"

        assertTrue(isEpubCoverDirectoryCandidate("OEBPS/Text/front-cover.xhtml", imageHtml))
        assertTrue(isEpubCoverDirectoryCandidate("OEBPS/Text/titlepage.xhtml", textHtml))
        assertFalse(isEpubCoverDirectoryCandidate("OEBPS/Text/front-cover.xhtml", textHtml))
    }

    @Test
    fun epubCoverDirectoryCandidateMatchesCaseInsensitiveStemAndBodyImageMarkup() {
        val imageHtml = "<HTML><BODY><IMG src=\"cover.jpg\" /></BODY></HTML>"

        assertTrue(isEpubCoverDirectoryCandidate("OEBPS/Text/COVER.XHTML", "<html><body></body></html>"))
        assertTrue(isEpubCoverDirectoryCandidate("OEBPS/Text/Front-Cover.xhtml", imageHtml))
    }

    @Test
    fun section0001CanUseOwnTitleInsteadOfCoverDetectionWhenRequested() {
        val chapter = EpubChapter(
            id = "c1",
            href = "Text/Section0001.xhtml",
            path = "OEBPS/Text/Section0001.xhtml",
            originalPath = "OEBPS/Text/Section0001.xhtml",
            pathAliases = mutableSetOf("OEBPS/Text/Section0001.xhtml"),
            title = "旧标题",
            html = "<html><body><h1>主标题</h1><img src=\"cover.jpg\" /></body></html>",
            wordCount = 2
        )

        assertEquals(EPUB_COVER_DIRECTORY_TITLE, chapter.epubDirectoryTitle())
        assertEquals("主标题", chapter.epubDirectoryTitle(useOwnSection0001Title = true))
    }

    @Test
    fun syncEpubDirectoryTitleFromHtmlUpdatesOnlyWhenTitleChanges() {
        val chapter = EpubChapter(
            id = "c1",
            href = "Text/c1.xhtml",
            path = "OEBPS/Text/c1.xhtml",
            originalPath = "OEBPS/Text/c1.xhtml",
            pathAliases = mutableSetOf("OEBPS/Text/c1.xhtml"),
            title = "旧标题",
            html = "<html><body><h1>新标题</h1></body></html>",
            wordCount = 3
        )

        assertTrue(chapter.syncEpubDirectoryTitleFromHtml())
        assertEquals("新标题", chapter.title)
        assertFalse(chapter.syncEpubDirectoryTitleFromHtml())
    }
}
