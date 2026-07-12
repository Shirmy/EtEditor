package com.eteditor

import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.ManifestItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubPackageTextMutationUtilsTest {
    @Test
    fun deletePackageTextSelectionChangesSnapshotOnly() {
        val source = sampleBook("<p>保留</p>\n<p>删除</p>\n<p>结尾</p>")
        val body = packageBody(source)
        val start = body.indexOf("删除")
        val prepared = prepareEpubMutationModel(source, sourceContentVersion = 0) { book ->
            deleteEpubPackageTextBodySelectionFromBook(book, PACKAGE_PATH, start, start + 2)
        }

        assertTrue(prepared.result.success)
        assertTrue(prepared.result.nextBody.contains("保留"))
        assertFalse(prepared.result.nextBody.contains("删除"))
        assertTrue(packageBody(source).contains("删除"))
        assertFalse(packageBody(prepared.book).contains("删除"))
    }

    @Test
    fun wrapPackageTextSelectionChangesSnapshotOnly() {
        val source = sampleBook("第一段\n第二段\n第三段")
        val body = packageBody(source)
        val start = body.indexOf("第二段")
        val prepared = prepareEpubMutationModel(source, sourceContentVersion = 0) { book ->
            wrapEpubPackageTextBodySelectionInBook(book, PACKAGE_PATH, start, start + 3)
        }

        assertTrue(prepared.result.success)
        assertTrue(prepared.result.nextBody.contains("<p>第二段</p>"))
        assertEquals(body, packageBody(source))
        assertEquals(
            prepared.result.nextBody.replace("\r\n", "\n"),
            packageBody(prepared.book).replace("\r\n", "\n")
        )
    }

    @Test
    fun setPackageTextSelectionAsVolumeChangesSnapshotOnly() {
        val source = sampleBook("第一卷\n卷说明\n<p>保留正文</p>")
        val body = packageBody(source)
        val selectionEnd = body.indexOf("<p>保留正文</p>")
        val prepared = prepareEpubMutationModel(source, sourceContentVersion = 0) { book ->
            setEpubPackageTextVolumeFromBodySelectionInBook(
                book = book,
                path = PACKAGE_PATH,
                insertIndex = 1,
                sourceStart = 0,
                sourceEnd = selectionEnd
            )
        }

        assertTrue(prepared.result.success)
        assertEquals("第一卷", prepared.result.insertedTitle)
        assertEquals(1, prepared.result.insertedIndex)
        assertEquals(1, source.chapters.size)
        assertEquals(2, prepared.book.chapters.size)
        assertTrue(packageBody(source).contains("第一卷"))
        assertFalse(packageBody(prepared.book).contains("第一卷"))
    }

    private fun sampleBook(packageBody: String): EpubBook {
        val chapterPath = "OEBPS/Text/Chapter0001.xhtml"
        val chapterHtml = "<html><body><h1>第1章</h1><p>正文</p></body></html>"
        val chapter = EpubChapter(
            id = "c1",
            href = "Text/Chapter0001.xhtml",
            path = chapterPath,
            originalPath = chapterPath,
            pathAliases = mutableSetOf(chapterPath),
            title = "第1章",
            html = chapterHtml,
            wordCount = 2
        )
        return EpubBook(
            originalName = "book.epub",
            metadataTitle = "Book",
            metadataAuthor = "",
            entries = linkedMapOf(
                chapterPath to chapterHtml.toByteArray(Charsets.UTF_8),
                PACKAGE_PATH to "<html><body>$packageBody</body></html>".toByteArray(Charsets.UTF_8)
            ),
            opfPath = "OEBPS/content.opf",
            tocPath = "OEBPS/toc.ncx",
            manifest = linkedMapOf(
                "c1" to ManifestItem("c1", "Text/Chapter0001.xhtml", "application/xhtml+xml", chapterPath),
                "extra" to ManifestItem("extra", "Text/Extra.xhtml", "application/xhtml+xml", PACKAGE_PATH)
            ),
            spineIds = mutableListOf("c1"),
            chapters = mutableListOf(chapter)
        )
    }

    private fun packageBody(book: EpubBook): String {
        return htmlBodyContentParts(epubPackageText(book, PACKAGE_PATH).orEmpty()).body
    }

    private companion object {
        const val PACKAGE_PATH = "OEBPS/Text/Extra.xhtml"
    }
}
