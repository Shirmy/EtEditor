package com.eteditor

import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.ManifestItem
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileRenameUtilsTest {
    @Test
    fun applyTemplatePlaceholdersExpandsSequenceTitleAndOldFileFields() {
        val rendered = applyTemplatePlaceholders(
            pattern = "{z3:5}-{Z2:10}-{}-{raw}-{n}-{i}-{title}-{file}.{ext}",
            index = 2,
            total = 12,
            title = "标题",
            sequenceIndex = 1,
            oldFileName = "old.xhtml",
            oldExtension = "xhtml"
        )

        assertEquals("006-11-0001-1-03-3-标题-old.xhtml", rendered)
    }

    @Test
    fun applyTemplatePlaceholdersUsesNextSequenceAndClampsFixedWidth() {
        val rendered = applyTemplatePlaceholders(
            pattern = "{z99:next}-{Z99:auto}-{Z0}-{file}-{ext}",
            index = 0,
            total = 3,
            title = "标题",
            sequenceIndex = 2,
            oldFileName = "old",
            oldExtension = "",
            templateStart = 8,
            templateStep = 3,
            nextSequenceStart = 20
        )

        assertEquals("000000000026-000000000026-14-old-", rendered)
    }

    @Test
    fun renderTemplateUsesDefaultSequencePatternWhenFormatIsBlank() {
        val rendered = renderTemplate(
            pattern = " ",
            context = TemplateContext(
                spineIndex = 5,
                sequenceIndex = 3,
                total = 9,
                title = "标题"
            )
        )

        assertEquals("0003", rendered)
    }

    @Test
    fun normalizeChapterFileNameRejectsInvalidNamesAndKeepsExtension() {
        val errors = mutableListOf<String>()

        assertNull(normalizeChapterFileName("   ", "OEBPS/Text/old.xhtml", onError = errors::add))
        assertNull(normalizeChapterFileName("../bad", "OEBPS/Text/old.xhtml", onError = errors::add))
        assertNull(normalizeChapterFileName(".", "OEBPS/Text/old.xhtml", onError = errors::add))

        assertEquals("New.xhtml", normalizeChapterFileName("New", "OEBPS/Text/old.xhtml"))
        assertEquals("New.html", normalizeChapterFileName("New.html", "OEBPS/Text/old.xhtml"))
        assertEquals("New", normalizeChapterFileName("New", "OEBPS/Text/old", keepExtension = true))
        assertEquals("New", normalizeChapterFileName("New", "OEBPS/Text/old.xhtml", keepExtension = false))
        assertEquals(listOf("文件名不能为空", "文件名不要包含路径", "文件名无效"), errors)
    }

    @Test
    fun normalizeChapterFileNameRejectsBackslashPathSeparators() {
        val errors = mutableListOf<String>()

        assertNull(normalizeChapterFileName("""dir\bad""", "OEBPS/Text/old.xhtml", onError = errors::add))

        assertEquals(listOf("文件名不要包含路径"), errors)
    }

    @Test
    fun buildFileRenamePlanRejectsDuplicateTargetNames() {
        val errors = mutableListOf<String>()
        val plan = buildFileRenamePlanItems(
            book = sampleBook(
                titles = listOf("同名", "同名"),
                paths = listOf("OEBPS/Text/Chapter0001.xhtml", "OEBPS/Text/Chapter0002.xhtml")
            ),
            parameters = FileRenameParameters(namingFormat = "{title}", preview = true),
            targetIndices = listOf(0, 1),
            onError = errors::add
        )

        assertEquals(emptyList<FileRenamePlanItem>(), plan)
        assertEquals(listOf("文件名重复：同名.xhtml；多章节请使用 {z4}"), errors)
    }

    @Test
    fun buildFileRenamePlanRejectsStaticMultiRenameAndExistingTargetPath() {
        val staticErrors = mutableListOf<String>()
        val staticPlan = buildFileRenamePlanItems(
            book = sampleBook(
                titles = listOf("第一章", "第二章"),
                paths = listOf("OEBPS/Text/Chapter0001.xhtml", "OEBPS/Text/Chapter0002.xhtml")
            ),
            parameters = FileRenameParameters(namingFormat = "Static", preview = true),
            targetIndices = listOf(0, 1),
            onError = staticErrors::add
        )
        val existingErrors = mutableListOf<String>()
        val book = sampleBook(
            titles = listOf("第一章"),
            paths = listOf("OEBPS/Text/Chapter0001.xhtml")
        )
        book.entries["OEBPS/Text/New.xhtml"] = "existing".toByteArray(StandardCharsets.UTF_8)
        val existingPlan = buildFileRenamePlanItems(
            book = book,
            parameters = FileRenameParameters(namingFormat = "New", preview = true),
            targetIndices = listOf(0),
            onError = existingErrors::add
        )

        assertEquals(emptyList<FileRenamePlanItem>(), staticPlan)
        assertEquals(listOf("多章节重命名时，命名格式需要包含 {z4}"), staticErrors)
        assertEquals(emptyList<FileRenamePlanItem>(), existingPlan)
        assertEquals(listOf("文件名已存在：New.xhtml"), existingErrors)
    }

    @Test
    fun buildAndApplyFileRenamePlanAllowsTargetPathFromSelectedChapters() {
        val errors = mutableListOf<String>()
        val book = sampleBook(
            titles = listOf("第一章", "第二章"),
            paths = listOf("OEBPS/Text/Chapter0001.xhtml", "OEBPS/Text/Chapter0002.xhtml")
        )

        val plan = buildFileRenamePlanItems(
            book = book,
            parameters = FileRenameParameters(namingFormat = "Chapter{z4:2}", preview = true),
            targetIndices = listOf(0, 1),
            onError = errors::add
        )

        assertEquals(emptyList<String>(), errors)
        assertEquals(
            listOf("OEBPS/Text/Chapter0002.xhtml", "OEBPS/Text/Chapter0003.xhtml"),
            plan.map { it.newPath }
        )

        val renamed = applyFileRenamePlanToEpub(book, plan)

        assertEquals(2, renamed)
        assertTrue(!book.entries.containsKey("OEBPS/Text/Chapter0001.xhtml"))
        assertEquals(
            "chapter 1",
            String(book.entries.getValue("OEBPS/Text/Chapter0002.xhtml"), StandardCharsets.UTF_8)
        )
        assertEquals(
            "chapter 2",
            String(book.entries.getValue("OEBPS/Text/Chapter0003.xhtml"), StandardCharsets.UTF_8)
        )
        assertEquals("OEBPS/Text/Chapter0002.xhtml", book.manifest.getValue("c1").path)
        assertEquals("OEBPS/Text/Chapter0003.xhtml", book.manifest.getValue("c2").path)
    }

    @Test
    fun buildFileRenamePlanReportsEmptyBookAndEmptyTargets() {
        val emptyBookErrors = mutableListOf<String>()
        val emptyTargetErrors = mutableListOf<String>()
        val emptyBook = sampleBook(titles = emptyList(), paths = emptyList())
        val normalBook = sampleBook(
            titles = listOf("第一章"),
            paths = listOf("OEBPS/Text/Chapter0001.xhtml")
        )

        assertEquals(
            emptyList<FileRenamePlanItem>(),
            buildFileRenamePlanItems(
                book = emptyBook,
                parameters = FileRenameParameters(namingFormat = "Chapter{z4}", preview = true),
                targetIndices = listOf(0),
                onError = emptyBookErrors::add
            )
        )
        assertEquals(
            emptyList<FileRenamePlanItem>(),
            buildFileRenamePlanItems(
                book = normalBook,
                parameters = FileRenameParameters(namingFormat = "Chapter{z4}", preview = true),
                targetIndices = emptyList(),
                onError = emptyTargetErrors::add
            )
        )

        assertEquals(listOf("没有可重命名的章节文件"), emptyBookErrors)
        assertEquals(listOf("没有可重命名的正文 HTML"), emptyTargetErrors)
    }

    @Test
    fun buildFileRenamePlanFallsBackToOldFileNameForBlankChapterTitle() {
        val plan = buildFileRenamePlanItems(
            book = sampleBook(
                titles = listOf(""),
                paths = listOf("OEBPS/Text/old.xhtml")
            ),
            parameters = FileRenameParameters(namingFormat = "{title}", preview = true),
            targetIndices = listOf(0)
        )

        assertEquals(1, plan.size)
        assertEquals("old.xhtml", plan.single().newFileName)
        assertEquals("OEBPS/Text/old.xhtml", plan.single().newPath)
        assertEquals(false, plan.single().changed)
    }

    @Test
    fun applyFileRenamePlanSkipsUnchangedItems() {
        val book = sampleBook(
            titles = listOf(""),
            paths = listOf("OEBPS/Text/old.xhtml")
        )
        val plan = buildFileRenamePlanItems(
            book = book,
            parameters = FileRenameParameters(namingFormat = "{title}", preview = true),
            targetIndices = listOf(0)
        )

        val renamed = applyFileRenamePlanToEpub(book, plan)

        assertEquals(0, renamed)
        assertTrue(book.entries.containsKey("OEBPS/Text/old.xhtml"))
        assertEquals("OEBPS/Text/old.xhtml", book.chapters.single().path)
        assertEquals(setOf("OEBPS/Text/old.xhtml"), book.chapters.single().pathAliases)
    }

    @Test
    fun replaceFileNameAndHrefKeepDirectoryAndFragment() {
        assertEquals("OEBPS/Text/New.xhtml", "OEBPS/Text/Old.xhtml".replaceFileName("New.xhtml"))
        assertEquals("New.xhtml", "Old.xhtml".replaceFileName("New.xhtml"))
        assertEquals("Text/New.xhtml#frag", "Text/Old.xhtml#frag".replaceHrefFileName("New.xhtml"))
    }

    @Test
    fun buildAndApplyFileRenamePlanMovesEntryAndUpdatesChapterManifestAndAliases() {
        val book = sampleBook(
            titles = listOf("第一章", "第二章"),
            paths = listOf("OEBPS/Text/Chapter0001.xhtml", "OEBPS/Text/Chapter0002.xhtml"),
            hrefs = listOf("Text/Chapter0001.xhtml#frag", "Text/Chapter0002.xhtml")
        )
        val plan = buildFileRenamePlanItems(
            book = book,
            parameters = FileRenameParameters(namingFormat = "Chapter{z4:auto}", preview = true),
            targetIndices = listOf(0)
        )

        assertEquals(1, plan.size)
        assertEquals("OEBPS/Text/Chapter0003.xhtml", plan.single().newPath)

        val renamed = applyFileRenamePlanToEpub(book, plan)

        assertEquals(1, renamed)
        assertTrue(book.entries.containsKey("OEBPS/Text/Chapter0003.xhtml"))
        assertTrue(!book.entries.containsKey("OEBPS/Text/Chapter0001.xhtml"))
        assertEquals("OEBPS/Text/Chapter0003.xhtml", book.chapters[0].path)
        assertEquals("Text/Chapter0003.xhtml#frag", book.chapters[0].href)
        assertEquals("OEBPS/Text/Chapter0003.xhtml", book.manifest.getValue("c1").path)
        assertEquals("Text/Chapter0003.xhtml#frag", book.manifest.getValue("c1").href)
        assertTrue(book.chapters[0].pathAliases.contains("OEBPS/Text/Chapter0001.xhtml"))
        assertTrue(book.chapters[0].pathAliases.contains("OEBPS/Text/Chapter0003.xhtml"))
    }

    @Test
    fun applyFileRenamePlanRewritesCrossChapterLinksInBody() {
        val book = sampleBook(
            titles = listOf("第一章", "第二章"),
            paths = listOf("OEBPS/Text/Chapter0001.xhtml", "OEBPS/Text/Chapter0002.xhtml")
        )
        book.chapters[1].html = "<html><body><a href=\"Chapter0001.xhtml#note\">脚注</a></body></html>"
        book.entries["OEBPS/Text/Chapter0002.xhtml"] = book.chapters[1].html.toByteArray(StandardCharsets.UTF_8)
        val plan = buildFileRenamePlanItems(
            book = book,
            parameters = FileRenameParameters(namingFormat = "Chapter{z4:9}", preview = true),
            targetIndices = listOf(0)
        )

        applyFileRenamePlanToEpub(book, plan)

        val updatedHtml = String(book.entries.getValue("OEBPS/Text/Chapter0002.xhtml"), StandardCharsets.UTF_8)
        assertTrue(updatedHtml.contains("Chapter0009.xhtml#note"))
        assertFalse(updatedHtml.contains("Chapter0001.xhtml#note"))
    }

    private fun sampleBook(
        titles: List<String>,
        paths: List<String>,
        hrefs: List<String> = paths.map { it.removePrefix("OEBPS/") }
    ): EpubBook {
        val entries = LinkedHashMap<String, ByteArray>()
        val manifest = mutableMapOf<String, ManifestItem>()
        val chapters = mutableListOf<EpubChapter>()

        paths.forEachIndexed { index, path ->
            val id = "c${index + 1}"
            val href = hrefs[index]
            entries[path] = "chapter ${index + 1}".toByteArray(StandardCharsets.UTF_8)
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
                html = "<html><head><title>${titles[index]}</title></head><body><h1>${titles[index]}</h1></body></html>",
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
