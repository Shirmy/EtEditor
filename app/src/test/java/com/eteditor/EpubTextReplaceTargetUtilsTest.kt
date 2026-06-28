package com.eteditor

import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.ManifestItem
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubTextReplaceTargetUtilsTest {
    @Test
    fun epubHtmlTextReplaceTargetsDeduplicateHtmlAndExcludeGeneratedCoverSections() {
        val book = sampleBook(
            entries = linkedMapOf(
                "OEBPS/Text/chapter1.xhtml" to html("第一章", "正文").toByteArray(StandardCharsets.UTF_8),
                "OEBPS/Text/cover.xhtml" to html("封面", "封面").toByteArray(StandardCharsets.UTF_8),
                "OEBPS/Text/section0001.xhtml" to html("封面目录", "目录").toByteArray(StandardCharsets.UTF_8),
                "OEBPS/Text/extra.html" to html("附加", "附加").toByteArray(StandardCharsets.UTF_8)
            ),
            chapterPath = "OEBPS/Text/chapter1.xhtml",
            extraManifestPath = "OEBPS/Text/extra.html"
        )

        val targets = epubHtmlTextReplaceTargets(book)

        assertEquals(listOf("OEBPS/Text/chapter1.xhtml", "OEBPS/Text/extra.html"), targets.map { it.path })
    }

    @Test
    fun epubPackageTextReplaceTargetsMatchesCurrentPathAfterNormalization() {
        val book = sampleBook()

        val targets = epubPackageTextReplaceTargets(
            book = book,
            scope = TOOL_SCOPE_CURRENT,
            currentPath = "OEBPS/./Text/chapter1.xhtml",
            introPath = "OEBPS/Text/intro.xhtml"
        )

        assertEquals(listOf("OEBPS/Text/chapter1.xhtml"), targets.map { it.path })
    }

    @Test
    fun epubPackageTextReplaceTargetsReturnsEmptyForCurrentScopeWhenCurrentPathIsMissing() {
        val book = sampleBook()

        val withoutCurrentPath = epubPackageTextReplaceTargets(
            book = book,
            scope = TOOL_SCOPE_CURRENT,
            currentPath = null,
            introPath = "OEBPS/Text/intro.xhtml"
        )
        val unmatchedCurrentPath = epubPackageTextReplaceTargets(
            book = book,
            scope = TOOL_SCOPE_CURRENT,
            currentPath = "OEBPS/Text/missing.xhtml",
            introPath = "OEBPS/Text/intro.xhtml"
        )

        assertTrue(withoutCurrentPath.isEmpty())
        assertTrue(unmatchedCurrentPath.isEmpty())
    }

    @Test
    fun epubHtmlTextReplaceTargetsIncludeHtmlEntriesOutsideChaptersAndManifest() {
        val entryOnlyHtml = html("附录", "后记正文")
        val book = sampleBook(
            entries = linkedMapOf(
                "OEBPS/Text/chapter1.xhtml" to html("第一章", "正文").toByteArray(StandardCharsets.UTF_8),
                "OEBPS/Misc/note.htm" to entryOnlyHtml.toByteArray(StandardCharsets.UTF_8)
            )
        )

        val targets = epubHtmlTextReplaceTargets(book)
        val sources = epubPackageTextSearchSources(
            book = book,
            parameters = parameters(scope = TOOL_SCOPE_ALL),
            currentPath = null,
            introPath = "OEBPS/Text/intro.xhtml"
        )

        assertEquals(listOf("OEBPS/Text/chapter1.xhtml", "OEBPS/Misc/note.htm"), targets.map { it.path })
        assertEquals("note.htm", targets[1].title)
        assertEquals(targets[1].sourceIndex, sources[1].chapterIndex)
        assertEquals("OEBPS/Misc/note.htm", sources[1].fileName)
        assertTrue(sources[1].text.contains("后记正文"))
    }

    @Test
    fun epubIntroScopeUsesExplicitIntroPathAndBuildsBodySearchSource() {
        val introHtml = html("简介标题", "简介正文")
        val book = sampleBook(
            entries = linkedMapOf(
                "OEBPS/Text/chapter1.xhtml" to html("第一章", "正文").toByteArray(StandardCharsets.UTF_8),
                "OEBPS/Text/section0001.xhtml" to introHtml.toByteArray(StandardCharsets.UTF_8)
            )
        )

        val targets = epubPackageTextReplaceTargets(
            book = book,
            scope = TEXT_REPLACE_SCOPE_INTRO,
            currentPath = "OEBPS/Text/chapter1.xhtml",
            introPath = "OEBPS/Text/section0001.xhtml"
        )
        val sources = epubPackageTextSearchSources(
            book = book,
            parameters = parameters(scope = TEXT_REPLACE_SCOPE_INTRO),
            currentPath = "OEBPS/Text/chapter1.xhtml",
            introPath = "OEBPS/Text/section0001.xhtml"
        )

        assertEquals(listOf("OEBPS/Text/section0001.xhtml"), targets.map { it.path })
        assertEquals(listOf("简介"), targets.map { it.title })
        assertEquals(1, sources.size)
        assertEquals("简介", sources.single().title)
        assertEquals("OEBPS/Text/section0001.xhtml", sources.single().fileName)
        assertTrue(sources.single().text.contains("简介正文"))
        assertEquals(introHtml.indexOf("<h1>"), sources.single().sourceOffset)
    }

    @Test
    fun epubPackageTextReplaceTargetCanResolveIntroTargetBySourceIndex() {
        val book = sampleBook(
            entries = linkedMapOf(
                "OEBPS/Text/chapter1.xhtml" to html("第一章", "正文").toByteArray(StandardCharsets.UTF_8),
                "OEBPS/Text/section0001.xhtml" to html("简介标题", "简介正文").toByteArray(StandardCharsets.UTF_8)
            )
        )
        val introTarget = epubPackageTextReplaceTargets(
            book = book,
            scope = TEXT_REPLACE_SCOPE_INTRO,
            currentPath = "OEBPS/Text/chapter1.xhtml",
            introPath = "OEBPS/Text/section0001.xhtml"
        ).single()

        val resolved = epubPackageTextReplaceTarget(
            book = book,
            sourceIndex = introTarget.sourceIndex,
            introPath = "OEBPS/Text/section0001.xhtml"
        )

        assertEquals("简介", resolved?.title)
        assertEquals("OEBPS/Text/section0001.xhtml", resolved?.path)
    }

    @Test
    fun replaceInEpubPackageTextCanReplaceOnlyVisibleBodyText() {
        val original = """<html><head><title>旧标题</title></head><body><p data-x="foo">foo <span>foo</span></p></body></html>"""
        val book = sampleBook(
            entries = linkedMapOf("OEBPS/Text/chapter1.xhtml" to original.toByteArray(StandardCharsets.UTF_8)),
            html = original
        )

        val result = replaceInEpubPackageText(
            book = book,
            parameters = parameters(scope = TOOL_SCOPE_ALL),
            currentPath = null,
            introPath = "OEBPS/Text/intro.xhtml",
            rules = listOf(
                TextReplaceRule(
                    find = "foo",
                    replacement = "bar",
                    regex = false,
                    textOnly = true
                )
            )
        )

        assertEquals(1, result.filesChanged)
        assertEquals(2, result.replacements)
        assertTrue(book.chapters[0].html.contains("data-x=\"foo\""))
        assertTrue(book.chapters[0].html.contains("""<p data-x="foo">bar <span>bar</span></p>"""))
    }

    @Test
    fun applySingleEpubPackageTextReplacementRejectsOutsideBodyAndAppliesInsideBody() {
        val original = html("旧标题", "foo 正文")
        val book = sampleBook(
            entries = linkedMapOf("OEBPS/Text/chapter1.xhtml" to original.toByteArray(StandardCharsets.UTF_8)),
            html = original
        )
        val target = epubHtmlTextReplaceTargets(book).single()
        val titleStart = original.indexOf("旧标题")
        val bodyStart = original.indexOf("foo")

        val outside = applySingleEpubPackageTextReplacement(
            book = book,
            introPath = "OEBPS/Text/intro.xhtml",
            sourceIndex = target.sourceIndex,
            sourceStart = titleStart,
            sourceEnd = titleStart + "旧标题".length,
            rule = TextReplaceRule(find = "旧标题", replacement = "新标题", regex = false),
            caseSensitive = true
        )
        val inside = applySingleEpubPackageTextReplacement(
            book = book,
            introPath = "OEBPS/Text/intro.xhtml",
            sourceIndex = target.sourceIndex,
            sourceStart = bodyStart,
            sourceEnd = bodyStart + "foo".length,
            rule = TextReplaceRule(find = "foo", replacement = "bar", regex = false),
            caseSensitive = true
        )

        assertNull(outside)
        assertEquals(0, inside)
        assertTrue(book.chapters[0].html.contains("bar 正文"))
        assertTrue(book.chapters[0].html.contains("<title>旧标题</title>"))
    }

    @Test
    fun applyReplacementMatchPlansToEpubPackageTextIgnoresPlansOutsideBody() {
        val original = html("旧标题", "foo 正文")
        val book = sampleBook(
            entries = linkedMapOf("OEBPS/Text/chapter1.xhtml" to original.toByteArray(StandardCharsets.UTF_8)),
            html = original
        )
        val target = epubHtmlTextReplaceTargets(book).single()
        val titleStart = original.indexOf("旧标题")
        val bodyStart = original.indexOf("foo")

        val changed = applyReplacementMatchPlansToEpubPackageText(
            book = book,
            introPath = "OEBPS/Text/intro.xhtml",
            plans = listOf(
                ReplacementMatchPlan(
                    chapterIndex = target.sourceIndex,
                    sourceStart = titleStart,
                    sourceEnd = titleStart + "旧标题".length,
                    replacementText = "不应替换"
                ),
                ReplacementMatchPlan(
                    chapterIndex = target.sourceIndex,
                    sourceStart = bodyStart,
                    sourceEnd = bodyStart + "foo".length,
                    replacementText = "bar"
                )
            )
        )

        assertEquals(1, changed)
        assertTrue(book.chapters[0].html.contains("bar 正文"))
        assertTrue(book.chapters[0].html.contains("<title>旧标题</title>"))
    }

    @Test
    fun updateEpubPackageTextSyncsDirectoryTitleFromUnifiedEpubRule() {
        val nextHtml = "<html><head><title>备用标题</title></head><body><h1>第一章<br/>副标题</h1><p>正文</p></body></html>"
        val book = sampleBook()

        updateEpubPackageText(book, "OEBPS/Text/chapter1.xhtml", nextHtml)

        assertEquals("第一章 副标题", book.chapters[0].title)
    }

    private fun parameters(scope: String): TextReplaceParameters {
        return TextReplaceParameters(
            mode = TEXT_REPLACE_MODE_SINGLE,
            target = TEXT_REPLACE_TARGET_SOURCE,
            scope = scope,
            selectedHtmlSourceIndices = emptySet(),
            matchPattern = "",
            matchRegexEnabled = true,
            findText = "",
            replaceText = "",
            findRegexEnabled = false,
            batchSource = "",
            batchText = "",
            batchFile = "",
            preview = true
        )
    }

    private fun sampleBook(
        entries: LinkedHashMap<String, ByteArray> = linkedMapOf(
            "OEBPS/Text/chapter1.xhtml" to html("第一章", "正文").toByteArray(StandardCharsets.UTF_8)
        ),
        chapterPath: String = "OEBPS/Text/chapter1.xhtml",
        extraManifestPath: String? = null,
        html: String = String(entries.getValue(chapterPath), StandardCharsets.UTF_8)
    ): EpubBook {
        val href = chapterPath.removePrefix("OEBPS/")
        val manifest = mutableMapOf(
            "c1" to ManifestItem(
                id = "c1",
                href = href,
                mediaType = "application/xhtml+xml",
                path = chapterPath
            )
        )
        extraManifestPath?.let { path ->
            manifest["extra"] = ManifestItem(
                id = "extra",
                href = path.removePrefix("OEBPS/"),
                mediaType = "application/xhtml+xml",
                path = path
            )
        }
        val chapter = EpubChapter(
            id = "c1",
            href = href,
            path = chapterPath,
            originalPath = chapterPath,
            pathAliases = mutableSetOf(chapterPath),
            title = "第一章",
            html = html,
            wordCount = 2
        )

        return EpubBook(
            originalName = "book.epub",
            metadataTitle = "Book",
            metadataAuthor = "",
            entries = entries,
            opfPath = "OEBPS/content.opf",
            tocPath = "OEBPS/toc.ncx",
            manifest = manifest,
            spineIds = mutableListOf("c1"),
            chapters = mutableListOf(chapter)
        )
    }

    private companion object {
        fun html(title: String, body: String): String {
            return "<html><head><title>$title</title></head><body><h1>$title</h1><p>$body</p></body></html>"
        }
    }
}
