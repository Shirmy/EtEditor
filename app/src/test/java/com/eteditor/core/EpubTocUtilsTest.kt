package com.eteditor.core

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubTocUtilsTest {
    @Test
    fun findNcxPathPrefersSpineTocAndFallsBackToManifestNcxItems() {
        val opf = """
            <package xmlns="http://www.idpf.org/2007/opf">
                <spine toc="toc-id" />
            </package>
        """.trimIndent()
        val spine = parseXml(opf.bytes()).elements("spine").single()
        val manifest = mapOf(
            "toc-id" to manifest("toc-id", "OEBPS/toc.ncx", "application/x-dtbncx+xml"),
            "fallback" to manifest("fallback", "OEBPS/alt.ncx", "application/octet-stream")
        )

        assertEquals("OEBPS/toc.ncx", findNcxPath(spine, manifest))
        assertEquals("OEBPS/alt.ncx", findNcxPath(null, manifest - "toc-id"))
    }

    @Test
    fun findNcxPathAcceptsLegacyNxcManifestExtensions() {
        val manifest = mapOf(
            "legacy-path" to manifest("legacy-path", "OEBPS/legacy.nxc", "application/octet-stream"),
            "legacy-href" to manifest("legacy-href", "OEBPS/toc.xhtml", "application/octet-stream").copy(href = "toc.nxc")
        )

        assertEquals("OEBPS/legacy.nxc", findNcxPath(null, manifest))
    }

    @Test
    fun parseNcxEntriesReadsNestedLevelsAndDecodedPaths() {
        val ncx = """
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/">
                <navMap>
                    <navPoint>
                        <navLabel><text>第一章</text></navLabel>
                        <content src="Text/Chapter%2001.xhtml#title" />
                        <navPoint>
                            <navLabel><text>第二章</text></navLabel>
                            <content src="Text/Chapter02.xhtml" />
                        </navPoint>
                    </navPoint>
                </navMap>
            </ncx>
        """.trimIndent()

        val entries = parseNcxEntries(ncx.bytes(), "OEBPS/toc.ncx")

        assertEquals(TocEntry("第一章", 0), entries["OEBPS/Text/Chapter%2001.xhtml"])
        assertEquals(TocEntry("第一章", 0), entries["OEBPS/Text/Chapter 01.xhtml"])
        assertEquals(TocEntry("第二章", 1), entries["OEBPS/Text/Chapter02.xhtml"])
    }

    @Test
    fun parseNcxEntriesFallsBackToNavPointsOutsideNavMap() {
        val ncx = """
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/">
                <pageList>
                    <navPoint>
                        <navLabel><text>页内目录</text></navLabel>
                        <content src="Text/Page.xhtml#top" />
                    </navPoint>
                </pageList>
            </ncx>
        """.trimIndent()

        val entries = parseNcxEntries(ncx.bytes(), "OEBPS/toc.ncx")

        assertEquals(mapOf("OEBPS/Text/Page.xhtml" to TocEntry("页内目录", 0)), entries)
    }

    @Test
    fun parseNavEntriesUsesNavDocumentAndNestedOrderedLists() {
        val nav = """
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                <body>
                    <nav epub:type="toc">
                        <ol>
                            <li>
                                <a href="Text/Chapter%2001.xhtml#top">第一章</a>
                                <ol>
                                    <li><p><a href="Text/Chapter02.xhtml">第二章</a></p></li>
                                </ol>
                            </li>
                        </ol>
                    </nav>
                </body>
            </html>
        """.trimIndent()
        val manifest = mapOf(
            "nav" to manifest("nav", "OEBPS/nav.xhtml", "application/xhtml+xml", properties = "nav")
        )

        val entries = parseNavEntries(
            entries = mapOf("OEBPS/nav.xhtml" to nav.bytes()),
            manifest = manifest
        )

        assertEquals(TocEntry("第一章", 0), entries["OEBPS/Text/Chapter 01.xhtml"])
        assertEquals(TocEntry("第二章", 1), entries["OEBPS/Text/Chapter02.xhtml"])
        assertTrue(manifest.getValue("nav").isNavDocument())
    }

    @Test
    fun parseNavEntriesReturnsEmptyWithoutNavManifestOrEntry() {
        val nav = "<html><body><nav><ol><li><a href=\"Text/A.xhtml\">A</a></li></ol></nav></body></html>"

        assertEquals(emptyMap<String, TocEntry>(), parseNavEntries(mapOf("OEBPS/nav.xhtml" to nav.bytes()), emptyMap()))
        assertEquals(
            emptyMap<String, TocEntry>(),
            parseNavEntries(
                entries = emptyMap(),
                manifest = mapOf("nav" to manifest("nav", "OEBPS/nav.xhtml", "application/xhtml+xml", properties = "nav"))
            )
        )
    }

    @Test
    fun parseNavEntriesFallsBackToFirstNavWhenNoTocTypeExists() {
        val nav = """
            <html xmlns="http://www.w3.org/1999/xhtml">
                <body>
                    <nav type="landmarks">
                        <ol>
                            <li><p><a href="Text/Chapter%2001.xhtml#spot">第一章</a></p></li>
                        </ol>
                    </nav>
                </body>
            </html>
        """.trimIndent()
        val manifest = mapOf(
            "nav" to manifest("nav", "OEBPS/nav.xhtml", "application/xhtml+xml", properties = "nav")
        )

        val entries = parseNavEntries(
            entries = mapOf("OEBPS/nav.xhtml" to nav.bytes()),
            manifest = manifest
        )

        assertEquals(TocEntry("第一章", 0), entries["OEBPS/Text/Chapter%2001.xhtml"])
        assertEquals(TocEntry("第一章", 0), entries["OEBPS/Text/Chapter 01.xhtml"])
    }

    @Test
    fun parseNavEntriesSelectsTocWhenTypeHasMultipleTokens() {
        val nav = """
            <html xmlns="http://www.w3.org/1999/xhtml">
                <body>
                    <nav type="landmarks">
                        <ol><li><a href="Text/Landmark.xhtml">地标</a></li></ol>
                    </nav>
                    <nav type="page-list toc">
                        <ol><li><a href="Text/Chapter.xhtml">正文目录</a></li></ol>
                    </nav>
                </body>
            </html>
        """.trimIndent()
        val manifest = mapOf(
            "nav" to manifest("nav", "OEBPS/nav.xhtml", "application/xhtml+xml", properties = "nav")
        )

        val entries = parseNavEntries(
            entries = mapOf("OEBPS/nav.xhtml" to nav.bytes()),
            manifest = manifest
        )

        assertEquals(mapOf("OEBPS/Text/Chapter.xhtml" to TocEntry("正文目录", 0)), entries)
    }

    @Test
    fun updateNcxTitlesRebuildsHierarchyFromInternalTocLevelsAndHidesSection0001() {
        val ncx = """
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/">
                <head><meta name="dtb:depth" content="1" /></head>
                <docAuthor><text>Old Author</text></docAuthor>
                <navMap>
                    <navPoint>
                        <navLabel><text>旧标题</text></navLabel>
                        <content src="Text/old.xhtml" />
                    </navPoint>
                </navMap>
            </ncx>
        """.trimIndent()
        val chapters = listOf(
            chapter("cover", "OEBPS/Text/Section0001.xhtml", "封面", "<html><body><h1>封面</h1></body></html>"),
            chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "旧第一章", "<html><body><h1>HTML<br/>第一章</h1></body></html>"),
            chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "旧第二章", "<html><body><h2>HTML 第二章</h2></body></html>")
                .copy(tocLevel = 1)
        )

        val updated = updateNcxTitles(
            bytes = ncx.bytes(),
            ncxPath = "OEBPS/toc.ncx",
            chapters = chapters,
            options = EpubExportOptions(
                hideSection0001FromNcx = true
            )
        )
        val text = updated.text()
        val entries = parseNcxEntries(updated, "OEBPS/toc.ncx")

        assertFalse(text.contains("docAuthor"))
        assertTrue(text.contains("""name="dtb:depth""""))
        assertTrue(text.contains("""content="2""""))
        assertFalse(entries.containsKey("OEBPS/Text/Section0001.xhtml"))
        assertEquals(TocEntry("HTML 第一章", 0), entries["OEBPS/Text/Chapter0001.xhtml"])
        assertEquals(TocEntry("HTML 第二章", 1), entries["OEBPS/Text/Chapter0002.xhtml"])
    }

    @Test
    fun updateNcxTitlesIgnoresHtmlHeadingLevelsWhenInternalTocLevelsDiffer() {
        val ncx = """
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/">
                <navMap>
                    <navPoint>
                        <navLabel><text>旧标题</text></navLabel>
                        <content src="Text/old.xhtml" />
                    </navPoint>
                </navMap>
            </ncx>
        """.trimIndent()
        val chapters = listOf(
            chapter(
                "c1",
                "OEBPS/Text/Chapter0001.xhtml",
                "旧内存标题",
                "<html><body><h2>HTML<br/>第一章</h2></body></html>"
            ),
            chapter(
                "c2",
                "OEBPS/Text/Chapter0002.xhtml",
                "旧第二章",
                "<html><body><h1>HTML 第二章</h1></body></html>"
            ).copy(tocLevel = 1)
        )

        val updated = updateNcxTitles(
            bytes = ncx.bytes(),
            ncxPath = "OEBPS/toc.ncx",
            chapters = chapters,
            options = EpubExportOptions(
                hideSection0001FromNcx = false
            )
        )
        val entries = parseNcxEntries(updated, "OEBPS/toc.ncx")

        assertEquals(TocEntry("HTML 第一章", 0), entries["OEBPS/Text/Chapter0001.xhtml"])
        assertEquals(TocEntry("HTML 第二章", 1), entries["OEBPS/Text/Chapter0002.xhtml"])
    }

    @Test
    fun updateNcxTitlesRemovesDocAuthorEvenWhenNavMapIsMissing() {
        val ncx = """
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/">
                <head><meta name="dtb:uid" content="book" /></head>
                <docAuthor><text>Old Author</text></docAuthor>
            </ncx>
        """.trimIndent()

        val updated = updateNcxTitles(
            bytes = ncx.bytes(),
            ncxPath = "OEBPS/toc.ncx",
            chapters = emptyList(),
            options = EpubExportOptions()
        ).text()

        assertFalse(updated.contains("docAuthor"))
        assertTrue(updated.contains("dtb:uid"))
    }

    @Test
    fun syncEpubDirectoryTitleUsesHtmlTitleAndCoverFallback() {
        val chapter = chapter(
            "c1",
            "OEBPS/Text/Chapter0001.xhtml",
            "旧标题",
            "<html><body><h1>第一章<br/>副标题</h1></body></html>"
        )
        val cover = chapter(
            "cover",
            "OEBPS/Text/cover.xhtml",
            "旧封面标题",
            "<html><body><img src=\"../Images/cover.jpg\"/></body></html>"
        )

        assertTrue(chapter.syncEpubDirectoryTitleFromHtml())
        assertTrue(cover.syncEpubDirectoryTitleFromHtml())
        assertEquals("第一章 副标题", chapter.title)
        assertEquals("封面", cover.title)
    }

    @Test
    fun updateNavLinksRebuildsTocAndRewritesAliasLinks() {
        val nav = """
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                <body>
                    <nav epub:type="toc">
                        <ol><li><a href="Text/Old.xhtml">旧标题</a></li></ol>
                    </nav>
                    <nav epub:type="landmarks">
                        <ol><li><a href="Text/Alias.xhtml#spot">旧链接</a></li></ol>
                    </nav>
                </body>
            </html>
        """.trimIndent()
        val chapter = chapter("c1", "OEBPS/Text/New.xhtml", "新标题", "<html><body>正文</body></html>")
            .copy(originalPath = "OEBPS/Text/Old.xhtml", pathAliases = mutableSetOf("OEBPS/Text/Alias.xhtml"))

        val updated = updateNavLinks(
            bytes = nav.bytes(),
            navPath = "OEBPS/nav.xhtml",
            chapters = listOf(chapter),
            options = EpubExportOptions()
        )
        val text = updated.text()
        val entries = parseNavEntries(
            entries = mapOf("OEBPS/nav.xhtml" to updated),
            manifest = mapOf("nav" to manifest("nav", "OEBPS/nav.xhtml", "application/xhtml+xml", properties = "nav"))
        )

        assertTrue(text.contains("New"))
        assertTrue(text.contains("href=\"Text/New.xhtml#spot\""))
        assertEquals(TocEntry("New", 0), entries["OEBPS/Text/New.xhtml"])
    }

    @Test
    fun updateNavLinksRewritesAliasLinksEvenWithoutTocNav() {
        val nav = """
            <html xmlns="http://www.w3.org/1999/xhtml">
                <body>
                    <nav type="landmarks">
                        <ol><li><a href="Text/Alias.xhtml#spot">旧链接</a></li></ol>
                    </nav>
                </body>
            </html>
        """.trimIndent()
        val chapter = chapter("c1", "OEBPS/Text/New.xhtml", "新标题")
            .copy(originalPath = "OEBPS/Text/Old.xhtml", pathAliases = mutableSetOf("OEBPS/Text/Alias.xhtml"))

        val updated = updateNavLinks(
            bytes = nav.bytes(),
            navPath = "OEBPS/nav.xhtml",
            chapters = listOf(chapter),
            options = EpubExportOptions()
        )
        val text = updated.text()

        assertTrue(text.contains("href=\"Text/New.xhtml#spot\""))
        assertFalse(text.contains("Text/Alias.xhtml"))
    }

    @Test
    fun sectionAndNavDocumentDetectionUsePathPropertiesAndFileNames() {
        assertTrue(chapter("c", "OEBPS/Text/Section0001.html", "封面").isSection0001Chapter())
        assertFalse(chapter("c", "OEBPS/Text/Section0002.xhtml", "简介").isSection0001Chapter())
        assertTrue(manifest("nav", "OEBPS/nav.html", "application/xhtml+xml").isNavDocument())
        assertTrue(manifest("x", "OEBPS/toc.xhtml", "application/xhtml+xml", properties = "cover nav").isNavDocument())
        assertFalse(manifest("x", "OEBPS/toc.xhtml", "application/xhtml+xml").isNavDocument())
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

    private fun chapter(
        id: String,
        path: String,
        title: String,
        html: String = "<html><body><h1>$title</h1></body></html>"
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

    private fun String.bytes(): ByteArray = toByteArray(StandardCharsets.UTF_8)

    private fun ByteArray.text(): String = String(this, StandardCharsets.UTF_8)
}
