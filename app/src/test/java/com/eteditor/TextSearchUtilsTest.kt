package com.eteditor

import com.eteditor.core.TxtChapter
import com.eteditor.core.TxtDocument
import org.junit.Assert.assertEquals
import org.junit.Test

class TextSearchUtilsTest {
    @Test
    fun buildTextSearchResultsSupportsPlainCaseInsensitiveAndRegexRules() {
        val plain = buildTextSearchResults(
            sources = listOf(SearchSource(0, "正文", "book.txt", "Alpha alpha")),
            rule = TextReplaceRule(find = "ALPHA", replacement = "", regex = false),
            caseSensitive = false,
            ruleIndex = 0,
            idPrefix = "plain",
            resolveLocation = { start, end, chapterIndex, title ->
                TextSearchResultLocation(chapterIndex, "$title@$start-$end")
            }
        )
        val regex = buildTextSearchResults(
            sources = listOf(SearchSource(1, "章节", "c.xhtml", "第12章 标题 第三章")),
            rule = TextReplaceRule(find = """第(\d+)章""", replacement = "", regex = true),
            caseSensitive = true,
            ruleIndex = 1,
            idPrefix = "regex",
            resolveLocation = { start, end, chapterIndex, title ->
                TextSearchResultLocation(chapterIndex, "$title@$start-$end")
            }
        )

        assertEquals(listOf("Alpha", "alpha"), plain.map { it.matchText })
        assertEquals(listOf(0, 6), plain.map { it.sourceStart })
        assertEquals(listOf("正文@0-5", "正文@6-11"), plain.map { it.chapterTitle })
        assertEquals(listOf("第12章"), regex.map { it.matchText })
        assertEquals(listOf("章节@0-4"), regex.map { it.chapterTitle })
    }

    @Test
    fun buildTextSearchResultsUsesSourceOffsetsAndResolvedLocation() {
        val results = buildTextSearchResults(
            sources = listOf(
                SearchSource(
                    chapterIndex = 2,
                    title = "原章",
                    fileName = "c.xhtml",
                    text = "前文 needle 后文",
                    sourceOffset = 100
                )
            ),
            rule = TextReplaceRule(find = "needle", replacement = "替换", regex = false),
            caseSensitive = true,
            ruleIndex = 3,
            idPrefix = "rule-x",
            resolveLocation = { start, end, chapterIndex, title ->
                TextSearchResultLocation(chapterIndex + 10, "$title@$start-$end")
            }
        )

        val result = results.single()
        assertEquals(3, result.ruleIndex)
        assertEquals(12, result.chapterIndex)
        assertEquals("原章@103-109", result.chapterTitle)
        assertEquals("needle", result.matchText)
        assertEquals(103, result.sourceStart)
        assertEquals(109, result.sourceEnd)
    }

    @Test
    fun textSearchResultsAfterSingleReplacementDropsReplacedAndShiftsFollowingMatches() {
        val results = listOf(
            searchResult(id = "before", start = 0, end = 4),
            searchResult(id = "replaced", start = 5, end = 9),
            searchResult(id = "after", start = 12, end = 16)
        )

        val shifted = textSearchResultsAfterSingleReplacement(
            results = results,
            sourceText = null,
            replacedId = "replaced",
            sourceStart = 5,
            sourceEnd = 9,
            replacementDelta = 2,
            resolveLocation = { _, _, chapterIndex, title -> TextSearchResultLocation(chapterIndex, title) }
        )

        assertEquals(listOf("before", "after"), shifted.map { it.id })
        assertEquals(14, shifted[1].sourceStart)
        assertEquals(18, shifted[1].sourceEnd)
    }

    @Test
    fun textSearchResultsAfterSingleReplacementDropsOverlapsAndRefreshesContextFromSourceText() {
        val results = listOf(
            searchResult(id = "overlap", start = 3, end = 8),
            searchResult(id = "replaced", start = 5, end = 9),
            searchResult(id = "after", start = 12, end = 16)
        )

        val shifted = textSearchResultsAfterSingleReplacement(
            results = results,
            sourceText = "0123456789abcdef",
            replacedId = "replaced",
            sourceStart = 5,
            sourceEnd = 9,
            replacementDelta = -2,
            resolveLocation = { start, end, chapterIndex, title ->
                TextSearchResultLocation(chapterIndex + 1, "$title@$start-$end")
            }
        )

        assertEquals(listOf("after"), shifted.map { it.id })
        assertEquals(10, shifted.single().sourceStart)
        assertEquals(14, shifted.single().sourceEnd)
        assertEquals("abcd", shifted.single().matchText)
        assertEquals("章节@10-14", shifted.single().chapterTitle)
    }

