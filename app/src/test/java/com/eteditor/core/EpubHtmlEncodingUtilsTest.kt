package com.eteditor.core

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubHtmlEncodingUtilsTest {
    @Test
    fun decodeEpubHtmlBytesUsesBomBeforeDeclaredCharset() {
        val html = "<html><body>标题</body></html>"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            html.toByteArray(StandardCharsets.UTF_16LE)

        assertEquals(html, decodeEpubHtmlBytes(bytes))
    }

    @Test
    fun decodeEpubHtmlBytesUsesUtf16BeBom() {
        val html = "<html><body>标题</body></html>"
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            html.toByteArray(StandardCharsets.UTF_16BE)

        assertEquals(html, decodeEpubHtmlBytes(bytes))
    }

    @Test
    fun decodeEpubHtmlBytesUsesUtf8BomAndTrimsBomMarker() {
        val html = "\uFEFF<html><body>标题</body></html>"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            html.toByteArray(StandardCharsets.UTF_8)

        assertEquals(html.drop(1), decodeEpubHtmlBytes(bytes))
    }

    @Test
    fun decodeEpubHtmlBytesUsesDeclaredChineseCharset() {
        val html = """<?xml version="1.0" encoding="gbk"?><html><body>第一章</body></html>"""
        val bytes = html.toByteArray(Charset.forName("GBK"))

        assertEquals(html, decodeEpubHtmlBytes(bytes))
    }

    @Test
    fun decodeEpubHtmlBytesUsesXmlEncodingBeforeMetaCharset() {
        val html = """
            <?xml version="1.0" encoding="gbk"?>
            <html><head><meta charset="utf-8"></head><body>第一章</body></html>
        """.trimIndent()
        val bytes = html.toByteArray(Charset.forName("GBK"))

        assertEquals(html, decodeEpubHtmlBytes(bytes))
    }

    @Test
    fun decodeEpubHtmlBytesMapsChineseCharsetAliasesCaseInsensitively() {
        val html = """<html><head><meta charset="WINDOWS-936"></head><body>第一章</body></html>"""
        val bytes = html.toByteArray(Charset.forName("GBK"))

        assertEquals(html, decodeEpubHtmlBytes(bytes))
    }

    @Test
    fun decodeEpubHtmlBytesUsesMetaCharsetAlias() {
        val html = "<html><head><meta charset=\"gb2312\"></head><body>第一章</body></html>"
        val bytes = html.toByteArray(Charset.forName("GBK"))

        assertEquals(html, decodeEpubHtmlBytes(bytes))
    }

    @Test
    fun decodeEpubHtmlBytesUsesBig5CharsetAlias() {
        val html = """<html><head><meta charset="big5-hkscs"></head><body>繁體</body></html>"""
        val bytes = html.toByteArray(Charset.forName("Big5"))

        assertEquals(html, decodeEpubHtmlBytes(bytes))
    }

    @Test
    fun decodeEpubHtmlBytesFallsBackToUtf8WhenDeclaredCharsetIsUnknown() {
        val html = """<html><head><meta charset="unknown-charset"></head><body>标题</body></html>"""

        assertEquals(html, decodeEpubHtmlBytes(html.toByteArray(StandardCharsets.UTF_8)))
    }

    @Test
    fun decodeEpubHtmlBytesFallsBackToChineseCharsetWhenNoCharsetIsDeclared() {
        val html = "<html><body>第一章</body></html>"
        val bytes = html.toByteArray(Charset.forName("GBK"))

        assertEquals(html, decodeEpubHtmlBytes(bytes))
    }

    @Test
    fun normalizeEpubHtmlUtf8DeclarationUpdatesXmlAndMetaCharsets() {
        val html = """
            <?xml version="1.0" encoding="gbk"?>
            <html>
              <head>
                <meta http-equiv="Content-Type" content="text/html; charset=GB2312" />
                <meta charset='Big5'>
              </head>
              <body>正文</body>
            </html>
        """.trimIndent()

        val normalized = normalizeEpubHtmlUtf8Declaration(html)

        assertTrue(normalized.contains("""encoding="utf-8""""))
        assertTrue(normalized.contains("""charset=utf-8""""))
        assertTrue(normalized.contains("""charset='utf-8'"""))
    }

    @Test
    fun normalizeEpubHtmlUtf8DeclarationKeepsMetaTagsWithoutCharset() {
        val html = """<html><head><meta name="viewport" content="width=device-width"></head><body>正文</body></html>"""

        assertEquals(html, normalizeEpubHtmlUtf8Declaration(html))
    }

    @Test
    fun encodeEpubHtmlUtf8NormalizesDeclarationAndWritesUtf8Bytes() {
        val encoded = encodeEpubHtmlUtf8(
            """<?xml version="1.0" encoding="gbk"?><html><body>标题</body></html>"""
        )
        val text = String(encoded, StandardCharsets.UTF_8)

        assertTrue(text.contains("""encoding="utf-8""""))
        assertTrue(text.contains("标题"))
    }

    @Test
    fun updateEpubChapterHtmlEntryNormalizesChapterHtmlAndEntryBytes() {
        val chapter = EpubChapter(
            id = "c1",
            href = "Text/c1.xhtml",
            path = "OEBPS/Text/c1.xhtml",
            originalPath = "OEBPS/Text/c1.xhtml",
            pathAliases = mutableSetOf("OEBPS/Text/c1.xhtml"),
            title = "第一章",
            html = """<?xml version="1.0" encoding="gbk"?><html><body>正文</body></html>""",
            wordCount = 2
        )
        val book = EpubBook(
            originalName = "book.epub",
            metadataTitle = "Book",
            metadataAuthor = "",
            metadataItems = mutableListOf(),
            entries = linkedMapOf(),
            opfPath = "OEBPS/content.opf",
            tocPath = null,
            manifest = mutableMapOf(),
            spineIds = mutableListOf("c1"),
            chapters = mutableListOf(chapter)
        )

        updateEpubChapterHtmlEntry(book, chapter)

        assertTrue(chapter.html.contains("""encoding="utf-8""""))
        assertEquals(chapter.html, String(book.entries.getValue(chapter.path), StandardCharsets.UTF_8))
    }
}
