package com.eteditor

import com.eteditor.core.DocumentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolParameterOverrideUtilsTest {
    @Test
    fun defaultToolParametersAndEmptyValueRulesFollowDefinitionsAndWhitespaceSensitivity() {
        val definition = ToolDefinition(
            id = "text_replace",
            title = "替换",
            category = "文本",
            description = "",
            implemented = true,
            parameters = textReplaceDefinitions()
        )

        assertEquals(emptyMap<String, String>(), defaultToolParameters(null))
        assertEquals(
            mapOf(
                TEXT_REPLACE_PARAM_SCOPE to TOOL_SCOPE_ALL,
                TEXT_REPLACE_PARAM_TARGET to TEXT_REPLACE_TARGET_SOURCE,
                TEXT_REPLACE_PARAM_FIND to "",
                TEXT_REPLACE_PARAM_REPLACE to "",
                TEXT_REPLACE_PARAM_FIND_REGEX to BOOL_FALSE,
                TEXT_REPLACE_PARAM_BATCH_FILE to ""
            ),
            defaultToolParameters(definition)
        )
        assertTrue(isEmptyToolParameterValue("generate_cover", COVER_PARAM_TITLE, " "))
        assertTrue(isEmptyToolParameterValue("text_replace", TEXT_REPLACE_PARAM_FIND, ""))
        assertFalse(isEmptyToolParameterValue("text_replace", TEXT_REPLACE_PARAM_REPLACE, " "))
    }

    @Test
    fun txtTextReplaceDefinitionsKeepOnlyTxtSupportedParameters() {
        val definitions = toolParameterDefinitionsForDocument(
            toolId = "text_replace",
            kind = DocumentKind.Txt,
            parameters = textReplaceDefinitions() + ToolParameterDefinition(
                key = TEXT_REPLACE_PARAM_BATCH_TEXT,
                label = "Batch",
                defaultValue = ""
            ),
            txtTextReplaceScopeOptions = listOf(TOOL_SCOPE_ALL to "All", TOOL_SCOPE_CURRENT to "Current")
        )

        assertEquals(
            listOf(
                TEXT_REPLACE_PARAM_SCOPE,
                TEXT_REPLACE_PARAM_FIND,
                TEXT_REPLACE_PARAM_REPLACE,
                TEXT_REPLACE_PARAM_FIND_REGEX
            ),
            definitions.map { it.key }
        )
        assertEquals(
            listOf(TOOL_SCOPE_ALL to "All", TOOL_SCOPE_CURRENT to "Current"),
            definitions.first { it.key == TEXT_REPLACE_PARAM_SCOPE }.options
        )
    }

    @Test
    fun cleanToolParameterOverridesDropsUnknownDefaultInvalidAndTransientValues() {
        val cleaned = cleanToolParameterOverrides(
            toolId = "text_replace",
            definitions = textReplaceDefinitions(),
            overrides = mapOf(
                TEXT_REPLACE_PARAM_SCOPE to TOOL_SCOPE_CURRENT,
                TEXT_REPLACE_PARAM_FIND to "needle",
                TEXT_REPLACE_PARAM_REPLACE to " ",
                TEXT_REPLACE_PARAM_FIND_REGEX to BOOL_TRUE,
                TEXT_REPLACE_PARAM_BATCH_FILE to "content://rules.txt",
                "unknown" to "ignored"
            ),
            textReplaceScopeOptionKeys = setOf(TOOL_SCOPE_ALL, TOOL_SCOPE_CURRENT)
        )

        assertEquals(
            mapOf(
                TEXT_REPLACE_PARAM_SCOPE to TOOL_SCOPE_CURRENT,
                TEXT_REPLACE_PARAM_FIND to "needle",
                TEXT_REPLACE_PARAM_REPLACE to " ",
                TEXT_REPLACE_PARAM_FIND_REGEX to BOOL_TRUE
            ),
            cleaned
        )
    }

    @Test
    fun sosadAuthParametersAreRecognizedAsSensitiveRuntimeValues() {
        assertTrue(isSosadAuthParameter("fetch_info", FETCH_INFO_PARAM_AUTH_COOKIE))
        assertTrue(isSosadAuthParameter("insert_chapter", INSERT_CHAPTER_PARAM_SOSAD_AUTH_COOKIE))
        assertFalse(isSosadAuthParameter("fetch_info", FETCH_INFO_PARAM_QUERY))
        assertFalse(isSosadAuthParameter("text_replace", TEXT_REPLACE_PARAM_FIND))
    }

    @Test
    fun cleanTextReplacePresetOverridesNormalizeScopesAndTargets() {
        val txtCleaned = cleanTxtTextReplacePresetParameterOverrides(
            definitions = textReplaceDefinitions(),
            find = "needle",
            replace = " ",
            regex = true,
            scope = "bad-scope",
            txtScopeOptions = listOf(TOOL_SCOPE_ALL to "All", TOOL_SCOPE_CURRENT to "Current"),
            textReplaceScopeOptionKeys = setOf(TOOL_SCOPE_ALL, TOOL_SCOPE_CURRENT)
        )
        val epubCleaned = cleanEpubTextReplacePresetParameterOverrides(
            definitions = textReplaceDefinitions(),
            find = "needle",
            replace = "replacement",
            regex = false,
            scope = "bad-scope",
            target = "bad-target",
            textReplaceScopeOptionKeys = setOf(TOOL_SCOPE_ALL, TOOL_SCOPE_CURRENT)
        )

        assertEquals(
            mapOf(
                TEXT_REPLACE_PARAM_FIND to "needle",
                TEXT_REPLACE_PARAM_REPLACE to " ",
                TEXT_REPLACE_PARAM_FIND_REGEX to BOOL_TRUE
            ),
            txtCleaned
        )
        assertEquals(
            mapOf(
                TEXT_REPLACE_PARAM_FIND to "needle",
                TEXT_REPLACE_PARAM_REPLACE to "replacement"
            ),
            epubCleaned
        )
    }

    @Test
    fun cleanBuiltInDefaultOverridesDropTextReplaceTargetAndBatchFile() {
        val cleaned = cleanBuiltInDefaultParameterOverridesForSave(
            toolId = "text_replace",
            definitions = textReplaceDefinitions(),
            overrides = mapOf(
                TEXT_REPLACE_PARAM_TARGET to TEXT_REPLACE_TARGET_VISIBLE,
                TEXT_REPLACE_PARAM_FIND to "needle",
                TEXT_REPLACE_PARAM_BATCH_FILE to "content://rules.txt"
            ),
            textReplaceScopeOptionKeys = setOf(TOOL_SCOPE_ALL, TOOL_SCOPE_CURRENT)
        )

        assertEquals(mapOf(TEXT_REPLACE_PARAM_FIND to "needle"), cleaned)
    }

    @Test
    fun textReplaceTargetIsKeptForPresetOverridesButDroppedForBuiltInDefaults() {
        val overrides = mapOf(
            TEXT_REPLACE_PARAM_TARGET to TEXT_REPLACE_TARGET_VISIBLE,
            TEXT_REPLACE_PARAM_FIND to "needle"
        )
        val editable = cleanToolParameterOverrides(
            toolId = "text_replace",
            definitions = textReplaceDefinitions(),
            overrides = overrides,
            textReplaceScopeOptionKeys = setOf(TOOL_SCOPE_ALL, TOOL_SCOPE_CURRENT)
        )
        val savedDefault = cleanBuiltInDefaultParameterOverridesForSave(
            toolId = "text_replace",
            definitions = textReplaceDefinitions(),
            overrides = overrides,
            textReplaceScopeOptionKeys = setOf(TOOL_SCOPE_ALL, TOOL_SCOPE_CURRENT)
        )

        assertEquals(
            mapOf(
                TEXT_REPLACE_PARAM_TARGET to TEXT_REPLACE_TARGET_VISIBLE,
                TEXT_REPLACE_PARAM_FIND to "needle"
            ),
            editable
        )
        assertEquals(mapOf(TEXT_REPLACE_PARAM_FIND to "needle"), savedDefault)
    }

    @Test
    fun textReplaceScopeOptionKeysMergeAllScopeSourcesForValidation() {
        val keys = textReplaceScopeOptionKeys(
            textReplaceScopeOptions = listOf(TOOL_SCOPE_ALL to "All", TEXT_REPLACE_SCOPE_INTRO to "Intro"),
            txtTextReplaceScopeOptions = listOf(TOOL_SCOPE_CURRENT to "Current", TOOL_SCOPE_ALL to "All"),
            epubTextReplaceBatchScopeOptions = listOf(TEXT_REPLACE_SCOPE_SELECTED_HTML to "Selected")
        )
        val scopeParameter = ToolParameterDefinition(
            key = TEXT_REPLACE_PARAM_SCOPE,
            label = "Scope",
            defaultValue = TOOL_SCOPE_ALL,
            options = listOf("ignored" to "Ignored")
        )

        assertEquals(
            setOf(TOOL_SCOPE_ALL, TEXT_REPLACE_SCOPE_INTRO, TOOL_SCOPE_CURRENT, TEXT_REPLACE_SCOPE_SELECTED_HTML),
            keys
        )
        assertEquals(
            keys,
            allowedToolParameterOptionKeys(
                toolId = "text_replace",
                key = TEXT_REPLACE_PARAM_SCOPE,
                parameter = scopeParameter,
                textReplaceScopeOptionKeys = keys
            )
        )
    }

    @Test
    fun cleanToolParameterOverridesUsesMergedScopeKeysOnlyForTextReplaceScope() {
        val cleaned = cleanToolParameterOverrides(
            toolId = "text_replace",
            definitions = textReplaceDefinitions(),
            overrides = mapOf(
                TEXT_REPLACE_PARAM_SCOPE to TEXT_REPLACE_SCOPE_INTRO,
                TEXT_REPLACE_PARAM_TARGET to TEXT_REPLACE_SCOPE_INTRO,
                TEXT_REPLACE_PARAM_FIND to "needle"
            ),
            textReplaceScopeOptionKeys = setOf(TOOL_SCOPE_ALL, TOOL_SCOPE_CURRENT, TEXT_REPLACE_SCOPE_INTRO)
        )

        assertEquals(
            mapOf(
                TEXT_REPLACE_PARAM_SCOPE to TEXT_REPLACE_SCOPE_INTRO,
                TEXT_REPLACE_PARAM_FIND to "needle"
            ),
            cleaned
        )
    }

    @Test
    fun cleanBuiltInToolOverridesKeepFreeTextDefaultButDropDefaultAndInvalidOptions() {
        val definitions = listOf(
            ToolParameterDefinition(
                key = "title",
                label = "Title",
                defaultValue = "默认标题"
            ),
            ToolParameterDefinition(
                key = "mode",
                label = "Mode",
                defaultValue = "insert",
                options = listOf("insert" to "Insert", "generate" to "Generate")
            ),
            ToolParameterDefinition(
                key = "layout",
                label = "Layout",
                defaultValue = "front",
                options = listOf("front" to "Front", "back" to "Back")
            )
        )

        val cleaned = cleanBuiltInToolParameterOverrides(
            toolId = "generate_cover",
            definitions = definitions,
            overrides = mapOf(
                "title" to "默认标题",
                "mode" to "insert",
                "layout" to "invalid",
                "unknown" to "ignored"
            ),
            textReplaceScopeOptionKeys = emptySet()
        )

        assertEquals(mapOf("title" to "默认标题"), cleaned)
    }

    @Test
    fun cleanToolParameterOverridesDropsFileRenameRangeFilters() {
        val definitions = listOf(
            ToolParameterDefinition(
                key = FILE_RENAME_PARAM_NAMING_FORMAT,
                label = "Format",
                defaultValue = "{title}"
            ),
            ToolParameterDefinition(
                key = FILE_RENAME_PARAM_SCOPE,
                label = "Scope",
                defaultValue = TOOL_SCOPE_ALL,
                options = listOf(TOOL_SCOPE_ALL to "All", TOOL_SCOPE_CURRENT to "Current")
            ),
            ToolParameterDefinition(
                key = FILE_RENAME_PARAM_MATCH_PATTERN,
                label = "Pattern",
                defaultValue = ""
            ),
            ToolParameterDefinition(
                key = FILE_RENAME_PARAM_MATCH_REGEX_ENABLED,
                label = "Regex",
                defaultValue = BOOL_FALSE,
                options = BOOLEAN_OPTIONS
            )
        )

        val cleaned = cleanToolParameterOverrides(
            toolId = "file_rename",
            definitions = definitions,
            overrides = mapOf(
                FILE_RENAME_PARAM_NAMING_FORMAT to "{author}-{title}",
                FILE_RENAME_PARAM_SCOPE to TOOL_SCOPE_CURRENT,
                FILE_RENAME_PARAM_MATCH_PATTERN to "第\\d+章",
                FILE_RENAME_PARAM_MATCH_REGEX_ENABLED to BOOL_TRUE
            ),
            textReplaceScopeOptionKeys = emptySet()
        )

        assertEquals(mapOf(FILE_RENAME_PARAM_NAMING_FORMAT to "{author}-{title}"), cleaned)
    }

    @Test
    fun cleanToolParameterOverridesDropsDefaultFetchIntroTargetAndCoverImageUri() {
        val fetchCleaned = cleanToolParameterOverrides(
            toolId = "fetch_info",
            definitions = listOf(
                ToolParameterDefinition(
                    key = FETCH_INFO_PARAM_INTRO_TARGET,
                    label = "Intro",
                    defaultValue = DEFAULT_FETCH_INFO_INTRO_TARGET
                )
            ),
            overrides = mapOf(FETCH_INFO_PARAM_INTRO_TARGET to DEFAULT_FETCH_INFO_INTRO_TARGET),
            textReplaceScopeOptionKeys = emptySet()
        )
        val coverCleaned = cleanToolParameterOverrides(
            toolId = "generate_cover",
            definitions = listOf(
                ToolParameterDefinition(
                    key = COVER_PARAM_MODE,
                    label = "Mode",
                    defaultValue = COVER_MODE_INSERT,
                    options = COVER_MODE_OPTIONS
                ),
                ToolParameterDefinition(
                    key = COVER_PARAM_PREVIEW,
                    label = "Preview",
                    defaultValue = BOOL_FALSE,
                    options = BOOLEAN_OPTIONS
                ),
                ToolParameterDefinition(
                    key = COVER_PARAM_IMAGE_URI,
                    label = "Image",
                    defaultValue = ""
                )
            ),
            overrides = mapOf(
                COVER_PARAM_MODE to COVER_MODE_IMAGE_INSERT,
                COVER_PARAM_PREVIEW to BOOL_TRUE,
                COVER_PARAM_IMAGE_URI to "content://cover.jpg"
            ),
            textReplaceScopeOptionKeys = emptySet()
        )

        assertEquals(emptyMap<String, String>(), fetchCleaned)
        assertEquals(mapOf(COVER_PARAM_MODE to COVER_MODE_IMAGE_INSERT), coverCleaned)
    }

    @Test
    fun cleanToolParameterOverridesKeepsNonDefaultFetchIntroTargets() {
        val definitions = listOf(
            ToolParameterDefinition(
                key = FETCH_INFO_PARAM_INTRO_TARGET,
                label = "Intro",
                defaultValue = DEFAULT_FETCH_INFO_INTRO_TARGET
            )
        )

        val xhtmlDefault = cleanToolParameterOverrides(
            toolId = "fetch_info",
            definitions = definitions,
            overrides = mapOf(FETCH_INFO_PARAM_INTRO_TARGET to "OEBPS/Text/Section0002.xhtml"),
            textReplaceScopeOptionKeys = emptySet()
        )
        val customPath = cleanToolParameterOverrides(
            toolId = "fetch_info",
            definitions = definitions,
            overrides = mapOf(FETCH_INFO_PARAM_INTRO_TARGET to "OEBPS/Text/custom-intro.xhtml"),
            textReplaceScopeOptionKeys = emptySet()
        )

        assertEquals(emptyMap<String, String>(), xhtmlDefault)
        assertEquals(mapOf(FETCH_INFO_PARAM_INTRO_TARGET to "OEBPS/Text/custom-intro.xhtml"), customPath)
    }

    @Test
    fun cleanToolParameterOverridesKeepsCoverPreviewForPreviewableModes() {
        val cleaned = cleanToolParameterOverrides(
            toolId = "generate_cover",
            definitions = listOf(
                ToolParameterDefinition(
                    key = COVER_PARAM_MODE,
                    label = "Mode",
                    defaultValue = COVER_MODE_INSERT,
                    options = COVER_MODE_OPTIONS
                ),
                ToolParameterDefinition(
                    key = COVER_PARAM_PREVIEW,
                    label = "Preview",
                    defaultValue = BOOL_FALSE,
                    options = BOOLEAN_OPTIONS
                )
            ),
            overrides = mapOf(
                COVER_PARAM_MODE to COVER_MODE_GENERATE,
                COVER_PARAM_PREVIEW to BOOL_TRUE
            ),
            textReplaceScopeOptionKeys = emptySet()
        )

        assertEquals(
            mapOf(
                COVER_PARAM_MODE to COVER_MODE_GENERATE,
                COVER_PARAM_PREVIEW to BOOL_TRUE
            ),
            cleaned
        )
    }

    @Test
    fun cleanBuiltInToolOverridesKeepRuntimeCoverImageAndDropImageInsertPreview() {
        val cleaned = cleanBuiltInToolParameterOverrides(
            toolId = "generate_cover",
            definitions = listOf(
                ToolParameterDefinition(
                    key = COVER_PARAM_MODE,
                    label = "Mode",
                    defaultValue = COVER_MODE_INSERT,
                    options = COVER_MODE_OPTIONS
                ),
                ToolParameterDefinition(
                    key = COVER_PARAM_IMAGE_URI,
                    label = "Image",
                    defaultValue = ""
                ),
                ToolParameterDefinition(
                    key = COVER_PARAM_IMAGE_INSERT_TYPE,
                    label = "Insert type",
                    defaultValue = COVER_IMAGE_INSERT_NOTE,
                    options = COVER_IMAGE_INSERT_OPTIONS
                ),
                ToolParameterDefinition(
                    key = COVER_PARAM_COMPRESS,
                    label = "Compress",
                    defaultValue = BOOL_TRUE,
                    options = BOOLEAN_OPTIONS
                ),
                ToolParameterDefinition(
                    key = COVER_PARAM_PREVIEW,
                    label = "Preview",
                    defaultValue = BOOL_FALSE,
                    options = BOOLEAN_OPTIONS
                )
            ),
            overrides = mapOf(
                COVER_PARAM_MODE to COVER_MODE_IMAGE_INSERT,
                COVER_PARAM_IMAGE_URI to "content://cover.jpg",
                COVER_PARAM_IMAGE_INSERT_TYPE to COVER_IMAGE_INSERT_CUSTOM,
                COVER_PARAM_COMPRESS to BOOL_TRUE,
                COVER_PARAM_PREVIEW to BOOL_TRUE,
                "unknown" to "ignored"
            ),
            textReplaceScopeOptionKeys = emptySet()
        )

        assertEquals(
            mapOf(
                COVER_PARAM_MODE to COVER_MODE_IMAGE_INSERT,
                COVER_PARAM_IMAGE_URI to "content://cover.jpg",
                COVER_PARAM_IMAGE_INSERT_TYPE to COVER_IMAGE_INSERT_CUSTOM
            ),
            cleaned
        )
    }

    @Test
    fun normalizedTextReplacePresetsRejectUnsupportedImportShapes() {
        val defaultParameters = mapOf(
            TEXT_REPLACE_PARAM_MODE to TEXT_REPLACE_MODE_SINGLE,
            TEXT_REPLACE_PARAM_TARGET to TEXT_REPLACE_TARGET_SOURCE
        )
        val emptyFind = normalizedTxtTextReplacePresetForImport(
            tool = EditorTool(
                id = "txt-1",
                name = "Empty",
                toolId = "text_replace"
            ),
            defaultParameters = defaultParameters,
            cleanPresetOverrides = ::cleanPresetOverridesForTest
        )
        val epubBatchFile = normalizedEpubTextReplacePresetForImport(
            tool = EditorTool(
                id = "epub-1",
                name = "Batch",
                toolId = "text_replace",
                parameterOverrides = mapOf(
                    TEXT_REPLACE_PARAM_FIND to "needle",
                    TEXT_REPLACE_PARAM_BATCH_SOURCE to TEXT_REPLACE_BATCH_FILE
                )
            ),
            defaultParameters = defaultParameters,
            cleanPresetOverrides = ::cleanEpubPresetOverridesForTest
        )

        assertNull(emptyFind)
        assertNull(epubBatchFile)
    }

    @Test
    fun normalizedEpubTextReplacePresetFallsBackToSourceTarget() {
        val preset = normalizedEpubTextReplacePresetForImport(
            tool = EditorTool(
                id = "epub-1",
                name = "Replace",
                toolId = "text_replace",
                parameterOverrides = mapOf(
                    TEXT_REPLACE_PARAM_FIND to "needle",
                    TEXT_REPLACE_PARAM_REPLACE to "replacement",
                    TEXT_REPLACE_PARAM_TARGET to "invalid"
                )
            ),
            defaultParameters = emptyMap(),
            cleanPresetOverrides = ::cleanEpubPresetOverridesForTest
        )

        assertEquals(
            mapOf(
                TEXT_REPLACE_PARAM_FIND to "needle",
                TEXT_REPLACE_PARAM_REPLACE to "replacement",
                TEXT_REPLACE_PARAM_FIND_REGEX to BOOL_FALSE,
                TEXT_REPLACE_PARAM_SCOPE to TEXT_REPLACE_TARGET_SOURCE
            ),
            preset?.parameterOverrides
        )
    }

    @Test
    fun toolParameterSaveErrorRequiresMatchPatternForTitleRenameFileRegexScope() {
        assertEquals(
            "标题匹配需要填写匹配规则",
            toolParameterSaveError(
                toolId = "chapter_title_rename",
                values = mapOf(
                    TITLE_RENAME_PARAM_SCOPE to TOOL_SCOPE_FILE_REGEX,
                    TITLE_RENAME_PARAM_MATCH_PATTERN to " "
                )
            )
        )
        assertNull(
            toolParameterSaveError(
                toolId = "chapter_title_rename",
                values = mapOf(
                    TITLE_RENAME_PARAM_SCOPE to TOOL_SCOPE_FILE_REGEX,
                    TITLE_RENAME_PARAM_MATCH_PATTERN to "Chapter\\d+"
                )
            )
        )
    }

    @Test
    fun toolParameterSaveErrorLimitsGeneratedCoverTitleByCodePointLength() {
        assertEquals(3, coverTitleLength("书🙂名"))
        assertEquals("image/jpeg", generatedCoverTargetMediaType())
        assertNull(
            toolParameterSaveError(
                toolId = "generate_cover",
                values = mapOf(
                    COVER_PARAM_MODE to COVER_MODE_GENERATE,
                    COVER_PARAM_TITLE to "一二三四五六七八九"
                )
            )
        )
        assertEquals(
            "封面标题最大 9 字",
            toolParameterSaveError(
                toolId = "generate_cover",
                values = mapOf(
                    COVER_PARAM_MODE to COVER_MODE_GENERATE,
                    COVER_PARAM_TITLE to "一二三四五六七八九十"
                )
            )
        )
    }

    private fun textReplaceDefinitions(): List<ToolParameterDefinition> {
        return listOf(
            ToolParameterDefinition(
                key = TEXT_REPLACE_PARAM_SCOPE,
                label = "Scope",
                defaultValue = TOOL_SCOPE_ALL,
                options = listOf(TOOL_SCOPE_ALL to "All", TOOL_SCOPE_CURRENT to "Current")
            ),
            ToolParameterDefinition(
                key = TEXT_REPLACE_PARAM_TARGET,
                label = "Target",
                defaultValue = TEXT_REPLACE_TARGET_SOURCE,
                options = listOf(TEXT_REPLACE_TARGET_SOURCE to "Source", TEXT_REPLACE_TARGET_VISIBLE to "Visible")
            ),
            ToolParameterDefinition(
                key = TEXT_REPLACE_PARAM_FIND,
                label = "Find",
                defaultValue = ""
            ),
            ToolParameterDefinition(
                key = TEXT_REPLACE_PARAM_REPLACE,
                label = "Replace",
                defaultValue = ""
            ),
            ToolParameterDefinition(
                key = TEXT_REPLACE_PARAM_FIND_REGEX,
                label = "Regex",
                defaultValue = BOOL_FALSE,
                options = BOOLEAN_OPTIONS
            ),
            ToolParameterDefinition(
                key = TEXT_REPLACE_PARAM_BATCH_FILE,
                label = "Batch file",
                defaultValue = ""
            )
        )
    }

    private fun cleanPresetOverridesForTest(
        find: String,
        replace: String,
        regex: Boolean,
        scope: String
    ): Map<String, String> {
        return mapOf(
            TEXT_REPLACE_PARAM_FIND to find,
            TEXT_REPLACE_PARAM_REPLACE to replace,
            TEXT_REPLACE_PARAM_FIND_REGEX to regex.toString(),
            TEXT_REPLACE_PARAM_SCOPE to scope
        )
    }

    private fun cleanEpubPresetOverridesForTest(
        find: String,
        replace: String,
        regex: Boolean,
        scope: String,
        target: String
    ): Map<String, String> {
        return mapOf(
            TEXT_REPLACE_PARAM_FIND to find,
            TEXT_REPLACE_PARAM_REPLACE to replace,
            TEXT_REPLACE_PARAM_FIND_REGEX to regex.toString(),
            TEXT_REPLACE_PARAM_SCOPE to target
        )
    }
}
