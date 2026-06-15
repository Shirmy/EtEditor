package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.EpubBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun loadInsertChapterSosadSource(
    parameters: InsertChapterParameters,
    fetchQuery: String,
    sourceUri: String
): InsertChapterSourceData {
    val query = parameters.sosadQuery.trim()
    if (query.isBlank()) error("请输入废文书名或网址")
    if (parameters.sosadAuthCookie.isBlank()) error("请先登录废文")
    val fetchParameters = insertChapterSosadFetchParameters(parameters.copy(sosadQuery = fetchQuery))
    val fetched = FetchInfoFetcherFactory.create(FetchInfoSources.SOSAD).fetch(fetchParameters)
    val chapters = insertChapterSosadCatalogRange(fetched.catalog, parameters)
        .mapIndexed { index, item ->
            val sourceIndex = (item.index - 1).coerceAtLeast(0)
            val title = item.title
                .ifBlank { item.chapterTitle }
                .ifBlank { "第${index + 1}章" }
            InsertableChapter(
                sourceIndex = sourceIndex,
                title = title,
                fileName = "废文-${(index + 1).toString().padStart(4, '0')}",
                sourcePath = item.url,
                html = null,
                text = "",
                wordCount = 0,
                tocLevel = 0,
                isVolume = false
            )
        }
    if (chapters.isEmpty()) error("抓取范围内没有废文目录")
    return InsertChapterSourceData(
        sourceUri = sourceUri,
        sourceType = INSERT_CHAPTER_SOURCE_SOSAD,
        originalName = fetched.title.ifBlank { "废文" },
        chapters = chapters
    )
}

internal fun insertChapterSosadFetchParameters(
    parameters: InsertChapterParameters
): FetchInfoParameters {
    return FetchInfoParameters(
        source = FetchInfoSources.SOSAD,
        searchMode = FETCH_INFO_SEARCH_KEYWORD,
        query = parameters.sosadQuery.trim(),
        content = FETCH_INFO_CONTENT_CATALOG,
        fetchCatalog = true,
        fetchIntro = false,
        fetchCover = false,
        authCookie = parameters.sosadAuthCookie,
        bodyRangeStart = parameters.sosadBodyRangeStart,
        bodyRangeEnd = parameters.sosadBodyRangeEnd,
        catalogFilter = "",
        autoTitleFormat = false,
        introFilter = "",
        writeCatalog = false,
        writeIntro = false,
        introTargetPath = DEFAULT_FETCH_INFO_INTRO_TARGET,
        writeCover = false
    )
}

internal fun insertChapterSosadCatalogRange(
    catalog: List<FetchedCatalogItem>,
    parameters: InsertChapterParameters
): List<FetchedCatalogItem> {
    val start = parameters.sosadBodyRangeStart.coerceAtLeast(1)
    val end = parameters.sosadBodyRangeEnd.takeIf { it >= start } ?: Int.MAX_VALUE
    var normalIndex = 0
    return catalog.mapNotNull { item ->
        if (item.isVolume) {
            null
        } else {
            normalIndex += 1
            item.takeIf { normalIndex in start..end }
        }
    }
}

internal suspend fun loadInsertChapterSosadBodies(
    parameters: InsertChapterParameters,
    selected: List<InsertableChapter>,
    targetBook: EpubBook?,
    onProgress: (Int, Int) -> Unit = { _, _ -> }
): List<InsertableChapter> {
    val missing = selected.filter { chapter ->
        !chapter.isVolume && chapter.text.isBlank()
    }
    if (missing.isEmpty()) return selected
    if (missing.any { it.sourcePath.isBlank() }) error("废文目录没有可抓取的正文链接")
    val bodyByUrl = SosadFetcher().fetchBodyDetailsForUrls(
        parameters = insertChapterSosadFetchParameters(parameters),
        urls = missing.map { it.sourcePath },
        onProgress = onProgress
    )
    val reservedImageStems = mutableSetOf<String>()
    val next = withContext(Dispatchers.IO) {
        selected.map { chapter ->
            if (chapter.isVolume || chapter.text.isNotBlank()) {
                chapter
            } else {
                val body = bodyByUrl[chapter.sourcePath] ?: return@map chapter
                val text = body.text.trim()
                val hasBodyContent = text.isNotBlank() || body.blocks.any { block ->
                    block.text.isNotBlank() || block.imageUrl.isNotBlank()
                }
                if (!hasBodyContent) {
                    chapter
                } else {
                    val prepared = prepareSosadInsertChapterBody(
                        book = targetBook,
                        parameters = parameters,
                        chapterUrl = chapter.sourcePath,
                        body = body,
                        reservedImageStems = reservedImageStems
                    )
                    val insertText = plainTextFromSosadInsertChapterBlocks(prepared.first).ifBlank { text }
                    chapter.copy(
                        text = insertText,
                        wordCount = ChapterDetector.countVisibleChars(insertText),
                        bodyBlocks = prepared.first,
                        imageResources = prepared.second
                    )
                }
            }
        }
    }
    if (next.any { chapter ->
            !chapter.isVolume &&
                chapter.text.isBlank() &&
                chapter.bodyBlocks.none { block -> block.text.isNotBlank() || block.imageFileName.isNotBlank() }
        }) {
        error("选中章节没有抓到正文，请确认范围和登录状态")
    }
    return next
}

