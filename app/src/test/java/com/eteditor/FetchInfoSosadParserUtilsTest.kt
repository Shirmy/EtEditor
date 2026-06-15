package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchInfoSosadParserUtilsTest {
    @Test
    fun sosadIntroParserPrefersProfileTagsAndMainText() {
        val html = """
            <html><body>
              <article class="article-body">
                <a href="/channels/original" title="Original Novel">Original Novel</a>
                <a href="/tag/he">Happy Ending</a>
                <a href="/tag/long">长篇</a>
              </article>
              <div class="main-text">
                <p>第一段简介</p>
                <p>第二段简介</p>
              </div>
              <table><tr><td>目录</td></tr></table>
            </body></html>
        """.trimIndent()
        val block = parseSosadIntroBlock(html)

        assertTrue(block.contains("Original Novel"))
        assertEquals(
            "原创小说、HE\n第一段简介\n第二段简介",
            parseSosadIntroText(html, block)
        )
    }

    @Test
    fun sosadIntroFallbackRemovesCatalogAndNavigationLines() {
        val block = """
            <div class="message-body">
              <p>这是简介正文</p>
              <ol><li>第1章</li></ol>
              <p>下一页</p>
            </div>
        """.trimIndent()

        assertEquals("这是简介正文", parseSosadIntroText("", block))
    }

    @Test
    fun sosadIntroBlockFallsBackToBodyAfterRemovingHiddenUiBlocks() {
        val html = """
            <html><body>
              <script>var bad = "不应出现";</script>
              <nav>导航</nav>
              <div>
                <p>这是正文简介</p>
                <p>第二段内容</p>
              </div>
            </body></html>
        """.trimIndent()

        val block = parseSosadIntroBlock(html)

        assertTrue(block.contains("这是正文简介"))
        assertFalse(block.contains("不应出现"))
        assertFalse(block.contains("导航"))
    }

    @Test
    fun sosadCoverAndTitleParsersFilterUiAndUnsafeImages() {
        val introBlock = """
            <div>
              <img src="/avatar/icon.png" />
              <img data-src="/covers/book.jpg?x=1" />
            </div>
        """.trimIndent()
        val html = """
            <html>
              <head><title>备用标题 - 废文</title></head>
              <body>
                <h1>作品标题</h1>
                <h2 class="chapter-title_02">进入论坛</h2>
                <strong class="h2">第1章 正文标题</strong>
              </body>
            </html>
        """.trimIndent()

        assertEquals(
            "https://sosad.fun/covers/book.jpg?x=1",
            parseSosadIntroCover(introBlock, "https://sosad.fun/threads/123")
        )
        assertEquals("作品标题", parseSosadTitle(html))
        assertEquals("第1章 正文标题", parseSosadPostTitle(html))
    }

    @Test
    fun sosadCoverAndTitleParsersUseSrcsetAndTitleFallbacks() {
        val introBlock = """
            <div>
              <img src="data:image/png;base64,abc" />
              <img src="/emoji/smile.png" />
              <img srcset="/covers/main.webp 1x, /covers/main@2x.webp 2x" />
            </div>
        """.trimIndent()
        val html = """
            <html>
              <head><title>备用标题 - 废文</title></head>
              <body>
                <h2 class="chapter-title_02">进入论坛</h2>
              </body>
            </html>
        """.trimIndent()

        assertEquals(
            "https://sosad.fun/covers/main.webp",
            parseSosadIntroCover(introBlock, "https://sosad.fun/threads/123")
        )
        assertEquals("备用标题", parseSosadTitle(html))
        assertEquals("", parseSosadPostTitle(html))
    }

    @Test
    fun completeSosadCatalogParsesTableRowsSkipsHeadersAndDeduplicatesUrls() {
        val html = """
            <table>
              <tr><th>章节名</th><th>概要</th><th>字数</th><th>发布时间</th></tr>
              <tr>
                <td><a href="/threads/book.123/posts/456">第1章</a></td>
                <td>开始</td><td>1000</td><td>2024-01-01</td>
              </tr>
              <tr>
                <td><a href="/threads/book.123/posts/456">第1章</a></td>
                <td>开始</td><td>1000</td><td>2024-01-01</td>
              </tr>
              <tr>
                <td><a href="/threads/book.123/posts/789">第2章</a></td>
                <td></td><td>900</td><td>2024-01-02</td>
              </tr>
            </table>
        """.trimIndent()

        val catalog = fetchCompleteSosadCatalog(html, "https://sosad.fun/threads/book.123")

        assertEquals(listOf("第1章 开始", "第2章"), catalog.map { it.title })
        assertEquals(listOf("1", "2"), catalog.map { it.sequence })
        assertEquals(
            listOf(
                "https://sosad.fun/threads/book.123/posts/456",
                "https://sosad.fun/threads/book.123/posts/789"
            ),
            catalog.map { it.url }
        )
        assertFalse(catalog.any { it.chapterTitle == "章节名" })
    }

    @Test
    fun completeSosadCatalogFallsBackToPostLinksWhenRowsAreUnavailable() {
        val html = """
            <div>
              <a href="/threads/book.123/posts/111">第1章 开始</a>
              <a href="/threads/book.123/posts/111">第1章 开始</a>
              <a href="/threads/book.123/posts/222">进入论坛</a>
              <a href="https://evil.example/threads/book.123/posts/333">第2章 坏链接</a>
              <a href="/threads/book.123/posts/444">第2章 继续</a>
            </div>
        """.trimIndent()

        val catalog = fetchCompleteSosadCatalog(html, "https://sosad.fun/threads/book.123")

        assertEquals(listOf("第1章 开始", "第2章 继续"), catalog.map { it.title })
        assertEquals(listOf("1", "2"), catalog.map { it.sequence })
        assertEquals(
            listOf(
                "https://sosad.fun/threads/book.123/posts/111",
                "https://sosad.fun/threads/book.123/posts/444"
            ),
            catalog.map { it.url }
        )
    }
}
