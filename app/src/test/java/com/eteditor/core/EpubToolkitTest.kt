package com.eteditor.core

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubToolkitTest {
    @Test
    fun parseReadsMinimalEpubMetadataSpineAndTocChapter() {
        val book = EpubToolkit.parse(minimalEpubBytes(), "sample")

        assertEquals("Sample Book", book.metadataTitle)
        assertEquals("Writer", book.metadataAuthor)
        assertEquals("OEBPS/content.opf", book.opfPath)
        assertEquals("OEBPS/toc.ncx", book.tocPath)
        assertEquals(listOf("chapter1"), book.spineIds)
        assertEquals(1, book.chapters.size)

        val chapter = book.chapters.single()
        assertEquals("HTML Chapter", chapter.title)
        assertEquals("OEBPS/Text/chapter1.xhtml", chapter.path)
        assertTrue(chapter.wordCount > 0)
        assertTrue(EpubToolkit.check(book).ok)
    }

    @Test
    fun exportWritesUpdatedMetadataChapterHtmlAndToc() {
        val book = EpubToolkit.parse(minimalEpubBytes(), "sample")
        val chapter = book.chapters.single()
        book.metadataTitle = "Updated Book"
        book.metadataAuthor = "New Writer"
        chapter.title = "Updated Chapter"
        chapter.html = chapterHtml("Updated Chapter", "Updated body")

        val exported = EpubToolkit.export(book)
        val entries = unzip(exported)
        val exportedHtml = entries.getValue("OEBPS/Text/chapter1.xhtml").text()
        val reparsed = EpubToolkit.parse(exported, "sample")

        assertEquals("mimetype", entries.keys.first())
        assertEquals("Updated Book", reparsed.metadataTitle)
        assertEquals("New Writer", reparsed.metadataAuthor)
        assertEquals("Updated Chapter", reparsed.chapters.single().title)
        assertTrue(exportedHtml.contains("Updated body"))
        assertTrue(exportedHtml.contains("encoding=\"utf-8\""))
        assertNotNull(entries["OEBPS/toc.ncx"])
    }

    @Test
    fun refreshTocEntriesUpdatesNcxFromCurrentHtmlTitle() {
        val book = EpubToolkit.parse(minimalEpubBytes(), "sample")
        val chapter = book.chapters.single()
        chapter.title = "旧内存标题"
        chapter.html = chapterHtml("实时标题", "Chapter body text")

        EpubToolkit.refreshTocEntries(book)

        val entries = parseNcxEntries(book.entries.getValue("OEBPS/toc.ncx"), "OEBPS/toc.ncx")
        assertEquals(TocEntry("实时标题", 0), entries["OEBPS/Text/chapter1.xhtml"])
    }

    @Test
    fun checkReportsMissingEntriesAndEmptyChapterWarnings() {
        val book = EpubToolkit.parse(minimalEpubBytes(), "sample")
        val chapter = book.chapters.single()
        book.entries.remove("mimetype")
        book.entries.remove("OEBPS/toc.ncx")
        book.entries.remove(chapter.path)
        chapter.title = ""
        chapter.wordCount = 0

        val report = EpubToolkit.check(book)

        assertTrue(report.errors.contains("toc.ncx 文件缺失：OEBPS/toc.ncx"))
        assertTrue(report.errors.contains("manifest 中有 1 个 HTML 文件缺失"))
        assertTrue(report.warnings.contains("缺少 EPUB mimetype 条目"))
        assertTrue(report.warnings.contains("1 个章节标题为空"))
        assertTrue(report.warnings.contains("1 个章节正文为空或无法统计字数"))
    }

    private fun minimalEpubBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putStoredEntry("mimetype", "application/epub+zip".bytes())
            zip.putDeflatedEntry("META-INF/container.xml", containerXml.bytes())
            zip.putDeflatedEntry("OEBPS/content.opf", contentOpf.bytes())
            zip.putDeflatedEntry("OEBPS/toc.ncx", tocNcx.bytes())
            zip.putDeflatedEntry(
                "OEBPS/Text/chapter1.xhtml",
                chapterHtml("HTML Chapter", "Chapter body text").bytes()
            )
        }
        return output.toByteArray()
    }

    private fun chapterHtml(title: String, body: String): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                    <title>$title</title>
                    <meta charset="UTF-8" />
                </head>
                <body>
                    <h1>$title</h1>
                    <p>$body</p>
                </body>
            </html>
        """.trimIndent()
    }

    private fun String.bytes(): ByteArray = toByteArray(StandardCharsets.UTF_8)

    private fun ByteArray.text(): String = String(this, StandardCharsets.UTF_8)

    private companion object {
        val containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml" />
                </rootfiles>
            </container>
        """.trimIndent()

        val contentOpf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Sample Book</dc:title>
                    <dc:creator>Writer</dc:creator>
                </metadata>
                <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml" />
                    <item id="chapter1" href="Text/chapter1.xhtml" media-type="application/xhtml+xml" />
                </manifest>
                <spine toc="ncx">
                    <itemref idref="chapter1" />
                </spine>
            </package>
        """.trimIndent()

        val tocNcx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                <head>
                    <meta name="dtb:depth" content="1" />
                </head>
                <docTitle>
                    <text>Sample Book</text>
                </docTitle>
                <navMap>
                    <navPoint id="navPoint-1" playOrder="1">
                        <navLabel>
                            <text>Chapter From Toc</text>
                        </navLabel>
                        <content src="Text/chapter1.xhtml" />
                    </navPoint>
                </navMap>
            </ncx>
        """.trimIndent()
    }
}
