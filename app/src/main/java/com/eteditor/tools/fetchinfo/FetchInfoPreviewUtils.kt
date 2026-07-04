package com.eteditor

import com.eteditor.core.DocumentKind
import com.eteditor.core.EpubBook

internal fun defaultFetchInfoQueryForDocument(
    kind: DocumentKind,
    searchMode: String,
    epubMetadataTitle: String,
    epubMetadataAuthor: String,
    title: String,
    authorSearchMode: String,
    keywordSearchMode: String
): String {
    return when (kind) {
        DocumentKind.Epub -> when (searchMode) {
            authorSearchMode -> epubMetadataAuthor
            keywordSearchMode -> ""
            else -> epubMetadataTitle.ifBlank { documentTitleWithoutExtension(title) }
        }
        DocumentKind.Txt -> documentTitleWithoutExtension(title).ifBlank { title }
        DocumentKind.None -> ""
    }
}

internal fun defaultCoverTitleForDocument(
    kind: DocumentKind,
    epubMetadataTitle: String,
    title: String
): String {
    return when (kind) {
        DocumentKind.Epub -> epubMetadataTitle
        DocumentKind.Txt -> documentTitleWithoutExtension(title).ifBlank { title }
        DocumentKind.None -> ""
    }
}

internal fun buildFetchInfoCatalogSummary(
    targetCount: Int,
    preview: FetchInfoPreview
): String {
    val fetchedCount = preview.filtered.catalog.count { !it.isVolume }
    return "原章节 ${targetCount} 章  抓取章节 ${fetchedCount} 章"
}

internal fun buildFetchInfoCatalogPreviewRows(
    book: EpubBook,
    preview: FetchInfoPreview,
    filtered: Boolean,
    fallbackChapterIndex: Int,
    renames: Map<Int, String> = emptyMap(),
    catalogOrder: List<Int>? = null
): List<FetchInfoCatalogPreviewRow> {
    val catalog = if (filtered) preview.filtered.catalog else preview.raw.catalog
    val targets = fetchInfoCatalogTargetChapters(book).map { it.second }
    val autoStyle = if (filtered) {
        fetchInfoCatalogAutoTitleStyle(preview.parameters, targets, catalog)
    } else {
        null
    }
    val usedVolumePaths = mutableSetOf<String>()
    var targetCursor = 0
    // 移动只支持无卷情况：catalogOrder 非 null 时按它给定的原始 position 顺序遍历非卷项，
    // position 取原始 position（稳定编号，deletes/renames 用它匹配），targetCursor 只用于配对 epub 章节。
    val nonVolumeCatalog = catalog.filter { !it.isVolume }
    val orderedNonVolumeItems: List<Pair<Int, FetchedCatalogItem>>? = catalogOrder?.mapNotNull { originalPosition ->
        nonVolumeCatalog.getOrNull(originalPosition)?.let { originalPosition to it }
    }
    return buildList {
        if (orderedNonVolumeItems != null) {
            // 无卷移动路径：按 catalogOrder 遍历非卷项
            orderedNonVolumeItems.forEach { (originalPosition, fetched) ->
                val position = originalPosition
                val chapter = targets.getOrNull(targetCursor)
                targetCursor += 1
                val baseTitle = chapter
                    ?.let { fetchInfoCatalogWriteBackTitle(it.title, fetched, autoStyle) }
                    ?: fetched.previewText()
                val isRenamed = renames.containsKey(position)
                val fetchedTitle = when {
                    isRenamed -> renames.getValue(position)
                    else -> baseTitle
                }
                add(
                    FetchInfoCatalogPreviewRow(
                        fileName = chapter?.path?.substringAfterLast('/').orEmpty(),
                        originalTitle = chapter?.title.orEmpty(),
                        fetchedTitle = fetchedTitle,
                        isVolume = false,
                        skipped = chapter == null,
                        chapterPosition = position,
                        fetchedItem = fetched,
                        autoStyle = autoStyle,
                        renamed = isRenamed
                    )
                )
            }
        } else {
            catalog.forEach { fetched ->
                if (fetched.isVolume) {
                    // 目标章节已用尽后出现的卷，后面没有可承载的章节，标记为不写入。
                    val volumeSkipped = targetCursor >= targets.size
                    val insertPosition = fetchInfoVolumeInsertPosition(book, targets, targetCursor, fallbackChapterIndex)
                    val volumeChapter = if (volumeSkipped) null else findFetchInfoAdjacentVolume(book, insertPosition, usedVolumePaths)
                    if (volumeChapter != null) usedVolumePaths += volumeChapter.path
                    add(
                        FetchInfoCatalogPreviewRow(
                            fileName = volumeChapter?.path?.substringAfterLast('/').orEmpty(),
                            originalTitle = volumeChapter?.title.orEmpty(),
                            fetchedTitle = fetched.previewText(),
                            isVolume = true,
                            willCreateVolume = !volumeSkipped && volumeChapter == null,
                            skipped = volumeSkipped
                        )
                    )
                } else {
                    val position = targetCursor
                    val chapter = targets.getOrNull(targetCursor)
                    targetCursor += 1
                    val baseTitle = chapter
                        ?.let { fetchInfoCatalogWriteBackTitle(it.title, fetched, autoStyle) }
                        ?: fetched.previewText()
                    val isRenamed = renames.containsKey(position)
                    val fetchedTitle = when {
                        isRenamed -> renames.getValue(position)
                        else -> baseTitle
                    }
                    add(
                        FetchInfoCatalogPreviewRow(
                            fileName = chapter?.path?.substringAfterLast('/').orEmpty(),
                            originalTitle = chapter?.title.orEmpty(),
                            fetchedTitle = fetchedTitle,
                            isVolume = false,
                            skipped = chapter == null,
                            chapterPosition = position,
                            fetchedItem = fetched,
                            autoStyle = autoStyle,
                            renamed = isRenamed
                        )
                    )
                }
            }
        }
        while (targetCursor < targets.size) {
            val chapter = targets[targetCursor]
            targetCursor += 1
            add(
                FetchInfoCatalogPreviewRow(
                    fileName = chapter.path.substringAfterLast('/'),
                    originalTitle = chapter.title,
                    fetchedTitle = "",
                    isVolume = false,
                    missingFetch = true
                )
            )
        }
    }
}

