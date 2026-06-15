package com.eteditor

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchInfoUrlUtilsTest {
    @Test
    fun attrAndAbsoluteUrlHandleQuotedAttributesAndRelativeLinks() {
        assertEquals("Text/chapter.xhtml", attr("""<a HREF="Text/chapter.xhtml">""", "href"))
        assertEquals("https://sosad.fun/threads/123", absoluteUrl("/threads/123", "https://sosad.fun/search?search=x"))
        assertEquals("https://img.example/a.jpg", absoluteUrl("//img.example/a.jpg", "https://sosad.fun/search"))
        assertEquals("https://site.test/a", absoluteUrl("https://site.test/a", "https://sosad.fun/search"))
    }

    @Test
    fun combineCatalogTitleAndUrlEncodeNormalizeCommonValues() {
        assertEquals("标题 摘要", combineCatalogTitle(" 标题 ", " 摘要 "))
        assertEquals("标题", combineCatalogTitle("标题", "标题"))
        assertEquals("%CA%E9%C3%FB", urlEncode("书名", Charset.forName("GBK")))
    }

    @Test
    fun resolveFetchInfoSourceForRetryUrlDetectsSupportedSites() {
        assertEquals("gongzi", resolveFetchInfoSourceForRetryUrl("https://www.gongzicp.com/novel-1.html", "gongzi", "sosad", "jjwxc"))
        assertEquals("sosad", resolveFetchInfoSourceForRetryUrl("https://sosad.fun/threads/123", "gongzi", "sosad", "jjwxc"))
        assertEquals("jjwxc", resolveFetchInfoSourceForRetryUrl("https://www.jjwxc.net/onebook.php?novelid=123", "gongzi", "sosad", "jjwxc"))
        assertEquals(null, resolveFetchInfoSourceForRetryUrl("https://example.com/book", "gongzi", "sosad", "jjwxc"))
    }

    @Test
    fun resolveFetchInfoSourceForRetryUrlTrimsAndIgnoresUrlCase() {
        assertEquals(
            "gongzi",
            resolveFetchInfoSourceForRetryUrl(" HTTPS://WWW.GONGZICP.COM/NOVEL-1.HTML ", "gongzi", "sosad", "jjwxc")
        )
        assertEquals(
            "jjwxc",
            resolveFetchInfoSourceForRetryUrl(" ONEBOOK.PHP?NOVELID=123 ", "gongzi", "sosad", "jjwxc")
        )
    }

    @Test
    fun urlHelpersHandleFallbacksBlankPiecesAndRetryAliases() {
        assertEquals("Text/chapter.xhtml", attr("""<a data-id="1" href='Text/chapter.xhtml'>""", "href"))
        assertEquals("", attr("""<a href="Text/chapter.xhtml">""", "src"))
        assertEquals("chapter.xhtml", absoluteUrl("chapter.xhtml", "://bad base"))
        assertEquals("摘要", combineCatalogTitle("", " 摘要 "))
        assertEquals("标题", combineCatalogTitle(" 标题 ", ""))
        assertEquals("%E4%B9%A6%E5%90%8D+a", urlEncode("书名 a"))
        assertEquals("gongzi", resolveFetchInfoSourceForRetryUrl("https://webapi.gongzicp.com/novel/1", "gongzi", "sosad", "jjwxc"))
        assertEquals("sosad", resolveFetchInfoSourceForRetryUrl("https://xn--pxtr7m.com/threads/123", "gongzi", "sosad", "jjwxc"))
        assertEquals("jjwxc", resolveFetchInfoSourceForRetryUrl("novelid=123", "gongzi", "sosad", "jjwxc"))
    }

    @Test
    fun sosadUrlHelpersAcceptSearchThreadAndPostUrlsOnlyOnAllowedHosts() {
        assertTrue(needsSosadSearch("书名"))
        assertTrue(needsSosadSearch("https://sosad.fun/search?search=book"))
        assertFalse(needsSosadSearch("https://sosad.fun/threads/123"))
        assertTrue(isSosadThreadDetailUrl("https://sosad.fun/threads/title.123"))
        assertFalse(isSosadThreadDetailUrl("https://sosad.fun/threads/title.123/posts/456"))
        assertTrue(isSosadPostUrl("https://sosad.fun/threads/title.123/posts/456"))
        assertEquals("456", sosadPostId("https://sosad.fun/threads/title.123/posts/456"))
    }

    @Test
    fun sosadUrlHelpersRejectBlankUnsupportedAndNonSearchUrls() {
        assertFalse(needsSosadSearch(""))
        assertFalse(needsSosadSearch("https://example.com/search?search=book"))
        assertFalse(isSosadSearchUrl("https://sosad.fun/threads/123"))
        assertFalse(isSosadThreadDetailUrl("http://sosad.fun/threads/123"))
        assertFalse(isSosadPostUrl("https://example.com/posts/456"))
    }

    @Test
    fun sosadUrlHelpersHandleTrailingSearchSlashAndStandalonePostUrls() {
        assertTrue(isSosadSearchUrl("https://www.sosad.fun/search/?search=book"))
        assertTrue(isSosadThreadDetailUrl("https://xn--pxtr7m.com/threads/book.123/profile?tab=info"))
        assertFalse(isSosadThreadDetailUrl("https://xn--pxtr7m.com/threads/book.123/posts/456?x=1"))
        assertTrue(isSosadPostUrl("https://sosad.fun/posts/789#comment"))
        assertEquals("789", sosadPostId("https://sosad.fun/posts/789#comment"))
        assertEquals("456", sosadPostId("https://sosad.fun/threads/book.123/posts/456?x=1"))
    }

    @Test
    fun parseSosadSearchAuthorFindsAuthorFromLinksClassesOrPlainText() {
        assertEquals(
            "作者A",
            parseSosadSearchAuthor("""<a href="/members/1">作者A</a>""")
        )
        assertEquals(
            "作者B",
            parseSosadSearchAuthor("""<span class="username">作者B</span>""")
        )
        assertEquals(
            "作者C",
            parseSosadSearchAuthor("作者：作者C\n正文")
        )
    }

    @Test
    fun parseSosadSearchAuthorSkipsUiTextAndCleansHtmlEntities() {
        val window = """
            <a href="/members/1">进入论坛</a>
            <span class="username">作者&nbsp;D</span>
            <div class="poster">备用作者</div>
        """.trimIndent()

        assertEquals("作者 D", parseSosadSearchAuthor(window))
        assertEquals("", parseSosadSearchAuthor("""<span class="username">进入论坛</span>"""))
    }
}
