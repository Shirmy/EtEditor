package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.TxtChapter
import com.eteditor.core.TxtDocument
import org.junit.Assert.assertEquals
import org.junit.Test

class TxtCatalogWorkflowUtilsTest {
    @Test
    fun detectTxtChaptersWithCatalogConfigHidesLinesAndForcesSupplementedLines() {
        val text = "第1章 隐藏\n正文\n插曲\n正文二\n第2章 正常\n正文三"
        val config = chapterConfig(hiddenLineIndices = setOf(0))

        val chapters = detectTxtChaptersWithCatalogConfig(
            text = text,
            config = config,
            autoKeys = activeKeys(config),
            supplementedCatalogLines = emptyList(),
            forcedLineIndicesOverride = setOf(0, 2)
        )

        assertEquals(listOf("插曲", "第2章 正常"), chapters.map { it.title })
        assertEquals(listOf(2, 4), chapters.map { it.lineIndex })
        assertEquals(listOf(1, 2), chapters.map { it.index })
    }

    @Test
    fun detectTxtChaptersWithMappingsAppliedToTextCountsMappedTitles() {
        val rulesText = "章节\t^第\\s*(\\d+)\\s*章\\s*(.*)$\t第{index}章 ${'$'}2"
        val config = TxtChapterDetectionConfig(
            rulesText = rulesText,
            shortThreshold = 0,
            longThreshold = 10000,
            hiddenLineIndices = emptySet()
        )
        val key = txtChapterRuleKey(parseTxtChapterRuleItems(rulesText).single())

        val result = detectTxtChaptersWithMappingsAppliedToText(
            text = "第9章 开始\n正文\n第10章 继续\n正文",
            config = config,
            autoKeys = setOf(key),
            supplementedCatalogLines = emptyList()
        )

        assertEquals(listOf("第1章 开始", "第2章 继续"), result.chapters.map { it.title })
        assertEquals(2, result.mappedTitleCount)
    }

    @Test
    fun detectTxtChaptersWithAutoSelectedRulesUsesMatchingRuleOnly() {
        val text = "第1章 开始\n正文\n第2章 继续\n正文"
        val rulesText = "章节\t^第\\s*(\\d+)\\s*章.*$\t\n幕\t^第\\s*(\\d+)\\s*幕.*$\t"
        val config = TxtChapterDetectionConfig(
            rulesText = rulesText,
            shortThreshold = 0,
            longThreshold = 10000,
            hiddenLineIndices = emptySet()
        )
        val chapterKey = txtChapterRuleKey(parseTxtChapterRuleItems(rulesText).first())
        val calls = mutableListOf<Set<String>>()

        val result = detectTxtChaptersWithAutoSelectedRules(text, config) { keys ->
            calls += keys
            TxtCatalogDetectionResult(
                enabledKeys = keys,
                text = text,
                chapters = ChapterDetector.detectTxtChapters(
                    text = text,
                    shortThreshold = config.shortThreshold,
                    longThreshold = config.longThreshold,
                    customRules = activeTxtChapterPatternRules(config, keys)
                ),
                mappedTitleCount = 0
            )
        }

        assertEquals(setOf(chapterKey), result.enabledKeys)
        assertEquals(listOf(setOf(chapterKey)), calls)
        assertEquals(listOf("第1章 开始", "第2章 继续"), result.chapters.map { it.title })
    }

    @Test
    fun detectTxtChaptersWithAutoSelectedRulesFallsBackToEmptyKeysWhenNoRuleMatches() {
        val text = "正文第一行\n正文第二行"
        val config = TxtChapterDetectionConfig(
            rulesText = "章节\t^AA.*$\t",
            shortThreshold = 0,
            longThreshold = 10000,
            hiddenLineIndices = emptySet()
        )
        val calls = mutableListOf<Set<String>>()

        val result = detectTxtChaptersWithAutoSelectedRules(text, config) { keys ->
            calls += keys
            fakeCatalogResult(keys, text, chapter("正文第一行"))
        }

        assertEquals(listOf(emptySet<String>()), calls)
        assertEquals(emptySet<String>(), result.enabledKeys)
    }

