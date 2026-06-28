package com.eteditor

import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.ManifestItem
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubVolumeUtilsTest {
    @Test
    fun nextVolumeStemSkipsExistingNormalExtraAndSpecialVolumes() {
        val book = sampleBook(
            paths = listOf(
                "OEBPS/Text/Vol01.xhtml",
                "OEBPS/Text/VolF01.xhtml",
                "OEBPS/Text/Chapter0001.xhtml"
            ),
            titles = listOf("第一卷", "番外一", "正文")
        )

        assertEquals("Vol02", nextVolumeStem(book, VOLUME_KIND_NORMAL))
        assertEquals("VolF02", nextVolumeStem(book, VOLUME_KIND_SPECIAL_EXTRA))
        assertEquals("Vol00", nextVolumeStem(book, VOLUME_KIND_EXTRA))
    }

    @Test
    fun nextVolumeStemAddsSuffixWhenCalculatedStemAlreadyExists() {
        val book = sampleBook(
            paths = listOf(
                "OEBPS/Text/Vol01.xhtml",
                "OEBPS/Text/Vol01_1.xhtml"
            ),
            titles = listOf("第一卷", "旧冲突卷")
        )

        assertEquals("Vol02", nextVolumeStem(book, VOLUME_KIND_NORMAL))
        assertEquals("Vol00", nextVolumeStem(book, VOLUME_KIND_EXTRA))
        book.entries["OEBPS/Text/Vol00.xhtml"] = ByteArray(0)
        book.entries["OEBPS/Text/Vol00_1.xhtml"] = ByteArray(0)
        assertEquals("Vol00_2", nextVolumeStem(book, VOLUME_KIND_EXTRA))
    }

    @Test
    fun volumeFilePreviewAndExtraVolumeDetectionUseExistingBookStems() {
        val book = sampleBook(
            paths = listOf(
                "OEBPS/Text/Vol00.xhtml",
                "OEBPS/Text/Vol01.xhtml",
                "OEBPS/Text/VolF01.xhtml",
                "OEBPS/Text/Chapter0001.xhtml"
            ),
            titles = listOf("番外卷", "第一卷", "番外一", "正文")
        )

        assertEquals("", epubVolumeFileNamePreview(null, VOLUME_KIND_NORMAL))
        assertEquals("Vol00.xhtml", epubVolumeFileNamePreview(book, VOLUME_KIND_EXTRA))
        assertEquals("Vol02.xhtml", epubVolumeFileNamePreview(book, VOLUME_KIND_NORMAL))
        assertEquals("VolF02.xhtml", epubVolumeFileNamePreview(book, VOLUME_KIND_SPECIAL_EXTRA))
        assertTrue(hasExtraEpubVolume(book))
        assertEquals("番外卷", defaultEpubVolumeTitle(VOLUME_KIND_EXTRA))
        assertEquals("", defaultEpubVolumeTitle(VOLUME_KIND_NORMAL))
    }

    @Test
    fun applyVolumeTocLevelsMakesOnlyChaptersAfterVolumesChildItems() {
        val book = sampleBook(
            paths = listOf(
                "OEBPS/Text/Chapter0001.xhtml",
                "OEBPS/Text/Vol01.xhtml",
                "OEBPS/Text/Chapter0002.xhtml",
                "OEBPS/Text/chapter_0003.xhtml",
                "OEBPS/Text/Section0003.xhtml",
                "OEBPS/Text/Chapter0004.xhtml",
                "OEBPS/Text/VolF01.xhtml",
                "OEBPS/Text/chapter_0005.xhtml"
            ),
            titles = listOf("序章", "第一卷", "第一章", "第二章", "说明页", "平级章", "番外一", "番外章")
        )

        applyVolumeTocLevels(book)

        assertEquals(listOf(0, 0, 1, 1, 0, 0, 0, 1), book.chapters.map { it.tocLevel })
    }

    @Test
    fun cleanVolumeTitleHelpersStripHtmlAndCarryMultipleLines() {
        assertEquals("备用", cleanVolumeTitleInput("   ", fallback = "备用"))
        assertEquals("第一卷\n副标题", cleanVolumeTitleWithBreaks("<h1>第一卷</h1>\n<p>副标题</p>"))
        assertEquals("卷名", cleanEpubBodyLineTitle("<h2>卷名</h2>"))
        assertEquals("卷名 副题", cleanEpubBodyLinePlainText("<h2>卷名 副题</h2>"))
        assertEquals("第一卷\n第二卷", epubVolumeDefaultTitleFromBodyLines(listOf("<h2>第一卷</h2>", "<p>第二卷</p>", "<p>正文</p>"), 0, "2"))
        assertEquals("<p>第一行</p>\r\n<p>第三行</p>", epubBodyWithoutLine(listOf("<p>第一行</p>", "<p>第二行</p>", "<p>第三行</p>"), 1))
        assertEquals("<p>正文</p>", epubBodyWithoutVolumeLines(listOf("<p>卷名</p>", "<p>副题</p>", "<p>正文</p>"), 0, 2))
    }

    @Test
    fun insertEpubVolumeChapterAddsEntryManifestSpineAndChapterAtPosition() {
        val book = sampleBook(
            paths = listOf("OEBPS/Text/Chapter0001.xhtml"),
            titles = listOf("第一章")
        )

        val inserted = insertEpubVolumeChapter(
            book = book,
            kind = VOLUME_KIND_NORMAL,
            volumeTitle = "第一卷",
            insertIndex = 0
        )

        assertEquals(0, inserted?.first)
        val chapter = inserted!!.second
        assertEquals("Vol01.xhtml", chapter.path.substringAfterLast('/'))
        assertEquals("第一卷", chapter.title)
        assertTrue(book.entries.containsKey(chapter.path))
        assertTrue(book.manifest.containsKey(chapter.id))
        assertEquals(chapter.id, book.spineIds.first())
        assertEquals(chapter, book.chapters.first())
    }

    @Test
    fun addEpubVolumeToBookRejectsDuplicateExtraVolume() {
        val book = sampleBook(
            paths = listOf("OEBPS/Text/Vol00.xhtml", "OEBPS/Text/Chapter0001.xhtml"),
            titles = listOf("番外卷", "第一章")
        )

        val result = addEpubVolumeToBook(
            book = book,
            kind = VOLUME_KIND_EXTRA,
            volumeTitle = "番外卷",
            insertIndex = 1
        )

        assertFalse(result.success)
        assertEquals("番外卷已存在", result.message)
    }

    @Test
    fun addEpubVolumeToBookUsesDefaultExtraTitleWhenTitleIsBlank() {
        val book = sampleBook(
            paths = listOf("OEBPS/Text/Chapter0001.xhtml"),
            titles = listOf("第一章")
        )

        val result = addEpubVolumeToBook(
            book = book,
            kind = VOLUME_KIND_EXTRA,
            volumeTitle = " ",
            insertIndex = 0
        )

        assertTrue(result.success)
        assertEquals("Vol00.xhtml", result.fileName)
        assertEquals("番外卷", book.chapters.first().title)
        assertTrue(hasExtraEpubVolume(book))
        assertTrue(book.entries.containsKey("OEBPS/Text/Vol00.xhtml"))
    }

    @Test
    fun addEpubExtraVolumeDoesNotResequenceNormalOrSpecialVolumeFiles() {
        val book = sampleBook(
            paths = listOf(
                "OEBPS/Text/Vol02.xhtml",
                "OEBPS/Text/Chapter0001.xhtml",
                "OEBPS/Text/Vol01.xhtml",
                "OEBPS/Text/VolF02.xhtml",
                "OEBPS/Text/VolF01.xhtml"
            ),
            titles = listOf("第二卷", "第一章", "第一卷", "特殊二", "特殊一")
        )

        val result = addEpubVolumeToBook(
            book = book,
            kind = VOLUME_KIND_EXTRA,
            volumeTitle = "",
            insertIndex = 0
        )

        assertTrue(result.success)
        assertEquals(
            listOf(
                "Vol00.xhtml",
                "Vol02.xhtml",
                "Chapter0001.xhtml",
                "Vol01.xhtml",
                "VolF02.xhtml",
                "VolF01.xhtml"
            ),
            book.chapters.map { it.path.substringAfterLast('/') }
        )
    }

    @Test
    fun insertEpubVolumeChapterRejectsBlankTitleAndSkipsExistingVolumeFile() {
        val blankErrors = mutableListOf<String>()
        val blankBook = sampleBook(
            paths = listOf("OEBPS/Text/Chapter0001.xhtml"),
            titles = listOf("第一章")
        )
        val duplicateErrors = mutableListOf<String>()
        val duplicateBook = sampleBook(
            paths = listOf("OEBPS/Text/Chapter0001.xhtml"),
            titles = listOf("第一章")
        )
        duplicateBook.entries["OEBPS/Text/Vol01.xhtml"] = "existing".toByteArray(StandardCharsets.UTF_8)

        val blank = insertEpubVolumeChapter(blankBook, VOLUME_KIND_NORMAL, " ", insertIndex = 0, onError = blankErrors::add)
        val duplicate = insertEpubVolumeChapter(duplicateBook, VOLUME_KIND_NORMAL, "第一卷", insertIndex = 0, onError = duplicateErrors::add)

        assertEquals(null, blank)
        assertEquals(listOf("无法添加，请输入卷名"), blankErrors)
        assertEquals(0, duplicate?.first)
        assertEquals("Vol02.xhtml", duplicate?.second?.path?.substringAfterLast('/'))
        assertEquals(emptyList<String>(), duplicateErrors)
        assertEquals(listOf("Vol02", "c1"), duplicateBook.spineIds)
    }

    @Test
    fun setEpubVolumeFromBodySelectionInBookResequencesNormalVolumeFilesByDirectoryOrder() {
        val body = "<p>第一卷</p>\n<p>正文</p>"
        val book = sampleBook(
            paths = listOf(
                "OEBPS/Text/Chapter0001.xhtml",
                "OEBPS/Text/Vol01.xhtml",
                "OEBPS/Text/Chapter0002.xhtml",
                "OEBPS/Text/Vol02.xhtml",
                "OEBPS/Text/Chapter0003.xhtml"
            ),
            titles = listOf("第一章", "第二卷", "第二章", "第三卷", "第三章"),
            htmls = listOf(
                "<html><body>$body</body></html>",
                "<html><body><h1>第二卷</h1></body></html>",
                "<html><body><h1>第二章</h1><p>正文</p></body></html>",
                "<html><body><h1>第三卷</h1></body></html>",
                "<html><body><h1>第三章</h1><p>正文</p></body></html>"
            )
        )

        val result = setEpubVolumeFromBodySelectionInBook(
            book = book,
            chapterIndex = 0,
            sourceStart = body.indexOf("<p>第一卷"),
            sourceEnd = body.indexOf("<p>正文")
        )

        assertTrue(result.success)
        assertEquals(
            listOf(
                "Chapter0001.xhtml",
                "Vol01.xhtml",
                "Vol02.xhtml",
                "Chapter0002.xhtml",
                "Vol03.xhtml",
                "Chapter0003.xhtml"
            ),
            book.chapters.map { it.path.substringAfterLast('/') }
        )
        listOf("Vol01.xhtml", "Vol02.xhtml", "Vol03.xhtml").forEach { fileName ->
            val path = "OEBPS/Text/$fileName"
            assertTrue(book.entries.containsKey(path))
            assertTrue(book.manifest.values.any { it.path == path })
        }
    }

    @Test
    fun addEpubSpecialExtraVolumeResequencesSpecialVolumeFilesAndKeepsExtraVolume() {
        val book = sampleBook(
            paths = listOf(
                "OEBPS/Text/Vol00.xhtml",
                "OEBPS/Text/VolF01.xhtml",
                "OEBPS/Text/Chapter0001.xhtml",
                "OEBPS/Text/VolF02.xhtml"
            ),
            titles = listOf("番外卷", "番外二", "第一章", "番外三")
        )

        val result = addEpubVolumeToBook(
            book = book,
            kind = VOLUME_KIND_SPECIAL_EXTRA,
            volumeTitle = "番外一",
            insertIndex = 1
        )

        assertTrue(result.success)
        assertEquals("VolF01.xhtml", result.fileName)
        assertEquals(
            listOf("Vol00.xhtml", "VolF01.xhtml", "VolF02.xhtml", "Chapter0001.xhtml", "VolF03.xhtml"),
            book.chapters.map { it.path.substringAfterLast('/') }
        )
        assertTrue(book.entries.containsKey("OEBPS/Text/Vol00.xhtml"))
        listOf("VolF01.xhtml", "VolF02.xhtml", "VolF03.xhtml").forEach { fileName ->
            val path = "OEBPS/Text/$fileName"
            assertTrue(book.entries.containsKey(path))
            assertTrue(book.manifest.values.any { it.path == path })
        }
    }

    @Test
    fun setEpubVolumeAtBodyLineInBookUsesCarriedLinesAsTitleAndKeepsVolumeAfterBody() {
        val html = "<html><body><h1>第一章</h1>\n<p>第一卷</p>\n<p>卷副题</p>\n<p>正文</p></body></html>"
        val book = sampleBook(
            paths = listOf("OEBPS/Text/Chapter0001.xhtml"),
            titles = listOf("第一章"),
            htmls = listOf(html)
        )

        val result = setEpubVolumeAtBodyLineInBook(
            book = book,
            chapterIndex = 0,
            lineIndex = 1,
            lineCountText = "2",
            volumeTitleText = ""
        )

        assertTrue(result.success)
        assertEquals("第一卷 卷副题", result.volumeDisplayTitle)
        assertEquals(2, book.chapters.size)
        assertEquals("第一章", book.chapters[0].title)
        assertEquals("第一卷 卷副题", book.chapters[1].title)
        assertTrue(book.chapters[0].html.contains("<p>正文</p>"))
        assertTrue(!book.chapters[0].html.contains("卷副题"))
        assertEquals(listOf(0, 0), book.chapters.map { it.tocLevel })
    }

    @Test
    fun setEpubVolumeAtBodyLineInBookSyncsOriginalAndInsertedEntries() {
        val html = "<html><body><h1>第一章</h1>\n<p>卷名候选</p>\n<p>正文</p></body></html>"
        val book = sampleBook(
            paths = listOf("OEBPS/Text/Chapter0001.xhtml"),
            titles = listOf("第一章"),
            htmls = listOf(html)
        )

        val result = setEpubVolumeAtBodyLineInBook(
            book = book,
            chapterIndex = 0,
            lineIndex = 1,
            lineCountText = "1",
            volumeTitleText = "自定义卷"
        )

        assertTrue(result.success)
        val body = book.chapters[0]
        val volume = book.chapters[1]
        assertEquals("自定义卷", volume.title)
        assertEquals(volume.html, String(book.entries.getValue(volume.path), StandardCharsets.UTF_8))
        assertEquals(body.html, String(book.entries.getValue(body.path), StandardCharsets.UTF_8))
        assertFalse(body.html.contains("卷名候选"))
        assertTrue(body.html.contains("<p>正文</p>"))
    }

    @Test
    fun setEpubVolumeAtBodyLineInBookRejectsInvalidCarryAndEmptyRemainingBody() {
        val html = "<html><body><h1>第一章</h1>\n<p>第一卷</p>\n<p>正文</p></body></html>"
        val book = sampleBook(
            paths = listOf("OEBPS/Text/Chapter0001.xhtml"),
            titles = listOf("第一章"),
            htmls = listOf(html)
        )

        val invalidCarry = setEpubVolumeAtBodyLineInBook(book, chapterIndex = 0, lineIndex = 1, lineCountText = "9", volumeTitleText = "")
        val emptyBody = setEpubVolumeAtBodyLineInBook(book, chapterIndex = 0, lineIndex = 0, lineCountText = "3", volumeTitleText = "整章")

        assertFalse(invalidCarry.success)
        assertEquals("带走行数必须在 1-2 行之间", invalidCarry.message)
        assertFalse(emptyBody.success)
        assertEquals("设为卷后当前章节正文为空", emptyBody.message)
        assertEquals(1, book.chapters.size)
        assertEquals(html, book.chapters.single().html)
    }

    @Test
    fun setEpubVolumeAtBodyLineInBookRejectsInvalidChapterOrLineWithoutChangingBook() {
        val html = "<html><body><h1>第一章</h1>\n<p>第一卷</p>\n<p>正文</p></body></html>"
        val book = sampleBook(
            paths = listOf("OEBPS/Text/Chapter0001.xhtml"),
            titles = listOf("第一章"),
            htmls = listOf(html)
        )
        val originalChapterIds = book.chapters.map { it.id }
        val originalSpineIds = book.spineIds.toList()
        val originalEntry = book.entries.getValue("OEBPS/Text/Chapter0001.xhtml").toList()

        val invalidChapter = setEpubVolumeAtBodyLineInBook(book, chapterIndex = 9, lineIndex = 0, lineCountText = "1", volumeTitleText = "卷")
        val invalidLine = setEpubVolumeAtBodyLineInBook(book, chapterIndex = 0, lineIndex = 99, lineCountText = "1", volumeTitleText = "卷")

        assertFalse(invalidChapter.success)
        assertFalse(invalidLine.success)
        assertEquals(originalChapterIds, book.chapters.map { it.id })
        assertEquals(originalSpineIds, book.spineIds)
        assertEquals(originalEntry, book.entries.getValue("OEBPS/Text/Chapter0001.xhtml").toList())
    }

    @Test
    fun setEpubVolumeFromBodySelectionInBookCreatesVolumeAndSyncsEntries() {
        val body = "<h1>第一章</h1>\n<p>第一卷</p>\n<p>副题 & 说明</p>\n<p>正文</p>"
        val html = "<html><body>$body</body></html>"
        val book = sampleBook(
            paths = listOf("OEBPS/Text/Chapter0001.xhtml"),
            titles = listOf("第一章"),
            htmls = listOf(html)
        )
        val sourceStart = body.indexOf("<p>第一卷")
        val sourceEnd = body.indexOf("<p>正文")

        val result = setEpubVolumeFromBodySelectionInBook(
            book = book,
            chapterIndex = 0,
            sourceStart = sourceStart,
            sourceEnd = sourceEnd
        )

        assertTrue(result.success)
        assertEquals(1, result.nextPreviewIndex)
        assertEquals("第一卷", result.volumeDisplayTitle)
        assertEquals(listOf(0, 0), book.chapters.map { it.tocLevel })
        assertEquals(book.chapters.map { it.id }, book.spineIds)
        assertEquals("第一卷", book.chapters[1].title)
        assertTrue(book.chapters[1].html.contains("<p>副题 &amp; 说明</p>"))
        assertFalse(book.chapters[0].html.contains("第一卷"))
        assertFalse(book.chapters[0].html.contains("副题"))
        assertTrue(book.chapters[0].html.contains("<p>正文</p>"))
        assertEquals(book.chapters[0].html, String(book.entries.getValue(book.chapters[0].path), StandardCharsets.UTF_8))
        assertEquals(book.chapters[1].html, String(book.entries.getValue(book.chapters[1].path), StandardCharsets.UTF_8))
    }

    @Test
    fun setEpubVolumeFromBodySelectionInBookExpandsPartialSelectionToWholeLine() {
        val body = "<h1>第一章</h1>\n<p>第一卷</p>\n<p>正文</p>"
        val html = "<html><body>$body</body></html>"
        val book = sampleBook(
            paths = listOf("OEBPS/Text/Chapter0001.xhtml"),
            titles = listOf("第一章"),
            htmls = listOf(html)
        )
        val sourceStart = body.indexOf("一卷")
        val sourceEnd = sourceStart + "一卷".length

        val result = setEpubVolumeFromBodySelectionInBook(
            book = book,
            chapterIndex = 0,
            sourceStart = sourceStart,
            sourceEnd = sourceEnd
        )

        assertTrue(result.success)
        assertEquals("第一卷", result.volumeDisplayTitle)
        assertEquals(1, result.nextPreviewIndex)
        assertEquals("第一卷", book.chapters[1].title)
        assertFalse(book.chapters[0].html.contains("第一卷"))
        assertTrue(book.chapters[0].html.contains("<p>正文</p>"))
        assertEquals(book.chapters[0].html, String(book.entries.getValue(book.chapters[0].path), StandardCharsets.UTF_8))
    }

    @Test
    fun setEpubVolumeFromBodySelectionInBookRejectsBlankSelectionAndEmptyRemainingBody() {
        val html = "<html><body><p>唯一正文</p></body></html>"
        val book = sampleBook(
            paths = listOf("OEBPS/Text/Chapter0001.xhtml"),
            titles = listOf("第一章"),
            htmls = listOf(html)
        )
        val originalEntry = book.entries.getValue("OEBPS/Text/Chapter0001.xhtml").toList()

        val blank = setEpubVolumeFromBodySelectionInBook(
            book = book,
            chapterIndex = 0,
            sourceStart = 3,
            sourceEnd = 3
        )
        val emptyBody = setEpubVolumeFromBodySelectionInBook(
            book = book,
            chapterIndex = 0,
            sourceStart = 0,
            sourceEnd = "<p>唯一正文</p>".length
        )

        assertFalse(blank.success)
        assertEquals("请先选择要设为卷的文字", blank.message)
        assertFalse(emptyBody.success)
        assertEquals("设为卷后当前章节正文为空", emptyBody.message)
        assertEquals(listOf("c1"), book.chapters.map { it.id })
        assertEquals(originalEntry, book.entries.getValue("OEBPS/Text/Chapter0001.xhtml").toList())
    }

    @Test
    fun deleteEpubBodySelectionFromBookRemovesSelectionAndSyncsEntry() {
        val body = "<h1>第一章</h1>\n<p>删除我</p>\n<p>正文</p>"
        val html = "<html><body>$body</body></html>"
        val book = sampleBook(
            paths = listOf("OEBPS/Text/Chapter0001.xhtml"),
            titles = listOf("第一章"),
            htmls = listOf(html)
        )

        val result = deleteEpubBodySelectionFromBook(
            book = book,
            chapterIndex = 0,
            sourceStart = body.indexOf("<p>删除我"),
            sourceEnd = body.indexOf("<p>正文")
        )

        assertTrue(result.success)
        assertEquals(1, book.chapters.size)
        assertFalse(book.chapters.single().html.contains("删除我"))
        assertTrue(book.chapters.single().html.contains("<p>正文</p>"))
        assertEquals(book.chapters.single().html, String(book.entries.getValue("OEBPS/Text/Chapter0001.xhtml"), StandardCharsets.UTF_8))
    }

    @Test
    fun deleteEpubBodySelectionFromBookRejectsBlankSelectionAndEmptyRemainingBody() {
        val html = "<html><body><p>唯一正文</p></body></html>"
        val book = sampleBook(
            paths = listOf("OEBPS/Text/Chapter0001.xhtml"),
            titles = listOf("第一章"),
            htmls = listOf(html)
        )
        val originalHtml = book.chapters.single().html

        val blank = deleteEpubBodySelectionFromBook(book, chapterIndex = 0, sourceStart = 4, sourceEnd = 4)
        val emptyBody = deleteEpubBodySelectionFromBook(
            book = book,
            chapterIndex = 0,
            sourceStart = 0,
            sourceEnd = "<p>唯一正文</p>".length
        )

        assertFalse(blank.success)
        assertEquals("请先选择要删除的文字", blank.message)
        assertFalse(emptyBody.success)
        assertEquals("删除后当前章节正文为空", emptyBody.message)
        assertEquals(originalHtml, book.chapters.single().html)
    }

    private fun sampleBook(
        paths: List<String>,
        titles: List<String>,
        htmls: List<String> = titles.map { title -> "<html><body><h1>$title</h1><p>正文</p></body></html>" }
    ): EpubBook {
        val entries = linkedMapOf<String, ByteArray>()
        val manifest = mutableMapOf<String, ManifestItem>()
        val chapters = mutableListOf<EpubChapter>()
        paths.forEachIndexed { index, path ->
            val id = "c${index + 1}"
            val html = htmls[index]
            val href = path.removePrefix("OEBPS/")
            entries[path] = html.toByteArray(StandardCharsets.UTF_8)
            manifest[id] = ManifestItem(
                id = id,
                href = href,
                mediaType = "application/xhtml+xml",
                path = path
            )
            chapters += EpubChapter(
                id = id,
                href = href,
                path = path,
                originalPath = path,
                pathAliases = mutableSetOf(path),
                title = titles[index],
                html = html,
                wordCount = titles[index].length
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
            chapters = chapters
        )
    }
}
