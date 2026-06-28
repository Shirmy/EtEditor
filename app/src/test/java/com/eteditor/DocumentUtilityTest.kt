package com.eteditor

import com.eteditor.core.CheckReport
import com.eteditor.core.DocumentKind
import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.ManifestItem
import com.eteditor.core.TxtChapter
import com.eteditor.core.TxtDocument
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentUtilityTest {
    @Test
    fun baseNameAndSanitizedSaveBaseNameCleanUnsafeFileNames() {
        assertEquals("book", "dir/sub/book.epub".baseName("fallback"))
        assertEquals("fallback", ".epub".baseName("fallback"))
        assertEquals("bad_name", " bad:name.txt ".sanitizedSaveBaseName("fallback"))
        assertEquals("bad__name", "bad\u0001/name.epub".sanitizedSaveBaseName("fallback"))
        assertEquals("fallback", " .txt ".sanitizedSaveBaseName("fallback"))
        assertEquals("fallback", ".. .epub".sanitizedSaveBaseName("fallback"))
    }

    @Test
    fun baseNameUsesOnlyForwardSlashAndLastDot() {
        assertEquals("archive.book", "dir/archive.book.epub".baseName("fallback"))
        assertEquals("""C:\dir\book""", """C:\dir\book.txt""".baseName("fallback"))
        assertEquals("fallback", "dir/.hidden".baseName("fallback"))
    }

    @Test
    fun sanitizedSaveBaseNameOnlyDropsTxtAndEpubExtensionsCaseInsensitively() {
        assertEquals("Book", " Book.EPUB ".sanitizedSaveBaseName("fallback"))
        assertEquals("Book", "Book.TxT".sanitizedSaveBaseName("fallback"))
        assertEquals("Book.pdf", "Book.pdf".sanitizedSaveBaseName("fallback"))
        assertEquals("bad_name__", "bad<name>|.txt".sanitizedSaveBaseName("fallback"))
    }

    @Test
    fun sanitizedSaveBaseNameTrimsOuterDotsAfterExtensionRemoval() {
        assertEquals("Book", "..Book..txt".sanitizedSaveBaseName("fallback"))
        assertEquals("Book.Part.1", ".Book.Part.1.".sanitizedSaveBaseName("fallback"))
    }

    @Test
    fun saveCheckAndWritableErrorMessagesExplainFailures() {
        assertEquals(
            "保存前检查未通过：\n1. 缺少 OPF\n2. 缺少章节",
            buildSaveCheckFailureMessage(CheckReport(errors = listOf("缺少 OPF", "缺少章节"), warnings = emptyList()))
        )
        assertEquals("保存前检查未通过", buildSaveCheckFailureMessage(CheckReport(emptyList(), emptyList())))
        assertEquals("当前文件位置是只读的，不能覆盖保存原文件", writableFileErrorMessage(IllegalStateException("read-only file")))
        assertEquals("没有写入权限，请重新从文件页打开原文件", writableFileErrorMessage(IllegalStateException("permission denied")))
        assertEquals("boom", writableFileErrorMessage(IllegalStateException("boom")))
        assertEquals("文件位置不允许写入或授权已失效，请重新从文件页打开原文件", writableFileErrorMessage(IllegalStateException()))
    }

    @Test
    fun saveCheckFailureMessageIgnoresWarningsWhenThereAreNoErrors() {
        val report = CheckReport(errors = emptyList(), warnings = listOf("缺少封面"))

        assertEquals("保存前检查未通过", buildSaveCheckFailureMessage(report))
    }

    @Test
    fun writableFileErrorMessagePrioritizesReadOnlyBeforePermissionWords() {
        assertEquals(
            "当前文件位置是只读的，不能覆盖保存原文件",
            writableFileErrorMessage(IllegalStateException("READ-ONLY permission denied"))
        )
        assertEquals(
            "没有写入权限，请重新从文件页打开原文件",
            writableFileErrorMessage(IllegalStateException("Access DENIED"))
        )
    }

    @Test
    fun lineEndingLabelsDistinguishCommonLineEndingShapes() {
        assertEquals("无", lineEndingLabel("没有换行"))
        assertEquals("CRLF", lineEndingLabel("一\r\n二\r\n"))
        assertEquals("CR", lineEndingLabel("一\r二\r"))
        assertEquals("LF", lineEndingLabel("一\n二\n"))
        assertEquals("混合", lineEndingLabel("一\r\n二\n三"))
    }

    @Test
    fun lineEndingLabelsTreatCrLfCrAndLfAsMixedWhenMultipleKindsAppear() {
        assertEquals("混合", lineEndingLabel("一\r\n二\r三"))
        assertEquals("混合", lineEndingLabel("一\r二\n三"))
    }

    @Test
    fun lineEndingHintsReportFullDocumentAndCurrentChapterWhenMixed() {
        val txt = TxtDocument(
            originalName = "book.txt",
            text = "第1章\r\n正文\r\n第2章\n正文",
            encoding = "UTF-8",
            chapters = listOf(
                TxtChapter(1, 0, 2, "第1章", 2, startIndex = 0, endIndex = 8),
                TxtChapter(2, 2, 4, "第2章", 2, startIndex = 8, endIndex = "第1章\r\n正文\r\n第2章\n正文".length)
            )
        )
        val epub = EpubBook(
            originalName = "book.epub",
            metadataTitle = "Book",
            metadataAuthor = "",
            entries = linkedMapOf(),
            opfPath = "OEBPS/content.opf",
            tocPath = "OEBPS/toc.ncx",
            manifest = mutableMapOf("c1" to ManifestItem("c1", "Text/c1.xhtml", "application/xhtml+xml", "OEBPS/Text/c1.xhtml")),
            spineIds = mutableListOf("c1"),
            chapters = mutableListOf(
                EpubChapter(
                    id = "c1",
                    href = "Text/c1.xhtml",
                    path = "OEBPS/Text/c1.xhtml",
                    originalPath = "OEBPS/Text/c1.xhtml",
                    pathAliases = mutableSetOf("OEBPS/Text/c1.xhtml"),
                    title = "第一章",
                    html = "<html><body><p>一</p>\n<p>二</p></body></html>",
                    wordCount = 2
                )
            )
        )

        assertEquals("全文：混合 | 本章：CRLF", bodyLineEndingHintForDocument(DocumentKind.Txt, null, txt, 0))
        assertEquals("全文：LF", bodyLineEndingHintForDocument(DocumentKind.Epub, epub, null, 0))
        epub.chapters += EpubChapter(
            id = "c2",
            href = "Text/c2.xhtml",
            path = "OEBPS/Text/c2.xhtml",
            originalPath = "OEBPS/Text/c2.xhtml",
            pathAliases = mutableSetOf("OEBPS/Text/c2.xhtml"),
            title = "第二章",
            html = "<html><body><p>三</p>\r\n<p>四</p></body></html>",
            wordCount = 2
        )
        assertEquals("全文：混合 | 本章：LF", bodyLineEndingHintForDocument(DocumentKind.Epub, epub, null, 9))
        assertEquals("全文：无", bodyLineEndingHintForDocument(DocumentKind.None, null, null, 0))
    }

    @Test
    fun lineEndingHintsUseFullTxtTextWhenCurrentChapterIndexIsMissing() {
        val txt = TxtDocument(
            originalName = "book.txt",
            text = "第1章\r\n正文\r\n第2章\n正文",
            encoding = "UTF-8",
            chapters = listOf(
                TxtChapter(1, 0, 2, "第1章", 2, startIndex = 0, endIndex = 8)
            )
        )

        assertEquals("全文：混合 | 本章：混合", bodyLineEndingHintForDocument(DocumentKind.Txt, null, txt, 9))
    }

    @Test
    fun lineEndingHintsUseFirstEpubChapterWhenPreviewIndexIsMissing() {
        val book = EpubBook(
            originalName = "book.epub",
            metadataTitle = "Book",
            metadataAuthor = "",
            entries = linkedMapOf(),
            opfPath = "OEBPS/content.opf",
            tocPath = "OEBPS/toc.ncx",
            manifest = mutableMapOf(),
            spineIds = mutableListOf("c1", "c2"),
            chapters = mutableListOf(
                EpubChapter(
                    id = "c1",
                    href = "Text/c1.xhtml",
                    path = "OEBPS/Text/c1.xhtml",
                    originalPath = "OEBPS/Text/c1.xhtml",
                    pathAliases = mutableSetOf("OEBPS/Text/c1.xhtml"),
                    title = "第一章",
                    html = "<html><body><p>一行</p></body></html>",
                    wordCount = 1
                ),
                EpubChapter(
                    id = "c2",
                    href = "Text/c2.xhtml",
                    path = "OEBPS/Text/c2.xhtml",
                    originalPath = "OEBPS/Text/c2.xhtml",
                    pathAliases = mutableSetOf("OEBPS/Text/c2.xhtml"),
                    title = "第二章",
                    html = "<html><body><p>二</p>\r\n<p>三</p>\n<p>四</p></body></html>",
                    wordCount = 3
                )
            )
        )

        assertEquals("全文：混合 | 本章：无", bodyLineEndingHintForDocument(DocumentKind.Epub, book, null, 9))
    }

    @Test
    fun epubSummaryMetaBuildsFromBodyLineEndingsAndWordCountLabel() {
        val book = EpubBook(
            originalName = "book.epub",
            metadataTitle = "Book",
            metadataAuthor = "",
            entries = linkedMapOf(),
            opfPath = "OEBPS/content.opf",
            tocPath = "OEBPS/toc.ncx",
            manifest = mutableMapOf(),
            spineIds = mutableListOf("c1", "c2"),
            chapters = mutableListOf(
                EpubChapter(
                    id = "c1",
                    href = "Text/c1.xhtml",
                    path = "OEBPS/Text/c1.xhtml",
                    originalPath = "OEBPS/Text/c1.xhtml",
                    pathAliases = mutableSetOf("OEBPS/Text/c1.xhtml"),
                    title = "第一章",
                    html = "<html><body><h1>第一章</h1>\r\n<p>一 二</p>\r\n<p><span>三</span></p></body></html>",
                    wordCount = 0
                ),
                EpubChapter(
                    id = "c2",
                    href = "Text/c2.xhtml",
                    path = "OEBPS/Text/c2.xhtml",
                    originalPath = "OEBPS/Text/c2.xhtml",
                    pathAliases = mutableSetOf("OEBPS/Text/c2.xhtml"),
                    title = "第二章",
                    html = "<html><body><h1>第二章</h1>\n<p>四五</p></body></html>",
                    wordCount = 0
                )
            )
        )

        val lineEnding = bodyLineEndingHintForDocument(DocumentKind.Epub, book, null, 0)
            .removePrefix("全文：")
            .substringBefore(" |")

        assertEquals("混合", lineEnding)
        assertEquals("UTF-8|混合|5", buildEpubSummaryMeta(lineEnding, wordCountLabel = "5"))
        assertEquals("UTF-8|...|...|1.5KB", buildEpubSummaryLoadingMeta(fileSizeBytes = 1536))
    }
}
