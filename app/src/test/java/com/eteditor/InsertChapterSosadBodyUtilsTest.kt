package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsertChapterSosadBodyUtilsTest {
    @Test
    fun insertChapterSosadFetchParametersForceBodyCatalogMode() {
        val parameters = insertChapterSosadFetchParameters(
            parameters = sosadParameters(
                query = "  书名  ",
                cookie = "session=secret",
                start = 2,
                end = 5,
                preview = false
            )
        )

        assertEquals(FetchInfoSources.SOSAD, parameters.source)
        assertEquals(FETCH_INFO_SEARCH_KEYWORD, parameters.searchMode)
        assertEquals("书名", parameters.query)
        assertEquals(FETCH_INFO_CONTENT_CATALOG, parameters.content)
        assertTrue(parameters.fetchCatalog)
        assertEquals("session=secret", parameters.authCookie)
        assertEquals(2, parameters.bodyRangeStart)
        assertEquals(5, parameters.bodyRangeEnd)
        assertEquals(DEFAULT_FETCH_INFO_INTRO_TARGET, parameters.introTargetPath)
    }

    @Test
    fun insertChapterSosadCatalogRangeSkipsVolumesAndCountsNormalChaptersOnly() {
        val catalog = listOf(
            FetchedCatalogItem(index = 1, title = "第一卷", isVolume = true),
            FetchedCatalogItem(index = 2, title = "第1章", url = "https://sosad.fun/posts/1"),
            FetchedCatalogItem(index = 3, title = "第2章", url = "https://sosad.fun/posts/2"),
            FetchedCatalogItem(index = 4, title = "第二卷", isVolume = true),
            FetchedCatalogItem(index = 5, title = "第3章", url = "https://sosad.fun/posts/3")
        )

        assertEquals(
            listOf("第2章"),
            insertChapterSosadCatalogRange(catalog, sosadParameters(start = 2, end = 2)).map { it.title }
        )
        assertEquals(
            listOf("第2章", "第3章"),
            insertChapterSosadCatalogRange(catalog, sosadParameters(start = 2, end = 1)).map { it.title }
        )
    }

    @Test
    fun prepareSosadInsertChapterBodyFallsBackToPlainTextWhenBlocksAreEmpty() {
        val prepared = prepareSosadInsertChapterBody(
            book = null,
            parameters = sosadParameters(),
            chapterUrl = "https://sosad.fun/posts/1",
            body = FetchedSosadBody(
                text = " 第一行 \n\n 第二行 ",
                blocks = emptyList()
            ),
            reservedImageStems = mutableSetOf()
        )

        assertEquals(listOf("第一行", "第二行"), prepared.first.map { it.text })
        assertEquals(emptyList<InsertChapterImageResource>(), prepared.second)
    }

    @Test
    fun prepareSosadInsertChapterBodySkipsImagesWithoutTargetBookAndSeparatesAuthorNote() {
        val prepared = prepareSosadInsertChapterBody(
            book = null,
            parameters = sosadParameters(),
            chapterUrl = "https://sosad.fun/posts/1",
            body = FetchedSosadBody(
                text = "",
                blocks = listOf(
                    FetchedSosadBodyBlock(text = "正文第一段"),
                    FetchedSosadBodyBlock(imageUrl = "https://i.ibb.co/image/pic.jpg"),
                    FetchedSosadBodyBlock(text = "Warning: 提示", cssClass = SOSAD_BODY_CSS_SYS),
                    FetchedSosadBodyBlock(text = "作者有话说", cssClass = SOSAD_BODY_CSS_AUTHOR_NOTE),
                    FetchedSosadBodyBlock(text = "普通但带其他class", cssClass = "other")
                )
            ),
            reservedImageStems = mutableSetOf()
        )

        assertEquals(
            listOf("正文第一段", "Warning: 提示", "-----------------------", "作者有话说", "普通但带其他class"),
            prepared.first.map { it.text }
        )
        assertEquals(listOf("", "sys", "", "", ""), prepared.first.map { it.cssClass })
        assertEquals(emptyList<InsertChapterImageResource>(), prepared.second)
    }

    @Test
    fun prepareSosadInsertChapterBodyKeepsWarningClassAndSeparatesAuthorNoteBlocks() {
        val prepared = prepareSosadInsertChapterBody(
            book = null,
            parameters = sosadParameters(),
            chapterUrl = "https://sosad.fun/posts/1",
            body = FetchedSosadBody(
                text = "",
                blocks = listOf(
                    FetchedSosadBodyBlock(text = "系统提示", cssClass = SOSAD_BODY_CSS_SYS),
                    FetchedSosadBodyBlock(text = "正文第一段"),
                    FetchedSosadBodyBlock(text = "作者有话说", cssClass = SOSAD_BODY_CSS_AUTHOR_NOTE),
                    FetchedSosadBodyBlock(text = "后记", cssClass = SOSAD_BODY_CSS_AUTHOR_NOTE)
                )
            ),
            reservedImageStems = mutableSetOf()
        )

        assertEquals(
            listOf("系统提示", "正文第一段", "-----------------------", "作者有话说", "后记"),
            prepared.first.map { it.text }
        )
        assertEquals(listOf("sys", "", "", "", ""), prepared.first.map { it.cssClass })
    }

    private fun sosadParameters(
        query: String = "书名",
        cookie: String = "session=secret",
        start: Int = 1,
        end: Int = 0,
        preview: Boolean = true
    ): InsertChapterParameters {
        return InsertChapterParameters(
            sourceType = INSERT_CHAPTER_SOURCE_SOSAD,
            sosadQuery = query,
            sosadAuthCookie = cookie,
            sosadBodyRangeStart = start,
            sosadBodyRangeEnd = end,
            preview = preview
        )
    }
}