    @Test
    fun txtSearchSourcesForPreviewBuildsAllPrefaceAndCurrentChapterSources() {
        val text = "前言\n第1章 开始\n正文一\n第2章 继续\n正文二"
        val firstStart = text.indexOf("第1章")
        val secondStart = text.indexOf("第2章")
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = listOf(
                TxtChapter(
                    index = 1,
                    lineIndex = 1,
                    endLineIndex = 3,
                    title = "第1章 开始",
                    wordCount = 3,
                    startIndex = firstStart,
                    endIndex = secondStart
                ),
                TxtChapter(
                    index = 2,
                    lineIndex = 3,
                    endLineIndex = 5,
                    title = "第2章 继续",
                    wordCount = 3,
                    startIndex = secondStart,
                    endIndex = text.length
                )
            )
        )

        val all = txtSearchSourcesForPreview(document, TOOL_SCOPE_ALL, previewChapterIndex = 1, prefaceEndIndex = firstStart)
        val preface = txtSearchSourcesForPreview(
            document,
            TOOL_SCOPE_CURRENT,
            previewChapterIndex = TXT_PREFACE_CHAPTER_INDEX,
            prefaceEndIndex = firstStart
        )
        val current = txtSearchSourcesForPreview(document, TOOL_SCOPE_CURRENT, previewChapterIndex = 1, prefaceEndIndex = firstStart)

