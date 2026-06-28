package com.eteditor

import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.ManifestItem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Test

class EpubBookCopyUtilsTest {
    @Test
    fun mutableDeepCopyPreservesValuesAndIsolatesMutableState() {
        val original = EpubBook(
            originalName = "book.epub",
            metadataTitle = "原书名",
            metadataAuthor = "原作者",
            entries = linkedMapOf("OEBPS/Text/chapter1.xhtml" to byteArrayOf(1, 2, 3)),
            opfPath = "OEBPS/content.opf",
            tocPath = "OEBPS/toc.ncx",
            manifest = mutableMapOf(
                "c1" to ManifestItem(
                    id = "c1",
                    href = "Text/chapter1.xhtml",
                    mediaType = "application/xhtml+xml",
                    path = "OEBPS/Text/chapter1.xhtml"
                )
            ),
            spineIds = mutableListOf("c1"),
            chapters = mutableListOf(
                EpubChapter(
                    id = "c1",
                    href = "Text/chapter1.xhtml",
                    path = "OEBPS/Text/chapter1.xhtml",
                    originalPath = "OEBPS/Text/chapter1.xhtml",
                    pathAliases = mutableSetOf("OEBPS/Text/chapter1.xhtml"),
                    title = "第一章",
                    html = "<html><body><p>正文</p></body></html>",
                    wordCount = 2
                )
            )
        )

        val copy = original.mutableDeepCopy()

        copy.entries.getValue("OEBPS/Text/chapter1.xhtml")[0] = 9
        copy.manifest.getValue("c1").path = "OEBPS/Text/copied.xhtml"
        copy.spineIds += "c2"
        copy.chapters[0].title = "副本第一章"
        copy.chapters[0].pathAliases += "OEBPS/Text/alias.xhtml"

        assertNotSame(original.entries, copy.entries)
        assertNotSame(original.entries.getValue("OEBPS/Text/chapter1.xhtml"), copy.entries.getValue("OEBPS/Text/chapter1.xhtml"))
        assertNotSame(original.manifest, copy.manifest)
        assertNotSame(original.manifest.getValue("c1"), copy.manifest.getValue("c1"))
        assertNotSame(original.spineIds, copy.spineIds)
        assertNotSame(original.chapters, copy.chapters)
        assertNotSame(original.chapters[0], copy.chapters[0])
        assertNotSame(original.chapters[0].pathAliases, copy.chapters[0].pathAliases)

        assertArrayEquals(byteArrayOf(1, 2, 3), original.entries.getValue("OEBPS/Text/chapter1.xhtml"))
        assertArrayEquals(byteArrayOf(9, 2, 3), copy.entries.getValue("OEBPS/Text/chapter1.xhtml"))
        assertEquals("OEBPS/Text/chapter1.xhtml", original.manifest.getValue("c1").path)
        assertEquals(listOf("c1"), original.spineIds)
        assertEquals("第一章", original.chapters[0].title)
        assertFalse("OEBPS/Text/alias.xhtml" in original.chapters[0].pathAliases)
    }
}
