package com.eteditor

import com.eteditor.core.DocumentKind

internal fun parseIndexSet(raw: String): Set<Int> {
    return raw.split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it >= 0 }
        .toSet()
}

internal fun buildFileRenameParameters(
    values: Map<String, String>,
    falseValue: String
): FileRenameParameters {
    return FileRenameParameters(
        namingFormat = values[FILE_RENAME_PARAM_NAMING_FORMAT]
            .orEmpty()
            .ifBlank { DEFAULT_FILE_RENAME_PATTERN },
        preview = values[FILE_RENAME_PARAM_PREVIEW] != falseValue
    )
}

internal fun buildTextReplaceParameters(
    values: Map<String, String>,
    kind: DocumentKind,
    runtimeFile: String,
    batchSourceOptions: List<Pair<String, String>>,
    textReplaceScopeOptions: List<Pair<String, String>>,
    epubBatchScopeOptions: List<Pair<String, String>>,
    txtScopeOptions: List<Pair<String, String>>,
    defaultBatchSource: String,
    trueValue: String,
    falseValue: String
): TextReplaceParameters {
    val storedBatchSource = values[TEXT_REPLACE_PARAM_BATCH_SOURCE].orEmpty()
        .takeIf { current -> batchSourceOptions.any { it.first == current } }
        ?: defaultBatchSource
    val storedMode = values[TEXT_REPLACE_PARAM_MODE].orEmpty()
    val legacyReplacementMode = storedMode == TEXT_REPLACE_MODE_BATCH &&
        storedBatchSource == TEXT_REPLACE_BATCH_FILE
    val rawMode = when {
        storedMode == TEXT_REPLACE_MODE_REPLACEMENT || legacyReplacementMode -> {
            TEXT_REPLACE_MODE_REPLACEMENT
        }
        storedMode == TEXT_REPLACE_MODE_BATCH -> TEXT_REPLACE_MODE_BATCH
        storedMode == TEXT_REPLACE_MODE_SINGLE -> TEXT_REPLACE_MODE_SINGLE
        else -> TEXT_REPLACE_MODE_SINGLE
    }
    val batchSource = if (rawMode == TEXT_REPLACE_MODE_REPLACEMENT) {
        TEXT_REPLACE_BATCH_FILE
    } else {
        storedBatchSource
    }
    val replacementMode = rawMode == TEXT_REPLACE_MODE_REPLACEMENT
    val epubScope = when (rawMode) {
        TEXT_REPLACE_MODE_BATCH -> {
            cleanOptionValue(
                value = values[TEXT_REPLACE_PARAM_SCOPE].orEmpty(),
                options = epubBatchScopeOptions,
                fallback = TOOL_SCOPE_ALL
            )
        }
        TEXT_REPLACE_MODE_REPLACEMENT -> TOOL_SCOPE_ALL
        else -> {
            cleanOptionValue(
                value = values[TEXT_REPLACE_PARAM_SCOPE].orEmpty(),
                options = textReplaceScopeOptions,
                fallback = TOOL_SCOPE_ALL
            )
        }
    }
    val txtScope = cleanOptionValue(
        value = values[TEXT_REPLACE_PARAM_SCOPE].orEmpty(),
        options = txtScopeOptions,
        fallback = TOOL_SCOPE_ALL
    )
    if (kind == DocumentKind.Txt) {
        return TextReplaceParameters(
            mode = TEXT_REPLACE_MODE_SINGLE,
            target = TEXT_REPLACE_TARGET_SOURCE,
            scope = txtScope,
            selectedHtmlSourceIndices = emptySet(),
            matchPattern = "",
            matchRegexEnabled = true,
            findText = values[TEXT_REPLACE_PARAM_FIND].orEmpty(),
            replaceText = values[TEXT_REPLACE_PARAM_REPLACE].orEmpty(),
            findRegexEnabled = values[TEXT_REPLACE_PARAM_FIND_REGEX] == trueValue,
            batchSource = defaultBatchSource,
            batchText = "",
            batchFile = "",
            preview = values[TEXT_REPLACE_PARAM_PREVIEW] != falseValue
        )
    }
    return TextReplaceParameters(
        mode = rawMode,
        target = values[TEXT_REPLACE_PARAM_TARGET].orEmpty()
            .takeIf { it == TEXT_REPLACE_TARGET_VISIBLE || it == TEXT_REPLACE_TARGET_SOURCE }
            ?: TEXT_REPLACE_TARGET_SOURCE,
        scope = epubScope,
        selectedHtmlSourceIndices = emptySet(),
        matchPattern = "",
        matchRegexEnabled = true,
        findText = values[TEXT_REPLACE_PARAM_FIND].orEmpty(),
        replaceText = values[TEXT_REPLACE_PARAM_REPLACE].orEmpty(),
        findRegexEnabled = values[TEXT_REPLACE_PARAM_FIND_REGEX] == trueValue,
        batchSource = batchSource,
        batchText = values[TEXT_REPLACE_PARAM_BATCH_TEXT].orEmpty(),
        batchFile = runtimeFile.ifBlank { values[TEXT_REPLACE_PARAM_BATCH_FILE].orEmpty() },
        preview = if (replacementMode) true else values[TEXT_REPLACE_PARAM_PREVIEW] != falseValue
    )
}

