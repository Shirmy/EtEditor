package com.eteditor

import com.eteditor.core.ChapterInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolScopeUtilsTest {
    @Test
    fun toolScopeTargetChapterIndicesReturnsCurrentAllAndMatchedFiles() {
        val chapters = listOf(
            chapter("Chapter0001.xhtml", "OEBPS/Text/Chapter0001.xhtml"),
            chapter("Intro.xhtml", "OEBPS/Text/Intro.xhtml"),
            chapter("Chapter0002.xhtml", "OEBPS/Text/Chapter0002.xhtml")
        )

        assertEquals(
            listOf(2),
            toolScopeTargetChapterIndices(TOOL_SCOPE_CURRENT, size = 3, currentIndex = 9, chapters = chapters)
        )
        assertEquals(
            listOf(0, 1, 2),
            toolScopeTargetChapterIndices(TOOL_SCOPE_ALL, size = 3, currentIndex = 1, chapters = chapters)
        )
        assertEquals(
            listOf(0, 2),
            toolScopeTargetChapterIndices(
                scope = TOOL_SCOPE_FILE_REGEX,
                size = 3,
                currentIndex = 1,
                chapters = chapters,
                matchPattern = """Chapter\d+""",
                matchRegexEnabled = true
            )
        )
    }

    @Test
    fun toolScopeTargetChapterIndicesClampNegativeCurrentAndFallbackUnknownScopeToAll() {
        val chapters = listOf(
            chapter("Chapter0001.xhtml", "OEBPS/Text/Chapter0001.xhtml"),
            chapter("Chapter0002.xhtml", "OEBPS/Text/Chapter0002.xhtml")
        )

        assertEquals(
            listOf(0),
            toolScopeTargetChapterIndices(TOOL_SCOPE_CURRENT, size = 2, currentIndex = -8, chapters = chapters)
        )
        assertEquals(
            listOf(0, 1),
            toolScopeTargetChapterIndices("unknown", size = 2, currentIndex = 0, chapters = chapters)
        )
    }

    @Test
    fun toolScopeTargetChapterIndicesMatchesSourcePathWhenFileNameDoesNotMatch() {
        val chapters = listOf(
            chapter("Chapter0001.xhtml", "OEBPS/Text/Chapter0001.xhtml"),
            chapter("chapter.xhtml", "OEBPS/Special/Bonus.xhtml")
        )

        assertEquals(
            listOf(1),
            toolScopeTargetChapterIndices(
                scope = TOOL_SCOPE_FILE_REGEX,
                size = 2,
                currentIndex = 0,
                chapters = chapters,
                matchPattern = "Special/Bonus",
                matchRegexEnabled = false
            )
        )
    }

    @Test
    fun toolScopeFileNameMatcherSupportsPlainMatchAndReportsErrors() {
        val errors = mutableListOf<String>()
        val plain = toolScopeFileNameMatcher("Intro", regexEnabled = false, onError = errors::add)
        val blank = toolScopeFileNameMatcher(" ", regexEnabled = false, onError = errors::add)
        val invalidRegex = toolScopeFileNameMatcher("(", regexEnabled = true, onError = errors::add)

        assertEquals(true, plain?.invoke("OEBPS/Text/Intro.xhtml"))
        assertEquals(false, plain?.invoke("OEBPS/Text/Chapter.xhtml"))
        assertNull(blank)
        assertNull(invalidRegex)
        assertEquals(listOf("请输入匹配规则", "作用范围正则错误"), errors)
    }

    @Test
    fun toolScopeFileNameMatcherTrimsPlainPatternAndMatchesCaseSensitively() {
        val errors = mutableListOf<String>()

        val matcher = toolScopeFileNameMatcher(" Intro ", regexEnabled = false, onError = errors::add)

        assertEquals(true, matcher?.invoke("OEBPS/Text/Intro.xhtml"))
        assertEquals(false, matcher?.invoke("OEBPS/Text/intro.xhtml"))
        assertEquals(emptyList<String>(), errors)
    }

    @Test
    fun toolScopeFileNameMatcherTrimsRegexPatternBeforeMatching() {
        val errors = mutableListOf<String>()

        val matcher = toolScopeFileNameMatcher(" Chapter\\d+\\.xhtml ", regexEnabled = true, onError = errors::add)

        assertEquals(true, matcher?.invoke("OEBPS/Text/Chapter0001.xhtml"))
        assertEquals(false, matcher?.invoke("OEBPS/Text/Intro.xhtml"))
        assertEquals(emptyList<String>(), errors)
    }

    @Test
    fun toolScopeTargetChapterIndicesDoesNotReportNoMatchWhenMatcherCannotBeBuilt() {
        val errors = mutableListOf<String>()

        val result = toolScopeTargetChapterIndices(
            scope = TOOL_SCOPE_FILE_REGEX,
            size = 1,
            currentIndex = 0,
            chapters = listOf(chapter("a.xhtml", "OEBPS/a.xhtml")),
            matchPattern = " ",
            matchRegexEnabled = true,
            onError = errors::add
        )

        assertEquals(emptyList<Int>(), result)
        assertEquals(listOf("请输入匹配规则"), errors)
    }

    @Test
    fun toolScopeTargetChapterIndicesReportsNoMatchAndEmptySize() {
        val errors = mutableListOf<String>()

        assertEquals(
            emptyList<Int>(),
            toolScopeTargetChapterIndices(
                scope = TOOL_SCOPE_FILE_REGEX,
                size = 2,
                currentIndex = 0,
                chapters = listOf(chapter("a.xhtml", "OEBPS/a.xhtml")),
                matchPattern = "missing",
                matchRegexEnabled = false,
                onError = errors::add
            )
        )
        assertEquals(
            emptyList<Int>(),
            toolScopeTargetChapterIndices(TOOL_SCOPE_ALL, size = 0, currentIndex = 0, chapters = emptyList())
        )
        assertTrue("作用范围未匹配章节" in errors)
    }

    @Test
    fun toolScopeTargetChapterIndicesReturnsEmptyWhenRegexIsInvalid() {
        val errors = mutableListOf<String>()

        val result = toolScopeTargetChapterIndices(
            scope = TOOL_SCOPE_FILE_REGEX,
            size = 1,
            currentIndex = 0,
            chapters = listOf(chapter("Chapter0001.xhtml", "OEBPS/Text/Chapter0001.xhtml")),
            matchPattern = "(",
            matchRegexEnabled = true,
            onError = errors::add
        )

        assertEquals(emptyList<Int>(), result)
        assertEquals(listOf("作用范围正则错误"), errors)
    }

    private fun chapter(fileName: String, source: String): ChapterInfo {
        return ChapterInfo(
            index = 1,
            title = fileName,
            wordCount = 0,
            source = source,
            fileName = fileName
        )
    }
}