    @Test
    fun detectTxtChaptersWithAutoSelectedRulesRejectsTrialThatIntroducesMissingChapter() {
        val text = "AA 开始\n正文\nBB 插曲\n正文"
        val config = TxtChapterDetectionConfig(
            rulesText = "A\t^AA.*$\t\nB\t^BB.*$\t",
            shortThreshold = 0,
            longThreshold = 10000,
            hiddenLineIndices = emptySet()
        )
        val ruleKeys = parseTxtChapterRuleItems(config.rulesText).map(::txtChapterRuleKey)
        val calls = mutableListOf<Set<String>>()

        val result = detectTxtChaptersWithAutoSelectedRules(text, config) { keys ->
            calls += keys
            when (keys) {
                setOf(ruleKeys[0]) -> fakeCatalogResult(
                    keys,
                    text,
                    chapter("AA 开始", status = listOf("超长章"), wordCount = 500)
                )
                setOf(ruleKeys[0], ruleKeys[1]) -> fakeCatalogResult(
                    keys,
                    text,
                    chapter("AA 开始"),
                    chapter("BB 插曲", status = listOf("疑似缺章"))
                )
                else -> fakeCatalogResult(keys, text)
            }
        }

        assertEquals(listOf(setOf(ruleKeys[0]), setOf(ruleKeys[0], ruleKeys[1])), calls)
        assertEquals(setOf(ruleKeys[0]), result.enabledKeys)
        assertEquals(listOf("超长章"), result.chapters.single().status)
    }

    @Test
    fun detectTxtChaptersWithAutoSelectedRulesKeepsAddingRulesUntilMissingClears() {
        val text = "AA 开始\n正文\nBB 插曲\n正文"
        val config = TxtChapterDetectionConfig(
            rulesText = "A\t^AA.*$\t\nB\t^BB.*$\t",
            shortThreshold = 0,
            longThreshold = 10000,
            hiddenLineIndices = emptySet()
        )
        val ruleKeys = parseTxtChapterRuleItems(config.rulesText).map(::txtChapterRuleKey)
        val calls = mutableListOf<Set<String>>()

        val result = detectTxtChaptersWithAutoSelectedRules(text, config) { keys ->
            calls += keys
            when (keys) {
                setOf(ruleKeys[0]) -> fakeCatalogResult(
                    keys,
                    text,
                    chapter("AA 开始", status = listOf("疑似缺章"))
                )
                setOf(ruleKeys[0], ruleKeys[1]) -> fakeCatalogResult(
                    keys,
                    text,
                    chapter("AA 开始"),
                    chapter("BB 插曲")
                )
                else -> fakeCatalogResult(keys, text)
            }
        }

        assertEquals(listOf(setOf(ruleKeys[0]), setOf(ruleKeys[0], ruleKeys[1])), calls)
        assertEquals(setOf(ruleKeys[0], ruleKeys[1]), result.enabledKeys)
        assertEquals(listOf("AA 开始", "BB 插曲"), result.chapters.map { it.title })
    }

    @Test
    fun detectTxtChaptersWithAutoSelectedRulesAcceptsTrialThatImprovesLongChapter() {
        val text = "AA 开始\n正文\nBB 插曲\n正文"
        val config = TxtChapterDetectionConfig(
            rulesText = "A\t^AA.*$\t\nB\t^BB.*$\t",
            shortThreshold = 0,
            longThreshold = 10000,
            hiddenLineIndices = emptySet()
        )
        val ruleKeys = parseTxtChapterRuleItems(config.rulesText).map(::txtChapterRuleKey)

        val result = detectTxtChaptersWithAutoSelectedRules(text, config) { keys ->
            when (keys) {
                setOf(ruleKeys[0]) -> fakeCatalogResult(
                    keys,
                    text,
                    chapter("AA 开始", status = listOf("超长章"), wordCount = 500)
                )
                setOf(ruleKeys[0], ruleKeys[1]) -> fakeCatalogResult(
                    keys,
                    text,
                    chapter("AA 开始", wordCount = 100),
                    chapter("BB 插曲", wordCount = 80)
                )
                else -> fakeCatalogResult(keys, text)
            }
        }

        assertEquals(setOf(ruleKeys[0], ruleKeys[1]), result.enabledKeys)
        assertEquals(listOf("AA 开始", "BB 插曲"), result.chapters.map { it.title })
    }

    @Test
    fun formatTxtLayoutFromCurrentCatalogIndentsContentAndRebuildsChapterOffsets() {
        val text = "  第1章 开始  \n\n正文一\n  第2章 继续\n正文二"
        val config = chapterConfig()
        val chapters = detect(text, config)

        val result = formatTxtLayoutFromCurrentCatalog(text, chapters, config)

        assertEquals("第1章 开始\r\n　　正文一\r\n\r\n\r\n第2章 继续\r\n　　正文二", result.text)
        assertEquals(1, result.removedBlankCount)
        assertEquals(2, result.contentLineCount)
        assertEquals(2, result.chapterLineCount)
        assertEquals(listOf("第1章 开始", "第2章 继续"), result.chapters.map { it.title })
        assertEquals(listOf(0, 4), result.chapters.map { it.lineIndex })
    }