internal fun effectiveTextReplaceParametersForRun(
    parameters: TextReplaceParameters
): TextReplaceParameters {
    return if (parameters.isReplacementMode()) {
        parameters.copy(target = TEXT_REPLACE_TARGET_SOURCE)
    } else {
        parameters
    }
}

internal fun buildTitleRenameParameters(
    values: Map<String, String>,
    toolScopes: Set<String>,
    falseValue: String
): TitleRenameParameters {
    return TitleRenameParameters(
        pattern = values[TITLE_RENAME_PARAM_PATTERN].orEmpty(),
        scope = values[TITLE_RENAME_PARAM_SCOPE]
            .orEmpty()
            .takeIf { it in toolScopes }
            ?: TOOL_SCOPE_ALL,
        matchPattern = values[TITLE_RENAME_PARAM_MATCH_PATTERN].orEmpty(),
        matchRegexEnabled = values[TITLE_RENAME_PARAM_MATCH_REGEX] != falseValue,
        preview = values[TITLE_RENAME_PARAM_PREVIEW] != falseValue
    )
}

internal fun buildTitleFormatParameters(
    values: Map<String, String>,
    modeOptions: List<Pair<String, String>>,
    styleOptions: List<Pair<String, String>>,
    scopeOptions: List<Pair<String, String>>,
    defaultScope: String,
    falseValue: String
): TitleFormatParameters {
    val mode = cleanOptionValue(
        value = values[TITLE_FORMAT_PARAM_MODE].orEmpty(),
        options = modeOptions,
        fallback = TITLE_FORMAT_MODE_PER_CHAPTER
    )
    return TitleFormatParameters(
        mode = mode,
        style = cleanOptionValue(
            value = values[TITLE_FORMAT_PARAM_STYLE].orEmpty(),
            options = styleOptions,
            fallback = TITLE_FORMAT_STYLE_DOUBLE
        ),
        preview = values[TITLE_FORMAT_PARAM_PREVIEW] != falseValue,
        scope = cleanOptionValue(
            value = values[TITLE_FORMAT_PARAM_SCOPE].orEmpty(),
            options = scopeOptions,
            fallback = defaultScope
        ),
        selectedChapterIndices = parseIndexSet(values[TITLE_FORMAT_PARAM_SELECTED_CHAPTERS].orEmpty())
    )
}