        assertEquals(listOf("全文"), all.map { it.title })
        assertEquals(listOf(0), all.map { it.sourceOffset })
        assertEquals(listOf("前言"), preface.map { it.title })
        assertEquals(listOf("前言\n"), preface.map { it.text })
        assertEquals(listOf("第2章 继续"), current.map { it.title })
        assertEquals(listOf(secondStart), current.map { it.sourceOffset })
    }

    @Test
    fun txtSearchSourcesForPreviewHandlesTextWithoutChaptersAndCurrentScope() {
        val document = TxtDocument(
            originalName = "book.txt",
            text = "正文一\n正文二",
            encoding = "UTF-8",
            chapters = emptyList()
        )

        val all = txtSearchSourcesForPreview(document, TOOL_SCOPE_ALL, previewChapterIndex = 9, prefaceEndIndex = null)
        val current = txtSearchSourcesForPreview(document, TOOL_SCOPE_CURRENT, previewChapterIndex = 0, prefaceEndIndex = null)
        val missingCurrent = txtSearchSourcesForPreview(document, TOOL_SCOPE_CURRENT, previewChapterIndex = 2, prefaceEndIndex = null)

        assertEquals(listOf("TXT 正文预览"), all.map { it.title })
        assertEquals(listOf("book.txt"), all.map { it.fileName })
        assertEquals(listOf("正文一\n正文二"), current.map { it.text })
        assertEquals(emptyList<SearchSource>(), missingCurrent)
    }

    @Test
    fun visibleTextSearchSourcesSplitsAroundTagsAndPreservesSourceOffsets() {
        val html = "<p>Alpha</p><br/><p> Beta </p><span></span>Gamma"
        val source = SearchSource(
            chapterIndex = 4,
            title = "HTML",
            fileName = "chapter.xhtml",
            text = html,
            sourceOffset = 50
        )

        val sources = visibleTextSearchSources(source)

        assertEquals(listOf("Alpha", " Beta ", "Gamma"), sources.map { it.text })
        assertEquals(
            listOf(
                50 + html.indexOf("Alpha"),
                50 + html.indexOf(" Beta "),
                50 + html.indexOf("Gamma")
            ),
            sources.map { it.sourceOffset }
        )
        assertEquals(listOf(4, 4, 4), sources.map { it.chapterIndex })
    }

    @Test
    fun visibleTextSearchSourcesSkipsBlankSegmentsBetweenTags() {
        val html = "<p> </p><span>Alpha</span>\n <b>Beta</b>"
        val source = SearchSource(
            chapterIndex = 5,
            title = "HTML",
            fileName = "chapter.xhtml",
            text = html,
            sourceOffset = 20
        )

        val sources = visibleTextSearchSources(source)

        assertEquals(listOf("Alpha", "Beta"), sources.map { it.text })
        assertEquals(
            listOf(20 + html.indexOf("Alpha"), 20 + html.indexOf("Beta")),
            sources.map { it.sourceOffset }
        )
    }

    @Test
    fun searchContextClipsAroundMatchAndReportsAdjustedRange() {
        val context = searchContext(
            source = "0123456789abcdefghij",
            start = 10,
            end = 13,
            contextChars = 4
        )

        assertEquals("...6789abcdefg...", context.text)
        assertEquals(7, context.matchStart)
        assertEquals(10, context.matchEnd)
    }

    @Test
    fun plainSearchRangesReturnsNonOverlappingForwardRanges() {
        val ranges = plainSearchRanges(
            source = "aaaa",
            find = "aa",
            caseSensitive = true
        )

        assertEquals(listOf(0 to 2, 2 to 4), ranges)
    }

    @Test
    fun regexSearchRangesSkipsZeroLengthMatches() {
        val ranges = regexSearchRanges(
            source = "Alpha Beta",
            pattern = Regex("""\b""")
        )

        assertEquals(emptyList<Pair<Int, Int>>(), ranges)
    }

    @Test
    fun buildReplacementPreviewMatchesExpandsRegexAndUsesAbsoluteOffsets() {
        val matches = buildReplacementPreviewMatches(
            sources = listOf(
                SearchSource(
                    chapterIndex = 2,
                    title = "章节",
                    fileName = "c.xhtml",
                    text = "第12章 标题",
                    sourceOffset = 40
                )
            ),
            rule = ParsedReplacementRule(
                lineNo = 3,
                pattern = """第(\d+)章""",
                replacement = "Chapter ${'$'}1",
                regex = true
            ),
            caseSensitive = true,
            idPrefix = "replace",
            resolveLocation = { start, end, chapterIndex, title ->
                TextSearchResultLocation(chapterIndex + 1, "$title@$start-$end")
            }
        )

        val match = matches.single()
        assertEquals("replace-2-40-0-0", match.id)
        assertEquals(3, match.chapterIndex)
        assertEquals("章节@40-44", match.chapterTitle)
        assertEquals(40, match.sourceStart)
        assertEquals(44, match.sourceEnd)
        assertEquals("第12章", match.matchText)
        assertEquals("Chapter 12", match.replacementText)
    }

    @Test
    fun buildReplacementPreviewMatchesRespectsMaxMatchesAcrossSources() {
        val sources = listOf(
            SearchSource(0, "第一章", "a.txt", "foo foo", sourceOffset = 10),
            SearchSource(1, "第二章", "b.txt", "foo", sourceOffset = 30)
        )
        val rule = ParsedReplacementRule(
            lineNo = 1,
            pattern = "foo",
            replacement = "bar",
            regex = false
        )

        val none = buildReplacementPreviewMatches(
            sources = sources,
            rule = rule,
            caseSensitive = true,
            idPrefix = "replace",
            resolveLocation = { _, _, chapterIndex, title -> TextSearchResultLocation(chapterIndex, title) },
            maxMatches = 0
        )
        val limited = buildReplacementPreviewMatches(
            sources = sources,
            rule = rule,
            caseSensitive = true,
            idPrefix = "replace",
            resolveLocation = { start, end, chapterIndex, title ->
                TextSearchResultLocation(chapterIndex, "$title@$start-$end")
            },
            maxMatches = 2
        )

        assertEquals(emptyList<ReplacementPreviewMatch>(), none)
        assertEquals(listOf("replace-0-10-0-0", "replace-0-10-1-4"), limited.map { it.id })
        assertEquals(listOf("第一章@10-13", "第一章@14-17"), limited.map { it.chapterTitle })
        assertEquals(listOf("bar", "bar"), limited.map { it.replacementText })
    }

    @Test
    fun singleMatchReplacementExpandsRegexOnlyForFullMatch() {
        val rule = TextReplaceRule(
            find = "第(\\d+)章",
            replacement = "Chapter \$1",
            regex = true
        )

        assertEquals("Chapter 12", singleMatchReplacement("第12章", rule, caseSensitive = true))
        assertEquals("Chapter \$1", singleMatchReplacement("前第12章后", rule, caseSensitive = true))
    }

    @Test
    fun applyReplacementPlansToTextAppliesFromEndAndSkipsOverlappingPlans() {
        val (text, count) = applyReplacementPlansToText(
            source = "abcdef",
            plans = listOf(
                ReplacementMatchPlan(chapterIndex = 0, sourceStart = 1, sourceEnd = 5, replacementText = "BAD"),
                ReplacementMatchPlan(chapterIndex = 0, sourceStart = 2, sourceEnd = 4, replacementText = "XX"),
                ReplacementMatchPlan(chapterIndex = 0, sourceStart = 4, sourceEnd = 6, replacementText = "YY")
            )
        )

        assertEquals("abXXYY", text)
        assertEquals(2, count)
    }

    @Test
    fun applyReplacementPlansToTextSkipsInvalidRangesWhileApplyingValidPlans() {
        val (text, count) = applyReplacementPlansToText(
            source = "abcdef",
            plans = listOf(
                ReplacementMatchPlan(chapterIndex = 0, sourceStart = -1, sourceEnd = 2, replacementText = "BAD"),
                ReplacementMatchPlan(chapterIndex = 0, sourceStart = 1, sourceEnd = 99, replacementText = "BAD"),
                ReplacementMatchPlan(chapterIndex = 0, sourceStart = 2, sourceEnd = 2, replacementText = "BAD"),
                ReplacementMatchPlan(chapterIndex = 0, sourceStart = 4, sourceEnd = 6, replacementText = "YY"),
                ReplacementMatchPlan(chapterIndex = 0, sourceStart = 0, sourceEnd = 1, replacementText = "A")
            )
        )

        assertEquals("AbcdYY", text)
        assertEquals(2, count)
    }

    @Test
    fun replaceInTxtDocumentTextUsesCurrentChapterScope() {
        val text = "第1章 旧标题\nAlpha\n第2章 第二章\nAlpha"
        val secondStart = text.indexOf("第2章")
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = listOf(
                TxtChapter(
                    index = 1,
                    lineIndex = 0,
                    endLineIndex = 2,
                    title = "第1章 旧标题",
                    wordCount = 5,
                    startIndex = 0,
                    endIndex = secondStart
                ),
                TxtChapter(
                    index = 2,
                    lineIndex = 2,
                    endLineIndex = 4,
                    title = "第2章 第二章",
                    wordCount = 5,
                    startIndex = secondStart,
                    endIndex = text.length
                )
            )
        )

        val result = replaceInTxtDocumentText(
            document = document,
            parameters = parameters(scope = TOOL_SCOPE_CURRENT),
            currentChapterIndex = 1,
            prefaceEndIndex = null,
            rules = listOf(TextReplaceRule(find = "Alpha", replacement = "Beta", regex = false))
        )

        assertEquals("第1章 旧标题\nAlpha\n第2章 第二章\nBeta", result.text)
        assertEquals(1, result.changedSources)
        assertEquals(1, result.replacements)
    }

    @Test
    fun replaceInTxtDocumentTextUsesCurrentPrefaceScope() {
        val text = "Alpha\n第1章 旧标题\nAlpha"
        val chapterStart = text.indexOf("第1章")
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = listOf(
                TxtChapter(
                    index = 1,
                    lineIndex = 1,
                    endLineIndex = 3,
                    title = "第1章 旧标题",
                    wordCount = 5,
                    startIndex = chapterStart,
                    endIndex = text.length
                )
            )
        )

        val result = replaceInTxtDocumentText(
            document = document,
            parameters = parameters(scope = TOOL_SCOPE_CURRENT),
            currentChapterIndex = TXT_PREFACE_CHAPTER_INDEX,
            prefaceEndIndex = chapterStart,
            rules = listOf(TextReplaceRule(find = "Alpha", replacement = "Beta", regex = false))
        )

        assertEquals("Beta\n第1章 旧标题\nAlpha", result.text)
        assertEquals(1, result.changedSources)
        assertEquals(1, result.replacements)
    }

    private fun searchResult(id: String, start: Int, end: Int): TextSearchResult {
        return TextSearchResult(
            id = id,
            ruleIndex = 0,
            chapterIndex = 0,
            chapterTitle = "章节",
            context = "",
            matchText = "",
            contextMatchStart = 0,
            contextMatchEnd = 0,
            sourceStart = start,
            sourceEnd = end
        )
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

    @Test
    fun countTextSearchMatchesCountsAllMatchesWithoutBuildingObjects() {
        val sources = listOf(
            SearchSource(0, "第一章", "a.xhtml", "foo bar foo baz foo"),
            SearchSource(1, "第二章", "b.xhtml", "foo foo")
        )
        val rule = TextReplaceRule(find = "foo", replacement = "", regex = false)
        assertEquals(5, countTextSearchMatches(sources, rule, caseSensitive = false))
    }

    @Test
    fun countTextSearchMatchesReturnsZeroForEmptyFind() {
        val sources = listOf(SearchSource(0, "章", "a.xhtml", "abc"))
        val rule = TextReplaceRule(find = "", replacement = "", regex = false)
        assertEquals(0, countTextSearchMatches(sources, rule, caseSensitive = false))
    }

    @Test
    fun buildTextSearchResultsIncrementalBuildsFirstBatchAndReturnsCursor() {
        val sources = listOf(
            SearchSource(0, "第一章", "a.xhtml", "foo bar foo baz foo"),
            SearchSource(1, "第二章", "b.xhtml", "foo foo")
        )
        val rule = TextReplaceRule(find = "foo", replacement = "", regex = false)
        val resolveLocation = { start: Int, end: Int, chapterIndex: Int, title: String ->
            TextSearchResultLocation(chapterIndex, "$title@$start-$end")
        }
        // 一次取 2 条
        val batch1 = buildTextSearchResultsIncremental(
            sources = sources, rule = rule, caseSensitive = false,
            ruleIndex = 0, idPrefix = "r", cursor = null, targetCount = 2,
            resolveLocation = resolveLocation
        )
        assertEquals(2, batch1.results.size)
        assertEquals(false, batch1.reachedEnd)
        // 游标指向 source 0 的第 2 个命中
        assertEquals(0, batch1.cursor!!.sourceIndex)
        assertEquals(2, batch1.cursor!!.matchIndexInSource)

        // 从游标处接着取 2 条
        val batch2 = buildTextSearchResultsIncremental(
            sources = sources, rule = rule, caseSensitive = false,
            ruleIndex = 0, idPrefix = "r", cursor = batch1.cursor, targetCount = 2,
            resolveLocation = resolveLocation
        )
        assertEquals(2, batch2.results.size)
        assertEquals(false, batch2.reachedEnd)
        // 游标跳到 source 1 的第 1 个命中
        assertEquals(1, batch2.cursor!!.sourceIndex)
        assertEquals(1, batch2.cursor!!.matchIndexInSource)

        // 从游标处接着取剩余
        val batch3 = buildTextSearchResultsIncremental(
            sources = sources, rule = rule, caseSensitive = false,
            ruleIndex = 0, idPrefix = "r", cursor = batch2.cursor, targetCount = 10,
            resolveLocation = resolveLocation
        )
        assertEquals(1, batch3.results.size)
        assertEquals(true, batch3.reachedEnd)
        assertEquals(null, batch3.cursor)
    }

    @Test
    fun buildTextSearchResultsIncrementalReturnsAllWhenTargetExceedsTotal() {
        val sources = listOf(SearchSource(0, "章", "a.xhtml", "foo foo"))
        val rule = TextReplaceRule(find = "foo", replacement = "", regex = false)
        val resolveLocation = { start: Int, end: Int, chapterIndex: Int, title: String ->
            TextSearchResultLocation(chapterIndex, "$title@$start-$end")
        }
        val batch = buildTextSearchResultsIncremental(
            sources = sources, rule = rule, caseSensitive = false,
            ruleIndex = 0, idPrefix = "r", cursor = null, targetCount = 100,
            resolveLocation = resolveLocation
        )
        assertEquals(2, batch.results.size)
        assertEquals(true, batch.reachedEnd)
        assertEquals(null, batch.cursor)
    }

    @Test
    fun buildTextSearchResultsIncrementalProducesConsistentIdsWithFullScan() {
        val sources = listOf(
            SearchSource(0, "第一章", "a.xhtml", "foo bar foo"),
            SearchSource(1, "第二章", "b.xhtml", "foo")
        )
        val rule = TextReplaceRule(find = "foo", replacement = "", regex = false)
        val resolveLocation = { start: Int, end: Int, chapterIndex: Int, title: String ->
            TextSearchResultLocation(chapterIndex, "$title@$start-$end")
        }
        // 全量扫描的 id
        val fullIds = buildTextSearchResults(
            sources = sources, rule = rule, caseSensitive = false,
            ruleIndex = 0, idPrefix = "r", resolveLocation = resolveLocation
        ).map { it.id }
        // 分两批增量扫描的 id
        val batch1 = buildTextSearchResultsIncremental(
            sources = sources, rule = rule, caseSensitive = false,
            ruleIndex = 0, idPrefix = "r", cursor = null, targetCount = 2,
            resolveLocation = resolveLocation
        )
        val batch2 = buildTextSearchResultsIncremental(
            sources = sources, rule = rule, caseSensitive = false,
            ruleIndex = 0, idPrefix = "r", cursor = batch1.cursor, targetCount = 10,
            resolveLocation = resolveLocation
        )
        val incrementalIds = (batch1.results + batch2.results).map { it.id }
        assertEquals(fullIds, incrementalIds)
    }

    @Test
    fun rebuildTextSearchPreviewAfterSingleReplacementKeepsDisplayedAndTotalCountsSeparate() {
        val beforeSources = listOf(
            SearchSource(0, "第一章", "a.xhtml", (1..99).joinToString(" ") { "foo" })
        )
        val afterSources = listOf(
            SearchSource(0, "第一章", "a.xhtml", "bar " + (1..98).joinToString(" ") { "foo" })
        )
        val rule = TextReplaceRule(find = "foo", replacement = "", regex = false)
        val resolveLocation = { start: Int, end: Int, chapterIndex: Int, title: String ->
            TextSearchResultLocation(chapterIndex, "$title@$start-$end")
        }
        val displayed = buildTextSearchResultsIncremental(
            sources = beforeSources,
            rule = rule,
            caseSensitive = false,
            ruleIndex = 0,
            idPrefix = "rule-0",
            cursor = null,
            targetCount = 30,
            resolveLocation = resolveLocation
        ).results

        val preview = rebuildTextSearchPreviewStateAfterSingleReplacement(
            rules = listOf(rule),
            sourceResolver = { _, _ -> afterSources },
            previousResults = displayed,
            previousRuleTotalCounts = mapOf(0 to 99),
            replacedId = displayed.first().id,
            replacedSourceIndex = 0,
            sourceStart = displayed.first().sourceStart,
            sourceEnd = displayed.first().sourceEnd,
            replacementDelta = 0,
            replacedRuleIndex = 0,
            shiftAllSources = false,
            caseSensitive = false,
            resolveLocation = resolveLocation
        )

        assertEquals(29, preview.results.size)
        assertEquals(displayed.drop(1).map { it.id }, preview.results.map { it.id })
        assertEquals(mapOf(0 to 98), preview.ruleTotalCounts)
        assertEquals(98, preview.totalCount)
        assertEquals(true, preview.cursors.containsKey(0))
    }

    @Test
    fun rebuildTextSearchPreviewAfterSingleReplacementRebuildsWhenAdjacentMatchesShift() {
        val beforeSources = listOf(SearchSource(0, "Chapter", "a.xhtml", "aaaa"))
        val afterSources = listOf(SearchSource(0, "Chapter", "a.xhtml", "aaa"))
        val rule = TextReplaceRule(find = "aa", replacement = "a", regex = false)
        val resolveLocation = { start: Int, end: Int, chapterIndex: Int, title: String ->
            TextSearchResultLocation(chapterIndex, "$title@$start-$end")
        }
        val displayed = buildTextSearchResultsIncremental(
            sources = beforeSources,
            rule = rule,
            caseSensitive = false,
            ruleIndex = 0,
            idPrefix = "rule-0",
            cursor = null,
            targetCount = 2,
            resolveLocation = resolveLocation
        ).results

        val preview = rebuildTextSearchPreviewStateAfterSingleReplacement(
            rules = listOf(rule),
            sourceResolver = { _, _ -> afterSources },
            previousResults = displayed,
            previousRuleTotalCounts = mapOf(0 to 2),
            replacedId = displayed.first().id,
            replacedSourceIndex = 0,
            sourceStart = displayed.first().sourceStart,
            sourceEnd = displayed.first().sourceEnd,
            replacementDelta = -1,
            replacedRuleIndex = 0,
            shiftAllSources = false,
            caseSensitive = false,
            resolveLocation = resolveLocation
        )

        assertEquals(1, preview.results.size)
        assertEquals(0, preview.results.single().sourceStart)
        assertEquals(2, preview.results.single().sourceEnd)
        assertEquals("aa", preview.results.single().matchText)
        assertEquals(1, preview.totalCount)
    }
}
