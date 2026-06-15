package com.eteditor

import com.eteditor.core.DocumentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun EditorController.insertChapterParameters(tool: EditorTool? = null): InsertChapterParameters {
    val values = if (tool == null) {
        defaultToolParameters("insert_chapter")
    } else {
        mergedToolParameters(tool)
    }
    return buildInsertChapterParameters(
        values = values,
        defaultQuery = defaultCoverTitle(),
        sosadLoginCookie = sosadLoginCookie(),
        falseValue = BOOL_FALSE
    )
}

fun EditorController.clearInsertChapterSourcePreview() {
    insertChapterSourcePreview = null
    insertChapterSourceData = null
}

fun EditorController.insertChapterSosadSourceUri(query: String, startRaw: String, endRaw: String): String {
    return buildInsertChapterSosadSourceUri(
        baseUri = INSERT_CHAPTER_SOSAD_URI,
        query = query,
        startRaw = startRaw,
        endRaw = endRaw
    )
}

private fun EditorController.insertChapterSosadSourceUri(parameters: InsertChapterParameters): String {
    return insertChapterSosadSourceUri(
        query = parameters.sosadQuery,
        startRaw = parameters.sosadBodyRangeStart.toString(),
        endRaw = parameters.sosadBodyRangeEnd.takeIf { it > 0 }?.toString().orEmpty()
    )
}

private fun EditorController.setInsertChapterSourceData(
    data: InsertChapterSourceData,
    previewSourceType: String = data.sourceType
) {
    insertChapterSourceData = data
    insertChapterSourcePreview = buildInsertChapterSourcePreview(data, previewSourceType)
}

suspend fun EditorController.prepareInsertChapterSourcePreview(sourceType: String, sourceUri: String): Boolean {
    if (sourceUri.isBlank()) {
        statusMessage = "请选择来源文件"
        return false
    }
    val previewSourceType = normalizeInsertChapterSourceType(sourceType)
    return try {
        val data = loadInsertChapterSource(appContext.contentResolver, previewSourceType, sourceUri)
        setInsertChapterSourceData(data, previewSourceType)
        statusMessage = "来源已读取：${data.chapters.size} 章"
        true
    } catch (error: Throwable) {
        clearInsertChapterSourcePreview()
        statusMessage = "来源读取失败：${error.message ?: error.javaClass.simpleName}"
        false
    }
}

suspend fun EditorController.prepareInsertChapterSosadPreviewForBuiltIn(
    toolId: String,
    onPhase: (String) -> Unit = {}
): Boolean {
    if (toolId != "insert_chapter") return false
    return prepareInsertChapterSosadPreview(builtInEditorTool(toolId), onPhase)
}

suspend fun EditorController.prepareInsertChapterSosadRangeCatalogForBuiltIn(
    toolId: String,
    onPhase: (String) -> Unit = {}
): Boolean {
    if (toolId != "insert_chapter") return false
    return prepareInsertChapterSosadCatalog(
        tool = builtInEditorTool(toolId),
        fullRange = true,
        onPhase = onPhase
    )
}

suspend fun EditorController.prepareInsertChapterSosadPreviewForAutomationStep(stepId: String): Boolean {
    val tool = automationStepToolForPreview(stepId) ?: return false
    if (tool.toolId != "insert_chapter") return false
    return prepareInsertChapterSosadPreview(tool)
}

private suspend fun EditorController.prepareInsertChapterSosadPreview(
    tool: EditorTool,
    onPhase: (String) -> Unit = {}
): Boolean {
    return prepareInsertChapterSosadCatalog(tool = tool, fullRange = false, onPhase = onPhase)
}

