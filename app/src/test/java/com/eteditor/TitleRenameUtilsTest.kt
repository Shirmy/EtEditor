package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.ChapterInfo
import com.eteditor.core.DocumentKind
import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.ManifestItem
import com.eteditor.core.TxtChapter
import com.eteditor.core.TxtDocument
import com.eteditor.core.decodeEpubHtmlBytes
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleRenameUtilsTest {
    @Test
    fun buildTitleRenamePlanItemsAppendsSuffixWhenTemplateHasNoTitlePlaceholder() {
        val plan = buildTitleRenamePlanItems(
            parameters = titleParameters(pattern = "第{Z2}章"),
            targetIndices = listOf(0, 1),
            sourceTitle = { index -> if (index == 0) "旧标题 A" else "旧标题 B" }
        )

        assertEquals(listOf("第01章 旧标题 A", "第02章 旧标题 B"), plan.map { it.newTitle })
        assertEquals(listOf("旧标题 A", "旧标题 B"), plan.map { it.suffix })
    }

    @Test
    fun buildTitleRenamePlanItemsUsesTitlePlaceholderWithoutAppendingSuffixTwice() {
        val plan = buildTitleRenamePlanItems(
            parameters = titleParameters(pattern = "正文 {title}"),
            targetIndices = listOf(0),
            sourceTitle = { "旧标题" }
        )

        assertEquals("正文 旧标题", plan.single().newTitle)
    }

    @Test
    fun buildTitleRenamePlanItemsReportsEmptyTemplate() {
        val errors = mutableListOf<String>()
        val plan = buildTitleRenamePlanItems(
            parameters = titleParameters(pattern = " "),
            targetIndices = listOf(0),
            sourceTitle = { "旧标题" },
            onError = errors::add
        )

        assertEquals(emptyList<TitleRenamePlanItem>(), plan)
        assertEquals(listOf("请输入标题模板"), errors)
    }

    @Test
    fun buildTitleRenamePlanModelMatchesEpubHeadingsAndSkipsVolumeChapters() {
        val volume = epubChapter(
            id = "v1",
            path = "OEBPS/Text/Vol01.xhtml",
            title = "第一卷",
            html = "<html><body><h1>匹配副标题</h1></body></html>"
        )
        val matched = epubChapter(
            id = "c1",
            path = "OEBPS/Text/Chapter0001.xhtml",
            title = "旧目录标题",
            html = "<html><body><h1>第一章</h1><h2>匹配副标题</h2></body></html>"
        )
        val missed = epubChapter(
            id = "c2",
            path = "OEBPS/Text/Chapter0002.xhtml",
            title = "第二章",
            html = "<html><body><h1>第二章</h1></body></html>"
        )
        val epubChapters = listOf(volume, matched, missed)
        val chapters = epubChapters.mapIndexed { index, chapter ->
            ChapterInfo(
                index = index + 1,
                title = chapter.title,
                wordCount = chapter.wordCount,
                source = chapter.path,
                fileName = chapter.path.substringAfterLast('/')
            )
        }

        val result = buildTitleRenamePlanModel(
            kind = DocumentKind.Epub,
            epubChapters = epubChapters,
            txtDocument = null,
            chapters = chapters,
            currentIndex = 0,
            parameters = TitleRenameParameters(
                pattern = "第{Z2}章 {title}",
                scope = TOOL_SCOPE_FILE_REGEX,
                matchPattern = "匹配副标题",
                matchRegexEnabled = false,
                preview = true
            )
        )

        assertEquals("", result.message)
        assertEquals(listOf(1), result.plan.map { it.chapterIndex })
        assertEquals(listOf("第01章 旧目录标题"), result.plan.map { it.newTitle })
    }

    @Test
    fun buildTitleRenamePlanModelReturnsEmptyWhenCurrentEpubChapterIsVolume() {
        val volume = epubChapter(
            id = "v1",
            path = "OEBPS/Text/Vol01.xhtml",
            title = "第一卷",
            html = "<html><body><h1>第一卷</h1></body></html>"
        )
        val body = epubChapter(
            id = "c1",
            path = "OEBPS/Text/Chapter0001.xhtml",
            title = "第一章",
            html = "<html><body><h1>第一章</h1></body></html>"
        )
        val epubChapters = listOf(volume, body)
        val chapters = epubChapters.mapIndexed { index, chapter ->
            ChapterInfo(
                index = index + 1,
                title = chapter.title,
                wordCount = chapter.wordCount,
                source = chapter.path,
                fileName = chapter.path.substringAfterLast('/'),
                isVolume = chapter.isVolumeChapter()
            )
        }

        val result = buildTitleRenamePlanModel(
            kind = DocumentKind.Epub,
            epubChapters = epubChapters,
            txtDocument = null,
            chapters = chapters,
            currentIndex = 0,
            parameters = titleParameters(pattern = "第{Z2}章 {title}").copy(scope = TOOL_SCOPE_CURRENT)
        )

        assertEquals(emptyList<TitleRenamePlanItem>(), result.plan)
        assertEquals("没有可重命名的普通章节", result.message)
    }

    @Test
    fun buildTitleRenamePlanModelMatchesTxtChapterTitlesForTitleScope() {
        val text = "第1章 开始\n正文\n第2章 命中标题\n正文"
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = detectTxtChapters(text)
        )
        val chapters = document.chapters.mapIndexed { index, chapter ->
            ChapterInfo(
                index = index + 1,
                title = chapter.title,
                wordCount = chapter.wordCount,
                source = "book.txt",
                fileName = "book.txt"
            )
        }

        val result = buildTitleRenamePlanModel(
            kind = DocumentKind.Txt,
            epubChapters = null,
            txtDocument = document,
            chapters = chapters,
            currentIndex = 0,
            parameters = TitleRenameParameters(
                pattern = "重命名{Z2} {title}",
                scope = TOOL_SCOPE_FILE_REGEX,
                matchPattern = "命中标题",
                matchRegexEnabled = false,
                preview = true
            )
        )

        assertEquals("", result.message)
        assertEquals(listOf(1), result.plan.map { it.chapterIndex })
        assertEquals(listOf("重命名01 第2章 命中标题"), result.plan.map { it.newTitle })
    }

    @Test
    fun extractH1H2TitlesStripsHtmlAndIgnoresBlankHeadings() {
        val titles = extractH1H2Titles(
            """
                <h1><span>第一章&nbsp;标题</span></h1>
                <h2>   </h2>
                <h2>第二章 &amp; 继续</h2>
                <h3>不作为标题</h3>
            """.trimIndent()
        )

        assertEquals(listOf("第一章 标题", "第二章 & 继续"), titles)
    }

    @Test
    fun titleRenameSuffixUsesFirstCaptureGroupWhenExtractorMatches() {
        val extractor = Regex("""^第\s*\d+\s*章\s*(.*)$""")

        assertEquals("旧标题", titleRenameSuffix("第12章 旧标题", extractor))
        assertEquals("序章", titleRenameSuffix("序章", extractor))
        assertEquals("第12章 旧标题", titleRenameSuffix("第12章 旧标题", Regex("""第\d+章""")))
    }

    @Test
    fun applyRenamedTitlesToEpubUpdatesTitleHtmlAndWordCount() {
        val book = sampleBook()
        val result = applyRenamedTitlesToEpub(book, listOf(0 to "新 <标题>"))

        assertTrue(result.attempted)
        assertEquals(1, result.count)
        assertEquals("新 <标题>", book.chapters[0].title)
        assertTrue(book.chapters[0].html.contains("<title>新 &lt;标题&gt;</title>"))
        assertTrue(book.chapters[0].html.contains("<h1>新 &lt;标题&gt;</h1>"))
        assertTrue(book.chapters[0].wordCount > 0)
    }

    @Test
    fun applyRenamedTitlesToEpubSyncsRenamedTitleIntoEntryBytes() {
        val book = sampleBook()
        applyRenamedTitlesToEpub(book, listOf(0 to "新标题"))

        // 回归：重命名后必须同步进 book.entries。否则执行链里后续读取 entries 的文本替换/批量替换
        // 会基于旧标题原始字节重写章节，把新标题清空。
        val entryHtml = decodeEpubHtmlBytes(book.entries.getValue("OEBPS/Text/chapter1.xhtml"))
        assertTrue(entryHtml.contains("<h1>新标题</h1>"))
        assertTrue(!entryHtml.contains("旧标题"))
    }

    @Test
    fun applyRenamedTitlesToEpubWithProgressSyncsRenamedTitleIntoEntryBytes() = runBlocking {
        val book = sampleBook()
        val progress = mutableListOf<Pair<Int, Int>>()
        val count = applyRenamedTitlesToEpubWithProgress(
            book = book,
            newTitles = listOf(0 to "新标题"),
            onProgress = { completed, total -> progress += completed to total }
        )

        val entryHtml = decodeEpubHtmlBytes(book.entries.getValue("OEBPS/Text/chapter1.xhtml"))
        assertEquals(1, count)
        assertEquals(listOf(1 to 1), progress)
        assertTrue(entryHtml.contains("<h1>新标题</h1>"))
        assertTrue(!entryHtml.contains("旧标题"))
    }

    @Test
    fun applyRenamedTitlesToTxtDropsBlankTitlesAndRedetectsChapters() {
        val text = "第1章 旧标题\n正文\n第2章 第二章\n正文"
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = detectTxtChapters(text)
        )

        val result = applyRenamedTitlesToTxt(
            document = document,
            newTitles = listOf(0 to "第1章 新标题", 1 to " "),
            detectChapters = ::detectTxtChapters
        )

        assertTrue(result.attempted)
        assertEquals(1, result.count)
        assertEquals("第1章 新标题\n正文\n第2章 第二章\n正文", document.text)
        assertEquals(listOf("第1章 新标题", "第2章 第二章"), document.chapters.map { it.title })
    }

    @Test
    fun applyRenamedTitlesToTxtDoesNotAttemptWhenAllTitlesAreBlank() {
        val text = "第1章 旧标题\n正文"
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = detectTxtChapters(text)
        )

        val result = applyRenamedTitlesToTxt(
            document = document,
            newTitles = listOf(0 to " "),
            detectChapters = ::detectTxtChapters
        )

        assertFalse(result.attempted)
        assertEquals(0, result.count)
        assertEquals(text, document.text)
    }

    private fun titleParameters(pattern: String): TitleRenameParameters {
        return TitleRenameParameters(
            pattern = pattern,
            scope = TOOL_SCOPE_ALL,
            matchPattern = "",
            matchRegexEnabled = true,
            preview = true
        )
    }

    private fun detectTxtChapters(text: String): List<TxtChapter> {
        return ChapterDetector.detectTxtChapters(
            text = text,
            customPatterns = listOf("""^第\s*(\d+)\s*章.*$""")
        )
    }

    private fun sampleBook(): EpubBook {
        val path = "OEBPS/Text/chapter1.xhtml"
        val href = "Text/chapter1.xhtml"
        val chapter = epubChapter(
            id = "c1",
            path = path,
            title = "旧标题",
            html = "<html><head><title>旧标题</title></head><body><h1>旧标题</h1><p>正文</p></body></html>"
        )

        return EpubBook(
            originalName = "book.epub",
            metadataTitle = "Book",
            metadataAuthor = "",
            metadataItems = mutableListOf(),
            entries = linkedMapOf(path to chapter.html.toByteArray(StandardCharsets.UTF_8)),
            opfPath = "OEBPS/content.opf",
            tocPath = "OEBPS/toc.ncx",
            manifest = mutableMapOf(
                "c1" to ManifestItem(
                    id = "c1",
                    href = href,
                    mediaType = "application/xhtml+xml",
                    path = path
                )
            ),
            spineIds = mutableListOf("c1"),
            chapters = mutableListOf(chapter)
        )
    }

    private fun epubChapter(
        id: String,
        path: String,
        title: String,
        html: String
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