internal fun fetchInfoParametersForSourceModel(
    base: FetchInfoParameters,
    source: String,
    defaultTitleQuery: String,
    sosadLoginCookie: String
): FetchInfoParameters {
    val content = base.content
    return base.copy(
        source = source,
        searchMode = FETCH_INFO_SEARCH_TITLE,
        query = defaultTitleQuery,
        fetchCatalog = content == FETCH_INFO_CONTENT_CATALOG,
        fetchIntro = content == FETCH_INFO_CONTENT_INTRO,
        fetchCover = content == FETCH_INFO_CONTENT_COVER,
        authCookie = if (source == FETCH_INFO_SOURCE_SOSAD) {
            base.authCookie.ifBlank { sosadLoginCookie }
        } else {
            ""
        },
        writeCatalog = content == FETCH_INFO_CONTENT_CATALOG,
        writeIntro = content == FETCH_INFO_CONTENT_INTRO,
        writeCover = content == FETCH_INFO_CONTENT_COVER,
    )
}

internal fun fetchedInfoHasRequestedContent(
    info: FetchedInfo,
    parameters: FetchInfoParameters
): Boolean {
    return when (parameters.content) {
        FETCH_INFO_CONTENT_COVER -> info.coverUrl.isNotBlank()
        FETCH_INFO_CONTENT_INTRO -> info.intro.isNotBlank()
        FETCH_INFO_CONTENT_CATALOG -> info.catalog.isNotEmpty()
        else -> false
    }
}

private fun documentTitleWithoutExtension(title: String): String {
    return title.substringAfterLast('/').substringBeforeLast('.')
}