private suspend fun EditorController.prepareInsertChapterSosadCatalog(
    tool: EditorTool,
    fullRange: Boolean,
    onPhase: (String) -> Unit = {}
): Boolean {
    val parameters = insertChapterParameters(tool)
    if (parameters.sourceType != INSERT_CHAPTER_SOURCE_SOSAD) {
        statusMessage = "请选择废文来源"
        return false
    }
    if (!sosadLoginReady(parameters.sosadAuthCookie)) {
        statusMessage = if (parameters.sosadAuthCookie.isBlank()) "请先登录废文" else "废文登录已失效，请重新登录"
        if (parameters.sosadAuthCookie.isNotBlank()) sosadLoginInvalid = true
        return false
    }
    networkUnavailableMessageForContext(appContext, "废文目录读取失败")?.let { message ->
        statusMessage = message
        return false
    }
    clearFetchInfoSearchChoiceRequest(tool.id)
    return try {
        val catalogParameters = if (fullRange) {
            parameters.copy(sosadBodyRangeStart = 1, sosadBodyRangeEnd = 0)
        } else {
            parameters
        }
        val fetchParameters = insertChapterSosadFetchParameters(catalogParameters)
        onPhase("搜索中...")
        val phaseProgress: FetchInfoProgress = { message ->
            withContext(Dispatchers.Main.immediate) {
                onPhase(message)
            }
        }
        val choices = distinctVisibleSearchChoices(
            FetchInfoFetcherFactory.create(FetchInfoSources.SOSAD).searchChoices(fetchParameters, phaseProgress)
        )
        val preferredChoice = preferredSearchChoiceByMetadata(choices, parameters.sosadQuery)
        if (choices.size > 1 && preferredChoice == null) {
            fetchInfoSearchChoiceRequest = FetchInfoSearchChoiceRequest(
                toolId = tool.id,
                parameters = fetchParameters,
                choices = choices
            )
            statusMessage = "请选择废文搜索结果"
            return false
        }
        val fetchQuery = (preferredChoice ?: choices.firstOrNull())?.detailUrl ?: parameters.sosadQuery
        onPhase("正在抓取目录")
        prepareInsertChapterSosadPreviewWithQuery(
            toolId = tool.id,
            parameters = catalogParameters,
            fetchQuery = fetchQuery,
            sourceUri = insertChapterSosadSourceUri(catalogParameters)
        )
    } catch (error: Throwable) {
        clearInsertChapterSourcePreview()
        statusMessage = networkAwareErrorMessage("废文目录读取失败", error)
        markSosadLoginInvalidIfNeeded(statusMessage, error)
        if (parameters.sosadAuthCookie.isNotBlank() &&
            statusMessage.contains("废文目录") &&
            statusMessage.contains("没有")
        ) {
            sosadLoginInvalid = true
        }
        false
    }
}

suspend fun EditorController.selectInsertChapterSosadSearchChoice(toolId: String, choice: FetchInfoSearchChoice): Boolean {
    val request = fetchInfoSearchChoiceRequest ?: run {
        statusMessage = "没有可选择的搜索结果"
        return false
    }
    if (request.toolId != toolId) {
        statusMessage = "搜索结果已变化"
        return false
    }
    if (request.parameters.source != FetchInfoSources.SOSAD) {
        statusMessage = "搜索结果来源不匹配"
        return false
    }
    if (request.choices.none { it.detailUrl == choice.detailUrl }) {
        statusMessage = "搜索结果已失效"
        return false
    }
    val parameters = InsertChapterParameters(
        sourceType = INSERT_CHAPTER_SOURCE_SOSAD,
        sosadQuery = request.parameters.query,
        sosadAuthCookie = request.parameters.authCookie,
        sosadBodyRangeStart = request.parameters.bodyRangeStart,
        sosadBodyRangeEnd = request.parameters.bodyRangeEnd,
        preview = true
    )
    return prepareInsertChapterSosadPreviewWithQuery(
        toolId = toolId,
        parameters = parameters,
        fetchQuery = choice.detailUrl,
        sourceUri = insertChapterSosadSourceUri(parameters)
    )
}

private suspend fun EditorController.prepareInsertChapterSosadPreviewWithQuery(
    toolId: String,
    parameters: InsertChapterParameters,
    fetchQuery: String,
    sourceUri: String
): Boolean {
    if (!sosadLoginReady(parameters.sosadAuthCookie)) {
        statusMessage = if (parameters.sosadAuthCookie.isBlank()) "请先登录废文" else "废文登录已失效，请重新登录"
        if (parameters.sosadAuthCookie.isNotBlank()) sosadLoginInvalid = true
        return false
    }
    networkUnavailableMessageForContext(appContext, "废文目录读取失败")?.let { message ->
        statusMessage = message
        return false
    }
    return try {
        val data = loadInsertChapterSosadSource(
            parameters = parameters,
            fetchQuery = fetchQuery,
            sourceUri = sourceUri
        )
        setInsertChapterSourceData(data)
        clearFetchInfoSearchChoiceRequest(toolId)
        statusMessage = "废文目录已读取：${data.chapters.size} 章，执行时抓正文"
        true
    } catch (error: Throwable) {
        clearInsertChapterSourcePreview()
        statusMessage = networkAwareErrorMessage("废文目录读取失败", error)
        markSosadLoginInvalidIfNeeded(statusMessage, error)
        if (parameters.sosadAuthCookie.isNotBlank() &&
            statusMessage.contains("废文目录") &&
            statusMessage.contains("没有")
        ) {
            sosadLoginInvalid = true
        }
        false
    }
}

suspend fun EditorController.insertChaptersFromBuiltIn(
    sourceUri: String,
    positionMode: String,
    targetChapterIndex: Int?,
    selectedSourceIndices: Set<Int>,
    useSelectedSourceIndices: Boolean = false,
    reverseSelectedOrder: Boolean = false,
    onProgress: InsertChapterProgressCallback = { _, _, _ -> }
): Boolean {
    return insertChaptersFromSource(
        tool = builtInEditorTool("insert_chapter"),
        sourceUri = sourceUri,
        positionMode = positionMode,
        targetChapterIndex = targetChapterIndex,
        selectedSourceIndices = selectedSourceIndices,
        useSelectedSourceIndices = useSelectedSourceIndices,
        reverseSelectedOrder = reverseSelectedOrder,
        onProgress = onProgress
    )
}

