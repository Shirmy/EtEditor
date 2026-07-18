package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubBodySelectionTagUtilsTest {
    @Test
    fun wrapWarningUsesWholeLinesAndPackagesParagraphs() {
        val body = "<p>刚开学没多久的孩</p>\r\n<p>韩煜城放学后</p>"
        val start = body.indexOf("没多久")
        val end = body.indexOf("韩煜城") + 1
        val result = wrapEpubBodySelectionAsWarning(body, start, end)

        assertTrue(result.success)
        assertTrue(
            result.nextBody.contains(
                "<div class=\"sys\">\r\n" +
                    "<p>刚开学没多久的孩</p>\r\n" +
                    "<p>韩煜城放学后</p>\r\n" +
                    "</div>"
            )
        )
    }

    @Test
    fun wrapWarningPutsClosingDivOnItsOwnLine() {
        val body = """<p class="post">书名：漂亮皮囊</p>"""
        val start = body.indexOf("书名")
        val end = start + 2
        val result = wrapEpubBodySelectionAsWarning(body, start, end)

        assertTrue(result.success)
        assertEquals(
            "<div class=\"sys\">\r\n" +
                """<p class="post">书名：漂亮皮囊</p>""" +
                "\r\n</div>",
            result.nextBody
        )
    }

    @Test
    fun wrapWarningSkipsWhenAlreadyInsideSys() {
        val body = "<div class=\"sys\">\r\n<p>韩煜城眉</p></div>"
        val start = body.indexOf("韩煜城眉")
        val end = start + 4
        val result = wrapEpubBodySelectionAsWarning(body, start, end)

        assertFalse(result.success)
        assertEquals("所选内容已在预警中，未处理", result.message)
    }

    @Test
    fun wrapWarningRequiresSelection() {
        val body = "<p>韩煜城眉</p>"
        val result = wrapEpubBodySelectionAsWarning(body, 3, 3)
        assertFalse(result.success)
        assertEquals("请先选中文字", result.message)
    }

    @Test
    fun authorNoteInsertsSeparatorBeforeFirstSelectedLineOnly() {
        val body = "<p>第一段</p>\r\n<p>第二段</p>"
        val start = body.indexOf("一段")
        val end = body.indexOf("二段") + 1
        val result = insertEpubBodyAuthorNoteSeparator(body, start, end)

        assertTrue(result.success)
        assertTrue(
            result.nextBody.startsWith(
                "<p>$EPUB_AUTHOR_NOTE_SEPARATOR_TEXT</p>\r\n<p>第一段</p>"
            )
        )
        assertEquals(
            1,
            Regex(Regex.escape(EPUB_AUTHOR_NOTE_SEPARATOR_TEXT)).findAll(result.nextBody).count()
        )
    }

    @Test
    fun authorNoteSkipsWhenSeparatorAlreadyAbove() {
        val body =
            "<p>$EPUB_AUTHOR_NOTE_SEPARATOR_TEXT</p>\r\n<p>第一段</p>\r\n<p>第二段</p>"
        val start = body.indexOf("第一段")
        val end = start + 3
        val result = insertEpubBodyAuthorNoteSeparator(body, start, end)

        assertFalse(result.success)
        assertEquals("所选前方已有分隔线，未处理", result.message)
    }

    @Test
    fun annotationInsertsNoteRefAndEmptyFootnoteOnSameLineSelection() {
        val body = "<p>刚开学没多久的孩</p>\r\n<p>韩煜城放学后</p>"
        val start = body.indexOf("没多久")
        val end = start + 3
        val result = insertEpubBodyAnnotation(body, start, end, "../Images/note.webp")

        assertTrue(result.success)
        assertTrue(
            result.nextBody.contains(
                "没多久<a epub:type=\"noteref\" href=\"#01\"><sup><img style=\"width: 0.85em;\" alt=\"note\" src=\"../Images/note.webp\"/></sup></a>的孩"
            )
        )
        assertTrue(result.nextBody.contains("<aside epub:type=\"footnote\" id=\"01\">"))
        assertTrue(result.nextBody.contains("</aside>"))
        assertTrue(result.nextBody.contains("<p>韩煜城放学后</p>"))
        // 脚注块内保留空行
        assertTrue(
            result.nextBody.contains(
                "<aside epub:type=\"footnote\" id=\"01\">\r\n\r\n</aside>"
            )
        )
    }

    @Test
    fun annotationRejectsCrossLineSelection() {
        val body = "<p>刚开学没多久的孩</p>\r\n<p>韩煜城放学后</p>"
        val start = body.indexOf("没多久")
        val end = body.indexOf("韩煜城") + 2
        val result = insertEpubBodyAnnotation(body, start, end, "../Images/note.webp")

        assertFalse(result.success)
        assertEquals("注解不支持跨段选择", result.message)
    }

    @Test
    fun annotationIncrementsFootnoteId() {
        val body =
            "<p>已有<a epub:type=\"noteref\" href=\"#01\">x</a></p>\r\n" +
                "<aside epub:type=\"footnote\" id=\"01\">\r\n\r\n</aside>\r\n" +
                "<p>刚开学没多久的孩</p>"
        val start = body.indexOf("没多久")
        val end = start + 3
        val result = insertEpubBodyAnnotation(body, start, end, "../Images/note.webp")

        assertTrue(result.success)
        assertTrue(result.nextBody.contains("href=\"#02\""))
        assertTrue(result.nextBody.contains("id=\"02\""))
    }

    @Test
    fun annotationRequiresSelection() {
        val result = insertEpubBodyAnnotation("<p>正文</p>", 1, 1, "../Images/note.webp")
        assertFalse(result.success)
        assertEquals("请先选中文字", result.message)
    }
}