    @Test
    fun formatTxtLayoutFromCurrentCatalogHandlesTextWithoutDetectedChapters() {
        val result = formatTxtLayoutFromCurrentCatalog(
            text = "正文一\n\n正文二",
            chapters = emptyList(),
            config = chapterConfig()
        )

        assertEquals("　　正文一\r\n　　正文二", result.text)
        assertEquals(emptyList<TxtChapter>(), result.chapters)
        assertEquals(1, result.removedBlankCount)
        assertEquals(2, result.contentLineCount)
        assertEquals(0, result.chapterLineCount)
    }

    @Test
    fun formatTxtLayoutFromCurrentCatalogTrimsCrLfInputAndNormalizesChapterSeparators() {
        val text = "  第1章 开始  \r\n  正文一  \r\n\r\n\r\n  第2章 继续  \r\n正文二  "
        val config = chapterConfig()
        val chapters = detect(text, config)

        val result = formatTxtLayoutFromCurrentCatalog(text, chapters, config)

        assertEquals("第1章 开始\r\n　　正文一\r\n\r\n\r\n第2章 继续\r\n　　正文二", result.text)
        assertEquals(2, result.removedBlankCount)
        assertEquals(2, result.contentLineCount)
        assertEquals(2, result.chapterLineCount)
        assertEquals(listOf("第1章 开始", "第2章 继续"), result.chapters.map { it.title })
        assertEquals(listOf(0, 4), result.chapters.map { it.lineIndex })
    }

    @Test
    fun formatTxtLayoutFromCurrentCatalogSeparatesConsecutiveChapterLines() {
        val text = "第1章 开始\n第2章 继续\n正文二"
        val config = chapterConfig()
        val chapters = detect(text, config)

        val result = formatTxtLayoutFromCurrentCatalog(text, chapters, config)

        assertEquals("第1章 开始\r\n\r\n\r\n第2章 继续\r\n　　正文二", result.text)
        assertEquals(0, result.removedBlankCount)
        assertEquals(1, result.contentLineCount)
        assertEquals(2, result.chapterLineCount)
        assertEquals(listOf("第1章 开始", "第2章 继续"), result.chapters.map { it.title })
        assertEquals(listOf(0, 3), result.chapters.map { it.lineIndex })
    }

    @Test
    fun formatTxtLayoutFromCurrentCatalogKeepsMappedChapterTitlesInRebuiltCatalog() {
        val text = "第9章 原始\n正文一\n第10章 原始二\n正文二"
        val chapters = listOf(
            TxtChapter(index = 1, lineIndex = 0, endLineIndex = 2, title = "第1章 映射后", wordCount = 3),
            TxtChapter(index = 2, lineIndex = 2, endLineIndex = 4, title = "第2章 映射后", wordCount = 3)
        )

        val result = formatTxtLayoutFromCurrentCatalog(text, chapters, chapterConfig())

        assertEquals("第9章 原始\r\n　　正文一\r\n\r\n\r\n第10章 原始二\r\n　　正文二", result.text)
        assertEquals(listOf("第1章 映射后", "第2章 映射后"), result.chapters.map { it.title })
        assertEquals(listOf(0, 4), result.chapters.map { it.lineIndex })
    }

    @Test
    fun catalogMarkersRemapAndRestoreNormalizedHiddenAndSupplementedLines() {
        val document = TxtDocument(
            originalName = "book.txt",
            text = "第1章 A\n正文\n第2章 B\n正文\n第3章 C",
            encoding = "UTF-8",
            chapters = emptyList()
        )
        val markers = txtHiddenCatalogMarkers(document, setOf(0, 2, 99))

        assertEquals(
            listOf(TxtHiddenCatalogMarker(0, "第1章 A"), TxtHiddenCatalogMarker(2, "第2章 B")),
            markers
        )
        assertEquals(
            setOf(2, 3),
            remapTxtHiddenCatalogLineIndices("前言\n第2章 B\n第1章 A\n第2章 B", markers)
        )

        val remapped = remapTxtSupplementedCatalogLines(
            text = "第1章 A\n第2章 B 补\n正文\n第2章 B 补",
            records = listOf(TxtSupplementedCatalogLine(5, "第2章 B", "第2章 B 补"))
        )
        val restored = restoreTxtSupplementedCatalogLinesInText(
            text = "第1章 A\n第2章 B 补\n正文\n第2章 B 补",
            records = remapped
        )

        assertEquals(listOf(TxtSupplementedCatalogLine(1, "第2章 B", "第2章 B 补")), remapped)
        assertEquals("第1章 A\n第2章 B\n正文\n第2章 B 补", restored.first)
        assertEquals(1, restored.second)
    }

