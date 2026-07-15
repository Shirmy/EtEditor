package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsertChapterSearchChoiceUtilsTest {
    private val choiceA = FetchInfoSearchChoice(
        source = FetchInfoSources.SOSAD,
        title = "书名A",
        author = "作者A",
        detailUrl = "https://example.com/a"
    )
    private val choiceB = FetchInfoSearchChoice(
        source = FetchInfoSources.SOSAD,
        title = "书名B",
        author = "作者B",
        detailUrl = "https://example.com/b"
    )

    @Test
    fun validSelectionRequiresClearingChoiceRequestBeforeFetch() {
        val request = sampleRequest(choices = listOf(choiceA, choiceB))
        val validation = validateInsertSosadSearchChoiceSelection(
            request = request,
            toolId = "tool-1",
            choice = choiceA
        )
        assertTrue(validation.ok)
        assertTrue(validation.clearChoiceRequestBeforeFetch)
        assertEquals("", validation.errorMessage)
    }

    @Test
    fun invalidSelectionDoesNotClearChoiceRequestBeforeFetch() {
        assertFalse(
            validateInsertSosadSearchChoiceSelection(
                request = null,
                toolId = "tool-1",
                choice = choiceA
            ).clearChoiceRequestBeforeFetch
        )
        assertFalse(
            validateInsertSosadSearchChoiceSelection(
                request = sampleRequest(toolId = "other"),
                toolId = "tool-1",
                choice = choiceA
            ).clearChoiceRequestBeforeFetch
        )
        assertFalse(
            validateInsertSosadSearchChoiceSelection(
                request = sampleRequest(source = FetchInfoSources.JJWXC),
                toolId = "tool-1",
                choice = choiceA
            ).clearChoiceRequestBeforeFetch
        )
        assertFalse(
            validateInsertSosadSearchChoiceSelection(
                request = sampleRequest(choices = listOf(choiceA)),
                toolId = "tool-1",
                choice = choiceB
            ).clearChoiceRequestBeforeFetch
        )
    }

    @Test
    fun invalidSelectionReportsDistinctMessages() {
        assertEquals(
            "没有可选择的搜索结果",
            validateInsertSosadSearchChoiceSelection(null, "tool-1", choiceA).errorMessage
        )
        assertEquals(
            "搜索结果已变化",
            validateInsertSosadSearchChoiceSelection(
                sampleRequest(toolId = "other"),
                "tool-1",
                choiceA
            ).errorMessage
        )
        assertEquals(
            "搜索结果来源不匹配",
            validateInsertSosadSearchChoiceSelection(
                sampleRequest(source = FetchInfoSources.JJWXC),
                "tool-1",
                choiceA
            ).errorMessage
        )
        assertEquals(
            "搜索结果已失效",
            validateInsertSosadSearchChoiceSelection(
                sampleRequest(choices = listOf(choiceA)),
                "tool-1",
                choiceB
            ).errorMessage
        )
    }

    @Test
    fun clearBeforeFetchMatchesFetchInfoDismissContract() {
        // 抓取信息选书：校验通过后先清 request 再抓；插入废文共用同一契约。
        val ok = validateInsertSosadSearchChoiceSelection(
            request = sampleRequest(),
            toolId = "tool-1",
            choice = choiceA
        )
        assertTrue(ok.ok && ok.clearChoiceRequestBeforeFetch)

        val fail = validateInsertSosadSearchChoiceSelection(
            request = sampleRequest(),
            toolId = "tool-1",
            choice = choiceB.copy(detailUrl = "missing")
        )
        assertFalse(fail.ok)
        assertFalse(fail.clearChoiceRequestBeforeFetch)
    }

    private fun sampleRequest(
        toolId: String = "tool-1",
        source: String = FetchInfoSources.SOSAD,
        choices: List<FetchInfoSearchChoice> = listOf(choiceA, choiceB)
    ): FetchInfoSearchChoiceRequest {
        return FetchInfoSearchChoiceRequest(
            toolId = toolId,
            parameters = FetchInfoParameters(
                source = source,
                searchMode = "keyword",
                query = "书名",
                content = "catalog",
                fetchCatalog = true,
                fetchIntro = false,
                fetchCover = false,
                authCookie = "cookie",
                bodyRangeStart = 1,
                bodyRangeEnd = 0,
                catalogFilter = "",
                autoTitleFormat = false,
                introFilter = "",
                writeCatalog = false,
                writeIntro = false,
                introTargetPath = "",
                writeCover = false
            ),
            choices = choices
        )
    }
}
