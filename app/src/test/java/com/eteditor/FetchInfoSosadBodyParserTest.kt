package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchInfoSosadBodyParserTest {
    @Test
    fun parseBodyKeepsChapterTextWhenNestedPostBodyStartsWithChapterMarkup() {
        val html = """
            <html>
              <body>
                <div id="post16308842">
                  <div class="message-body">
                    <div class="bbWrapper">
                      <div class="bbCodeBlock">Warning: 内容提示</div>
                      <div class="body-inner">
                        &lt;h2 class="chapter-title_02"&gt;第21章 IF线01&lt;/h2&gt;
                        &lt;p&gt;建设一些说明，是一条IF线，但是比正文阴暗不少
                        Warning: 内容提示&lt;/p&gt;
                        &lt;p&gt;正文第一段&lt;/p&gt;
                        <p>进入论坛模式</p>
                        <p>106</p>
                      </div>
                    </div>
                  </div>
                  <div class="message-footer">回复</div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val body = parseSosadBodyDetail(html, "https://sosad.fun/posts/16308842")

        assertTrue(body.text.contains("建设一些说明"))
        assertTrue(body.text.contains("Warning: 内容提示"))
        assertTrue(body.text.contains("正文第一段"))
        assertFalse(body.text.contains("进入论坛模式"))
        assertFalse(body.text.contains("106"))
    }

    @Test
    fun parseBodyDropsHiddenCollapsedPreviewSpan() {
        val html = """
            <html><body>
              <div id="post16290389">
                <div class="message-body">
                  <div class="main-text indentation no-selection">
                    <span id="full16290389" class="">
                      <p>正文第一段</p>
                      <p>正文第二段</p>
                      <p>正文第三段</p>
                      <p></p>
                    </span>
                    <span class="hidden">
                      <span id="abbreviated16290389">
                        正文第一段 正文第二段 正文第三段 这是被折叠截断的预览…
                      </span>
                      &nbsp;&nbsp;&nbsp;<a type="button" id="expand16290389">展开</a>
                    </span>
                    <div class="font-4">
                      <a href="https://example.com/thread-posts/16290389"><em>进入论坛模式</em></a>
                    </div>
                  </div>
                </div>
              </div>
            </body></html>
        """.trimIndent()

        val body = parseSosadBodyDetail(html, "https://sosad.fun/posts/16290389")

        assertEquals(listOf("正文第一段", "正文第二段", "正文第三段"), body.blocks.map { it.text })
        assertFalse(body.text.contains("这是被折叠截断的预览"))
        assertFalse(body.text.contains("展开"))
    }

    @Test
    fun parseBodyClassifiesWarningAndAuthorNoteGrayoutBlocksForInsert() {
        val html = """
            <html>
              <body>
                <div id="post16308842">
                  <div class="message-body">
                    <div class="bbWrapper">
                      <h2 class="chapter-title_02">第21章 IF线01</h2>
                      <div class="text-center grayout warning-tag">建设一些舅侄盖饭，是一条车祸没有发生的IF线，但是比正文阴暗不少 Warning：内含未成年人偷窥性爱场景</div>
                      <p>正文第一段</p>
                      <div class="text-left grayout">这几天欧美同人看多了文风都变了，但感觉意外地适合这个有点阴间的IF线（摸下巴）<br/>不管怎样先祝大家520快乐</div>
                    </div>
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val body = parseSosadBodyDetail(html, "https://sosad.fun/posts/16308842")
        val prepared = prepareSosadInsertChapterBody(
            book = null,
            parameters = InsertChapterParameters(
                sourceType = INSERT_CHAPTER_SOURCE_SOSAD,
                sosadQuery = "https://sosad.fun/posts/16308842",
                sosadAuthCookie = "cookie=value",
                sosadBodyRangeStart = 1,
                sosadBodyRangeEnd = 0,
                preview = false
            ),
            chapterUrl = "https://sosad.fun/posts/16308842",
            body = body,
            reservedImageStems = mutableSetOf()
        ).first
        val renderedHtml = chapterHtml("第21章 IF线01", prepared) { "" }

        assertFalse(body.text.contains("第21章 IF线01"))
        assertEquals(SOSAD_BODY_CSS_SYS, body.blocks.first().cssClass)
        assertEquals(SOSAD_BODY_CSS_AUTHOR_NOTE, body.blocks[2].cssClass)
        assertTrue(renderedHtml.contains("<div class=\"sys\">\n    <p>建设一些舅侄盖饭"))
        assertTrue(renderedHtml.contains("<p>正文第一段</p>"))
        assertTrue(renderedHtml.contains("<p>-----------------------</p>"))
        assertTrue(renderedHtml.contains("<p>这几天欧美同人"))
        assertTrue(renderedHtml.contains("<p>不管怎样先祝大家520快乐</p>"))
        assertFalse(renderedHtml.contains("""<div class="sys">这几天欧美同人"""))
    }

    @Test
    fun parseBodyDoesNotTreatPlainSysClassAsSpecialBlock() {
        val html = """
            <html><body>
              <div id="post16308842">
                <div class="message-body">
                  <div class="sys">旧规则系统块</div>
                  <p>正文第一段</p>
                </div>
              </div>
            </body></html>
        """.trimIndent()

        val body = parseSosadBodyDetail(html, "https://sosad.fun/posts/16308842")

        assertEquals(listOf("旧规则系统块", "正文第一段"), body.blocks.map { it.text })
        assertEquals(listOf("", ""), body.blocks.map { it.cssClass })
    }

    @Test
    fun parseBodyScopesToRequestedPostAndStopsBeforeForumMode() {
        val html = """
            <html><body>
              <div id="post111">
                <div class="message-body"><p>错误帖子正文</p></div>
              </div>
              <div id="post222">
                <div class="message-body">
                  <p>目标正文第一段</p>
                  <p>目标正文第二段</p>
                  <p>进入论坛模式</p>
                  <p>论坛后的噪音</p>
                </div>
              </div>
            </body></html>
        """.trimIndent()

        val body = parseSosadBodyDetail(html, "https://sosad.fun/posts/222")

        assertEquals("目标正文第一段\n目标正文第二段", body.text)
        assertEquals(listOf("目标正文第一段", "目标正文第二段"), body.blocks.map { it.text })
        assertFalse(body.text.contains("错误帖子正文"))
        assertFalse(body.text.contains("论坛后的噪音"))
    }

    @Test
    fun parseBodyCollectsAllowedImagesAndDeduplicatesUrls() {
        val html = """
            <html><body>
              <div id="post333">
                <div class="message-body">
                  <p>正文第一段</p>
                  <img src="/images/pic.jpg?x=1" />
                  <a href="/images/pic.jpg?x=1">重复图片</a>
                  <img src="/avatar/icon.png" />
                  <img srcset="/images/second.webp 1x, /images/second@2x.webp 2x" />
                  https i ibb co abc123 image name png
                </div>
              </div>
            </body></html>
        """.trimIndent()

        val body = parseSosadBodyDetail(html, "https://sosad.fun/posts/333")

        assertEquals(
            listOf(
                "https://sosad.fun/images/pic.jpg?x=1",
                "https://sosad.fun/images/second.webp",
                "https://i.ibb.co/abc123/image-name.png"
            ),
            body.blocks.mapNotNull { it.imageUrl.takeIf(String::isNotBlank) }
        )
        assertEquals(listOf("正文第一段"), body.blocks.mapNotNull { it.text.takeIf(String::isNotBlank) })
    }
}