internal fun buildFetchInfoParameters(
    values: Map<String, String>,
    sourceOptions: List<Pair<String, String>>,
    contentOptionsForSource: (String) -> List<Pair<String, String>>,
    defaultQuery: String,
    expectedAuthor: String,
    sosadLoginCookie: String,
    introTargetPath: String,
    trueValue: String
): FetchInfoParameters {
    val source = cleanOptionValue(
        value = values[FETCH_INFO_PARAM_SOURCE].orEmpty(),
        options = sourceOptions,
        fallback = FETCH_INFO_SOURCE_JJWXC
    )
    val allowedContent = contentOptionsForSource(source).map { it.first }.toSet()
    val content = values[FETCH_INFO_PARAM_CONTENT]
        .orEmpty()
        .takeIf { it in allowedContent }
        ?: allowedContent.firstOrNull()
        ?: FETCH_INFO_CONTENT_CATALOG
    return FetchInfoParameters(
        source = source,
        searchMode = FETCH_INFO_SEARCH_TITLE,
        query = defaultQuery,
        expectedAuthor = expectedAuthor,
        content = content,
        fetchCatalog = content == FETCH_INFO_CONTENT_CATALOG,
        fetchIntro = content == FETCH_INFO_CONTENT_INTRO,
        fetchCover = content == FETCH_INFO_CONTENT_COVER,
        authCookie = values[FETCH_INFO_PARAM_AUTH_COOKIE].orEmpty()
            .ifBlank { sosadLoginCookie },
        bodyRangeStart = 1,
        bodyRangeEnd = 0,
        catalogFilter = values[FETCH_INFO_PARAM_CATALOG_FILTER].orEmpty(),
        catalogFilterEnabled = values[FETCH_INFO_PARAM_CATALOG_FILTER_ENABLED]?.let { it == trueValue } ?: true,
        autoTitleFormat = values[FETCH_INFO_PARAM_AUTO_TITLE_FORMAT] == trueValue,
        introFilter = values[FETCH_INFO_PARAM_INTRO_FILTER].orEmpty(),
        writeCatalog = content == FETCH_INFO_CONTENT_CATALOG,
        writeIntro = content == FETCH_INFO_CONTENT_INTRO,
        introTargetPath = introTargetPath,
        writeCover = content == FETCH_INFO_CONTENT_COVER
    )
}

internal fun buildInsertChapterParameters(
    values: Map<String, String>,
    defaultQuery: String,
    sosadLoginCookie: String,
    falseValue: String
): InsertChapterParameters {
    val sosadRangeStart = values[INSERT_CHAPTER_PARAM_SOSAD_RANGE_START]
        .orEmpty()
        .toIntOrNull()
        ?.coerceAtLeast(1)
        ?: 1
    return InsertChapterParameters(
        sourceType = normalizeInsertChapterSourceType(
            values[INSERT_CHAPTER_PARAM_SOURCE_TYPE].orEmpty()
        ),
        sosadQuery = values[INSERT_CHAPTER_PARAM_SOSAD_QUERY].orEmpty()
            .ifBlank { defaultQuery },
        sosadAuthCookie = values[INSERT_CHAPTER_PARAM_SOSAD_AUTH_COOKIE].orEmpty()
            .ifBlank { sosadLoginCookie },
        sosadBodyRangeStart = sosadRangeStart,
        sosadBodyRangeEnd = values[INSERT_CHAPTER_PARAM_SOSAD_RANGE_END]
            .orEmpty()
            .toIntOrNull()
            ?.takeIf { it >= sosadRangeStart }
            ?: 0,
        preview = values[INSERT_CHAPTER_PARAM_PREVIEW] != falseValue
    )
}

internal fun normalizeInsertChapterSourceType(value: String): String {
    return when (value) {
        INSERT_CHAPTER_SOURCE_SOSAD -> INSERT_CHAPTER_SOURCE_SOSAD
        else -> INSERT_CHAPTER_SOURCE_UPLOAD
    }
}

internal fun buildCoverParameters(
    values: Map<String, String>,
    modeOptions: List<Pair<String, String>>,
    imageInsertOptions: List<Pair<String, String>>,
    trueValue: String
): CoverParameters {
    val mode = cleanOptionValue(
        value = values[COVER_PARAM_MODE].orEmpty(),
        options = modeOptions,
        fallback = COVER_MODE_INSERT
    )
    return CoverParameters(
        mode = mode,
        title = values[COVER_PARAM_TITLE].orEmpty(),
        imageUri = values[COVER_PARAM_IMAGE_URI].orEmpty(),
        imageInsertType = cleanOptionValue(
            value = values[COVER_PARAM_IMAGE_INSERT_TYPE].orEmpty(),
            options = imageInsertOptions,
            fallback = COVER_IMAGE_INSERT_NOTE
        ),
        compress = values[COVER_PARAM_COMPRESS].orEmpty()
            .ifBlank { trueValue } == trueValue,
        preview = mode == COVER_MODE_GENERATE &&
            values[COVER_PARAM_PREVIEW].orEmpty() == trueValue
    )
}

private fun cleanOptionValue(
    value: String,
    options: List<Pair<String, String>>,
    fallback: String
): String {
    return value.takeIf { current -> options.any { it.first == current } } ?: fallback
}
