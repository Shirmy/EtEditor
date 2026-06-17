package com.eteditor

import com.eteditor.core.DocumentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRuntimeParameterUtilsTest {
    @Test
    fun parseIndexSetKeepsOnlyNonNegativeNumbers() {
        assertEquals(setOf(0, 2, 5), parseIndexSet("0, -1, 2, bad, 5, 2"))
    }

    @Test
    fun buildTextReplaceParametersForTxtForcesSingleSourceModeAndTxtScope() {
        val parameters = buildTextReplaceParameters(
            values = mapOf(
                TEXT_REPLACE_PARAM_MODE to TEXT_REPLACE_MODE_BATCH,
                TEXT_REPLACE_PARAM_TARGET to TEXT_REPLACE_TARGET_VISIBLE,
                TEXT_REPLACE_PARAM_SCOPE to TOOL_SCOPE_CURRENT,
                TEXT_REPLACE_PARAM_FIND to "foo",
                TEXT_REPLACE_PARAM_REPLACE to "bar",
                TEXT_REPLACE_PARAM_FIND_REGEX to BOOL_TRUE,
                TEXT_REPLACE_PARAM_PREVIEW to BOOL_FALSE
            ),
            kind = DocumentKind.Txt,
            runtimeFile = "",
            batchSourceOptions = batchSourceOptions(),
            textReplaceScopeOptions = epubSingleScopeOptions(),
            epubBatchScopeOptions = epubBatchScopeOptions(),
            txtScopeOptions = txtScopeOptions(),
            defaultBatchSource = TEXT_REPLACE_MODE_BATCH,
            trueValue = BOOL_TRUE,
            falseValue = BOOL_FALSE
        )

        assertEquals(TEXT_REPLACE_MODE_SINGLE, parameters.mode)
        assertEquals(TEXT_REPLACE_TARGET_SOURCE, parameters.target)
        assertEquals(TOOL_SCOPE_CURRENT, parameters.scope)
        assertEquals("foo", parameters.findText)
        assertEquals("bar", parameters.replaceText)
        assertTrue(parameters.findRegexEnabled)
        assertFalse(parameters.preview)
    }

    @Test
    fun buildTextReplaceParametersForEpubNormalizesLegacyReplacementModeAndRuntimeFile() {
        val parameters = buildTextReplaceParameters(
            values = mapOf(
                TEXT_REPLACE_PARAM_MODE to TEXT_REPLACE_MODE_BATCH,
                TEXT_REPLACE_PARAM_BATCH_SOURCE to TEXT_REPLACE_BATCH_FILE,
                TEXT_REPLACE_PARAM_TARGET to TEXT_REPLACE_TARGET_VISIBLE,
                TEXT_REPLACE_PARAM_SCOPE to TEXT_REPLACE_SCOPE_INTRO,
                TEXT_REPLACE_PARAM_BATCH_FILE to "stored.replacement",
                TEXT_REPLACE_PARAM_PREVIEW to BOOL_FALSE
            ),
            kind = DocumentKind.Epub,
            runtimeFile = "runtime.replacement",
            batchSourceOptions = batchSourceOptions(),
            textReplaceScopeOptions = epubSingleScopeOptions(),
            epubBatchScopeOptions = epubBatchScopeOptions(),
            txtScopeOptions = txtScopeOptions(),
            defaultBatchSource = TEXT_REPLACE_MODE_BATCH,
            trueValue = BOOL_TRUE,
            falseValue = BOOL_FALSE
        )

        assertEquals(TEXT_REPLACE_MODE_REPLACEMENT, parameters.mode)
        assertEquals(TEXT_REPLACE_BATCH_FILE, parameters.batchSource)
        assertEquals(TOOL_SCOPE_ALL, parameters.scope)
        assertEquals("runtime.replacement", parameters.batchFile)
        assertEquals(TEXT_REPLACE_TARGET_VISIBLE, parameters.target)
        assertTrue(parameters.preview)
        assertEquals(TEXT_REPLACE_TARGET_SOURCE, effectiveTextReplaceParametersForRun(parameters).target)
    }

    @Test
    fun buildTextReplaceParametersForEpubReplacementModeForcesFileSourceAndStoredFileFallback() {
        val parameters = buildTextReplaceParameters(
            values = mapOf(
                TEXT_REPLACE_PARAM_MODE to TEXT_REPLACE_MODE_REPLACEMENT,
                TEXT_REPLACE_PARAM_BATCH_SOURCE to TEXT_REPLACE_MODE_BATCH,
                TEXT_REPLACE_PARAM_TARGET to TEXT_REPLACE_TARGET_VISIBLE,
                TEXT_REPLACE_PARAM_SCOPE to TOOL_SCOPE_CURRENT,
                TEXT_REPLACE_PARAM_BATCH_FILE to "stored.replacement",
                TEXT_REPLACE_PARAM_PREVIEW to BOOL_FALSE
            ),
            kind = DocumentKind.Epub,
            runtimeFile = "",
            batchSourceOptions = batchSourceOptions(),
            textReplaceScopeOptions = epubSingleScopeOptions(),
            epubBatchScopeOptions = epubBatchScopeOptions(),
            txtScopeOptions = txtScopeOptions(),
            defaultBatchSource = TEXT_REPLACE_MODE_BATCH,
            trueValue = BOOL_TRUE,
            falseValue = BOOL_FALSE
        )

        assertEquals(TEXT_REPLACE_MODE_REPLACEMENT, parameters.mode)
        assertEquals(TEXT_REPLACE_BATCH_FILE, parameters.batchSource)
        assertEquals(TOOL_SCOPE_ALL, parameters.scope)
        assertEquals("stored.replacement", parameters.batchFile)
        assertEquals(TEXT_REPLACE_TARGET_VISIBLE, parameters.target)
        assertTrue(parameters.preview)
        assertEquals(TEXT_REPLACE_TARGET_SOURCE, effectiveTextReplaceParametersForRun(parameters).target)
    }

    @Test
    fun effectiveTextReplaceParametersForRunLeavesNonReplacementModeUnchanged() {
        val parameters = buildTextReplaceParameters(
            values = mapOf(
                TEXT_REPLACE_PARAM_MODE to TEXT_REPLACE_MODE_SINGLE,
                TEXT_REPLACE_PARAM_TARGET to TEXT_REPLACE_TARGET_VISIBLE,
                TEXT_REPLACE_PARAM_SCOPE to TOOL_SCOPE_CURRENT,
                TEXT_REPLACE_PARAM_PREVIEW to BOOL_FALSE
            ),
            kind = DocumentKind.Epub,
            runtimeFile = "",
            batchSourceOptions = batchSourceOptions(),
            textReplaceScopeOptions = epubSingleScopeOptions(),
            epubBatchScopeOptions = epubBatchScopeOptions(),
            txtScopeOptions = txtScopeOptions(),
            defaultBatchSource = TEXT_REPLACE_MODE_BATCH,
            trueValue = BOOL_TRUE,
            falseValue = BOOL_FALSE
        )

        assertEquals(parameters, effectiveTextReplaceParametersForRun(parameters))
        assertEquals(TEXT_REPLACE_TARGET_VISIBLE, effectiveTextReplaceParametersForRun(parameters).target)
    }

    @Test
    fun buildTextReplaceParametersForEpubSingleModeFallsBackInvalidTargetScopeAndBatchSource() {
        val parameters = buildTextReplaceParameters(
            values = mapOf(
                TEXT_REPLACE_PARAM_MODE to TEXT_REPLACE_MODE_SINGLE,
                TEXT_REPLACE_PARAM_TARGET to "bad-target",
                TEXT_REPLACE_PARAM_SCOPE to "bad-scope",
                TEXT_REPLACE_PARAM_BATCH_SOURCE to "bad-source",
                TEXT_REPLACE_PARAM_PREVIEW to BOOL_FALSE
            ),
            kind = DocumentKind.Epub,
            runtimeFile = "",
            batchSourceOptions = batchSourceOptions(),
            textReplaceScopeOptions = epubSingleScopeOptions(),
            epubBatchScopeOptions = epubBatchScopeOptions(),
            txtScopeOptions = txtScopeOptions(),
            defaultBatchSource = TEXT_REPLACE_MODE_BATCH,
            trueValue = BOOL_TRUE,
            falseValue = BOOL_FALSE
        )

        assertEquals(TEXT_REPLACE_MODE_SINGLE, parameters.mode)
        assertEquals(TEXT_REPLACE_TARGET_SOURCE, parameters.target)
        assertEquals(TOOL_SCOPE_ALL, parameters.scope)
        assertEquals(TEXT_REPLACE_MODE_BATCH, parameters.batchSource)
        assertFalse(parameters.preview)
    }

    @Test
    fun buildTextReplaceParametersForEpubBatchModeFallsBackInvalidBatchScope() {
        val parameters = buildTextReplaceParameters(
            values = mapOf(
                TEXT_REPLACE_PARAM_MODE to TEXT_REPLACE_MODE_BATCH,
                TEXT_REPLACE_PARAM_BATCH_SOURCE to "bad-source",
                TEXT_REPLACE_PARAM_SCOPE to TEXT_REPLACE_SCOPE_INTRO,
                TEXT_REPLACE_PARAM_BATCH_TEXT to "foo=>bar",
                TEXT_REPLACE_PARAM_PREVIEW to BOOL_FALSE
            ),
            kind = DocumentKind.Epub,
            runtimeFile = "",
            batchSourceOptions = batchSourceOptions(),
            textReplaceScopeOptions = epubSingleScopeOptions(),
            epubBatchScopeOptions = epubBatchScopeOptions(),
            txtScopeOptions = txtScopeOptions(),
            defaultBatchSource = TEXT_REPLACE_MODE_BATCH,
            trueValue = BOOL_TRUE,
            falseValue = BOOL_FALSE
        )

        assertEquals(TEXT_REPLACE_MODE_BATCH, parameters.mode)
        assertEquals(TEXT_REPLACE_MODE_BATCH, parameters.batchSource)
        assertEquals(TOOL_SCOPE_ALL, parameters.scope)
        assertEquals("foo=>bar", parameters.batchText)
        assertFalse(parameters.preview)
    }

    @Test
    fun buildTextReplaceParametersForEpubBatchModeAcceptsIntroScopeFromRuntimeOptions() {
        val parameters = buildTextReplaceParameters(
            values = mapOf(
                TEXT_REPLACE_PARAM_MODE to TEXT_REPLACE_MODE_BATCH,
                TEXT_REPLACE_PARAM_BATCH_SOURCE to TEXT_REPLACE_BATCH_INPUT,
                TEXT_REPLACE_PARAM_SCOPE to TEXT_REPLACE_SCOPE_INTRO,
                TEXT_REPLACE_PARAM_BATCH_TEXT to "简介=>intro"
            ),
            kind = DocumentKind.Epub,
            runtimeFile = "",
            batchSourceOptions = TEXT_REPLACE_BATCH_SOURCE_OPTIONS,
            textReplaceScopeOptions = TEXT_REPLACE_SCOPE_OPTIONS,
            epubBatchScopeOptions = EPUB_TEXT_REPLACE_BATCH_SCOPE_OPTIONS,
            txtScopeOptions = TXT_TEXT_REPLACE_SCOPE_OPTIONS,
            defaultBatchSource = TEXT_REPLACE_BATCH_INPUT,
            trueValue = BOOL_TRUE,
            falseValue = BOOL_FALSE
        )

        assertEquals(TEXT_REPLACE_MODE_BATCH, parameters.mode)
        assertEquals(TEXT_REPLACE_BATCH_INPUT, parameters.batchSource)
        assertEquals(TEXT_REPLACE_SCOPE_INTRO, parameters.scope)
        assertEquals("简介=>intro", parameters.batchText)
        assertTrue(parameters.preview)
    }

    @Test
    fun buildFileRenameParametersFallsBackFormatAndPreviewFlag() {
        val parameters = buildFileRenameParameters(
            values = mapOf(
                FILE_RENAME_PARAM_NAMING_FORMAT to " ",
                FILE_RENAME_PARAM_PREVIEW to BOOL_FALSE
            ),
            falseValue = BOOL_FALSE
        )

        assertEquals(DEFAULT_FILE_RENAME_PATTERN, parameters.namingFormat)
        assertFalse(parameters.preview)
    }

    @Test
    fun buildTitleRenameParametersFallsBackScopeAndBooleanFlags() {
        val parameters = buildTitleRenameParameters(
            values = mapOf(
                TITLE_RENAME_PARAM_PATTERN to "{index}-{title}",
                TITLE_RENAME_PARAM_SCOPE to "bad-scope",
                TITLE_RENAME_PARAM_MATCH_PATTERN to "Chapter\\d+",
                TITLE_RENAME_PARAM_MATCH_REGEX to BOOL_FALSE,
                TITLE_RENAME_PARAM_PREVIEW to BOOL_FALSE
            ),
            toolScopes = setOf(TOOL_SCOPE_ALL, TOOL_SCOPE_CURRENT),
            falseValue = BOOL_FALSE
        )

        assertEquals("{index}-{title}", parameters.pattern)
        assertEquals(TOOL_SCOPE_ALL, parameters.scope)
        assertEquals("Chapter\\d+", parameters.matchPattern)
        assertFalse(parameters.matchRegexEnabled)
        assertFalse(parameters.preview)
    }

    @Test
    fun buildTitleFormatParametersCleansOptionsAndSelectedIndices() {
        val parameters = buildTitleFormatParameters(
            values = mapOf(
                TITLE_FORMAT_PARAM_MODE to "bad",
                TITLE_FORMAT_PARAM_STYLE to TITLE_FORMAT_STYLE_LEFT,
                TITLE_FORMAT_PARAM_SCOPE to TITLE_FORMAT_SCOPE_SELECTED,
                TITLE_FORMAT_PARAM_SELECTED_CHAPTERS to "1, -1, 3",
                TITLE_FORMAT_PARAM_PREVIEW to BOOL_FALSE
            ),
            modeOptions = listOf(TITLE_FORMAT_MODE_PER_CHAPTER to "Per", TITLE_FORMAT_MODE_UNIFORM to "Uniform"),
            styleOptions = listOf(TITLE_FORMAT_STYLE_LEFT to "Left", TITLE_FORMAT_STYLE_DOUBLE to "Double"),
            scopeOptions = listOf(TOOL_SCOPE_ALL to "All", TITLE_FORMAT_SCOPE_SELECTED to "Selected"),
            defaultScope = TOOL_SCOPE_ALL,
            falseValue = BOOL_FALSE
        )

        assertEquals(TITLE_FORMAT_MODE_PER_CHAPTER, parameters.mode)
        assertEquals(TITLE_FORMAT_STYLE_LEFT, parameters.style)
        assertEquals(TITLE_FORMAT_SCOPE_SELECTED, parameters.scope)
        assertEquals(setOf(1, 3), parameters.selectedChapterIndices)
        assertFalse(parameters.preview)
    }

    @Test
    fun buildFetchInfoParametersReadsCatalogFilterEnabledDefaultingTrue() {
        val on = buildFetchInfoParameters(
            values = mapOf(FETCH_INFO_PARAM_CONTENT to FETCH_INFO_CONTENT_CATALOG),
            sourceOptions = FetchInfoSources.options,
            contentOptionsForSource = { listOf(FETCH_INFO_CONTENT_CATALOG to "Catalog") },
            defaultQuery = "Book",
            expectedAuthor = "",
            sosadLoginCookie = "",
            introTargetPath = "OEBPS/Text/intro.xhtml",
            trueValue = BOOL_TRUE
        )
        assertTrue(on.catalogFilterEnabled)

        val off = buildFetchInfoParameters(
            values = mapOf(
                FETCH_INFO_PARAM_CONTENT to FETCH_INFO_CONTENT_CATALOG,
                FETCH_INFO_PARAM_CATALOG_FILTER_ENABLED to BOOL_FALSE
            ),
            sourceOptions = FetchInfoSources.options,
            contentOptionsForSource = { listOf(FETCH_INFO_CONTENT_CATALOG to "Catalog") },
            defaultQuery = "Book",
            expectedAuthor = "",
            sosadLoginCookie = "",
            introTargetPath = "OEBPS/Text/intro.xhtml",
            trueValue = BOOL_TRUE
        )
        assertFalse(off.catalogFilterEnabled)
    }

    @Test
    fun buildFetchInfoParametersFallsBackInvalidOptions() {
        val parameters = buildFetchInfoParameters(
            values = mapOf(
                FETCH_INFO_PARAM_SOURCE to "bad-source",
                FETCH_INFO_PARAM_CONTENT to "bad-content",
                FETCH_INFO_PARAM_AUTH_COOKIE to "",
                FETCH_INFO_PARAM_AUTO_TITLE_FORMAT to BOOL_TRUE
            ),
            sourceOptions = FetchInfoSources.options,
            contentOptionsForSource = { listOf(FETCH_INFO_CONTENT_INTRO to "Intro", FETCH_INFO_CONTENT_CATALOG to "Catalog") },
            defaultQuery = "Book",
            expectedAuthor = "Author",
            sosadLoginCookie = "cookie",
            introTargetPath = "OEBPS/Text/intro.xhtml",
            trueValue = BOOL_TRUE
        )

        assertEquals(FETCH_INFO_SOURCE_JJWXC, parameters.source)
        assertEquals(FETCH_INFO_CONTENT_INTRO, parameters.content)
        assertEquals("Book", parameters.query)
        assertEquals("Author", parameters.expectedAuthor)
        assertEquals("cookie", parameters.authCookie)
        assertEquals(1, parameters.bodyRangeStart)
        assertEquals(0, parameters.bodyRangeEnd)
        assertTrue(parameters.fetchIntro)
        assertTrue(parameters.writeIntro)
        assertTrue(parameters.autoTitleFormat)
    }

    @Test
    fun buildFetchInfoParametersUsesSelectedCoverContentAndExplicitCookie() {
        val parameters = buildFetchInfoParameters(
            values = mapOf(
                FETCH_INFO_PARAM_SOURCE to FETCH_INFO_SOURCE_SOSAD,
                FETCH_INFO_PARAM_CONTENT to FETCH_INFO_CONTENT_COVER,
                FETCH_INFO_PARAM_AUTH_COOKIE to "manual-cookie",
                FETCH_INFO_PARAM_CATALOG_FILTER to "skip",
                FETCH_INFO_PARAM_INTRO_FILTER to "trim"
            ),
            sourceOptions = FetchInfoSources.options,
            contentOptionsForSource = { listOf(FETCH_INFO_CONTENT_COVER to "Cover", FETCH_INFO_CONTENT_INTRO to "Intro") },
            defaultQuery = "Book",
            expectedAuthor = "",
            sosadLoginCookie = "saved-cookie",
            introTargetPath = "OEBPS/Text/intro.xhtml",
            trueValue = BOOL_TRUE
        )

        assertEquals(FETCH_INFO_SOURCE_SOSAD, parameters.source)
        assertEquals(FETCH_INFO_CONTENT_COVER, parameters.content)
        assertEquals("manual-cookie", parameters.authCookie)
        assertTrue(parameters.fetchCover)
        assertTrue(parameters.writeCover)
        assertFalse(parameters.fetchCatalog)
        assertFalse(parameters.fetchIntro)
        assertEquals("skip", parameters.catalogFilter)
        assertEquals("trim", parameters.introFilter)
    }

    @Test
    fun buildFetchInfoParametersFallsBackCatalogWhenSourceHasNoContentOptions() {
        val parameters = buildFetchInfoParameters(
            values = mapOf(
                FETCH_INFO_PARAM_SOURCE to FETCH_INFO_SOURCE_SOSAD,
                FETCH_INFO_PARAM_CONTENT to FETCH_INFO_CONTENT_COVER
            ),
            sourceOptions = FetchInfoSources.options,
            contentOptionsForSource = { emptyList() },
            defaultQuery = "Book",
            expectedAuthor = "Author",
            sosadLoginCookie = "",
            introTargetPath = "OEBPS/Text/intro.xhtml",
            trueValue = BOOL_TRUE
        )

        assertEquals(FETCH_INFO_SOURCE_SOSAD, parameters.source)
        assertEquals(FETCH_INFO_CONTENT_CATALOG, parameters.content)
        assertTrue(parameters.fetchCatalog)
        assertTrue(parameters.writeCatalog)
        assertFalse(parameters.fetchIntro)
        assertFalse(parameters.fetchCover)
    }

    @Test
    fun buildInsertChapterParametersNormalizesSourceCookieAndRange() {
        val parameters = buildInsertChapterParameters(
            values = mapOf(
                INSERT_CHAPTER_PARAM_SOURCE_TYPE to INSERT_CHAPTER_SOURCE_SOSAD,
                INSERT_CHAPTER_PARAM_SOSAD_QUERY to "",
                INSERT_CHAPTER_PARAM_SOSAD_AUTH_COOKIE to "",
                INSERT_CHAPTER_PARAM_SOSAD_RANGE_START to "4",
                INSERT_CHAPTER_PARAM_SOSAD_RANGE_END to "2",
                INSERT_CHAPTER_PARAM_PREVIEW to BOOL_FALSE
            ),
            defaultQuery = "Book",
            sosadLoginCookie = "cookie",
            falseValue = BOOL_FALSE
        )

        assertEquals(INSERT_CHAPTER_SOURCE_SOSAD, parameters.sourceType)
        assertEquals("Book", parameters.sosadQuery)
        assertEquals("cookie", parameters.sosadAuthCookie)
        assertEquals(4, parameters.sosadBodyRangeStart)
        assertEquals(0, parameters.sosadBodyRangeEnd)
        assertFalse(parameters.preview)
    }

    @Test
    fun buildInsertChapterParametersFallsBackInvalidSourceAndKeepsValidRangeEnd() {
        val parameters = buildInsertChapterParameters(
            values = mapOf(
                INSERT_CHAPTER_PARAM_SOURCE_TYPE to "bad-source",
                INSERT_CHAPTER_PARAM_SOSAD_QUERY to "Manual",
                INSERT_CHAPTER_PARAM_SOSAD_AUTH_COOKIE to "manual-cookie",
                INSERT_CHAPTER_PARAM_SOSAD_RANGE_START to "bad",
                INSERT_CHAPTER_PARAM_SOSAD_RANGE_END to "3"
            ),
            defaultQuery = "Book",
            sosadLoginCookie = "saved-cookie",
            falseValue = BOOL_FALSE
        )

        assertEquals(INSERT_CHAPTER_SOURCE_UPLOAD, parameters.sourceType)
        assertEquals("Manual", parameters.sosadQuery)
        assertEquals("manual-cookie", parameters.sosadAuthCookie)
        assertEquals(1, parameters.sosadBodyRangeStart)
        assertEquals(3, parameters.sosadBodyRangeEnd)
        assertTrue(parameters.preview)
    }

    @Test
    fun buildInsertChapterParametersCoercesZeroRangeStartBeforeKeepingEnd() {
        val parameters = buildInsertChapterParameters(
            values = mapOf(
                INSERT_CHAPTER_PARAM_SOSAD_RANGE_START to "0",
                INSERT_CHAPTER_PARAM_SOSAD_RANGE_END to "1"
            ),
            defaultQuery = "Book",
            sosadLoginCookie = "",
            falseValue = BOOL_FALSE
        )

        assertEquals(1, parameters.sosadBodyRangeStart)
        assertEquals(1, parameters.sosadBodyRangeEnd)
    }

    @Test
    fun buildCoverParametersCleansModeInsertTypeAndCompressFlag() {
        val parameters = buildCoverParameters(
            values = mapOf(
                COVER_PARAM_MODE to "bad-mode",
                COVER_PARAM_TITLE to "封面",
                COVER_PARAM_IMAGE_URI to "content://cover",
                COVER_PARAM_IMAGE_INSERT_TYPE to COVER_IMAGE_INSERT_WARNING,
                COVER_PARAM_COMPRESS to BOOL_FALSE,
                COVER_PARAM_PREVIEW to BOOL_TRUE
            ),
            modeOptions = listOf(COVER_MODE_INSERT to "Insert", COVER_MODE_GENERATE to "Generate"),
            imageInsertOptions = listOf(COVER_IMAGE_INSERT_NOTE to "Note", COVER_IMAGE_INSERT_WARNING to "Warning"),
            trueValue = BOOL_TRUE
        )

        assertEquals(COVER_MODE_INSERT, parameters.mode)
        assertEquals("封面", parameters.title)
        assertEquals("content://cover", parameters.imageUri)
        assertEquals(COVER_IMAGE_INSERT_WARNING, parameters.imageInsertType)
        assertFalse(parameters.compress)
        // mode 被清洗为 INSERT，而 preview 仅在 GENERATE 模式下生效，故应为 false
        assertFalse(parameters.preview)
    }

    @Test
    fun buildCoverParametersDefaultsBlankCompressToEnabled() {
        val parameters = buildCoverParameters(
            values = mapOf(COVER_PARAM_COMPRESS to ""),
            modeOptions = listOf(COVER_MODE_INSERT to "Insert", COVER_MODE_GENERATE to "Generate"),
            imageInsertOptions = listOf(COVER_IMAGE_INSERT_NOTE to "Note"),
            trueValue = BOOL_TRUE
        )

        assertEquals(COVER_MODE_INSERT, parameters.mode)
        assertEquals(COVER_IMAGE_INSERT_NOTE, parameters.imageInsertType)
        assertTrue(parameters.compress)
        assertFalse(parameters.preview)
    }

    @Test
    fun buildCoverParametersDisablesPreviewForImageResourceInsertMode() {
        val parameters = buildCoverParameters(
            values = mapOf(
                COVER_PARAM_MODE to COVER_MODE_IMAGE_INSERT,
                COVER_PARAM_IMAGE_INSERT_TYPE to COVER_IMAGE_INSERT_WARNING,
                COVER_PARAM_PREVIEW to BOOL_TRUE
            ),
            modeOptions = listOf(COVER_MODE_INSERT to "Insert", COVER_MODE_IMAGE_INSERT to "Image"),
            imageInsertOptions = listOf(COVER_IMAGE_INSERT_NOTE to "Note", COVER_IMAGE_INSERT_WARNING to "Warning"),
            trueValue = BOOL_TRUE
        )

        assertEquals(COVER_MODE_IMAGE_INSERT, parameters.mode)
        assertEquals(COVER_IMAGE_INSERT_WARNING, parameters.imageInsertType)
        assertFalse(parameters.preview)
    }

    private fun batchSourceOptions(): List<Pair<String, String>> {
        return listOf(TEXT_REPLACE_MODE_BATCH to "Text", TEXT_REPLACE_BATCH_FILE to "File")
    }

    private fun epubSingleScopeOptions(): List<Pair<String, String>> {
        return listOf(TOOL_SCOPE_ALL to "All", TOOL_SCOPE_CURRENT to "Current", TEXT_REPLACE_SCOPE_INTRO to "Intro")
    }

    private fun epubBatchScopeOptions(): List<Pair<String, String>> {
        return listOf(TOOL_SCOPE_ALL to "All", TOOL_SCOPE_CURRENT to "Current")
    }

    private fun txtScopeOptions(): List<Pair<String, String>> {
        return listOf(TOOL_SCOPE_ALL to "All", TOOL_SCOPE_CURRENT to "Current")
    }
}