    @Test
    fun catalogMarkersSkipBlankMissingAndAmbiguousSupplementedLines() {
        val document = TxtDocument(
            originalName = "book.txt",
            text = "\n第1章 A\n正文",
            encoding = "UTF-8",
            chapters = emptyList()
        )
        val markers = txtHiddenCatalogMarkers(document, setOf(0, 5))
        val remapped = remapTxtSupplementedCatalogLines(
            text = "第1章 A 补\n正文\n第1章 A 补",
            records = listOf(
                TxtSupplementedCatalogLine(9, "第1章 A", "第1章 A 补"),
                TxtSupplementedCatalogLine(9, "第1章 A", "第1章 A 补"),
                TxtSupplementedCatalogLine(0, "空", " ")
            )
        )
        val ambiguousRestore = restoreTxtSupplementedCatalogLinesInText(
            text = "第1章 A 补\n正文\n第1章 A 补",
            records = listOf(TxtSupplementedCatalogLine(9, "第1章 A", "第1章 A 补"))
        )

        assertEquals(emptyList<TxtHiddenCatalogMarker>(), markers)
        assertEquals(
            listOf(
                TxtSupplementedCatalogLine(0, "第1章 A", "第1章 A 补"),
                TxtSupplementedCatalogLine(2, "第1章 A", "第1章 A 补")
            ),
            remapped
        )
        assertEquals("第1章 A 补\n正文\n第1章 A 补", ambiguousRestore.first)
        assertEquals(0, ambiguousRestore.second)
    }

    @Test
    fun catalogMarkersUseDirectLineIndexWhenSupplementedLineIsDuplicated() {
        val record = TxtSupplementedCatalogLine(
            lineIndex = 2,
            originalLine = "第1章 A",
            supplementedLine = "第1章 A 补"
        )
        val text = "第1章 A 补\n正文\n第1章 A 补"

        val remapped = remapTxtSupplementedCatalogLines(text, listOf(record))
        val restored = restoreTxtSupplementedCatalogLinesInText(text, remapped)

        assertEquals(listOf(record), remapped)
        assertEquals("第1章 A 补\n正文\n第1章 A", restored.first)
        assertEquals(1, restored.second)
    }

    private fun chapterConfig(
        hiddenLineIndices: Set<Int> = emptySet()
    ): TxtChapterDetectionConfig {
        return TxtChapterDetectionConfig(
            rulesText = "章节\t^第\\s*(\\d+)\\s*章.*$\t",
            shortThreshold = 0,
            longThreshold = 10000,
            hiddenLineIndices = hiddenLineIndices
        )
    }

    private fun activeKeys(config: TxtChapterDetectionConfig): Set<String> {
        return parseTxtChapterRuleItems(config.rulesText)
            .mapTo(mutableSetOf(), ::txtChapterRuleKey)
    }

    private fun detect(text: String, config: TxtChapterDetectionConfig): List<TxtChapter> {
        return ChapterDetector.detectTxtChapters(
            text = text,
            shortThreshold = config.shortThreshold,
            longThreshold = config.longThreshold,
            customRules = activeTxtChapterPatternRules(config, activeKeys(config)),
            hiddenLineIndices = config.hiddenLineIndices
        )
    }

    private fun fakeCatalogResult(
        keys: Set<String>,
        text: String,
        vararg chapters: TxtChapter
    ): TxtCatalogDetectionResult {
        return TxtCatalogDetectionResult(
            enabledKeys = keys,
            text = text,
            chapters = chapters.toList(),
            mappedTitleCount = 0
        )
    }

    private fun chapter(
        title: String,
        status: List<String> = emptyList(),
        wordCount: Int = 100
    ): TxtChapter {
        return TxtChapter(
            index = 1,
            lineIndex = 0,
            endLineIndex = 0,
            title = title,
            wordCount = wordCount,
            status = status
        )
    }
}
