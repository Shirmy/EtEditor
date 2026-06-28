package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.ManifestItem
import com.eteditor.core.TxtChapter
import com.eteditor.core.TxtDocument
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsertChapterUtilsTest {
    @Test
    fun detectInsertChapterUploadSourceTypeUsesExtensionThenZipSignature() {
        assertEquals(INSERT_CHAPTER_SOURCE_TXT, detectInsertChapterUploadSourceType("book.txt", byteArrayOf('P'.code.toByte(), 'K'.code.toByte())))
        assertEquals(INSERT_CHAPTER_SOURCE_EPUB, detectInsertChapterUploadSourceType("book.epub", "text".toByteArray(StandardCharsets.UTF_8)))
        assertEquals(INSERT_CHAPTER_SOURCE_EPUB, detectInsertChapterUploadSourceType("book.bin", byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4)))
        assertEquals(INSERT_CHAPTER_SOURCE_TXT, detectInsertChapterUploadSourceType("book.bin", "text".toByteArray(StandardCharsets.UTF_8)))
    }

    @Test
    fun parseInsertSourceTxtSplitsNumericChaptersOrFallsBackToSingleChapter() {
        val split = parseInsertSourceTxt(
            sourceUri = "content://book",
            name = "source.txt",
            bytes = "第1章 开始\n正文一\n第2章 继续\n正文二".toByteArray(StandardCharsets.UTF_8)
        )
        val fallback = parseInsertSourceTxt(
            sourceUri = "content://single",
            name = "single.txt",
            bytes = "没有章节标题\n正文".toByteArray(StandardCharsets.UTF_8)
        )

        assertEquals(INSERT_CHAPTER_SOURCE_TXT, split.sourceType)
        assertEquals(listOf("第1章 开始", "第2章 继续"), split.chapters.map { it.title })
        assertEquals(listOf("正文一", "正文二"), split.chapters.map { it.text })
        assertEquals(listOf("TXT-0001", "TXT-0002"), split.chapters.map { it.fileName })
        assertEquals("single", fallback.chapters.single().title)
        assertEquals("没有章节标题\n正文", fallback.chapters.single().text)
    }

    @Test
    fun parseInsertSourceTxtHandlesCrOnlyNumericChapters() {
        val data = parseInsertSourceTxt(
            sourceUri = "content://book",
            name = "source.txt",
            bytes = "第1章 开始\r正文一\r第2章 继续\r正文二".toByteArray(StandardCharsets.UTF_8)
        )

        assertEquals(listOf("第1章 开始", "第2章 继续"), data.chapters.map { it.title })
        assertEquals(listOf("正文一", "正文二"), data.chapters.map { it.text })
    }

    @Test
    fun parseInsertSourceTxtIgnoresOverlongNumericTitleAndUsesUnnamedFallback() {
        val overlongTitle = "第2章 " + "很".repeat(130)
        val split = parseInsertSourceTxt(
            sourceUri = "content://book",
            name = "source.txt",
            bytes = "第1章 开始\r\n正文一\r\n$overlongTitle\r\n仍是正文".toByteArray(StandardCharsets.UTF_8)
        )
        val fallback = parseInsertSourceTxt(
            sourceUri = "content://unnamed",
            name = ".txt",
            bytes = "普通正文".toByteArray(StandardCharsets.UTF_8)
        )

        assertEquals(listOf("第1章 开始"), split.chapters.map { it.title })
        assertEquals("正文一\r\n$overlongTitle\r\n仍是正文", split.chapters.single().text)
        assertEquals("未命名章节", fallback.chapters.single().title)
    }

    @Test
    fun resolveInsertChapterPositionsHandleCurrentTargetAndVolumeEnd() {
        val book = sampleBook(
            listOf(
                epubChapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章"),
                epubChapter("v1", "OEBPS/Text/Vol01.xhtml", "第一卷"),
                epubChapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章")
            )
        )
        val document = txtDocument("第1章 开始\n正文\n第2章 继续\n正文")

        assertEquals(1, resolveEpubInsertChapterPosition(book, INSERT_CHAPTER_POSITION_CURRENT_AFTER, null, 0))
        assertEquals(1, resolveEpubInsertChapterPosition(book, INSERT_CHAPTER_POSITION_VOLUME_END, null, 0))
        assertEquals(3, resolveEpubInsertChapterPosition(book, INSERT_CHAPTER_POSITION_VOLUME_END, null, 1))
        assertEquals(0, resolveTxtInsertChapterPosition(document, INSERT_CHAPTER_POSITION_START, null, 1))
        assertEquals(2, resolveTxtInsertChapterPosition(document, INSERT_CHAPTER_POSITION_TARGET_AFTER, 1, 0))
    }

    @Test
    fun resolveInsertChapterPositionsClampMissingTargetsAndEmptyTxtDocuments() {
        val book = sampleBook(
            listOf(
                epubChapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章"),
                epubChapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章")
            )
        )
        val emptyDocument = TxtDocument(
            originalName = "book.txt",
            text = "正文",
            encoding = "UTF-8",
            chapters = emptyList()
        )

        assertEquals(1, resolveEpubInsertChapterPosition(book, INSERT_CHAPTER_POSITION_TARGET_BEFORE, 99, 0))
        assertEquals(1, resolveEpubInsertChapterPosition(book, INSERT_CHAPTER_POSITION_TARGET_AFTER, -9, 0))
        assertEquals(0, resolveTxtInsertChapterPosition(emptyDocument, INSERT_CHAPTER_POSITION_START, null, 0))
        assertEquals(1, resolveTxtInsertChapterPosition(emptyDocument, INSERT_CHAPTER_POSITION_END, null, 0))
        assertEquals(0, txtChapterInsertOffset(emptyDocument, 0))
        assertEquals(emptyDocument.text.length, txtChapterInsertOffset(emptyDocument, 1))
    }

    @Test
    fun resolveInsertChapterPositionsHandleEmptyEpubAndInvalidTxtChapterOffsets() {
        val emptyBook = sampleBook(emptyList())
        val brokenDocument = TxtDocument(
            originalName = "broken.txt",
            text = "第1章 开始\n正文",
            encoding = "UTF-8",
            chapters = listOf(
                TxtChapter(
                    index = 1,
                    lineIndex = 9,
                    endLineIndex = 10,
                    title = "第1章 开始",
                    wordCount = 2
                )
            )
        )

        assertEquals(0, resolveEpubInsertChapterPosition(emptyBook, INSERT_CHAPTER_POSITION_CURRENT_AFTER, null, 99))
        assertEquals(0, resolveEpubInsertChapterPosition(emptyBook, INSERT_CHAPTER_POSITION_TARGET_AFTER, 5, 0))
        assertEquals(0, resolveTxtInsertChapterPosition(brokenDocument, INSERT_CHAPTER_POSITION_TARGET_BEFORE, null, 0))
        assertEquals(brokenDocument.text.length, txtChapterInsertOffset(brokenDocument, insertPosition = 0))
    }

    @Test
    fun insertChaptersIntoTxtDocumentTextRenumbersAndInsertsAtResolvedPosition() {
        val document = txtDocument("第1章 开始\n正文一\n第2章 继续\n正文二")
        val result = insertChaptersIntoTxtDocumentText(
            document = document,
            selected = listOf(
                InsertableChapter(
                    sourceIndex = 0,
                    title = "第9章 外部",
                    fileName = "source",
                    sourcePath = "source",
                    html = null,
                    text = "插入正文",
                    wordCount = 4,
                    tocLevel = 0,
                    isVolume = false
                )
            ),
            positionMode = INSERT_CHAPTER_POSITION_CURRENT_AFTER,
            targetChapterIndex = null,
            currentChapterIndex = 0
        )

        assertEquals(1, result?.insertPosition)
        assertEquals(1, result?.insertedCount)
        assertEquals(
            "第1章 开始\r\n正文一\r\n\r\n第2章 外部\r\n插入正文\r\n\r\n第2章 继续\r\n正文二",
            result?.text
        )
    }

    @Test
    fun insertChaptersIntoTxtDocumentTextSkipsBlankChunksButReportsProgress() {
        val progress = mutableListOf<Pair<Int, Int>>()
        val document = txtDocument("第1章 开始\n正文一\n第2章 继续\n正文二")
        val result = insertChaptersIntoTxtDocumentText(
            document = document,
            selected = listOf(
                insertable(0, " ", isVolume = true).copy(text = " "),
                insertable(1, "外篇", isVolume = true).copy(text = ""),
                insertable(2, "第9章 外部").copy(text = "新正文")
            ),
            positionMode = INSERT_CHAPTER_POSITION_END,
            targetChapterIndex = null,
            currentChapterIndex = 0,
            onProgress = { completed, total -> progress += completed to total }
        )

        assertEquals(2, result?.insertedCount)
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)
        assertEquals(
            "第1章 开始\r\n正文一\r\n第2章 继续\r\n正文二\r\n\r\n外篇\r\n\r\n第3章 外部\r\n新正文",
            result?.text
        )
    }

    @Test
    fun insertChaptersIntoTxtDocumentTextReturnsNullWhenNothingCanBeInserted() {
        val progress = mutableListOf<Pair<Int, Int>>()

        val result = insertChaptersIntoTxtDocumentText(
            document = txtDocument("第1章 开始\n正文"),
            selected = emptyList(),
            positionMode = INSERT_CHAPTER_POSITION_END,
            targetChapterIndex = null,
            currentChapterIndex = 0,
            onProgress = { completed, total -> progress += completed to total }
        )

        assertNull(result)
        assertEquals(emptyList<Pair<Int, Int>>(), progress)
    }

    @Test
    fun previewSelectionAndSourceMatchUseSourceTypeRules() {
        val data = InsertChapterSourceData(
            sourceUri = "content://source",
            sourceType = INSERT_CHAPTER_SOURCE_TXT,
            originalName = "source.txt",
            chapters = listOf(
                insertable(0, "第一章"),
                insertable(1, "第二章")
            )
        )

        val preview = buildInsertChapterSourcePreview(data, previewSourceType = INSERT_CHAPTER_SOURCE_UPLOAD)
        val selected = selectInsertableChapters(data, selectedSourceIndices = setOf(1), useSelectedSourceIndices = true, reverseSelectedOrder = false)
        val reversed = selectInsertableChapters(data, selectedSourceIndices = emptySet(), useSelectedSourceIndices = false, reverseSelectedOrder = true)

        assertEquals(INSERT_CHAPTER_SOURCE_UPLOAD, preview.sourceType)
        assertEquals(listOf("第一章", "第二章"), preview.items.map { it.title })
        assertEquals(listOf("第二章"), selected.map { it.title })
        assertEquals(listOf("第二章", "第一章"), reversed.map { it.title })
        assertTrue(insertChapterSourceDataMatches(data, "content://source", INSERT_CHAPTER_SOURCE_UPLOAD))
        assertFalse(insertChapterSourceDataMatches(data, "content://other", INSERT_CHAPTER_SOURCE_UPLOAD))
        assertFalse(insertChapterSourceDataMatches(data, "content://source", INSERT_CHAPTER_SOURCE_EPUB))
    }

    @Test
    fun insertChapterSourceDataMatchesUploadOnlyReusesParsedUploadSources() {
        val epubData = InsertChapterSourceData(
            sourceUri = "content://source",
            sourceType = INSERT_CHAPTER_SOURCE_EPUB,
            originalName = "source.epub",
            chapters = listOf(insertable(0, "第一章"))
        )
        val sosadData = InsertChapterSourceData(
            sourceUri = "content://source",
            sourceType = INSERT_CHAPTER_SOURCE_SOSAD,
            originalName = "废文目录",
            chapters = listOf(insertable(0, "第一章"))
        )

        assertTrue(insertChapterSourceDataMatches(epubData, "content://source", INSERT_CHAPTER_SOURCE_UPLOAD))
        assertFalse(insertChapterSourceDataMatches(sosadData, "content://source", INSERT_CHAPTER_SOURCE_UPLOAD))
        assertTrue(insertChapterSourceDataMatches(sosadData, "content://source", INSERT_CHAPTER_SOURCE_SOSAD))
        assertFalse(insertChapterSourceDataMatches(epubData, "content://source", INSERT_CHAPTER_SOURCE_SOSAD))
    }

    @Test
    fun buildInsertChapterSourcePreviewKeepsDisplayFieldsFromSourceChapters() {
        val data = InsertChapterSourceData(
            sourceUri = "content://source",
            sourceType = INSERT_CHAPTER_SOURCE_EPUB,
            originalName = "source.epub",
            chapters = listOf(
                InsertableChapter(
                    sourceIndex = 7,
                    title = "第一卷",
                    fileName = "Volume01.xhtml",
                    sourcePath = "OEBPS/Text/Volume01.xhtml",
                    html = "<h1>第一卷</h1>",
                    text = "卷说明",
                    wordCount = 12,
                    tocLevel = 2,
                    isVolume = true
                )
            )
        )

        val preview = buildInsertChapterSourcePreview(data)
        val item = preview.items.single()

        assertEquals("content://source", preview.sourceUri)
        assertEquals(INSERT_CHAPTER_SOURCE_EPUB, preview.sourceType)
        assertEquals(7, item.sourceIndex)
        assertEquals("第一卷", item.title)
        assertEquals("Volume01.xhtml", item.fileName)
        assertEquals(12, item.wordCount)
        assertEquals(2, item.tocLevel)
        assertTrue(item.isVolume)
    }

    @Test
    fun selectInsertableChaptersFiltersBySourceIndexBeforeReversingOrder() {
        val data = InsertChapterSourceData(
            sourceUri = "content://source",
            sourceType = INSERT_CHAPTER_SOURCE_TXT,
            originalName = "source.txt",
            chapters = listOf(
                insertable(0, "第一章"),
                insertable(1, "第二章"),
                insertable(2, "第三章")
            )
        )

        val selected = selectInsertableChapters(
            source = data,
            selectedSourceIndices = setOf(2, 0),
            useSelectedSourceIndices = true,
            reverseSelectedOrder = true
        )

        assertEquals(listOf("第三章", "第一章"), selected.map { it.title })
    }

    @Test
    fun renumberInsertedChapterTitleKeepsSuffixAndFallbacksToNumberOnly() {
        assertEquals("第3章外部", renumberInsertedChapterTitle("第9章 外部", 3))
        assertEquals("第3章", renumberInsertedChapterTitle("第9章", 3))
        assertEquals("第5章番外", renumberInsertedChapterTitle("第十二回 番外", 5))
        assertEquals("第6章卷末", renumberInsertedChapterTitle("第 二 卷 卷末", 6))
    }

    @Test
    fun insertChapterProgressLabelAndValueClampVisibleProgress() {
        assertEquals("解析 2/5", insertChapterProgressLabel("插入", "解析", completed = 2, total = 5))
        assertEquals("插入 5/5", insertChapterProgressLabel("插入", "", completed = 8, total = 5))
        assertEquals("插入章节", insertChapterProgressLabel("", "", completed = 0, total = 0))
        assertEquals(0.4f, insertChapterProgressValue(completed = 2, total = 5), 0.0001f)
        assertEquals(1f, insertChapterProgressValue(completed = 8, total = 5), 0.0001f)
        assertEquals(0f, insertChapterProgressValue(completed = 1, total = 0), 0.0001f)
    }

    private fun insertable(index: Int, title: String, isVolume: Boolean = false): InsertableChapter {
        return InsertableChapter(
            sourceIndex = index,
            title = title,
            fileName = "chapter$index.xhtml",
            sourcePath = "chapter$index.xhtml",
            html = null,
            text = "正文",
            wordCount = 2,
            tocLevel = 0,
            isVolume = isVolume
        )
    }

    private fun txtDocument(text: String): TxtDocument {
        return TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = detectTxtChapters(text)
        )
    }

    private fun detectTxtChapters(text: String): List<TxtChapter> {
        return ChapterDetector.detectTxtChapters(
            text = text,
            customPatterns = listOf("""^第\s*(\d+)\s*章.*$""")
        )
    }

    private fun sampleBook(chapters: List<EpubChapter>): EpubBook {
        return EpubBook(
            originalName = "book.epub",
            metadataTitle = "Book",
            metadataAuthor = "",
            entries = linkedMapOf(),
            opfPath = "OEBPS/content.opf",
            tocPath = "OEBPS/toc.ncx",
            manifest = chapters.associate { chapter ->
                chapter.id to ManifestItem(chapter.id, chapter.href, "application/xhtml+xml", chapter.path)
            }.toMutableMap(),
            spineIds = chapters.map { it.id }.toMutableList(),
            chapters = chapters.toMutableList()
        )
    }

    private fun epubChapter(id: String, path: String, title: String): EpubChapter {
        return EpubChapter(
            id = id,
            href = path.removePrefix("OEBPS/"),
            path = path,
            originalPath = path,
            pathAliases = mutableSetOf(path),
            title = title,
            html = "<html><body><h1>$title</h1></body></html>",
            wordCount = title.length
        )
    }
}
