package com.eteditor

import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.ManifestItem
import com.eteditor.core.TxtDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsertChapterEpubImportUtilsTest {
    @Test
    fun importRootsDefaultPathsAndTocLevelsFollowTargetBookStructure() {
        val book = sampleBook(
            listOf(
                epubChapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章"),
                epubChapter("v1", "OEBPS/Text/Vol01.xhtml", "第一卷"),
                epubChapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章")
            )
        )
        book.entries["Imported/Book/asset.png"] = byteArrayOf(1)

        assertEquals("Imported/Book_1/", importedEpubRoot(book, "Book.epub"))
        assertEquals("Imported/My_Book/", importedEpubRoot(book, "My Book!.epub"))
        assertEquals("OEBPS/Text/InsertedChapterA.xhtml", defaultInsertedChapterPath(book, 0))
        assertEquals("OEBPS/Text/InsertedChapterAA.xhtml", defaultInsertedChapterPath(book, 26))
        // 有模板时,废文章节也按书的章节命名模板生成路径(从而 id 与上传文件的风格一致)
        val template = EpubChapterFileNameTemplate("OEBPS/Text/", "Chapter", "", "xhtml", 4, 1)
        // book 已有 2 个正文章节 + volume 不计入, number = 2+1+0 = 3
        assertEquals("OEBPS/Text/Chapter0003.xhtml", defaultInsertedChapterPath(book, 0, template))
        assertEquals("OEBPS/Text/Chapter0029.xhtml", defaultInsertedChapterPath(book, 26, template))
        assertEquals(0, targetTocLevelForInsert(book, insertPosition = 1))
        assertEquals(1, targetTocLevelForInsert(book, insertPosition = 2))
    }

    @Test
    fun copyImportedEpubAssetsSkipsBookStructureHtmlCssAndCoverProperties() {
        val target = sampleBook(listOf(epubChapter("t1", "OEBPS/Text/Chapter0001.xhtml", "第1章")))
        val source = sampleBook(
            listOf(
                epubChapter("s1", "OEBPS/Text/source1.xhtml", "源1"),
                epubChapter("s2", "OEBPS/Text/source2.xhtml", "源2")
            ),
            extraManifest = listOf(
                manifest("img", "OEBPS/Images/pic.jpg", "image/jpeg"),
                manifest("cover", "OEBPS/Images/cover.jpg", "image/jpeg", properties = "cover-image nav"),
                manifest("css", "OEBPS/Styles/source.css", "text/css"),
                manifest("font", "OEBPS/Fonts/font.otf", "font/otf")
            ),
            extraEntries = linkedMapOf(
                "mimetype" to byteArrayOf(0),
                "META-INF/container.xml" to byteArrayOf(1),
                "OEBPS/Images/pic.jpg" to byteArrayOf(2),
                "OEBPS/Images/cover.jpg" to byteArrayOf(3),
                "OEBPS/Styles/source.css" to byteArrayOf(4),
                "OEBPS/Fonts/font.otf" to byteArrayOf(5)
            )
        )

        copyImportedEpubAssets(
            target = target,
            source = source,
            selectedChapterPaths = setOf("OEBPS/Text/source1.xhtml")
        )

        assertTrue(target.entries.containsKey("OEBPS/Images/pic.jpg"))
        assertTrue(target.entries.containsKey("OEBPS/Images/cover.jpg"))
        assertTrue(target.entries.containsKey("OEBPS/Fonts/font.otf"))
        assertFalse(target.entries.containsKey("OEBPS/Text/source1.xhtml"))
        assertFalse(target.entries.containsKey("OEBPS/Styles/source.css"))
        assertFalse(target.entries.containsKey("META-INF/container.xml"))
        assertEquals(
            "nav",
            target.manifest.values.single { it.path == "OEBPS/Images/cover.jpg" }.properties
        )
    }

    @Test
    fun epubCssPathsAndDefaultStylesheetPreferNearbyChapterLinks() {
        val first = epubChapter(
            id = "c1",
            path = "OEBPS/Text/Chapter0001.xhtml",
            title = "第1章",
            html = """<html><head><link rel="stylesheet" href="../Styles/main.css?x=1"/></head><body>正文</body></html>"""
        )
        val second = epubChapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章")
        val book = sampleBook(
            listOf(first, second),
            extraManifest = listOf(
                manifest("main_css", "OEBPS/Styles/main.css", "text/css"),
                manifest("global_css", "OEBPS/Styles/global.css", "text/css")
            ),
            extraEntries = linkedMapOf("OEBPS/Styles/global.css" to byteArrayOf(1))
        )
        val cssPaths = epubCssPaths(book)

        assertEquals(listOf("OEBPS/Styles/main.css", "OEBPS/Styles/global.css"), cssPaths)
        assertEquals("OEBPS/Styles/main.css", defaultEpubStylesheetPath(book, cssPaths, insertPosition = 1))
        assertEquals("OEBPS/Styles/main.css", defaultEpubStylesheetPath(book, cssPaths, insertPosition = 0))
    }

    @Test
    fun rewriteMovedImportedChapterReferencesRebasesAssetsAndUsesTargetCss() {
        val chapter = epubChapter(
            id = "new1",
            path = "OEBPS/Text/Chapter0002.xhtml",
            title = "插入章",
            html = """
                <html><head><link rel="stylesheet" href="../Styles/source.css"/></head>
                <body><img src="../Images/pic.jpg?x=1"/><a href="#local">本页</a><a href="https://example.com">外链</a></body></html>
            """.trimIndent()
        )
        val book = sampleBook(
            listOf(chapter),
            extraManifest = listOf(
                manifest("img", "OEBPS/Images/pic.jpg", "image/jpeg"),
                manifest("css", "OEBPS/Styles/main.css", "text/css")
            ),
            extraEntries = linkedMapOf(
                "OEBPS/Images/pic.jpg" to byteArrayOf(1),
                "OEBPS/Styles/main.css" to byteArrayOf(2)
            )
        )

        rewriteMovedImportedChapterReferences(
            book = book,
            inserted = listOf(chapter),
            importedSourcePathsById = mapOf("new1" to "OEBPS/Text/source1.xhtml"),
            targetCssPaths = listOf("OEBPS/Styles/main.css"),
            defaultTargetCssPath = "OEBPS/Styles/main.css"
        )

        assertTrue(chapter.html.contains("href=\"../Styles/main.css\""))
        assertTrue(chapter.html.contains("src=\"../Images/pic.jpg?x=1\""))
        assertTrue(chapter.html.contains("href=\"#local\""))
        assertTrue(chapter.html.contains("href=\"https://example.com\""))
        assertEquals(chapter.html.toByteArray(Charsets.UTF_8).toList(), book.entries[chapter.path]?.toList())
    }

    @Test
    fun rewriteMovedImportedChapterReferencesKeepsNonLocalAndMissingReferences() {
        val chapter = epubChapter(
            id = "new1",
            path = "OEBPS/Text/Chapter0002.xhtml",
            title = "插入章",
            html = """
                <html><body>
                <img src="/OEBPS/Images/pic.jpg"/>
                <img src="//cdn.example.com/pic.jpg"/>
                <img src="data:image/png;base64,aaa"/>
                <img src="../Images/missing.jpg"/>
                <a href="mailto:test@example.com">mail</a>
                </body></html>
            """.trimIndent()
        )
        val book = sampleBook(
            listOf(chapter),
            extraManifest = listOf(
                manifest("img", "OEBPS/Images/pic.jpg", "image/jpeg")
            ),
            extraEntries = linkedMapOf(
                "OEBPS/Images/pic.jpg" to byteArrayOf(1)
            )
        )

        rewriteMovedImportedChapterReferences(
            book = book,
            inserted = listOf(chapter),
            importedSourcePathsById = mapOf("new1" to "OEBPS/Text/source1.xhtml"),
            targetCssPaths = emptyList(),
            defaultTargetCssPath = null
        )

        assertTrue(chapter.html.contains("src=\"/OEBPS/Images/pic.jpg\""))
        assertTrue(chapter.html.contains("src=\"//cdn.example.com/pic.jpg\""))
        assertTrue(chapter.html.contains("src=\"data:image/png;base64,aaa\""))
        assertTrue(chapter.html.contains("src=\"../Images/missing.jpg\""))
        assertTrue(chapter.html.contains("href=\"mailto:test@example.com\""))
        assertEquals(chapter.html.toByteArray(Charsets.UTF_8).toList(), book.entries[chapter.path]?.toList())
    }

    @Test
    fun txtInsertPositionAndTextHandleEmptyDocuments() {
        val document = TxtDocument(
            originalName = "empty.txt",
            text = "",
            encoding = "UTF-8",
            chapters = emptyList()
        )

        val result = insertChaptersIntoTxtDocumentText(
            document = document,
            selected = listOf(insertable("第9章 外部", "正文")),
            positionMode = INSERT_CHAPTER_POSITION_END,
            targetChapterIndex = null,
            currentChapterIndex = 0
        )

        assertEquals(0, resolveTxtInsertChapterPosition(document, INSERT_CHAPTER_POSITION_START, null, 0))
        assertEquals(1, resolveTxtInsertChapterPosition(document, INSERT_CHAPTER_POSITION_END, null, 0))
        assertEquals(0, txtChapterInsertOffset(document, insertPosition = 0))
        assertEquals(0, txtChapterInsertOffset(document, insertPosition = 1))
        assertEquals(1, result?.insertPosition)
        assertEquals(1, result?.insertedCount)
        assertEquals("第1章\r\n正文", result?.text)
    }

    private fun sampleBook(
        chapters: List<EpubChapter>,
        extraManifest: List<ManifestItem> = emptyList(),
        extraEntries: LinkedHashMap<String, ByteArray> = linkedMapOf()
    ): EpubBook {
        val entries = linkedMapOf<String, ByteArray>()
        val manifest = linkedMapOf<String, ManifestItem>()
        chapters.forEach { chapter ->
            entries[chapter.path] = chapter.html.toByteArray(Charsets.UTF_8)
            manifest[chapter.id] = ManifestItem(chapter.id, chapter.href, "application/xhtml+xml", chapter.path)
        }
        extraEntries.forEach { (path, bytes) -> entries[path] = bytes }
        extraManifest.forEach { item -> manifest[item.id] = item }
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

    private fun epubChapter(
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

    private fun manifest(
        id: String,
        path: String,
        mediaType: String,
        properties: String = ""
    ): ManifestItem {
        return ManifestItem(
            id = id,
            href = path.removePrefix("OEBPS/"),
            mediaType = mediaType,
            path = path,
            properties = properties
        )
    }

    private fun insertable(title: String, text: String): InsertableChapter {
        return InsertableChapter(
            sourceIndex = 0,
            title = title,
            fileName = "source.xhtml",
            sourcePath = "source.xhtml",
            html = null,
            text = text,
            wordCount = text.length,
            tocLevel = 0,
            isVolume = false
        )
    }
}