suspend fun EditorController.insertChaptersFromEditorTool(
    editorToolId: String,
    sourceUri: String,
    positionMode: String,
    targetChapterIndex: Int?,
    selectedSourceIndices: Set<Int>,
    useSelectedSourceIndices: Boolean = false,
    reverseSelectedOrder: Boolean = false,
    onProgress: InsertChapterProgressCallback = { _, _, _ -> }
): Boolean {
    val tool = selectedEditorTool?.takeIf { it.id == editorToolId }
        ?: editorTools.firstOrNull { it.id == editorToolId }
        ?: return false
    if (tool.toolId != "insert_chapter") return false
    return insertChaptersFromSource(
        tool = tool,
        sourceUri = sourceUri,
        positionMode = positionMode,
        targetChapterIndex = targetChapterIndex,
        selectedSourceIndices = selectedSourceIndices,
        useSelectedSourceIndices = useSelectedSourceIndices,
        reverseSelectedOrder = reverseSelectedOrder,
        onProgress = onProgress
    )
}

suspend fun EditorController.insertChaptersFromAutomationStep(
    stepId: String,
    sourceUri: String,
    positionMode: String,
    targetChapterIndex: Int?,
    selectedSourceIndices: Set<Int>,
    useSelectedSourceIndices: Boolean = false,
    reverseSelectedOrder: Boolean = false,
    onProgress: InsertChapterProgressCallback = { _, _, _ -> }
): Boolean {
    val tool = automationStepToolForPreview(stepId) ?: return false
    if (tool.toolId != "insert_chapter") return false
    return insertChaptersFromSource(
        tool = tool,
        sourceUri = sourceUri,
        positionMode = positionMode,
        targetChapterIndex = targetChapterIndex,
        selectedSourceIndices = selectedSourceIndices,
        useSelectedSourceIndices = useSelectedSourceIndices,
        reverseSelectedOrder = reverseSelectedOrder,
        onProgress = onProgress
    )
}

internal suspend fun EditorController.insertChaptersFromSource(
    tool: EditorTool,
    sourceUri: String,
    positionMode: String,
    targetChapterIndex: Int?,
    selectedSourceIndices: Set<Int>,
    useSelectedSourceIndices: Boolean,
    reverseSelectedOrder: Boolean,
    onProgress: InsertChapterProgressCallback = { _, _, _ -> }
): Boolean {
    if (kind == DocumentKind.None) {
        statusMessage = "请先打开 EPUB 或 TXT"
        return false
    }
    val parameters = insertChapterParameters(tool)
    if (parameters.sourceType == INSERT_CHAPTER_SOURCE_SOSAD) {
        if (parameters.sosadQuery.isBlank()) {
            statusMessage = "请输入废文书名或网址"
            return false
        }
        if (parameters.sosadAuthCookie.isBlank()) {
            statusMessage = "请先登录废文"
            return false
        }
        if (!sosadLoginReady(parameters.sosadAuthCookie)) {
            statusMessage = "废文登录已失效，请重新登录"
            sosadLoginInvalid = true
            return false
        }
        networkUnavailableMessageForContext(appContext, "废文正文读取失败")?.let { message ->
            statusMessage = message
            return false
        }
    } else if (sourceUri.isBlank()) {
        statusMessage = "请选择来源文件"
        return false
    }
    val source = insertChapterSourceData
        ?.takeIf { data -> insertChapterSourceDataMatches(data, sourceUri, parameters.sourceType) }
        ?: try {
            val loaded = if (parameters.sourceType == INSERT_CHAPTER_SOURCE_SOSAD) {
                if (!prepareInsertChapterSosadPreview(tool)) return false
                insertChapterSourceData
                    ?.takeIf { it.sourceUri == sourceUri && it.sourceType == parameters.sourceType }
                    ?: error("废文目录读取失败")
            } else {
                loadInsertChapterSource(appContext.contentResolver, parameters.sourceType, sourceUri)
            }
            loaded.also { data ->
                setInsertChapterSourceData(data, parameters.sourceType)
            }
        } catch (error: Throwable) {
            statusMessage = if (parameters.sourceType == INSERT_CHAPTER_SOURCE_SOSAD) {
                networkAwareErrorMessage("废文目录读取失败", error)
            } else {
                "来源读取失败：${error.message ?: error.javaClass.simpleName}"
            }
            if (parameters.sourceType == INSERT_CHAPTER_SOURCE_SOSAD) {
                markSosadLoginInvalidIfNeeded(statusMessage, error)
            }
            return false
        }

    val selected = selectInsertableChapters(
        source = source,
        selectedSourceIndices = selectedSourceIndices,
        useSelectedSourceIndices = useSelectedSourceIndices,
        reverseSelectedOrder = reverseSelectedOrder
    )
    if (selected.isEmpty()) {
        statusMessage = "没有可插入章节"
        return false
    }

    val chaptersToInsert = if (parameters.sourceType == INSERT_CHAPTER_SOURCE_SOSAD) {
        try {
            statusMessage = "正在读取废文正文：${selected.size} 章"
            loadInsertChapterSosadBodies(
                parameters = parameters,
                selected = selected,
                targetBook = epub.takeIf { kind == DocumentKind.Epub },
                onProgress = { completed, total ->
                    onProgress("读取正文", completed, total)
                }
            )
        } catch (error: Throwable) {
            statusMessage = networkAwareErrorMessage("废文正文读取失败", error)
            markSosadLoginInvalidIfNeeded(statusMessage, error)
            return false
        }
    } else {
        selected
    }

    return when (kind) {
        DocumentKind.Epub -> insertChaptersIntoEpub(source, chaptersToInsert, positionMode, targetChapterIndex, onProgress)
        DocumentKind.Txt -> insertChaptersIntoTxt(chaptersToInsert, positionMode, targetChapterIndex, onProgress)
        DocumentKind.None -> false
    }
}

