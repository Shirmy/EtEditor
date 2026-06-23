package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolDefinitionCatalogTest {
    @Test
    fun defaultToolDefinitionsKeepExpectedIdsInOrder() {
        assertEquals(
            listOf(
                "file_rename",
                "text_replace",
                "insert_chapter",
                "chapter_title_rename",
                "title_format",
                "fetch_info",
                "generate_cover"
            ),
            defaultEditorToolDefinitions().map { it.id }
        )
    }

    @Test
    fun defaultToolDefinitionsHaveUniqueToolIdsAndParameterKeys() {
        val definitions = defaultEditorToolDefinitions()

        assertEquals(definitions.map { it.id }.distinct(), definitions.map { it.id })
        definitions.forEach { definition ->
            val keys = definition.parameters.map { it.key }
            assertEquals("duplicate parameter keys in ${definition.id}", keys.distinct(), keys)
            assertTrue("blank title in ${definition.id}", definition.title.isNotBlank())
            assertTrue("blank description in ${definition.id}", definition.description.isNotBlank())
        }
    }

    @Test
    fun optionParameterDefaultsExistInTheirOptionLists() {
        defaultEditorToolDefinitions().forEach { definition ->
            definition.parameters
                .filter { it.options.isNotEmpty() }
                .forEach { parameter ->
                    val optionKeys = parameter.options.map { it.first }
                    assertTrue(
                        "${definition.id}.${parameter.key} default ${parameter.defaultValue} missing from options $optionKeys",
                        parameter.defaultValue in optionKeys
                    )
                }
        }
    }

    @Test
    fun textReplaceDefinitionKeepsModeRuleFileAndPreviewDefaults() {
        val parameters = defaultEditorToolDefinitions()
            .first { it.id == "text_replace" }
            .parameters
            .associateBy { it.key }

        assertEquals(TEXT_REPLACE_MODE_SINGLE, parameters[TEXT_REPLACE_PARAM_MODE]?.defaultValue)
        assertEquals(
            listOf(TEXT_REPLACE_MODE_SINGLE, TEXT_REPLACE_MODE_BATCH, TEXT_REPLACE_MODE_REPLACEMENT),
            parameters[TEXT_REPLACE_PARAM_MODE]?.options?.map { it.first }
        )
        assertEquals(TEXT_REPLACE_BATCH_INPUT, parameters[TEXT_REPLACE_PARAM_BATCH_SOURCE]?.defaultValue)
        assertEquals(
            listOf(TEXT_REPLACE_BATCH_INPUT, TEXT_REPLACE_BATCH_FILE),
            parameters[TEXT_REPLACE_PARAM_BATCH_SOURCE]?.options?.map { it.first }
        )
        assertEquals("", parameters[TEXT_REPLACE_PARAM_BATCH_FILE]?.defaultValue)
        assertEquals(BOOL_TRUE, parameters[TEXT_REPLACE_PARAM_PREVIEW]?.defaultValue)
    }

    @Test
    fun fetchInfoDefinitionKeepsSourceContentIntroAndFilterDefaults() {
        val parameters = defaultEditorToolDefinitions()
            .first { it.id == "fetch_info" }
            .parameters
            .associateBy { it.key }

        assertEquals(FETCH_INFO_SOURCE_JJWXC, parameters[FETCH_INFO_PARAM_SOURCE]?.defaultValue)
        assertEquals(FetchInfoSources.options, parameters[FETCH_INFO_PARAM_SOURCE]?.options)
        assertEquals(FETCH_INFO_SEARCH_TITLE, parameters[FETCH_INFO_PARAM_SEARCH_MODE]?.defaultValue)
        assertEquals(FETCH_INFO_CONTENT_CATALOG, parameters[FETCH_INFO_PARAM_CONTENT]?.defaultValue)
        assertEquals(FETCH_INFO_CONTENT_OPTIONS, parameters[FETCH_INFO_PARAM_CONTENT]?.options)
        assertEquals("trim\ncompressBlankLines", parameters[FETCH_INFO_PARAM_INTRO_FILTER]?.defaultValue)
        assertEquals(BOOL_FALSE, parameters[FETCH_INFO_PARAM_AUTO_TITLE_FORMAT]?.defaultValue)
        assertEquals(BOOLEAN_OPTIONS, parameters[FETCH_INFO_PARAM_AUTO_TITLE_FORMAT]?.options)
    }

    @Test
    fun coverDefinitionKeepsImageInsertAndPreviewDefaults() {
        val parameters = defaultEditorToolDefinitions()
            .first { it.id == "generate_cover" }
            .parameters
            .associateBy { it.key }

        assertEquals(COVER_MODE_INSERT, parameters[COVER_PARAM_MODE]?.defaultValue)
        assertEquals(COVER_MODE_OPTIONS, parameters[COVER_PARAM_MODE]?.options)
        assertEquals(COVER_IMAGE_INSERT_NOTE, parameters[COVER_PARAM_IMAGE_INSERT_TYPE]?.defaultValue)
        assertEquals(COVER_IMAGE_INSERT_OPTIONS, parameters[COVER_PARAM_IMAGE_INSERT_TYPE]?.options)
        assertEquals(BOOL_TRUE, parameters[COVER_PARAM_COMPRESS]?.defaultValue)
        assertEquals(BOOL_FALSE, parameters[COVER_PARAM_PREVIEW]?.defaultValue)
        assertEquals(BOOLEAN_OPTIONS, parameters[COVER_PARAM_PREVIEW]?.options)
    }

    @Test
    fun chapterFileAndTitleToolDefinitionsKeepDefaultParameters() {
        val definitions = defaultEditorToolDefinitions().associateBy { it.id }
        val fileRename = definitions.getValue("file_rename").parameters.associateBy { it.key }
        val insertChapter = definitions.getValue("insert_chapter").parameters.associateBy { it.key }
        val titleRename = definitions.getValue("chapter_title_rename").parameters.associateBy { it.key }
        val titleFormat = definitions.getValue("title_format").parameters.associateBy { it.key }

        assertEquals(DEFAULT_FILE_RENAME_PATTERN, fileRename[FILE_RENAME_PARAM_NAMING_FORMAT]?.defaultValue)
        assertEquals(BOOL_TRUE, fileRename[FILE_RENAME_PARAM_PREVIEW]?.defaultValue)
        assertEquals(BOOLEAN_OPTIONS, fileRename[FILE_RENAME_PARAM_PREVIEW]?.options)

        assertEquals(INSERT_CHAPTER_SOURCE_UPLOAD, insertChapter[INSERT_CHAPTER_PARAM_SOURCE_TYPE]?.defaultValue)
        assertEquals(INSERT_CHAPTER_SOURCE_OPTIONS, insertChapter[INSERT_CHAPTER_PARAM_SOURCE_TYPE]?.options)
        assertEquals("1", insertChapter[INSERT_CHAPTER_PARAM_SOSAD_RANGE_START]?.defaultValue)
        assertEquals("", insertChapter[INSERT_CHAPTER_PARAM_SOSAD_RANGE_END]?.defaultValue)
        assertEquals(BOOL_TRUE, insertChapter[INSERT_CHAPTER_PARAM_PREVIEW]?.defaultValue)

        assertEquals(TOOL_SCOPE_ALL, titleRename[TITLE_RENAME_PARAM_SCOPE]?.defaultValue)
        assertEquals(TITLE_RENAME_SCOPE_OPTIONS, titleRename[TITLE_RENAME_PARAM_SCOPE]?.options)
        assertEquals(BOOL_TRUE, titleRename[TITLE_RENAME_PARAM_MATCH_REGEX]?.defaultValue)
        assertEquals(BOOLEAN_OPTIONS, titleRename[TITLE_RENAME_PARAM_MATCH_REGEX]?.options)
        assertEquals(BOOL_TRUE, titleRename[TITLE_RENAME_PARAM_PREVIEW]?.defaultValue)

        assertEquals(TITLE_FORMAT_MODE_PER_CHAPTER, titleFormat[TITLE_FORMAT_PARAM_MODE]?.defaultValue)
        assertEquals(TITLE_FORMAT_MODE_OPTIONS, titleFormat[TITLE_FORMAT_PARAM_MODE]?.options)
        assertEquals(TITLE_FORMAT_STYLE_DOUBLE, titleFormat[TITLE_FORMAT_PARAM_STYLE]?.defaultValue)
        assertEquals(TITLE_FORMAT_STYLE_OPTIONS, titleFormat[TITLE_FORMAT_PARAM_STYLE]?.options)
        assertEquals(TOOL_SCOPE_ALL, titleFormat[TITLE_FORMAT_PARAM_SCOPE]?.defaultValue)
        assertEquals(TITLE_FORMAT_SCOPE_OPTIONS, titleFormat[TITLE_FORMAT_PARAM_SCOPE]?.options)
        assertEquals("", titleFormat[TITLE_FORMAT_PARAM_SELECTED_CHAPTERS]?.defaultValue)
        assertEquals(BOOL_TRUE, titleFormat[TITLE_FORMAT_PARAM_PREVIEW]?.defaultValue)
    }

    @Test
    fun cookieParametersAreRegisteredAsSensitiveAndOptionKeysAreUnique() {
        val cookieKeys = defaultEditorToolDefinitions()
            .flatMap { it.parameters }
            .map { it.key }
            .filter { it.contains("cookie", ignoreCase = true) }
            .toSet()
        val optionLists = listOf(
            BOOLEAN_OPTIONS,
            TITLE_RENAME_SCOPE_OPTIONS,
            TEXT_REPLACE_MODE_OPTIONS,
            TEXT_REPLACE_TARGET_OPTIONS,
            TEXT_REPLACE_BATCH_SOURCE_OPTIONS,
            TEXT_REPLACE_SCOPE_OPTIONS,
            EPUB_TEXT_REPLACE_BATCH_SCOPE_OPTIONS,
            TXT_TEXT_REPLACE_SCOPE_OPTIONS,
            TITLE_FORMAT_MODE_OPTIONS,
            TITLE_FORMAT_STYLE_OPTIONS,
            TITLE_FORMAT_SCOPE_OPTIONS,
            INSERT_CHAPTER_SOURCE_OPTIONS,
            COVER_MODE_OPTIONS,
            COVER_IMAGE_INSERT_OPTIONS,
            FETCH_INFO_SOURCE_OPTIONS,
            FETCH_INFO_SEARCH_OPTIONS,
            FETCH_INFO_CONTENT_OPTIONS
        )

        assertEquals(cookieKeys, SENSITIVE_PARAMETER_KEYS)
        optionLists.forEach { options ->
            val keys = options.map { it.first }
            assertEquals("duplicate option keys in $keys", keys.distinct(), keys)
        }
    }
}
