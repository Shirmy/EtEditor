package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchInfoSearchUtilsTest {
    @Test
    fun jjwxcSearchHelpersDetectDirectIdsAndBuildNormalizedQueries() {
        assertTrue(needsJjwxcSearch("《书 名》 作者：作者A"))
        assertFalse(needsJjwxcSearch(""))
        assertFalse(needsJjwxcSearch("12345"))
        assertFalse(needsJjwxcSearch("onebook.php?novelid=12345"))
        assertFalse(needsJjwxcSearch("https://example.com/book"))
        assertFalse(needsJjwxcSearch("https://www.jjwxc.net/onebook.php?novelid=12345"))
        assertEquals("12345", extractJjwxcNovelId("onebook.php?novelid=12345"))
        assertEquals("作者A", extractJjwxcAuthor("《书名》 作者：作者A；其他"))
        assertEquals(
            listOf("书 名", "《书 名》 作者：作者A"),
            jjwxcSearchQueries("《书 名》 作者：作者A")
        )
        assertEquals(listOf("书 名"), jjwxcSearchQueries(" 《书 名》 "))
        assertEquals("书名", "《 书 名 》".normalizeJjwxcSearchText())
    }

    @Test
    fun jjwxcSearchHelpersTrimAuthorSuffixAndKeepPlainDigitsAsNonUrlInput() {
        assertNull(extractJjwxcNovelId("12345"))
        assertEquals("作者A", extractJjwxcAuthor("《书名》 作者: 作者A\n下一行"))
        assertEquals(
            listOf("书名", "书名 作者: 作者A"),
            jjwxcSearchQueries("书名 作者: 作者A")
        )
    }

    @Test
    fun gongzicpSearchHelpersNormalizeIdsAndUrls() {
        assertTrue(needsGongzicpSearch("作品名"))
        assertFalse(needsGongzicpSearch(""))
        assertFalse(needsGongzicpSearch("456"))
        assertFalse(needsGongzicpSearch("http://example.com/novel-456.html"))
        assertFalse(needsGongzicpSearch("https://www.gongzicp.com/novel-456.html"))
        assertEquals("456", extractGongzicpNovelId("https://www.gongzicp.com/novel-456.html"))
        assertEquals("456", extractGongzicpNovelId("novel/456"))
        assertEquals("789", extractGongzicpNovelId("novel_id=789"))
        assertEquals("https://www.gongzicp.com/novel-456.html", normalizeGongzicpDetailUrl("456"))
        assertEquals(
            "https://www.gongzicp.com/novel-456.html",
            normalizeGongzicpDetailUrl("https://www.gongzicp.com/novel-456.html")
        )
        assertNull(normalizeGongzicpDetailUrl("作品名"))
    }

    @Test
    fun gongzicpSearchHelpersReadAlternateIdKeysAndTrimBlankUrls() {
        assertEquals("789", extractGongzicpNovelId("novelid=789"))
        assertEquals("789", extractGongzicpNovelId("NOVEL_789"))
        assertNull(normalizeGongzicpDetailUrl("   "))
        assertEquals("https://www.gongzicp.com/novel-789.html", normalizeGongzicpDetailUrl(" novel_789 "))
    }

    @Test
    fun searchChoiceResolutionMatchesTitleAndAuthorAfterNormalization() {
        val choices = listOf(
            choice(title = "书名", author = "作者A", detailUrl = "a"),
            choice(title = "书名", author = "作者B", detailUrl = "b")
        )

        assertEquals(
            "b",
            resolveFetchInfoSearchChoiceByMetadata(
                choices = choices,
                query = "",
                metadataTitle = "《书 名》",
                metadataAuthor = "作者B"
            ).choice?.detailUrl
        )
        assertEquals(
            "书名匹配但作者不匹配",
            resolveFetchInfoSearchChoiceByMetadata(
                choices = choices,
                query = "书名",
                metadataTitle = "",
                metadataAuthor = "作者C"
            ).skipReason
        )
    }

    @Test
    fun searchChoiceResolutionUsesExplicitQueryBeforeMetadataTitle() {
        val resolution = resolveFetchInfoSearchChoiceByMetadata(
            choices = listOf(choice(title = "元数据书名", author = "作者A", detailUrl = "a")),
            query = "用户输入书名",
            metadataTitle = "元数据书名",
            metadataAuthor = "作者A"
        )

        assertNull(resolution.choice)
        assertEquals("没有匹配书名", resolution.skipReason)
    }

    @Test
    fun searchChoiceResolutionUsesMetadataTitleWhenQueryIsBlankAndAuthorIsMissing() {
        val resolution = resolveFetchInfoSearchChoiceByMetadata(
            choices = listOf(
                choice(title = "《元 数据 书名》", author = "作者A", detailUrl = "a"),
                choice(title = "别的书", author = "作者B", detailUrl = "b")
            ),
            query = "",
            metadataTitle = "元数据书名",
            metadataAuthor = ""
        )

        assertEquals("a", resolution.choice?.detailUrl)
        assertEquals(emptyList<FetchInfoSearchChoice>(), resolution.promptChoices)
        assertNull(resolution.skipReason)
    }

    @Test
    fun preferredSearchChoiceByMetadataUsesAuthorThenTitleWhenNeeded() {
        val choices = listOf(
            choice(title = "书名", author = "作者A", detailUrl = "a"),
            choice(title = "番外", author = "作者A", detailUrl = "extra"),
            choice(title = "书名", author = "作者B", detailUrl = "b")
        )

        assertEquals(
            "a",
            preferredSearchChoiceByMetadata(
                choices = choices,
                query = "《书 名》",
                metadataTitle = "",
                metadataAuthor = " 作者A "
            )?.detailUrl
        )
        assertNull(
            preferredSearchChoiceByMetadata(
                choices = choices,
                query = "",
                metadataTitle = "",
                metadataAuthor = ""
            )
        )
        assertEquals(
            "only",
            preferredSearchChoiceByMetadata(
                choices = listOf(choice(title = "任意", author = "", detailUrl = "only")),
                query = "",
                metadataTitle = "",
                metadataAuthor = ""
            )?.detailUrl
        )
    }

    @Test
    fun preferredSearchChoiceByMetadataReturnsUniqueAuthorMatchBeforeCheckingTitle() {
        val choices = listOf(
            choice(title = "番外", author = "作者A", detailUrl = "extra"),
            choice(title = "书名", author = "作者B", detailUrl = "b")
        )

        assertEquals(
            "extra",
            preferredSearchChoiceByMetadata(
                choices = choices,
                query = "书名",
                metadataTitle = "",
                metadataAuthor = "作者A"
            )?.detailUrl
        )
    }

    @Test
    fun preferredSearchChoiceByMetadataReturnsNullWhenAuthorMatchesStayAmbiguous() {
        val choices = listOf(
            choice(title = "番外一", author = "作者A", detailUrl = "extra-1"),
            choice(title = "番外二", author = " 作者A ", detailUrl = "extra-2"),
            choice(title = "书名", author = "作者B", detailUrl = "b")
        )

        assertNull(
            preferredSearchChoiceByMetadata(
                choices = choices,
                query = "书名",
                metadataTitle = "",
                metadataAuthor = "作者A"
            )
        )
    }

    @Test
    fun searchChoiceResolutionSkipsWhenNoNormalizedTitleMatches() {
        val resolution = resolveFetchInfoSearchChoiceByMetadata(
            choices = listOf(choice(title = "别的书", author = "作者A", detailUrl = "a")),
            query = "书名",
            metadataTitle = "",
            metadataAuthor = ""
        )

        assertNull(resolution.choice)
        assertEquals(emptyList<FetchInfoSearchChoice>(), resolution.promptChoices)
        assertEquals("没有匹配书名", resolution.skipReason)
    }

    @Test
    fun searchChoiceResolutionPromptsWhenTitleMatchesButAuthorIsMissing() {
        val choices = listOf(
            choice(title = "书名", author = "作者A", detailUrl = "a"),
            choice(title = "书名", author = "作者B", detailUrl = "b"),
            choice(title = "别的书", author = "作者C", detailUrl = "c")
        )

        val resolution = resolveFetchInfoSearchChoiceByMetadata(
            choices = choices,
            query = "书名",
            metadataTitle = "",
            metadataAuthor = ""
        )

        assertNull(resolution.choice)
        assertEquals(listOf("a", "b"), resolution.promptChoices.map { it.detailUrl })
    }

    @Test
    fun searchChoiceResolutionPromptsWhenTitleAndAuthorStillMatchMultipleChoices() {
        val choices = listOf(
            choice(title = "书名", author = "作者A", detailUrl = "a1"),
            choice(title = "《书 名》", author = " 作者A ", detailUrl = "a2"),
            choice(title = "书名", author = "作者B", detailUrl = "b")
        )

        val resolution = resolveFetchInfoSearchChoiceByMetadata(
            choices = choices,
            query = "",
            metadataTitle = "书名",
            metadataAuthor = "作者A"
        )

        assertNull(resolution.choice)
        assertEquals(listOf("a1", "a2"), resolution.promptChoices.map { it.detailUrl })
        assertNull(resolution.skipReason)
        assertEquals(SearchChoiceResolution(), resolveFetchInfoSearchChoiceByMetadata(emptyList(), "", "", ""))
    }

    @Test
    fun distinctVisibleSearchChoicesDeduplicatesByDetailUrlThenTitleAndAuthor() {
        val choices = listOf(
            // 网址各不相同的独立帖子：即便同名同作者也应全部保留
            choice(title = "书名", author = "匿名", detailUrl = "a"),
            choice(title = "书名", author = "匿名", detailUrl = "b"),
            // 网址为空时才回退到「书名+作者」去重
            choice(title = "《书 名》", author = "作者A", detailUrl = ""),
            choice(title = "书名", author = " 作者A ", detailUrl = "")
        )

        assertEquals(
            listOf("a", "b", ""),
            distinctVisibleSearchChoices(choices).map { it.detailUrl }
        )
    }

    private fun choice(
        title: String,
        author: String,
        detailUrl: String
    ): FetchInfoSearchChoice {
        return FetchInfoSearchChoice(
            source = FETCH_INFO_SOURCE_JJWXC,
            title = title,
            author = author,
            detailUrl = detailUrl
        )
    }
}
