package com.eteditor

import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.ManifestItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchInfoTargetUtilsTest {
    @Test
    fun fetchInfoProgressHelpersMapMessagesAndSourceProgress() {
        assertEquals(0.02f, fetchInfoSourceProgress(0, 3, 0f), 0.0001f)
        assertEquals(0.375f, fetchInfoSourceProgress(1, 4, 0.5f), 0.0001f)
        assertEquals(0.96f, fetchInfoSourceProgress(99, 1, 1f), 0.0001f)
        assertEquals(0.9f, fetchInfoProgressPhase("正在抓取正文 1/2"), 0.0001f)
        assertEquals(0.9f, fetchInfoProgressPhase("正在读取正文"), 0.0001f)
        assertEquals(0.78f, fetchInfoProgressPhase("正在抓取目录"), 0.0001f)
        assertEquals(0.16f, fetchInfoProgressPhase("搜索中"), 0.0001f)
        assertEquals(0.42f, fetchInfoProgressPhase("其他"), 0.0001f)
    }

    @Test
    fun fetchInfoProgressHelpersMapRemainingNamedPhasesAndClampBounds() {
        assertEquals(0.02f, fetchInfoSourceProgress(-4, 3, -1f), 0.0001f)
        assertEquals(0.96f, fetchInfoSourceProgress(2, 2, 9f), 0.0001f)
        assertEquals(0.74f, fetchInfoProgressPhase("正在抓取简介"), 0.0001f)
        assertEquals(0.74f, fetchInfoProgressPhase("正在抓取封面"), 0.0001f)
        assertEquals(0.55f, fetchInfoProgressPhase("正在读取详情页"), 0.0001f)
        assertEquals(0.42f, fetchInfoProgressPhase("正在确认作者"), 0.0001f)
        assertEquals(0.28f, fetchInfoProgressPhase("搜索镜像 1/3"), 0.0001f)
        assertEquals(0.28f, fetchInfoProgressPhase("正在尝试搜索源 2/3"), 0.0001f)
    }

    @Test
    fun fetchInfoAutoSourcesAndLabelsFollowContentType() {
        assertEquals(
            listOf(FETCH_INFO_SOURCE_JJWXC, FETCH_INFO_SOURCE_GONGZICP, FETCH_INFO_SOURCE_SOSAD),
            fetchInfoAutoSources(FETCH_INFO_CONTENT_INTRO)
        )
        assertEquals(
            listOf(FETCH_INFO_SOURCE_JJWXC, FETCH_INFO_SOURCE_SOSAD),
            fetchInfoAutoSources(FETCH_INFO_CONTENT_CATALOG)
        )
        assertEquals(
            listOf(FETCH_INFO_SOURCE_JJWXC, FETCH_INFO_SOURCE_GONGZICP, FETCH_INFO_SOURCE_SOSAD),
            fetchInfoAutoSources(FETCH_INFO_CONTENT_COVER)
        )
        assertEquals(listOf(FETCH_INFO_SOURCE_JJWXC), fetchInfoAutoSources("unknown"))
        assertEquals("封面", fetchInfoContentLabel(FETCH_INFO_CONTENT_COVER))
        assertEquals("简介", fetchInfoContentLabel(FETCH_INFO_CONTENT_INTRO))
        assertEquals("目录", fetchInfoContentLabel(FETCH_INFO_CONTENT_CATALOG))
        assertEquals("内容", fetchInfoContentLabel("unknown"))
    }

    @Test
    fun fetchInfoCatalogTargetsSkipVolumeAndCoverChapters() {
        val body1 = chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章")
        val volume = chapter("v1", "OEBPS/Text/Vol01.xhtml", "第一卷")
        val cover = chapter("cover", "OEBPS/Text/Section0001.xhtml", "封面")
        val body2 = chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章")
        val book = sampleBook(listOf(body1, volume, cover, body2))

        val targets = fetchInfoCatalogTargetChapters(book)

        assertEquals(listOf(0, 3), targets.map { it.first })
        assertEquals(listOf(body1, body2), targets.map { it.second })
        assertEquals(3, fetchInfoVolumeInsertPosition(book, targets.map { it.second }, targetCursor = 1, fallbackChapterIndex = 9))
        assertEquals(4, fetchInfoVolumeInsertPosition(book, targets.map { it.second }, targetCursor = 2, fallbackChapterIndex = 9))
        assertSame(volume, findFetchInfoAdjacentVolume(book, insertPosition = 1, usedVolumePaths = emptySet()))
        assertNull(findFetchInfoAdjacentVolume(book, insertPosition = 1, usedVolumePaths = setOf(volume.path)))
    }

    @Test
    fun fetchInfoCatalogTargetsAlsoSkipSection0002CoverChapter() {
        val body1 = chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章")
        val section0002 = chapter("cover2", "OEBPS/Text/Section0002.html", "封面附页")
        val body2 = chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章")
        val book = sampleBook(listOf(body1, section0002, body2))

        val targets = fetchInfoCatalogTargetChapters(book)

        assertEquals(listOf(0, 2), targets.map { it.first })
        assertEquals(listOf(body1, body2), targets.map { it.second })
    }

    @Test
    fun fetchInfoCatalogTargetsSkipCoverNameAndVolumeAliasChapters() {
        val body = chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章")
        val cover = chapter("cover", "OEBPS/Text/COVER.HTML", "封面")
        val renamedVolume = chapter("v1", "OEBPS/Text/Chapter0002.xhtml", "第一卷")
        renamedVolume.pathAliases += "OEBPS/Text/VolF1.xhtml"
        val book = sampleBook(listOf(body, cover, renamedVolume))

        val targets = fetchInfoCatalogTargetChapters(book)

        assertEquals(listOf(0), targets.map { it.first })
        assertEquals(listOf(body), targets.map { it.second })
    }

    @Test
    fun findFetchInfoAdjacentVolumeSkipsUsedPreviousVolumeAndCanUseNextVolume() {
        val previousVolume = chapter("v1", "OEBPS/Text/Vol01.xhtml", "第一卷")
        val body = chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章")
        val nextVolume = chapter("v2", "OEBPS/Text/Vol02.xhtml", "第二卷")
        val book = sampleBook(listOf(previousVolume, body, nextVolume))

        assertSame(
            previousVolume,
            findFetchInfoAdjacentVolume(book, insertPosition = 1, usedVolumePaths = emptySet())
        )
        assertSame(
            nextVolume,
            findFetchInfoAdjacentVolume(book, insertPosition = 2, usedVolumePaths = setOf(previousVolume.path))
        )
        assertNull(
            findFetchInfoAdjacentVolume(
                book,
                insertPosition = 2,
                usedVolumePaths = setOf(previousVolume.path, nextVolume.path)
            )
        )
    }

    @Test
    fun fetchInfoVolumeInsertPositionFallsBackWhenTargetsAreMissing() {
        val volume = chapter("v1", "OEBPS/Text/Vol01.xhtml", "第一卷")
        val body = chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章")
        val book = sampleBook(listOf(volume, body))

        assertEquals(0, fetchInfoVolumeInsertPosition(book, emptyList(), targetCursor = 0, fallbackChapterIndex = -5))
        assertEquals(2, fetchInfoVolumeInsertPosition(book, emptyList(), targetCursor = 0, fallbackChapterIndex = 99))
        assertEquals(2, fetchInfoVolumeInsertPosition(book, listOf(body), targetCursor = 9, fallbackChapterIndex = 0))
        assertSame(volume, findFetchInfoAdjacentVolume(book, insertPosition = 0, usedVolumePaths = emptySet()))
    }

    @Test
    fun fetchInfoVolumeInsertPositionUsesFallbackWhenTargetObjectsAreStale() {
        val body = chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章")
        val stale = chapter("old", "OEBPS/Text/Old.xhtml", "旧章")
        val book = sampleBook(listOf(body))

        assertEquals(0, fetchInfoVolumeInsertPosition(book, listOf(stale), targetCursor = 0, fallbackChapterIndex = -1))
        assertEquals(1, fetchInfoVolumeInsertPosition(book, listOf(stale), targetCursor = 1, fallbackChapterIndex = 9))
    }

    @Test
    fun fetchInfoVolumeInsertPositionUsesPreviousTargetWhenCurrentTargetIsStale() {
        val body1 = chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章")
        val body2 = chapter("c2", "OEBPS/Text/Chapter0002.xhtml", "第2章")
        val stale = chapter("old", "OEBPS/Text/Old.xhtml", "旧章")
        val book = sampleBook(listOf(body1, body2))

        val position = fetchInfoVolumeInsertPosition(
            book = book,
            targets = listOf(body1, stale),
            targetCursor = 1,
            fallbackChapterIndex = 99
        )

        assertEquals(1, position)
    }

    @Test
    fun fetchInfoIntroTargetsResolveExistingPathsAndDefaults() {
        val book = sampleBook(
            listOf(chapter("c1", "OEBPS/Text/Chapter0001.xhtml", "第1章")),
            extraEntries = listOf("OEBPS/Text/Section0002.html", "OEBPS/Text/custom.htm")
        )

        assertEquals("OEBPS/Text/Section0002.html", defaultFetchInfoIntroTarget(book))
        assertEquals("OEBPS/Text/Section0002.xhtml", resolveFetchInfoIntroTarget("OEBPS/Text/Section0002.xhtml", book))
        assertEquals("OEBPS/Text/Section0002.html", existingEpubPath("oebps/text/section0002.HTML", book))
        assertEquals("OEBPS/Text/new.xhtml", resolveFetchInfoIntroTarget("OEBPS/./Text/new.xhtml", book))
        assertTrue(isDefaultFetchInfoIntroTargetOverride(""))
        assertTrue(isDefaultFetchInfoIntroTargetOverride("OEBPS/Text/Section0002.xhtml"))
        assertFalse(isDefaultFetchInfoIntroTargetOverride("OEBPS/Text/Section0002.html"))
        assertTrue(isSection0001Path("OEBPS/Text/Section0001.xhtml#toc"))
        assertTrue(isSection0001Path("Section0001.html"))
        assertEquals(
            listOf(
                "OEBPS/Text/Chapter0001.xhtml",
                "OEBPS/Text/Section0002.xhtml",
                "OEBPS/Text/Section0002.html",
                "OEBPS/Text/custom.htm"
            ),
            buildFetchInfoIntroTargetOptions(book, "OEBPS/Text/Chapter0001.xhtml").map { it.first }
        )
        assertEquals(
            listOf("Chapter0001.xhtml", "Section0002.xhtml", "Section0002.html", "custom.htm"),
            buildFetchInfoIntroTargetOptions(book, "OEBPS/Text/Chapter0001.xhtml").map { it.second }
        )
        assertTrue(epubExportOptions(hideSection0001FromNcx = false).ncxFollowHtmlFileTitle)
        assertEquals(false, epubExportOptions(hideSection0001FromNcx = false).hideSection0001FromNcx)
    }

    @Test
    fun fetchInfoIntroDefaultPrefersExistingXhtmlBeforeHtmlFallback() {
        val book = sampleBook(
            chapters = emptyList(),
            extraEntries = listOf("OEBPS/Text/SECTION0002.HTML", DEFAULT_FETCH_INFO_INTRO_TARGET)
        )

        assertEquals(DEFAULT_FETCH_INFO_INTRO_TARGET, defaultFetchInfoIntroTarget(book))
        assertEquals(DEFAULT_FETCH_INFO_INTRO_TARGET, resolveFetchInfoIntroTarget("", book))
    }

    @Test
    fun fetchInfoIntroTargetsUseDefaultsWithoutBookAndSkipNonHtmlOptions() {
        assertEquals(DEFAULT_FETCH_INFO_INTRO_TARGET, defaultFetchInfoIntroTarget(null))
        assertEquals(DEFAULT_FETCH_INFO_INTRO_TARGET, resolveFetchInfoIntroTarget("", null))
        assertEquals("OEBPS/Text/intro.txt", resolveFetchInfoIntroTarget("OEBPS/Text/intro.txt", null))
        assertEquals(
            listOf(
                "OEBPS/Text/custom.xhtml",
                DEFAULT_FETCH_INFO_INTRO_TARGET,
                "OEBPS/Text/Section0002.html"
            ),
            buildFetchInfoIntroTargetOptions(null, "OEBPS/Text/custom.xhtml").map { it.first }
        )
        assertEquals(
            listOf(DEFAULT_FETCH_INFO_INTRO_TARGET, "OEBPS/Text/Section0002.html"),
            buildFetchInfoIntroTargetOptions(null, "OEBPS/Text/intro.txt").map { it.first }
        )
    }

    @Test
    fun fetchInfoIntroTargetsDeduplicateCaseInsensitivePathsAndKeepExistingPathCasing() {
        val chapter = chapter("intro", "OEBPS/Text/Intro.xhtml", "简介")
        val book = sampleBook(
            listOf(chapter),
            extraEntries = listOf("OEBPS/Text/INTRO.XHTML", "OEBPS/Text/extra.css")
        )

        val options = buildFetchInfoIntroTargetOptions(book, "oebps/text/intro.xhtml")

        assertEquals(
            listOf("OEBPS/Text/Intro.xhtml", DEFAULT_FETCH_INFO_INTRO_TARGET, "OEBPS/Text/Section0002.html"),
            options.map { it.first }
        )
        assertEquals(
            listOf("Intro.xhtml", "Section0002.xhtml", "Section0002.html"),
            options.map { it.second }
        )
    }

    @Test
    fun fetchInfoIntroTargetsIncludeManifestOnlyHtmlEntries() {
        val book = sampleBook(emptyList())
        book.manifest["intro"] = ManifestItem(
            id = "intro",
            href = "Text/ManifestOnly.xhtml",
            mediaType = "application/xhtml+xml",
            path = "OEBPS/Text/ManifestOnly.xhtml"
        )

        val options = buildFetchInfoIntroTargetOptions(book, currentPath = "")

        assertEquals(
            listOf(
                DEFAULT_FETCH_INFO_INTRO_TARGET,
                "OEBPS/Text/Section0002.html",
                "OEBPS/Text/ManifestOnly.xhtml"
            ),
            options.map { it.first }
        )
        assertEquals("OEBPS/Text/ManifestOnly.xhtml", existingEpubPath("oebps/text/manifestonly.xhtml", book))
    }

    private fun sampleBook(
        chapters: List<EpubChapter>,
        extraEntries: List<String> = emptyList()
    ): EpubBook {
        val entries = linkedMapOf<String, ByteArray>()
        val manifest = mutableMapOf<String, ManifestItem>()
        chapters.forEach { chapter ->
            entries[chapter.path] = chapter.html.toByteArray(Charsets.UTF_8)
            manifest[chapter.id] = ManifestItem(chapter.id, chapter.href, "application/xhtml+xml", chapter.path)
        }
        extraEntries.forEachIndexed { index, path ->
            entries[path] = ByteArray(0)
            manifest["extra$index"] = ManifestItem("extra$index", path.removePrefix("OEBPS/"), "application/xhtml+xml", path)
        }
        return EpubBook(
            originalName = "book.epub",
            metadataTitle = "Book",
            metadataAuthor = "",
            metadataItems = mutableListOf(),
            entries = entries,
            opfPath = "OEBPS/content.opf",
            tocPath = "OEBPS/toc.ncx",
            manifest = manifest,
            spineIds = chapters.map { it.id }.toMutableList(),
            chapters = chapters.toMutableList()
        )
    }

    private fun chapter(id: String, path: String, title: String): EpubChapter {
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
