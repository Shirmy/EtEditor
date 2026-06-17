package com.eteditor

import com.eteditor.core.DocumentKind
import com.eteditor.core.TxtChapter
import com.eteditor.core.TxtDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextSourceLocationUtilsTest {
    @Test
    fun selectedTxtPreviewLocationPrefersSelectedSearchResult() {
        val location = selectedTxtPreviewLocationModel(
            kind = DocumentKind.Txt,
            selectedTextSearchResultId = "result-1",
            textSearchResults = listOf(searchResult("result-1", chapterIndex = 2, start = 10, end = 15)),
            selectedReplacementPreviewMatchId = "match-1",
            replacementFilePreview = previewWithMatch("match-1", chapterIndex = 3)
        )

        assertEquals(TextSourceLocation(chapterIndex = 2, sourceStart = 10, sourceEnd = 15), location)
    }

    @Test
    fun selectedTxtPreviewLocationFallsBackToReplacementPreviewMatch() {
        val location = selectedTxtPreviewLocationModel(
            kind = DocumentKind.Txt,
            selectedTextSearchResultId = null,
            textSearchResults = emptyList(),
            selectedReplacementPreviewMatchId = "match-1",
            replacementFilePreview = previewWithMatch("match-1", chapterIndex = 3)
        )

        assertEquals(TextSourceLocation(chapterIndex = 3, sourceStart = 20, sourceEnd = 25), location)
        assertNull(
            selectedTxtPreviewLocationModel(
                kind = DocumentKind.Epub,
                selectedTextSearchResultId = "result-1",
                textSearchResults = listOf(searchResult("result-1", 0, 1, 2)),
                selectedReplacementPreviewMatchId = null,
                replacementFilePreview = null
            )
        )
    }

    @Test
    fun selectedTxtPreviewLocationCanUseSingleReplacementPreviewMatch() {
        val location = selectedTxtPreviewLocationModel(
            kind = DocumentKind.Txt,
            selectedTextSearchResultId = null,
            textSearchResults = emptyList(),
            selectedReplacementPreviewMatchId = "single-match",
            replacementFilePreview = ReplacementFilePreview(
                toolId = "tool",
                totalRules = 1,
                multiRules = emptyList(),
                singleRules = listOf(
                    ReplacementPreviewRule(
                        id = "rule",
                        lineNo = 1,
                        pattern = "foo",
                        replacement = "bar",
                        regex = false,
                        matches = listOf(
                            ReplacementPreviewMatch(
                                id = "single-match",
                                chapterIndex = 4,
                                chapterTitle = "章节",
                                fileName = "book.txt",
                                context = "foo",
                                matchText = "foo",
                                contextMatchStart = 0,
                                contextMatchEnd = 3,
                                sourceStart = 30,
                                sourceEnd = 33,
                                replacementText = "bar"
                            )
                        )
                    )
                ),
                zeroRules = emptyList(),
                skippedRules = emptyList()
            )
        )

        assertEquals(TextSourceLocation(chapterIndex = 4, sourceStart = 30, sourceEnd = 33), location)
    }

    @Test
    fun selectedTxtPreviewLocationIgnoresZeroRuleReplacementMatches() {
        assertNull(
            selectedTxtPreviewLocationModel(
                kind = DocumentKind.Txt,
                selectedTextSearchResultId = null,
                textSearchResults = emptyList(),
                selectedReplacementPreviewMatchId = "zero-match",
                replacementFilePreview = previewWithZeroRuleMatch("zero-match", chapterIndex = 5)
            )
        )
    }

    @Test
    fun selectedTxtPreviewLocationReturnsNullForStaleSelectionIds() {
        assertNull(
            selectedTxtPreviewLocationModel(
                kind = DocumentKind.Txt,
                selectedTextSearchResultId = "missing-result",
                textSearchResults = listOf(searchResult("result-1", chapterIndex = 0, start = 1, end = 2)),
                selectedReplacementPreviewMatchId = "missing-match",
                replacementFilePreview = previewWithMatch("match-1", chapterIndex = 1)
            )
        )
    }

    @Test
    fun selectedTxtPreviewLocationFallsBackToReplacementWhenSearchSelectionIsStale() {
        val location = selectedTxtPreviewLocationModel(
            kind = DocumentKind.Txt,
            selectedTextSearchResultId = "missing-result",
            textSearchResults = listOf(searchResult("result-1", chapterIndex = 0, start = 1, end = 2)),
            selectedReplacementPreviewMatchId = "match-1",
            replacementFilePreview = previewWithMatch("match-1", chapterIndex = 3)
        )

        assertEquals(TextSourceLocation(chapterIndex = 3, sourceStart = 20, sourceEnd = 25), location)
    }

    @Test
    fun textSearchResultLocationMapsOffsetsToTxtChaptersAndPreface() {
        val text = "前言\n第1章 开始\n正文\n第2章 继续\n正文"
        val firstStart = text.indexOf("第1章")
        val secondStart = text.indexOf("第2章")
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = listOf(
                TxtChapter(1, 1, 3, "第1章 开始", 2, startIndex = firstStart, endIndex = secondStart),
                TxtChapter(2, 3, 5, "第2章 继续", 2, startIndex = secondStart, endIndex = text.length)
            )
        )

        val preface = textSearchResultLocationForDocument(
            kind = DocumentKind.Txt,
            document = document,
            absoluteStart = 0,
            absoluteEnd = 2,
            fallbackChapterIndex = 9,
            fallbackTitle = "Fallback",
            prefaceEndIndex = firstStart
        )
        val crossChapter = textSearchResultLocationForDocument(
            kind = DocumentKind.Txt,
            document = document,
            absoluteStart = firstStart,
            absoluteEnd = secondStart + 2,
            fallbackChapterIndex = 9,
            fallbackTitle = "Fallback",
            prefaceEndIndex = firstStart
        )

        assertEquals(TextSearchResultLocation(TXT_PREFACE_CHAPTER_INDEX, "前言"), preface)
        assertEquals(TextSearchResultLocation(0, "第1章 开始 -> 第2章 继续"), crossChapter)
    }

    @Test
    fun textSearchResultLocationTreatsEndOffsetAsExclusiveAtChapterBoundary() {
        val text = "前言\n第1章 开始\n正文\n第2章 继续\n正文"
        val firstStart = text.indexOf("第1章")
        val secondStart = text.indexOf("第2章")
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = listOf(
                TxtChapter(1, 1, 3, "第1章 开始", 2, startIndex = firstStart, endIndex = secondStart),
                TxtChapter(2, 3, 5, "第2章 继续", 2, startIndex = secondStart, endIndex = text.length)
            )
        )

        assertEquals(
            TextSearchResultLocation(TXT_PREFACE_CHAPTER_INDEX, "前言"),
            textSearchResultLocationForDocument(
                kind = DocumentKind.Txt,
                document = document,
                absoluteStart = 0,
                absoluteEnd = firstStart,
                fallbackChapterIndex = 9,
                fallbackTitle = "Fallback",
                prefaceEndIndex = firstStart
            )
        )
        assertEquals(
            TextSearchResultLocation(0, "第1章 开始"),
            textSearchResultLocationForDocument(
                kind = DocumentKind.Txt,
                document = document,
                absoluteStart = firstStart,
                absoluteEnd = secondStart,
                fallbackChapterIndex = 9,
                fallbackTitle = "Fallback",
                prefaceEndIndex = firstStart
            )
        )
    }

    @Test
    fun textSearchResultLocationUsesFallbackForEmptyTxtAndBeforeFirstChapterWithoutPreface() {
        val emptyDocument = TxtDocument(
            originalName = "empty.txt",
            text = "正文",
            encoding = "UTF-8",
            chapters = emptyList()
        )
        val text = "前置文本\n第1章\n正文"
        val firstStart = text.indexOf("第1章")
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = listOf(
                TxtChapter(1, 1, 3, "第1章", 2, startIndex = firstStart, endIndex = text.length)
            )
        )

        assertEquals(
            TextSearchResultLocation(7, "Fallback"),
            textSearchResultLocationForDocument(
                kind = DocumentKind.Txt,
                document = emptyDocument,
                absoluteStart = 0,
                absoluteEnd = 1,
                fallbackChapterIndex = 7,
                fallbackTitle = "Fallback",
                prefaceEndIndex = null
            )
        )
        assertEquals(
            TextSearchResultLocation(7, "Fallback"),
            textSearchResultLocationForDocument(
                kind = DocumentKind.Txt,
                document = document,
                absoluteStart = 0,
                absoluteEnd = 2,
                fallbackChapterIndex = 7,
                fallbackTitle = "Fallback",
                prefaceEndIndex = null
            )
        )
    }

    @Test
    fun textSearchResultLocationUsesDefaultTitleForBlankChapterTitle() {
        val text = "第1章\n正文\n第2章\n正文"
        val firstStart = text.indexOf("第1章")
        val secondStart = text.indexOf("第2章")
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = listOf(
                TxtChapter(1, 0, 2, "", 2, startIndex = firstStart, endIndex = secondStart),
                TxtChapter(2, 2, 4, "第二章", 2, startIndex = secondStart, endIndex = text.length)
            )
        )

        assertEquals(
            TextSearchResultLocation(0, "第 1 章"),
            textSearchResultLocationForDocument(
                kind = DocumentKind.Txt,
                document = document,
                absoluteStart = firstStart,
                absoluteEnd = firstStart + 2,
                fallbackChapterIndex = 9,
                fallbackTitle = "Fallback",
                prefaceEndIndex = null
            )
        )
    }

    @Test
    fun textSearchResultLocationCoercesOutOfRangeOffsetsToLastChapter() {
        val text = "第1章\n正文\n第2章\n正文"
        val firstStart = text.indexOf("第1章")
        val secondStart = text.indexOf("第2章")
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = listOf(
                TxtChapter(1, 0, 2, "第一章", 2, startIndex = firstStart, endIndex = secondStart),
                TxtChapter(2, 2, 4, "第二章", 2, startIndex = secondStart, endIndex = text.length)
            )
        )

        assertEquals(
            TextSearchResultLocation(1, "第二章"),
            textSearchResultLocationForDocument(
                kind = DocumentKind.Txt,
                document = document,
                absoluteStart = text.length + 20,
                absoluteEnd = text.length + 30,
                fallbackChapterIndex = 9,
                fallbackTitle = "Fallback",
                prefaceEndIndex = firstStart
            )
        )
    }

    @Test
    fun textSearchResultLocationUsesFallbackForNonTxtDocuments() {
        assertEquals(
            TextSearchResultLocation(5, "Fallback"),
            textSearchResultLocationForDocument(
                kind = DocumentKind.Epub,
                document = null,
                absoluteStart = 0,
                absoluteEnd = 1,
                fallbackChapterIndex = 5,
                fallbackTitle = "Fallback",
                prefaceEndIndex = null
            )
        )
    }

    @Test
    fun txtCurrentChapterIndexForLocationResolvesSearchLocationToCurrentChapter() {
        val text = "Intro\nChapter 1\nBody 1\nChapter 2\nBody 2"
        val firstStart = text.indexOf("Chapter 1")
        val secondStart = text.indexOf("Chapter 2")
        val document = TxtDocument(
            originalName = "book.txt",
            text = text,
            encoding = "UTF-8",
            chapters = listOf(
                TxtChapter(1, 1, 3, "Chapter 1", 2, startIndex = firstStart, endIndex = secondStart),
                TxtChapter(2, 3, 5, "Chapter 2", 2, startIndex = secondStart, endIndex = text.length)
            )
        )

        assertEquals(
            1,
            txtCurrentChapterIndexForLocation(
                document = document,
                requestedChapterIndex = 9,
                absoluteStart = text.indexOf("Body 2")
            )
        )
    }

    @Test
    fun txtCurrentChapterIndexForLocationResolvesPrefaceAndUnchapteredText() {
        val prefaceText = "Intro\nChapter 1\nBody 1"
        val firstStart = prefaceText.indexOf("Chapter 1")
        val documentWithPreface = TxtDocument(
            originalName = "book.txt",
            text = prefaceText,
            encoding = "UTF-8",
            chapters = listOf(
                TxtChapter(1, 1, 3, "Chapter 1", 2, startIndex = firstStart, endIndex = prefaceText.length)
            )
        )
        val documentWithoutChapters = TxtDocument(
            originalName = "plain.txt",
            text = "Plain body",
            encoding = "UTF-8",
            chapters = emptyList()
        )

        assertEquals(
            TXT_PREFACE_CHAPTER_INDEX,
            txtCurrentChapterIndexForLocation(
                document = documentWithPreface,
                requestedChapterIndex = 0,
                absoluteStart = 0
            )
        )
        assertEquals(
            0,
            txtCurrentChapterIndexForLocation(
                document = documentWithoutChapters,
                requestedChapterIndex = 3,
                absoluteStart = 0
            )
        )
    }

    private fun searchResult(id: String, chapterIndex: Int, start: Int, end: Int): TextSearchResult {
        return TextSearchResult(
            id = id,
            ruleIndex = 0,
            chapterIndex = chapterIndex,
            chapterTitle = "章节",
            context = "",
            matchText = "",
            contextMatchStart = 0,
            contextMatchEnd = 0,
            sourceStart = start,
            sourceEnd = end
        )
    }

    private fun previewWithMatch(id: String, chapterIndex: Int): ReplacementFilePreview {
        return ReplacementFilePreview(
            toolId = "tool",
            totalRules = 1,
            multiRules = listOf(
                ReplacementPreviewRule(
                    id = "rule",
                    lineNo = 1,
                    pattern = "foo",
                    replacement = "bar",
                    regex = false,
                    matches = listOf(
                        ReplacementPreviewMatch(
                            id = id,
                            chapterIndex = chapterIndex,
                            chapterTitle = "章节",
                            fileName = "book.txt",
                            context = "foo",
                            matchText = "foo",
                            contextMatchStart = 0,
                            contextMatchEnd = 3,
                            sourceStart = 20,
                            sourceEnd = 25,
                            replacementText = "bar"
                        )
                    )
                )
            ),
            singleRules = emptyList(),
            zeroRules = emptyList(),
            skippedRules = emptyList()
        )
    }

    private fun previewWithZeroRuleMatch(id: String, chapterIndex: Int): ReplacementFilePreview {
        return ReplacementFilePreview(
            toolId = "tool",
            totalRules = 1,
            multiRules = emptyList(),
            singleRules = emptyList(),
            zeroRules = listOf(
                ReplacementPreviewRule(
                    id = "zero-rule",
                    lineNo = 1,
                    pattern = "foo",
                    replacement = "bar",
                    regex = false,
                    matches = listOf(
                        ReplacementPreviewMatch(
                            id = id,
                            chapterIndex = chapterIndex,
                            chapterTitle = "章节",
                            fileName = "book.txt",
                            context = "foo",
                            matchText = "foo",
                            contextMatchStart = 0,
                            contextMatchEnd = 3,
                            sourceStart = 40,
                            sourceEnd = 43,
                            replacementText = "bar"
                        )
                    )
                )
            ),
            skippedRules = emptyList()
        )
    }
}
