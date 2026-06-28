package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.DocumentKind
import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.ManifestItem
import com.eteditor.core.TxtChapter
import com.eteditor.core.TxtDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleFormatUtilsTest {
    @Test
    fun parseTitleFormatPartsNormalizesPrefixAndSuffix() {
        assertEquals(
            TitleFormatParts(prefix = "第十二章", suffix = "旧标题"),
            parseTitleFormatParts("第 十二 章 —— 旧标题")
        )
        assertEquals(TitleFormatParts(prefix = null, suffix = ""), parseTitleFormatParts("序章"))
    }

    @Test
    fun autoDecisionChoosesNoneLeftOrDoubleBySuffixDistribution() {
        assertEquals(
            TITLE_FORMAT_STYLE_NONE,
            titleFormatAutoDecision(
                listOf(
                    TitleFormatParts("第1章", ""),
                    TitleFormatParts("第2章", ""),
                    TitleFormatParts("第3章", ""),
                    TitleFormatParts("第4章", ""),
                    TitleFormatParts("第5章", "短")
                ),
                shortThreshold = 3
            ).style
        )
        assertEquals(
            TITLE_FORMAT_STYLE_LEFT,
            titleFormatAutoDecision(
                listOf(
                    TitleFormatParts("第1章", "很长很长的标题"),
                    TitleFormatParts("第2章", "也很长的标题"),
                    TitleFormatParts("第3章", "短")
                ),
                shortThreshold = 3
            ).style
        )
        assertEquals(
            TITLE_FORMAT_STYLE_DOUBLE,
            titleFormatAutoDecision(
                listOf(TitleFormatParts("第1章", "短"), TitleFormatParts("第2章", "标题")),
                shortThreshold = 3
            ).style
        )
    }

    @Test
    fun titleFormatMessagesUsePlanReasonOrUniformStyle() {
        val autoParameters = TitleFormatParameters(
            mode = TITLE_FORMAT_MODE_PER_CHAPTER,
            style = TITLE_FORMAT_STYLE_DOUBLE,
            preview = true,
            scope = TITLE_FORMAT_SCOPE_ALL,
            selectedChapterIndices = emptySet()
        )
        val plan = buildTitleFormatPlanItems(
            parameters = autoParameters,
            targetIndices = listOf(0, 1, 2, 3, 4),
            titles = listOf("第1章", "第2章", "第3章", "第4章", "第5章 短")
        )
        val uniformParameters = autoParameters.copy(
            mode = TITLE_FORMAT_MODE_UNIFORM,
            style = TITLE_FORMAT_STYLE_LEFT
        )

        assertEquals("自动：判断为无横线", titleFormatLogicText(autoParameters, plan))
        assertEquals("统一：左竖线", titleFormatLogicText(uniformParameters, plan))
        assertEquals(
            "判断原因：自动：判断为无横线，所以无需修改（检查 5 章，修改 0 章）",
            titleFormatNoChangeMessage(plan)
        )
        assertEquals(
            "判断原因：自动：判断为双横线，所以无需修改（检查 0 章，修改 0 章）",
            titleFormatNoChangeMessage(emptyList())
        )
        assertEquals(
            "标题格式完成：处理 5 章，修改 2 章",
            titleFormatCompletionMessage(plan, changed = 2)
        )
    }

    @Test
    fun renderTitleFormatUsesLineBreakHeadingForLeftStyle() {
        val rendered = renderTitleFormat(
            prefix = "第1章",
            suffix = "标题 & 备注",
            style = TITLE_FORMAT_STYLE_LEFT
        )

        assertEquals("第1章 标题 & 备注", rendered.plainTitle)
        assertEquals("第1章<br/>标题 &amp; 备注", rendered.headingHtml)
        assertEquals(TITLE_FORMAT_STYLE_LEFT, rendered.styleCode)
    }

    @Test
    fun buildTitleFormatPlanModelFiltersSelectedEpubChapters() {
        val book = sampleBook()
        val result = buildTitleFormatPlanModel(
            kind = DocumentKind.Epub,
            epubChapters = book.chapters,
            txtDocument = null,
            parameters = TitleFormatParameters(
                mode = TITLE_FORMAT_MODE_UNIFORM,
                style = TITLE_FORMAT_STYLE_NONE,
                preview = true,
                scope = TITLE_FORMAT_SCOPE_SELECTED,
                selectedChapterIndices = setOf(1, 2)
            )
        )

        assertEquals(listOf(1), result.plan.map { it.chapterIndex })
        assertEquals("第9章", result.plan.single().newTitle)
        assertEquals("统一：无横线", result.plan.single().reason)
    }

    @Test
    fun buildTitleFormatPlanModelFiltersSelectedTxtChapters() {
        val text = "第1章 开始\n正文\n第2章 第二章\n正文"
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = detectTxtChapters(text)
        )

        val result = buildTitleFormatPlanModel(
            kind = DocumentKind.Txt,
            epubChapters = null,
            txtDocument = document,
            parameters = TitleFormatParameters(
                mode = TITLE_FORMAT_MODE_UNIFORM,
                style = TITLE_FORMAT_STYLE_NONE,
                preview = true,
                scope = TITLE_FORMAT_SCOPE_SELECTED,
                selectedChapterIndices = setOf(1, 99)
            )
        )

        assertEquals("", result.message)
        assertEquals(listOf(1), result.plan.map { it.chapterIndex })
        assertEquals("第2章 第二章", result.plan.single().oldTitle)
        assertEquals("第2章", result.plan.single().newTitle)
        assertEquals("统一：无横线", result.plan.single().reason)
    }

    @Test
    fun buildTitleFormatPlanModelReportsSelectedScopeWithoutValidTargets() {
        val book = sampleBook()
        val result = buildTitleFormatPlanModel(
            kind = DocumentKind.Epub,
            epubChapters = book.chapters,
            txtDocument = null,
            parameters = TitleFormatParameters(
                mode = TITLE_FORMAT_MODE_UNIFORM,
                style = TITLE_FORMAT_STYLE_DOUBLE,
                preview = true,
                scope = TITLE_FORMAT_SCOPE_SELECTED,
                selectedChapterIndices = setOf(0, 2)
            )
        )

        assertEquals(emptyList<TitleFormatPlanItem>(), result.plan)
        assertEquals("请选择HTML章节", result.message)
    }

    @Test
    fun titleFormatSelectableChapterOptionsSkipEpubVolumesAndCovers() {
        val book = sampleBook()
        val text = "第1章\n正文\n第2章 标题\n正文"
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = detectTxtChapters(text)
        )

        assertEquals(
            listOf("1" to "第9章 旧标题"),
            titleFormatSelectableChapterOptionsModel(DocumentKind.Epub, book.chapters, null)
        )
        assertEquals(
            listOf("0" to "第1章", "1" to "第2章 标题"),
            titleFormatSelectableChapterOptionsModel(DocumentKind.Txt, null, document)
        )
        assertEquals(emptyList<Pair<String, String>>(), titleFormatSelectableChapterOptionsModel(DocumentKind.None, null, null))
    }

    @Test
    fun titleFormatSelectableChapterOptionsUseDefaultLabelsForBlankTxtTitles() {
        val document = TxtDocument(
            originalName = "book.txt",
            text = "正文",
            encoding = "UTF-8",
            chapters = listOf(
                TxtChapter(
                    index = 1,
                    lineIndex = 0,
                    endLineIndex = 1,
                    title = "",
                    wordCount = 2,
                    startIndex = 0,
                    bodyStartIndex = 0,
                    endIndex = 2
                )
            )
        )

        assertEquals(
            listOf("0" to "第 1 页"),
            titleFormatSelectableChapterOptionsModel(DocumentKind.Txt, null, document)
        )
    }

    @Test
    fun buildTitleFormatPlanModelMarksEpubStyleOnlyChanges() {
        val book = sampleBook(
            mutableListOf(
                chapter(
                    "c1",
                    "OEBPS/Text/Chapter0001.xhtml",
                    "第1章 标题",
                    """<html><head><title>第1章 标题</title></head><body><h1 class="chapter-title_02">第1章 标题</h1></body></html>"""
                )
            )
        )

        val result = buildTitleFormatPlanModel(
            kind = DocumentKind.Epub,
            epubChapters = book.chapters,
            txtDocument = null,
            parameters = TitleFormatParameters(
                mode = TITLE_FORMAT_MODE_UNIFORM,
                style = TITLE_FORMAT_STYLE_LEFT,
                preview = true,
                scope = TITLE_FORMAT_SCOPE_ALL,
                selectedChapterIndices = emptySet()
            )
        )

        val item = result.plan.single()
        assertEquals("第1章 标题", item.oldTitle)
        assertEquals("第1章 标题", item.newTitle)
        assertTrue(item.changed)
    }

    @Test
    fun applyEpubTitleFormatsUpdatesTitleHeadingClassAndWordCount() {
        val book = sampleBook()
        val rendered = renderTitleFormat("第1章", "新标题", TITLE_FORMAT_STYLE_LEFT)

        val changed = applyEpubTitleFormatsToBook(book, listOf(1 to rendered))

        assertEquals(1, changed)
        assertEquals("第1章 新标题", book.chapters[1].title)
        assertTrue(book.chapters[1].html.contains("<title>第1章 新标题</title>"))
        assertTrue(book.chapters[1].html.contains("""<h1 class="keep chapter-title_01">第1章<br/>新标题</h1>"""))
        assertTrue(book.chapters[1].wordCount > 0)
    }

    @Test
    fun insertReferenceTitleStyleUsesNearestBodyChapter() {
        val book = sampleBook(
            mutableListOf(
                chapter("v1", "OEBPS/Text/Vol01.xhtml", "第一卷", "<html><body><h1>第一卷</h1></body></html>"),
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 长标题", "<html><body><h1>第1章<br/>长标题</h1></body></html>"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章", "<html><body><h1>第2章</h1></body></html>")
            )
        )
        val text = "第1章\n正文\n第2章 标题\n正文"
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = detectTxtChapters(text)
        )

        assertEquals(TITLE_FORMAT_STYLE_LEFT, epubInsertReferenceTitleStyle(book, insertPosition = 0))
        assertEquals(TITLE_FORMAT_STYLE_LEFT, epubInsertReferenceTitleStyle(book, insertPosition = 2))
        assertEquals(TITLE_FORMAT_STYLE_NONE, txtInsertReferenceTitleStyle(document, insertPosition = 0))
        assertEquals(TITLE_FORMAT_STYLE_DOUBLE, txtInsertReferenceTitleStyle(document, insertPosition = 2))
    }

    @Test
    fun applyTxtTitleFormatsUpdatesTextAndRedetectsChapters() {
        val text = "第1章 旧标题\n正文\n第2章 第二章\n正文"
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = detectTxtChapters(text)
        )
        val rendered = renderTitleFormat("第1章", "新标题", TITLE_FORMAT_STYLE_DOUBLE)

        val changed = applyTxtTitleFormatsToDocument(
            document = document,
            renderedByIndex = listOf(0 to rendered),
            detectChapters = ::detectTxtChapters
        )

        assertEquals(1, changed)
        assertEquals("第1章 新标题\n正文\n第2章 第二章\n正文", document.text)
        assertEquals(listOf("第1章 新标题", "第2章 第二章"), document.chapters.map { it.title })
    }

    @Test
    fun inheritedHeadingFormatIsAppliedWhenUpdatingHtmlTitle() {
        val inherited = inheritedTitleHeadingFormat("""<h2 class="chapter-title_02 custom">旧标题</h2>""")
        val updated = updateHtmlTitleWithInheritedFormat(
            html = "<html><head><title>旧</title></head><body><h1>旧</h1></body></html>",
            newTitle = "新 <标题>",
            inheritedFormat = inherited
        )

        assertEquals("h2", inherited?.tag)
        assertEquals("chapter-title_02 custom", inherited?.classValue)
        assertTrue(updated.contains("<title>新 &lt;标题&gt;</title>"))
        assertTrue(updated.contains("""<h2 class="chapter-title_02 custom">新 &lt;标题&gt;</h2>"""))
    }

    @Test
    fun inheritedHeadingFormatIsInsertedWhenHtmlHasBodyButNoHeading() {
        val updated = updateHtmlTitleWithInheritedFormat(
            html = """<html><head><title>旧</title></head><body data-x="1"><p>正文</p></body></html>""",
            newTitle = "新 & 标题",
            inheritedFormat = EpubTitleHeadingFormat(
                tag = "h2",
                classValue = "chapter-title_01 custom"
            )
        )

        assertTrue(updated.contains("<title>新 &amp; 标题</title>"))
        assertTrue(
            updated.contains(
                """
                <body data-x="1">
                <h2 class="chapter-title_01 custom">新 &amp; 标题</h2>
                <p>正文</p>
                """.trimIndent()
            )
        )
    }

    @Test
    fun updateHtmlTitleForFormatInsertsEscapedHeadingWhenNoHeadingExists() {
        val rendered = renderTitleFormat("第1章", "标题 & 注", TITLE_FORMAT_STYLE_LEFT)
        val updated = updateHtmlTitleForFormat(
            html = "<html><head><title>旧</title></head><body><p>正文</p></body></html>",
            plainTitle = rendered.plainTitle,
            headingHtml = rendered.headingHtml,
            styleCode = rendered.styleCode
        )

        assertTrue(updated.contains("<title>第1章 标题 &amp; 注</title>"))
        assertTrue(updated.contains("<body>\n<h1 class=\"chapter-title_01\">第1章<br/>标题 &amp; 注</h1><p>正文</p>"))
    }

    @Test
    fun buildTitleFormatPlanModelGroupsByVolumeInPerChapterModeWithNormalAndExtraVolumes() {
        val book = sampleBook(
            mutableListOf(
                chapter("v1", "OEBPS/Text/Vol01.xhtml", "第一卷", "<html><body><h1>第一卷</h1></body></html>"),
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 很长的标题文字", "<html><body><h1>第1章 很长的标题文字</h1></body></html>"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章 也很长的标题文字", "<html><body><h1>第2章 也很长的标题文字</h1></body></html>"),
                chapter("v0", "OEBPS/Text/Vol00.xhtml", "番外卷", "<html><body><h1>番外卷</h1></body></html>"),
                chapter("c3", "OEBPS/Text/Chapter0003.xhtml", "第3章", "<html><body><h1>第3章</h1></body></html>"),
                chapter("c4", "OEBPS/Text/Chapter0004.xhtml", "第4章", "<html><body><h1>第4章</h1></body></html>")
            )
        )
        val parameters = TitleFormatParameters(
            mode = TITLE_FORMAT_MODE_PER_CHAPTER,
            style = TITLE_FORMAT_STYLE_DOUBLE,
            preview = true,
            scope = TITLE_FORMAT_SCOPE_ALL,
            selectedChapterIndices = emptySet()
        )

        val result = buildTitleFormatPlanModel(
            kind = DocumentKind.Epub,
            epubChapters = book.chapters,
            txtDocument = null,
            parameters = parameters
        )

        assertEquals(listOf(1, 2, 4, 5), result.plan.map { it.chapterIndex })
        assertEquals(listOf(1, 2, 3, 4), result.plan.map { it.sequenceNumber })
        val bodyStyles = result.plan.filter { it.chapterIndex in listOf(1, 2) }.map { it.styleCode }
        val extraStyles = result.plan.filter { it.chapterIndex in listOf(4, 5) }.map { it.styleCode }
        assertEquals(listOf(TITLE_FORMAT_STYLE_LEFT, TITLE_FORMAT_STYLE_LEFT), bodyStyles)
        assertEquals(listOf(TITLE_FORMAT_STYLE_NONE, TITLE_FORMAT_STYLE_NONE), extraStyles)
        assertEquals("自动：正文左竖线，番外无横线", titleFormatLogicText(parameters, result.plan))
        assertEquals(
            "判断原因：自动：正文左竖线，番外无横线，所以无需修改（检查 4 章，修改 0 章）",
            titleFormatNoChangeMessage(result.plan)
        )
        assertEquals(
            "标题格式完成：自动：正文左竖线，番外无横线；处理 4 章，修改 2 章",
            titleFormatCompletionMessage(result.plan, changed = 2)
        )
    }

    @Test
    fun buildTitleFormatPlanModelTreatsChaptersBeforeExtraAsBodyWhenNoNormalVolume() {
        val book = sampleBook(
            mutableListOf(
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 很长的标题文字", "<html><body><h1>第1章 很长的标题文字</h1></body></html>"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章 也很长的标题文字", "<html><body><h1>第2章 也很长的标题文字</h1></body></html>"),
                chapter("v0", "OEBPS/Text/Vol00.xhtml", "番外卷", "<html><body><h1>番外卷</h1></body></html>"),
                chapter("c3", "OEBPS/Text/Chapter0003.xhtml", "第3章", "<html><body><h1>第3章</h1></body></html>"),
                chapter("c4", "OEBPS/Text/Chapter0004.xhtml", "第4章", "<html><body><h1>第4章</h1></body></html>")
            )
        )

        val result = buildTitleFormatPlanModel(
            kind = DocumentKind.Epub,
            epubChapters = book.chapters,
            txtDocument = null,
            parameters = TitleFormatParameters(
                mode = TITLE_FORMAT_MODE_PER_CHAPTER,
                style = TITLE_FORMAT_STYLE_DOUBLE,
                preview = true,
                scope = TITLE_FORMAT_SCOPE_ALL,
                selectedChapterIndices = emptySet()
            )
        )

        assertEquals(listOf(0, 1, 3, 4), result.plan.map { it.chapterIndex })
        assertEquals(listOf(1, 2, 3, 4), result.plan.map { it.sequenceNumber })
        val bodyStyles = result.plan.filter { it.chapterIndex in listOf(0, 1) }.map { it.styleCode }
        val extraStyles = result.plan.filter { it.chapterIndex in listOf(3, 4) }.map { it.styleCode }
        assertEquals(listOf(TITLE_FORMAT_STYLE_LEFT, TITLE_FORMAT_STYLE_LEFT), bodyStyles)
        assertEquals(listOf(TITLE_FORMAT_STYLE_NONE, TITLE_FORMAT_STYLE_NONE), extraStyles)
    }

    @Test
    fun buildTitleFormatPlanModelDoesNotGroupWithoutExtraVolume() {
        val book = sampleBook(
            mutableListOf(
                chapter("v1", "OEBPS/Text/Vol01.xhtml", "第一卷", "<html><body><h1>第一卷</h1></body></html>"),
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 标题", "<html><body><h1>第1章 标题</h1></body></html>"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章 标题", "<html><body><h1>第2章 标题</h1></body></html>"),
                chapter("c3", "OEBPS/Text/Chapter0003.xhtml", "第3章 标题", "<html><body><h1>第3章 标题</h1></body></html>")
            )
        )

        val result = buildTitleFormatPlanModel(
            kind = DocumentKind.Epub,
            epubChapters = book.chapters,
            txtDocument = null,
            parameters = TitleFormatParameters(
                mode = TITLE_FORMAT_MODE_PER_CHAPTER,
                style = TITLE_FORMAT_STYLE_DOUBLE,
                preview = true,
                scope = TITLE_FORMAT_SCOPE_ALL,
                selectedChapterIndices = emptySet()
            )
        )

        assertEquals(listOf(1, 2, 3), result.plan.map { it.chapterIndex })
        assertEquals(listOf(1, 2, 3), result.plan.map { it.sequenceNumber })
    }

    @Test
    fun buildTitleFormatPlanModelDoesNotGroupInUniformModeWithExtraVolume() {
        val book = sampleBook(
            mutableListOf(
                chapter("v1", "OEBPS/Text/Vol01.xhtml", "第一卷", "<html><body><h1>第一卷</h1></body></html>"),
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 标题", "<html><body><h1>第1章 标题</h1></body></html>"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章 标题", "<html><body><h1>第2章 标题</h1></body></html>"),
                chapter("v0", "OEBPS/Text/Vol00.xhtml", "番外卷", "<html><body><h1>番外卷</h1></body></html>"),
                chapter("c3", "OEBPS/Text/Chapter0003.xhtml", "第3章 标题", "<html><body><h1>第3章 标题</h1></body></html>"),
                chapter("c4", "OEBPS/Text/Chapter0004.xhtml", "第4章 标题", "<html><body><h1>第4章 标题</h1></body></html>")
            )
        )

        val result = buildTitleFormatPlanModel(
            kind = DocumentKind.Epub,
            epubChapters = book.chapters,
            txtDocument = null,
            parameters = TitleFormatParameters(
                mode = TITLE_FORMAT_MODE_UNIFORM,
                style = TITLE_FORMAT_STYLE_DOUBLE,
                preview = true,
                scope = TITLE_FORMAT_SCOPE_ALL,
                selectedChapterIndices = emptySet()
            )
        )

        assertEquals(listOf(1, 2, 4, 5), result.plan.map { it.chapterIndex })
        assertEquals(listOf(1, 2, 3, 4), result.plan.map { it.sequenceNumber })
        assertEquals(
            listOf(TITLE_FORMAT_STYLE_DOUBLE, TITLE_FORMAT_STYLE_DOUBLE, TITLE_FORMAT_STYLE_DOUBLE, TITLE_FORMAT_STYLE_DOUBLE),
            result.plan.map { it.styleCode }
        )
    }

    @Test
    fun buildTitleFormatPlanModelKeepsExtraTitlePrefixOnGroupingWithoutReset() {
        val book = sampleBook(
            mutableListOf(
                chapter("v1", "OEBPS/Text/Vol01.xhtml", "第一卷", "<html><body><h1>第一卷</h1></body></html>"),
                chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章 很长的标题文字", "<html><body><h1>第1章 很长的标题文字</h1></body></html>"),
                chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章 也很长的标题文字", "<html><body><h1>第2章 也很长的标题文字</h1></body></html>"),
                chapter("v0", "OEBPS/Text/Vol00.xhtml", "番外卷", "<html><body><h1>番外卷</h1></body></html>"),
                chapter("c3", "OEBPS/Text/Chapter0003.xhtml", "生日番外", "<html><body><h1>生日番外</h1></body></html>"),
                chapter("c4", "OEBPS/Text/Chapter0004.xhtml", "特别篇", "<html><body><h1>特别篇</h1></body></html>")
            )
        )

        val result = buildTitleFormatPlanModel(
            kind = DocumentKind.Epub,
            epubChapters = book.chapters,
            txtDocument = null,
            parameters = TitleFormatParameters(
                mode = TITLE_FORMAT_MODE_PER_CHAPTER,
                style = TITLE_FORMAT_STYLE_DOUBLE,
                preview = true,
                scope = TITLE_FORMAT_SCOPE_ALL,
                selectedChapterIndices = emptySet()
            )
        )

        val extraItems = result.plan.filter { it.chapterIndex in listOf(4, 5) }
        assertEquals(listOf(3, 4), extraItems.map { it.sequenceNumber })
        assertEquals(listOf("生日番外", "特别篇"), extraItems.map { it.newTitle })
        assertEquals(listOf(TITLE_FORMAT_STYLE_NONE, TITLE_FORMAT_STYLE_NONE), extraItems.map { it.styleCode })
    }

    private fun detectTxtChapters(text: String): List<TxtChapter> {
        return ChapterDetector.detectTxtChapters(
            text = text,
            customPatterns = listOf("""^第\s*(\d+)\s*章.*$""")
        )
    }

    private fun sampleBook(
        chapters: MutableList<EpubChapter> = mutableListOf(
            chapter("v1", "OEBPS/Text/Vol01.xhtml", "第一卷", "<html><body><h1>第一卷</h1></body></html>"),
            chapter(
                "c1",
                "OEBPS/Text/Chapter0001.xhtml",
                "第9章 旧标题",
                """<html><head><title>旧</title></head><body><h1 class="keep chapter-title_02">旧</h1><p>正文</p></body></html>"""
            ),
            chapter("cover", "OEBPS/Text/Section0001.xhtml", "封面", "<html><body><h1>封面</h1></body></html>")
        )
    ): EpubBook {
        val entries = linkedMapOf<String, ByteArray>()
        val manifest = mutableMapOf<String, ManifestItem>()
        chapters.forEach { chapter ->
            entries[chapter.path] = chapter.html.toByteArray(Charsets.UTF_8)
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
            chapters = chapters
        )
    }

    private fun chapter(id: String, path: String, title: String, html: String): EpubChapter {
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