internal fun prepareSosadInsertChapterBody(
    book: EpubBook?,
    parameters: InsertChapterParameters,
    chapterUrl: String,
    body: FetchedSosadBody,
    reservedImageStems: MutableSet<String>
): Pair<List<InsertChapterBodyBlock>, List<InsertChapterImageResource>> {
    val imageResources = mutableListOf<InsertChapterImageResource>()
    val blocks = buildList {
        body.blocks.forEach { block ->
            when {
                block.imageUrl.isNotBlank() -> {
                    if (book == null) return@forEach
                    val image = downloadSosadInsertChapterImage(
                        book = book,
                        parameters = parameters,
                        imageUrl = block.imageUrl,
                        chapterUrl = chapterUrl,
                        reservedImageStems = reservedImageStems
                    )
                    imageResources += image
                    add(InsertChapterBodyBlock(imageFileName = image.fileName))
                }
                block.text.isNotBlank() -> {
                    addSosadFetchedTextBlock(block.text, block.cssClass)
                }
            }
        }
    }.ifEmpty {
        body.text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line -> InsertChapterBodyBlock(text = line) }
    }
    return blocks to imageResources
}

private fun MutableList<InsertChapterBodyBlock>.addSosadFetchedTextBlock(
    rawText: String,
    rawCssClass: String
) {
    if (rawText.isBlank()) return
    val text = rawText.trim()
    when (rawCssClass) {
        SOSAD_BODY_CSS_SYS -> {
            add(InsertChapterBodyBlock(text = text, cssClass = SOSAD_BODY_CSS_SYS))
        }
        SOSAD_BODY_CSS_AUTHOR_NOTE -> {
            addSosadAuthorNoteSeparator()
            add(InsertChapterBodyBlock(text = text))
        }
        else -> {
            add(InsertChapterBodyBlock(text = text))
        }
    }
}

private fun MutableList<InsertChapterBodyBlock>.addSosadAuthorNoteSeparator() {
    if (lastOrNull()?.text == SOSAD_SYS_SEPARATOR) return
    if (any { block -> block.text == SOSAD_SYS_SEPARATOR }) return
    add(InsertChapterBodyBlock(text = SOSAD_SYS_SEPARATOR))
}

private fun plainTextFromSosadInsertChapterBlocks(blocks: List<InsertChapterBodyBlock>): String {
    return blocks.asSequence()
        .mapNotNull { block -> block.text.takeIf { it.isNotBlank() } }
        .joinToString("\n")
        .trim()
}

private const val SOSAD_SYS_SEPARATOR = "-----------------------"

private fun downloadSosadInsertChapterImage(
    book: EpubBook,
    parameters: InsertChapterParameters,
    imageUrl: String,
    chapterUrl: String,
    reservedImageStems: MutableSet<String>
): InsertChapterImageResource {
    val safeImageUrl = requireSosadAllowedImageUrl(imageUrl, "废文正文图片链接")
    val response = FetchHttpClient.getBytes(
        safeImageUrl,
        buildSosadInsertChapterImageHeaders(parameters.sosadAuthCookie, safeImageUrl, chapterUrl),
        ::isSosadImageHttpsRedirect,
        maxBytes = HTTP_IMAGE_RESPONSE_MAX_BYTES
    )
    if (response.bytes.isEmpty()) error("废文图片内容为空：$safeImageUrl")
    val mediaType = coverMediaTypeFromBytes(response.bytes)
        ?: coverMediaType(safeImageUrl, response.contentType)
    validateInsertImageMediaType(mediaType)
    val fileName = nextCustomInsertImageFileName(
        book = book,
        extension = coverExtension(mediaType),
        reservedStems = reservedImageStems
    )
    return InsertChapterImageResource(
        url = safeImageUrl,
        fileName = fileName,
        bytes = response.bytes,
        mediaType = mediaType
    )
}
