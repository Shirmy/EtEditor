package com.eteditor

import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.ManifestItem
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubStructureUtilsTest {
    @Test
    fun chapterTypeHelpersDetectCoverVolumeAndBodyChapters() {
        val cover = chapter("cover", "OEBPS/Text/Section0001.xhtml", "封面")
        val volume = chapter("v1", "OEBPS/Text/Vol01.xhtml", "第一卷")
        val body = chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 开始")

        assertTrue(cover.isCoverSection0001Or0002())
        assertTrue(volume.isVolumeChapter())
        assertTrue(body.isEpubBodyNumberedChapter())
        assertFalse(cover.isEpubBodyNumberedChapter())
        assertFalse(volume.isEpubBodyNumberedChapter())
    }

    @Test
    fun chapterTypeHelpersTreatVolumeAliasesAsVolumeChapters() {
        val renamedVolume = chapter("v1", "OEBPS/Text/Chapter0001.xhtml", "第一卷")
        renamedVolume.pathAliases += "OEBPS/Text/VolF1.xhtml"

        assertTrue(renamedVolume.isVolumeChapter())
        assertFalse(renamedVolume.isEpubBodyNumberedChapter())
    }

    @Test
    fun updateEpubChapterItemModelRenamesFileAndTitleAcrossBookState() {
        val book = sampleBook(
            listOf(
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 旧标题")
            )
        )

        val result = updateEpubChapterItemModel(
            book = book,
            chapterIndex = 0,
            fileName = "Chapter0009",
            chapterTitle = "第1章 新标题"
        )

        assertTrue(result.success)
        assertEquals("OEBPS/Text/Chapter0009.xhtml", book.chapters[0].path)
        assertEquals("Text/Chapter0009.xhtml", book.chapters[0].href)
        assertEquals("第1章 新标题", book.chapters[0].title)
        assertTrue(book.entries.containsKey("OEBPS/Text/Chapter0009.xhtml"))
        assertFalse(book.entries.containsKey("OEBPS/Text/Chapter0001.xhtml"))
        assertEquals("OEBPS/Text/Chapter0009.xhtml", book.manifest.getValue("c1").path)
        assertTrue(book.chapters[0].html.contains("<title>第1章 新标题</title>"))
    }

    @Test
    fun updateEpubChapterItemModelRebuildsVolumeLevelsWhenChildChapterBecomesSection() {
        val book = sampleBook(
            listOf(
                chapter("v1", "OEBPS/Text/Vol01.xhtml", "Volume 1"),
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "Chapter 1"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "Chapter 2")
            )
        )
        applyVolumeTocLevels(book)

        val result = updateEpubChapterItemModel(
            book = book,
            chapterIndex = 1,
            fileName = "Section0002",
            chapterTitle = "Section 2"
        )

        assertTrue(result.success)
        assertEquals("OEBPS/Text/Section0002.xhtml", book.chapters[1].path)
        assertEquals(listOf(0, 0, 0), book.chapters.map { it.tocLevel })
    }

    @Test
    fun updateEpubChapterItemModelRejectsBlankTitleAndExistingFileName() {
        val book = sampleBook(
            listOf(
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章")
            )
        )

        val blank = updateEpubChapterItemModel(book, chapterIndex = 0, fileName = "Chapter0001", chapterTitle = " ")
        val duplicate = updateEpubChapterItemModel(book, chapterIndex = 0, fileName = "Chapter0002", chapterTitle = "第1章")

        assertFalse(blank.success)
        assertEquals("章节标题不能为空", blank.message)
        assertFalse(duplicate.success)
        assertEquals("文件名已存在：Chapter0002.xhtml", duplicate.message)
        assertEquals("OEBPS/Text/Chapter0001.xhtml", book.chapters[0].path)
    }

    @Test
    fun updateEpubChapterItemModelUsesCoverFallbackForBlankCoverTitle() {
        val coverHtml = "<html><body><img src=\"../Images/cover.jpg\"/></body></html>"
        val book = sampleBook(
            listOf(
                chapter("cover", "OEBPS/Text/cover.xhtml", "旧封面标题", coverHtml)
            )
        )

        val result = updateEpubChapterItemModel(
            book = book,
            chapterIndex = 0,
            fileName = "cover",
            chapterTitle = ""
        )

        assertTrue(result.success)
        assertEquals("封面", book.chapters[0].title)
        assertEquals(coverHtml, book.chapters[0].html)
    }

    @Test
    fun moveEpubChapterAfterInBookUpdatesChapterAndSpineOrder() {
        val book = sampleBook(
            listOf(
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章"),
                chapter("v1", "OEBPS/Text/Vol01.xhtml", "第一卷"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章")
            )
        )

        val result = moveEpubChapterAfterInBook(
            book = book,
            sourceIndex = 0,
            targetIndex = 99,
            bookStartTarget = -1,
            bookEndTarget = 99
        )

        assertTrue(result.success)
        assertEquals(2, result.nextPreviewIndex)
        assertEquals(listOf("v1", "c2", "c1"), book.chapters.map { it.id })
        assertEquals(listOf("v1", "c2", "c1"), book.spineIds)
        assertEquals(listOf(0, 1, 1), book.chapters.map { it.tocLevel })
    }

    @Test
    fun moveEpubChapterAfterInBookSupportsBookStartSentinel() {
        val book = sampleBook(
            listOf(
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章"),
                chapter("c3", "OEBPS/Text/Chapter0003.xhtml", "第3章")
            )
        )

        val result = moveEpubChapterAfterInBook(
            book = book,
            sourceIndex = 2,
            targetIndex = -1,
            bookStartTarget = -1,
            bookEndTarget = 99
        )

        assertTrue(result.success)
        assertEquals(0, result.nextPreviewIndex)
        assertEquals(listOf("c3", "c1", "c2"), book.chapters.map { it.id })
        assertEquals(listOf("c3", "c1", "c2"), book.spineIds)
    }

    @Test
    fun moveEpubChapterAfterInBookRebuildsSpineWhenSpineStateIsMismatched() {
        val book = sampleBook(
            listOf(
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章"),
                chapter("c3", "OEBPS/Text/Chapter0003.xhtml", "第3章")
            )
        )
        book.spineIds.clear()

        val result = moveEpubChapterAfterInBook(
            book = book,
            sourceIndex = 2,
            targetIndex = 0,
            bookStartTarget = -1,
            bookEndTarget = 99
        )

        assertTrue(result.success)
        assertEquals(1, result.nextPreviewIndex)
        assertEquals(listOf("c1", "c3", "c2"), book.chapters.map { it.id })
        assertEquals(listOf("c1", "c3", "c2"), book.spineIds)
    }

    @Test
    fun moveEpubChapterAfterInBookRejectsSameTargetWithoutReordering() {
        val book = sampleBook(
            listOf(
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章"),
                chapter("c3", "OEBPS/Text/Chapter0003.xhtml", "第3章")
            )
        )
        val originalChapterIds = book.chapters.map { it.id }
        val originalSpineIds = book.spineIds.toList()

        val result = moveEpubChapterAfterInBook(
            book = book,
            sourceIndex = 1,
            targetIndex = 1,
            bookStartTarget = -1,
            bookEndTarget = 99
        )

        assertFalse(result.success)
        assertEquals(originalChapterIds, book.chapters.map { it.id })
        assertEquals(originalSpineIds, book.spineIds)
    }

    @Test
    fun deleteEpubChapterFromBookRejectsLastChapterAndCleansBookState() {
        val cover = chapter("cover", "OEBPS/Text/cover.xhtml", "封面")
        cover.pathAliases += "OEBPS/Text/old-cover.xhtml"
        val body = chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章")
        val book = sampleBook(listOf(cover, body))
        book.entries["OEBPS/Text/old-cover.xhtml"] = "old".toByteArray(StandardCharsets.UTF_8)

        val deleted = deleteEpubChapterFromBook(book, chapterIndex = 0)

        assertTrue(deleted.success)
        assertEquals("封面", deleted.deletedDisplayTitle)
        assertEquals(0, deleted.nextPreviewIndex)
        assertEquals(listOf("c1"), book.chapters.map { it.id })
        assertEquals(listOf("c1"), book.spineIds)
        assertFalse(book.manifest.containsKey("cover"))
        assertFalse(book.entries.containsKey("OEBPS/Text/cover.xhtml"))
        assertFalse(book.entries.containsKey("OEBPS/Text/old-cover.xhtml"))

        val last = deleteEpubChapterFromBook(book, chapterIndex = 0)

        assertFalse(last.success)
        assertEquals("至少需要保留 1 个 HTML 章节", last.message)
    }

    @Test
    fun deleteEpubChapterFromBookResequencesBodyTitlesFilesAndEntries() {
        val book = sampleBook(
            listOf(
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章"),
                chapter("c3", "OEBPS/Text/Chapter0003.xhtml", "第3章")
            )
        )

        val result = deleteEpubChapterFromBook(book, chapterIndex = 1)

        assertTrue(result.success)
        assertEquals(EpubStructureResequenceResult(renamedFiles = 1, renamedTitles = 1), result.resequence)
        assertEquals(listOf("c1", "c3"), book.chapters.map { it.id })
        assertEquals(listOf("第1章", "第2章"), book.chapters.map { it.title })
        assertEquals(listOf("OEBPS/Text/Chapter0001.xhtml", "OEBPS/Text/Chapter0002.xhtml"), book.chapters.map { it.path })
        assertEquals(listOf("c1", "c3"), book.spineIds)
        assertFalse(book.manifest.containsKey("c2"))
        assertEquals("OEBPS/Text/Chapter0002.xhtml", book.manifest.getValue("c3").path)
        assertTrue(book.entries.containsKey("OEBPS/Text/Chapter0001.xhtml"))
        assertTrue(book.entries.containsKey("OEBPS/Text/Chapter0002.xhtml"))
        assertFalse(book.entries.containsKey("OEBPS/Text/Chapter0003.xhtml"))
        assertTrue(String(book.entries.getValue("OEBPS/Text/Chapter0002.xhtml"), StandardCharsets.UTF_8).contains("第2章"))
    }

    @Test
    fun deleteEpubBodyLineFromBookRejectsLastLineAndUpdatesEntryOnSuccess() {
        val html = "<html><body><p>第一行</p>\n<p>第二行</p></body></html>"
        val book = sampleBook(listOf(chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章", html)))

        val result = deleteEpubBodyLineFromBook(book, chapterIndex = 0, lineIndex = 0)

        assertTrue(result.success)
        assertFalse(book.chapters[0].html.contains("第一行"))
        assertTrue(book.chapters[0].html.contains("第二行"))
        assertEquals(book.chapters[0].html, String(book.entries.getValue(book.chapters[0].path), StandardCharsets.UTF_8))

        val failed = deleteEpubBodyLineFromBook(book, chapterIndex = 0, lineIndex = 0)

        assertFalse(failed.success)
        assertEquals("删除后当前章节正文为空", failed.message)
    }

    @Test
    fun deleteEpubBodyLineFromBookRejectsInvalidIndicesWithoutChangingEntry() {
        val html = "<html><body><p>第一行</p>\n<p>第二行</p></body></html>"
        val book = sampleBook(listOf(chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章", html)))
        val originalHtml = book.chapters[0].html
        val originalEntry = book.entries.getValue("OEBPS/Text/Chapter0001.xhtml").toList()

        val invalidChapter = deleteEpubBodyLineFromBook(book, chapterIndex = 99, lineIndex = 0)
        val invalidLine = deleteEpubBodyLineFromBook(book, chapterIndex = 0, lineIndex = 99)

        assertFalse(invalidChapter.success)
        assertFalse(invalidLine.success)
        assertEquals(originalHtml, book.chapters[0].html)
        assertEquals(originalEntry, book.entries.getValue("OEBPS/Text/Chapter0001.xhtml").toList())
    }

    @Test
    fun splitEpubChapterAtLineInBookCreatesNewChapterAndManifestEntry() {
        val html = "<html><head><title>第1章</title></head><body><h1 class=\"chapter-title_02\">第1章</h1>\n<p>前半</p>\n<h1>第2章 新章</h1>\n<p>后半</p></body></html>"
        val book = sampleBook(listOf(chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章", html)))

        val result = splitEpubChapterAtLineInBook(
            book = book,
            chapterIndex = 0,
            lineNumberText = "3",
            newTitleText = "",
            dropSplitLineFromBody = true
        )

        assertTrue(result.success)
        assertEquals(2, book.chapters.size)
        assertEquals("第2章 新章", result.newTitle)
        assertEquals("c1", book.spineIds[0])
        assertEquals(book.chapters[1].id, book.spineIds[1])
        assertTrue(book.manifest.containsKey(book.chapters[1].id))
        assertTrue(book.entries.containsKey(book.chapters[1].path))
        assertTrue(book.chapters[0].html.contains("前半"))
        assertTrue(book.chapters[1].html.contains("后半"))
    }

    @Test
    fun splitEpubChapterAtLineInBookRejectsInvalidPositionWithoutChangingBook() {
        val html = "<html><body><p>前半</p>\n<p>后半</p></body></html>"
        val book = sampleBook(listOf(chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章", html)))
        val originalHtml = book.chapters[0].html
        val originalEntry = book.entries.getValue("OEBPS/Text/Chapter0001.xhtml").toList()

        val beforeFirstLine = splitEpubChapterAtLineInBook(
            book = book,
            chapterIndex = 0,
            lineNumberText = "1",
            newTitleText = "第2章"
        )
        val notNumber = splitEpubChapterAtLineInBook(
            book = book,
            chapterIndex = 0,
            lineNumberText = "abc",
            newTitleText = "第2章"
        )

        assertFalse(beforeFirstLine.success)
        assertEquals("分章位置必须在 2-2 行之间", beforeFirstLine.message)
        assertFalse(notNumber.success)
        assertEquals("分章位置必须在 2-2 行之间", notNumber.message)
        assertEquals(listOf("c1"), book.chapters.map { it.id })
        assertEquals(listOf("c1"), book.spineIds)
        assertEquals(originalHtml, book.chapters[0].html)
        assertEquals(originalEntry, book.entries.getValue("OEBPS/Text/Chapter0001.xhtml").toList())
    }

    @Test
    fun splitEpubChapterAtLineInBookRejectsEmptySecondChapterWithoutChangingBook() {
        val html = "<html><body><p>前半</p>\n<h1>第2章 空章</h1></body></html>"
        val book = sampleBook(listOf(chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章", html)))
        val originalHtml = book.chapters[0].html
        val originalEntryKeys = book.entries.keys.toList()

        val result = splitEpubChapterAtLineInBook(
            book = book,
            chapterIndex = 0,
            lineNumberText = "2",
            newTitleText = "",
            dropSplitLineFromBody = true
        )

        assertFalse(result.success)
        assertEquals("分章后不能产生空章节", result.message)
        assertEquals(listOf("c1"), book.chapters.map { it.id })
        assertEquals(listOf("c1"), book.spineIds)
        assertEquals(originalHtml, book.chapters[0].html)
        assertEquals(originalEntryKeys, book.entries.keys.toList())
    }

    @Test
    fun splitTitleSuggestionAndPathSkipBlankLinesAndExistingSplitPath() {
        val book = sampleBook(
            listOf(
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章"),
                chapter("c2", "OEBPS/Text/Chapter0001_split.xhtml", "已存在")
            )
        )

        assertEquals(
            "第2章 新章",
            suggestEpubSplitChapterTitleFromBodyLines(
                listOf("", "<h1>第2章 新章</h1>", "<p>正文</p>"),
                lineNumberText = "1"
            )
        )
        assertEquals("OEBPS/Text/Chapter0001_split_1.xhtml", splitEpubChapterPath(book, "OEBPS/Text/Chapter0001.xhtml"))
    }

    @Test
    fun buildEpubStructureChangeMessageIncludesResequenceCounts() {
        assertEquals(
            "已删除；文件名连号 2，标题顺序 1",
            buildEpubStructureChangeMessage("已删除", EpubStructureResequenceResult(2, 1))
        )
        assertEquals(
            "已删除",
            buildEpubStructureChangeMessage("已删除", EpubStructureResequenceResult(0, 0))
        )
    }

    @Test
    fun rebuildHtmlWithBodyContentNormalizesBoundaryLineEndings() {
        assertEquals(
            "<html><body>\r\n<p>正文</p>\r\n</body></html>",
            rebuildHtmlWithBodyContent("<html><body>\n\n", "\r\n<p>正文</p>\n", "\n</body></html>")
        )
    }

    private fun sampleBook(chapters: List<EpubChapter>): EpubBook {
        val entries = linkedMapOf<String, ByteArray>()
        val manifest = mutableMapOf<String, ManifestItem>()
        chapters.forEach { chapter ->
            entries[chapter.path] = chapter.html.toByteArray(StandardCharsets.UTF_8)
            manifest[chapter.id] = ManifestItem(
                id = chapter.id,
                href = chapter.href,
                mediaType = "application/xhtml+xml",
                path = chapter.path
            )
        }
        return EpubBook(
            originalName = "book.epub",
            metadataTitle = "Book",
            metadataAuthor = "",
            entries = entries,
            opfPath = "OEBPS/content.opf",
            tocPath = "OEBPS/toc.ncx",
            manifest = manifest,
            spineIds = chapters.map { it.id }.toMutableList(),
            chapters = chapters.toMutableList()
        )
    }

    private fun chapter(
        id: String,
        path: String,
        title: String,
        html: String = "<html><head><title>$title</title></head><body><h1>$title</h1><p>正文</p></body></html>"
    ): EpubChapter {
        return EpubChapter(
            id = id,
            href = path.removePrefix("OEBPS/"),
            path = path,
            originalPath = path,
            pathAliases = mutableSetOf(path),
            title = title,
            html = html,
            wordCount = title.length
        )
    }
}