private suspend fun EditorController.insertChaptersIntoEpub(
    source: InsertChapterSourceData,
    selected: List<InsertableChapter>,
    positionMode: String,
    targetChapterIndex: Int?,
    onProgress: InsertChapterProgressCallback
): Boolean {
    val sourceBook = epub ?: return false
    val nextBook = sourceBook.mutableDeepCopy()
    val currentChapterIndex = previewChapterIndex
    onProgress("插入章节", 0, selected.size.coerceAtLeast(1))
    val result = withContext(Dispatchers.Default) {
        insertChaptersIntoEpubBook(
            book = nextBook,
            source = source,
            selected = selected,
            positionMode = positionMode,
            targetChapterIndex = targetChapterIndex,
            currentChapterIndex = currentChapterIndex,
            onProgress = { _, _ -> }
        )
    }
    onProgress("插入章节", selected.size, selected.size.coerceAtLeast(1))
    epub = nextBook
    previewChapterIndex = result.insertPosition.coerceIn(0, nextBook.chapters.lastIndex.coerceAtLeast(0))
    checkReport = null
    markDocumentChanged()
    clearFileRenamePlan()
    clearTextSearchState()
    refreshChapters()
    statusMessage = buildString {
        append("已插入 ${result.insertedCount} 章")
        if (result.insertedImages > 0) append("，图片 ${result.insertedImages} 张")
        if (result.renamedFiles > 0) append("，文件名连号 ${result.renamedFiles}")
    }
    return true
}

private suspend fun EditorController.insertChaptersIntoTxt(
    selected: List<InsertableChapter>,
    positionMode: String,
    targetChapterIndex: Int?,
    onProgress: InsertChapterProgressCallback
): Boolean {
    if (warnTxtMoveChapterSyncPending("插入章节")) return false
    val document = txt ?: return false
    val currentChapterIndex = previewChapterIndex
    val snapshot = document.copy(chapters = document.chapters.toList())
    onProgress("插入章节", 0, selected.size.coerceAtLeast(1))
    val result = withContext(Dispatchers.Default) {
        insertChaptersIntoTxtDocumentText(
            document = snapshot,
            selected = selected,
            positionMode = positionMode,
            targetChapterIndex = targetChapterIndex,
            currentChapterIndex = currentChapterIndex,
            onProgress = { _, _ -> }
        )
    }
    if (result == null) {
        statusMessage = "没有可插入章节"
        return false
    }
    val config = currentTxtChapterDetectionConfig()
    val autoKeys = txtEnabledChapterRuleKeys
    val supplementedCatalogLines = txtSupplementedCatalogLines
    val nextChapters = withContext(Dispatchers.Default) {
        detectTxtChaptersWithCatalogConfig(
            text = result.text,
            config = config,
            autoKeys = autoKeys,
            supplementedCatalogLines = supplementedCatalogLines
        )
    }
    onProgress("插入章节", selected.size, selected.size.coerceAtLeast(1))
    document.text = result.text
    document.chapters = nextChapters
    applyTxtCatalogPurifyRulesAfterCatalogChange()
    previewChapterIndex = result.insertPosition.coerceIn(0, document.chapters.lastIndex.coerceAtLeast(0))
    checkReport = null
    markDocumentChanged()
    clearTextSearchState()
    refreshChapters()
    statusMessage = "已插入 ${result.insertedCount} 章"
    return true
}
