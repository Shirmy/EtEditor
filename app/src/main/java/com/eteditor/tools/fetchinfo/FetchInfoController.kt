package com.eteditor

import com.eteditor.core.DocumentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

suspend fun EditorController.prepareFetchInfoPreviewForEditorTool(editorToolId: String): Boolean {
    val tool = selectedEditorTool?.takeIf { it.id == editorToolId }
        ?: editorTools.firstOrNull { it.id == editorToolId }
        ?: return false
    if (tool.toolId != "fetch_info") return false
    return prepareFetchInfoPreview(tool)
}

suspend fun EditorController.prepareFetchInfoPreviewForBuiltIn(toolId: String): Boolean {
    if (toolId != "fetch_info") return false
    return prepareFetchInfoPreview(builtInEditorTool(toolId))
}

suspend fun EditorController.prepareFetchInfoPreviewForAutomationStep(stepId: String): Boolean {
    val tool = automationStepToolForPreview(stepId) ?: return false
    if (tool.toolId != "fetch_info") return false
    return prepareFetchInfoPreview(tool)
}

fun EditorController.fetchInfoInitialProgressTextForEditorTool(editorToolId: String): String {
    val tool = selectedEditorTool?.takeIf { it.id == editorToolId }
        ?: editorTools.firstOrNull { it.id == editorToolId }
        ?: return ""
    return fetchInfoInitialProgressText(tool)
}

fun EditorController.fetchInfoInitialProgressTextForBuiltIn(toolId: String): String {
    if (toolId != "fetch_info") return ""
    return fetchInfoInitialProgressText(builtInEditorTool(toolId))
}

fun EditorController.clearFetchInfoPreview(toolId: String? = null) {
    if (toolId == null || fetchInfoPreview?.toolId == toolId) {
        fetchInfoPreview = null
        fetchInfoSearchChoiceRequest = null
        fetchInfoRetryRequest = null
        fetchInfoProgress = 0f
    }
    clearFetchInfoSearchChoiceRequest(toolId)
    clearFetchInfoRetryRequest(toolId)
}

fun EditorController.clearFetchInfoSearchChoiceRequest(toolId: String? = null) {
    if (toolId == null || fetchInfoSearchChoiceRequest?.toolId == toolId) {
        fetchInfoSearchChoiceRequest = null
        if (fetchInfoPreview == null) fetchInfoProgress = 0f
    }
}

fun EditorController.clearFetchInfoRetryRequest(toolId: String? = null) {
    if (toolId == null || fetchInfoRetryRequest?.toolId == toolId) {
        fetchInfoRetryRequest = null
        if (fetchInfoPreview == null && fetchInfoSearchChoiceRequest == null) fetchInfoProgress = 0f
    }
}

internal fun EditorController.fetchInfoParameters(tool: EditorTool? = null): FetchInfoParameters {
    val baseValues = if (tool == null) {
        defaultToolParameters("fetch_info")
    } else {
        mergedToolParameters(tool)
    }
    // 目录/简介规则是 fetch_info 的全局设置，统一以全局值为准（内存优先，回退落盘默认），
    // 避免自动化步骤里残留的旧快照覆盖掉功能页里编辑/导入的最新规则。
    val values = withGlobalFetchInfoRules(baseValues)
    return buildFetchInfoParameters(
        values = values,
        sourceOptions = FETCH_INFO_SOURCE_OPTIONS,
        contentOptionsForSource = { source -> this.fetchInfoContentOptions(source) },
        defaultQuery = defaultFetchInfoQuery(FETCH_INFO_SEARCH_TITLE),
        expectedAuthor = epub?.metadataAuthor.orEmpty(),
        sosadLoginCookie = sosadLoginCookie(),
        introTargetPath = resolveFetchInfoIntroTarget(values[FETCH_INFO_PARAM_INTRO_TARGET].orEmpty(), epub),
        trueValue = BOOL_TRUE
    )
}

// 以全局 fetch_info 设置里的目录/简介规则覆盖传入值：内存覆盖优先，回退落盘默认。
private fun EditorController.withGlobalFetchInfoRules(values: Map<String, String>): Map<String, String> {
    val memory = builtInParameterOverrides["fetch_info"].orEmpty()
    val saved = savedBuiltInDefaultOverrides["fetch_info"].orEmpty()
    val result = values.toMutableMap()
    listOf(FETCH_INFO_PARAM_CATALOG_FILTER, FETCH_INFO_PARAM_INTRO_FILTER).forEach { key ->
        val globalValue = memory[key] ?: saved[key]
        if (globalValue != null) {
            result[key] = globalValue
        } else {
            result.remove(key)
        }
    }
    return result
}

fun EditorController.defaultFetchInfoQuery(): String {
    return defaultFetchInfoQuery(FETCH_INFO_SEARCH_TITLE)
}

fun EditorController.defaultFetchInfoQuery(searchMode: String): String {
    return defaultFetchInfoQueryForDocument(
        kind = kind,
        searchMode = searchMode,
        epubMetadataTitle = epub?.metadataTitle.orEmpty(),
        epubMetadataAuthor = epub?.metadataAuthor.orEmpty(),
        title = title,
        authorSearchMode = FETCH_INFO_SEARCH_AUTHOR,
        keywordSearchMode = FETCH_INFO_SEARCH_KEYWORD
    )
}

fun EditorController.fetchInfoWritableChapterCount(): Int {
    val book = epub ?: return 0
    return fetchInfoCatalogTargetChapters(book).size
}

fun EditorController.fetchInfoExistingIntroText(preview: FetchInfoPreview): String {
    val book = epub ?: return ""
    return extractEpubIntroText(book, preview.parameters.introTargetPath)
}

fun EditorController.fetchInfoCatalogSummary(preview: FetchInfoPreview): String {
    return buildFetchInfoCatalogSummary(fetchInfoWritableChapterCount(), preview)
}

fun EditorController.fetchInfoCatalogPreviewRows(
    preview: FetchInfoPreview,
    filtered: Boolean,
    renames: Map<Int, String> = emptyMap(),
    deletes: Set<Int> = emptySet()
): List<FetchInfoCatalogPreviewRow> {
    val book = epub ?: return emptyList()
    return buildFetchInfoCatalogPreviewRows(
        book = book,
        preview = preview,
        filtered = filtered,
        fallbackChapterIndex = previewChapterIndex,
        renames = renames,
        deletes = deletes
    )
}

internal fun EditorController.preferredSearchChoiceByMetadata(
    choices: List<FetchInfoSearchChoice>,
    query: String
): FetchInfoSearchChoice? {
    return preferredSearchChoiceByMetadata(
        choices = choices,
        query = query,
        metadataTitle = epub?.metadataTitle.orEmpty(),
        metadataAuthor = epub?.metadataAuthor.orEmpty()
    )
}

private fun EditorController.resolveFetchInfoSearchChoiceByMetadata(
    choices: List<FetchInfoSearchChoice>,
    query: String
): SearchChoiceResolution {
    return resolveFetchInfoSearchChoiceByMetadata(
        choices = choices,
        query = query,
        metadataTitle = epub?.metadataTitle.orEmpty(),
        metadataAuthor = epub?.metadataAuthor.orEmpty()
    )
}

private suspend fun EditorController.updateFetchInfoProgress(message: String, progress: Float? = null) {
    withContext(Dispatchers.Main.immediate) {
        statusMessage = message
        progress?.let { fetchInfoProgress = it.coerceIn(0f, 1f) }
    }
}

private suspend fun EditorController.updateFetchInfoProgress(source: String, message: String, progress: Float? = null) {
    val sourceLabel = FetchInfoSources.label(source)
    val cleanMessage = message.trim()
    updateFetchInfoProgress(
        if (cleanMessage.startsWith("\u3010")) cleanMessage else "\u3010$sourceLabel\u3011$cleanMessage",
        progress
    )
}

private fun EditorController.fetchInfoProgressForSource(source: String): FetchInfoProgress {
    return { message -> updateFetchInfoProgress(source, message) }
}

private fun EditorController.fetchInfoProgressForAutoSource(
    source: String,
    sourceIndex: Int,
    sourceTotal: Int
): FetchInfoProgress {
    return { message ->
        val cleanMessage = message.trim()
        val displayMessage = if (cleanMessage.startsWith("\u6b63\u5728\u5c1d\u8bd5\u641c\u7d22\u6e90")) {
            "\u6b63\u5728\u5c1d\u8bd5\u641c\u7d22\u6e90 ${sourceIndex + 1}/$sourceTotal"
        } else {
            cleanMessage
        }
        updateFetchInfoProgress(
            source,
            displayMessage,
            fetchInfoSourceProgress(sourceIndex, sourceTotal, fetchInfoProgressPhase(cleanMessage))
        )
    }
}

private fun EditorController.fetchInfoInitialProgressText(tool: EditorTool): String {
    if (tool.toolId != "fetch_info") return ""
    val parameters = fetchInfoParameters(tool)
    val source = fetchInfoAutoSources(parameters.content).firstOrNull() ?: parameters.source
    return "【${FetchInfoSources.label(source)}】搜索中..."
}

private fun EditorController.fetchInfoParametersForSource(base: FetchInfoParameters, source: String): FetchInfoParameters {
    return fetchInfoParametersForSourceModel(
        base = base,
        source = source,
        defaultTitleQuery = defaultFetchInfoQuery(FETCH_INFO_SEARCH_TITLE),
        sosadLoginCookie = sosadLoginCookie()
    )
}

private suspend fun EditorController.prepareFetchInfoPreview(tool: EditorTool): Boolean {
    return prepareFetchInfoPreviewFromParameters(tool.id, fetchInfoParameters(tool))
}

// 只有正在运行的 fetch_info 自动化步骤才共享认书结果：toolId 必须等于当前确认步骤的 stepId。
private fun EditorController.isFetchInfoAutomationRun(toolId: String): Boolean {
    val request = automationConfirmationRequest ?: return false
    return request.toolId == "fetch_info" && request.stepId == toolId
}

// 读取本次运行里同源已认好的书（详情页地址）；非自动化运行或无缓存时返回 null。
private fun EditorController.fetchInfoRunCachedUrl(toolId: String, source: String): String? {
    if (!isFetchInfoAutomationRun(toolId)) return null
    return fetchInfoRunResolvedUrls[source]?.takeIf { it.isNotBlank() }
}

// 把刚认好的书写入运行级缓存（按 source）；非自动化运行时不缓存，避免影响手动单次抓取。
private fun EditorController.cacheFetchInfoRunResolvedUrl(toolId: String, source: String, url: String) {
    if (url.isBlank() || !isFetchInfoAutomationRun(toolId)) return
    fetchInfoRunResolvedUrls = fetchInfoRunResolvedUrls + (source to url)
}

// 缓存的书抓不到当前内容时清掉，回退到正常搜索认书。
private fun EditorController.clearFetchInfoRunCachedUrl(source: String) {
    if (fetchInfoRunResolvedUrls.containsKey(source)) {
        fetchInfoRunResolvedUrls = fetchInfoRunResolvedUrls - source
    }
}

private suspend fun EditorController.prepareFetchInfoPreviewFromParameters(
    toolId: String,
    baseParameters: FetchInfoParameters,
    clearRetryRequestOnStart: Boolean = true
): Boolean {
    if (kind != DocumentKind.Epub) {
        statusMessage = "抓取信息仅支持 EPUB"
        return false
    }
    if (!baseParameters.fetchCatalog && !baseParameters.fetchIntro && !baseParameters.fetchCover) {
        statusMessage = "请选择抓取内容"
        return false
    }
    networkUnavailableMessageForContext(appContext, "抓取失败")?.let { message ->
        statusMessage = message
        return false
    }
    fetchInfoProgress = 0f
    fetchInfoPreview = null
    fetchInfoSearchChoiceRequest = null
    if (clearRetryRequestOnStart) fetchInfoRetryRequest = null
    val sources = fetchInfoAutoSources(baseParameters.content)
    val contentLabel = fetchInfoContentLabel(baseParameters.content)
    var lastFailure = ""
    for (sourceIndex in sources.indices) {
        val source = sources[sourceIndex]
        val parameters = fetchInfoParametersForSource(baseParameters, source)
        val sourceLabel = FetchInfoSources.label(source)
        if (parameters.query.isBlank()) {
            statusMessage = "没有可用于搜索的书名"
            fetchInfoRetryRequest = FetchInfoRetryRequest(
                toolId = toolId,
                parameters = baseParameters,
                message = statusMessage
            )
            return false
        }
        try {
            val cachedUrl = fetchInfoRunCachedUrl(toolId, parameters.source)
            if (cachedUrl != null) {
                // 本次运行已认好同源的书：直接读详情页，跳过搜索认书。
                val cachedParameters = parameters.copy(
                    searchMode = FETCH_INFO_SEARCH_KEYWORD,
                    query = cachedUrl
                )
                if (prepareFetchInfoPreviewWithParameters(
                        toolId,
                        cachedParameters,
                        requireRequestedContent = true,
                        sourceIndex = sourceIndex,
                        sourceTotal = sources.size
                    )
                ) {
                    return true
                }
                // 缓存的书抓不到当前内容：清掉缓存，回退到正常搜索认书。
                clearFetchInfoRunCachedUrl(parameters.source)
                fetchInfoPreview = null
                fetchInfoSearchChoiceRequest = null
            }
            val fetcher = FetchInfoFetcherFactory.create(parameters.source)
            val sourceProgress = fetchInfoProgressForAutoSource(parameters.source, sourceIndex, sources.size)
            updateFetchInfoProgress(
                parameters.source,
                "搜索中...",
                fetchInfoSourceProgress(sourceIndex, sources.size, 0.12f)
            )
            val choices = distinctVisibleSearchChoices(fetcher.searchChoices(parameters, sourceProgress))
            val choiceResolution = resolveFetchInfoSearchChoiceByMetadata(choices, parameters.query)
            if (choiceResolution.skipReason != null) {
                lastFailure = "${sourceLabel}${choiceResolution.skipReason}"
                fetchInfoPreview = null
                fetchInfoSearchChoiceRequest = null
                continue
            }
            if (choiceResolution.promptChoices.isNotEmpty()) {
                fetchInfoRetryRequest = null
                fetchInfoSearchChoiceRequest = FetchInfoSearchChoiceRequest(
                    toolId = toolId,
                    parameters = parameters,
                    choices = choiceResolution.promptChoices
                )
                statusMessage = "请选择${sourceLabel}搜索结果"
                return false
            }
            val resolvedParameters = choiceResolution.choice?.let { choice ->
                parameters.copy(query = choice.detailUrl)
            } ?: parameters
            if (prepareFetchInfoPreviewWithParameters(
                    toolId,
                    resolvedParameters,
                    requireRequestedContent = true,
                    sourceIndex = sourceIndex,
                    sourceTotal = sources.size
                )
            ) {
                return true
            }
            lastFailure = statusMessage.ifBlank { "${sourceLabel}没有抓到$contentLabel" }
            fetchInfoPreview = null
            fetchInfoSearchChoiceRequest = null
        } catch (error: Throwable) {
            fetchInfoPreview = null
            fetchInfoSearchChoiceRequest = null
            lastFailure = networkAwareErrorMessage("${sourceLabel}抓取失败", error)
            if (parameters.source == FETCH_INFO_SOURCE_SOSAD) {
                markSosadLoginInvalidIfNeeded(lastFailure, error)
            }
        }
    }
    val failureMessage = lastFailure.ifBlank { "没有抓到$contentLabel" }
    statusMessage = failureMessage
    fetchInfoRetryRequest = FetchInfoRetryRequest(
        toolId = toolId,
        parameters = baseParameters,
        message = failureMessage
    )
    return false
}

suspend fun EditorController.selectFetchInfoSearchChoice(toolId: String, choice: FetchInfoSearchChoice): Boolean {
    val request = fetchInfoSearchChoiceRequest ?: run {
        statusMessage = "没有可选择的搜索结果"
        return false
    }
    if (request.toolId != toolId) {
        statusMessage = "搜索结果已变化"
        return false
    }
    if (request.choices.none { it.detailUrl == choice.detailUrl }) {
        statusMessage = "搜索结果已失效"
        return false
    }
    fetchInfoSearchChoiceRequest = null
    fetchInfoRetryRequest = null
    return prepareFetchInfoPreviewWithParameters(
        toolId = toolId,
        parameters = request.parameters.copy(query = choice.detailUrl),
        requireRequestedContent = true
    )
}

fun EditorController.openFetchInfoManualUrlRetry(toolId: String): Boolean {
    val request = fetchInfoSearchChoiceRequest ?: return false
    if (request.toolId != toolId) return false
    fetchInfoSearchChoiceRequest = null
    statusMessage = "请填写详情页网址"
    fetchInfoRetryRequest = FetchInfoRetryRequest(
        toolId = toolId,
        parameters = request.parameters,
        message = statusMessage
    )
    fetchInfoProgress = 0f
    return true
}

// 换书：在已有预览的基础上，按当前来源重新搜索候选，并弹出选择框让用户重选。
// 重选后会经由 selectFetchInfoSearchChoice 用新书的详情页地址重抓三样内容，并刷新运行级缓存。
suspend fun EditorController.reselectFetchInfoBook(toolId: String): Boolean {
    val preview = fetchInfoPreview?.takeIf { it.toolId == toolId } ?: run {
        statusMessage = "没有可换书的抓取预览"
        return false
    }
    if (kind != DocumentKind.Epub) {
        statusMessage = "抓取信息仅支持 EPUB"
        return false
    }
    networkUnavailableMessageForContext(appContext, "抓取失败")?.let { message ->
        statusMessage = message
        return false
    }
    val source = preview.parameters.source
    // 先清掉本次运行里该来源的缓存：换书意味着之前认的书作废，避免后续步骤继续复用旧书。
    clearFetchInfoRunCachedUrl(source)
    // 用当前书名按书名模式重新搜索，拿到候选书列表。
    val searchParameters = fetchInfoParametersForSource(preview.parameters, source).copy(
        searchMode = FETCH_INFO_SEARCH_TITLE,
        query = defaultFetchInfoQuery(FETCH_INFO_SEARCH_TITLE)
    )
    val sourceLabel = FetchInfoSources.label(source)
    if (searchParameters.query.isBlank()) {
        statusMessage = "没有可用于搜索的书名"
        return false
    }
    fetchInfoProgress = 0f
    updateFetchInfoProgress(source, "搜索中...", 0.16f)
    return try {
        val fetcher = FetchInfoFetcherFactory.create(searchParameters.source)
        val choices = distinctVisibleSearchChoices(
            fetcher.searchChoices(searchParameters, fetchInfoProgressForSource(searchParameters.source))
        )
        if (choices.isEmpty()) {
            statusMessage = "${sourceLabel}没有搜到可换的书"
            fetchInfoPreview = null
            fetchInfoRetryRequest = FetchInfoRetryRequest(
                toolId = toolId,
                parameters = searchParameters,
                message = statusMessage
            )
            fetchInfoProgress = 0f
            false
        } else {
            // 换书一律弹框让用户重选，不做自动认书。
            fetchInfoPreview = null
            fetchInfoRetryRequest = null
            fetchInfoSearchChoiceRequest = FetchInfoSearchChoiceRequest(
                toolId = toolId,
                parameters = searchParameters,
                choices = choices
            )
            statusMessage = "请选择${sourceLabel}搜索结果"
            fetchInfoProgress = 0f
            true
        }
    } catch (error: Throwable) {
        statusMessage = networkAwareErrorMessage("${sourceLabel}搜索失败", error)
        if (searchParameters.source == FETCH_INFO_SOURCE_SOSAD) {
            markSosadLoginInvalidIfNeeded(statusMessage, error)
        }
        fetchInfoProgress = 0f
        false
    }
}

suspend fun EditorController.retryFetchInfoAfterFailure(toolId: String, urlText: String): Boolean {
    val request = fetchInfoRetryRequest ?: run {
        statusMessage = "没有可重试的抓取任务"
        return false
    }
    if (request.toolId != toolId) {
        statusMessage = "抓取任务已变化"
        return false
    }
    val cleanUrl = urlText.trim()
    if (cleanUrl.isBlank()) {
        return prepareFetchInfoPreviewFromParameters(
            toolId = toolId,
            baseParameters = request.parameters,
            clearRetryRequestOnStart = false
        )
    }
    val source = fetchInfoSourceForRetryUrl(cleanUrl)
    if (source == null) {
        statusMessage = "无法识别网址来源，请输入晋江、长佩或废文详情页网址"
        fetchInfoRetryRequest = request.copy(message = statusMessage)
        return false
    }
    val parameters = fetchInfoParametersForSource(request.parameters, source).copy(
        searchMode = FETCH_INFO_SEARCH_KEYWORD,
        query = cleanUrl
    )
    val ok = prepareFetchInfoPreviewWithParameters(
        toolId = toolId,
        parameters = parameters,
        requireRequestedContent = true
    )
    if (!ok) {
        fetchInfoRetryRequest = request.copy(message = statusMessage.ifBlank { "抓取失败" })
    }
    return ok
}

private fun fetchInfoSourceForRetryUrl(urlText: String): String? {
    return resolveFetchInfoSourceForRetryUrl(
        urlText = urlText,
        gongziSource = FETCH_INFO_SOURCE_GONGZICP,
        sosadSource = FETCH_INFO_SOURCE_SOSAD,
        jjwxcSource = FETCH_INFO_SOURCE_JJWXC
    )
}

private suspend fun EditorController.prepareFetchInfoPreviewWithParameters(
    toolId: String,
    parameters: FetchInfoParameters,
    requireRequestedContent: Boolean = false,
    sourceIndex: Int? = null,
    sourceTotal: Int? = null
): Boolean {
    networkUnavailableMessageForContext(appContext, "抓取失败")?.let { message ->
        statusMessage = message
        return false
    }
    val autoProgress = if (sourceIndex != null && sourceTotal != null) {
        fetchInfoSourceProgress(sourceIndex, sourceTotal, 0.5f)
    } else {
        0.42f
    }
    updateFetchInfoProgress(parameters.source, "\u6b63\u5728\u8bfb\u53d6\u8be6\u60c5\u9875", autoProgress)
    return try {
        val progress = if (sourceIndex != null && sourceTotal != null) {
            fetchInfoProgressForAutoSource(parameters.source, sourceIndex, sourceTotal)
        } else {
            fetchInfoProgressForSource(parameters.source)
        }
        val raw = FetchInfoFetcherFactory.create(parameters.source).fetch(
            parameters,
            progress
        )
        if (requireRequestedContent && !fetchedInfoHasRequestedContent(raw, parameters)) {
            statusMessage = "${FetchInfoSources.label(parameters.source)}没有抓到${fetchInfoContentLabel(parameters.content)}"
            return false
        }
        val (filtered, issues) = FetchInfoFilter.apply(raw, parameters)
        fetchInfoPreview = FetchInfoPreview(
            toolId = toolId,
            parameters = parameters,
            raw = raw,
            filtered = filtered,
            filterIssues = issues
        )
        // 认书成功后把详情页地址写入运行级缓存，供后续同源步骤复用（仅自动化运行内生效）。
        cacheFetchInfoRunResolvedUrl(toolId, parameters.source, raw.resolvedUrl)
        fetchInfoSearchChoiceRequest = null
        fetchInfoRetryRequest = null
        clearTextSearchState()
        clearFileRenamePlan()
        val catalogCount = filtered.catalog.size.takeIf { parameters.fetchCatalog } ?: 0
        val introReady = parameters.fetchIntro && filtered.intro.isNotBlank()
        val coverReady = parameters.fetchCover && filtered.coverUrl.isNotBlank()
        fetchInfoProgress = 1f
        statusMessage = "抓取预览：目录 $catalogCount，简介 ${if (introReady) "有" else "无"}，封面 ${if (coverReady) "有" else "无"}"
        true
    } catch (error: Throwable) {
        fetchInfoPreview = null
        fetchInfoSearchChoiceRequest = null
        statusMessage = networkAwareErrorMessage("抓取失败", error)
        if (parameters.source == FETCH_INFO_SOURCE_SOSAD) {
            markSosadLoginInvalidIfNeeded(statusMessage, error)
        }
        false
    }
}

suspend fun EditorController.applyFetchInfoPreview(toolId: String): Boolean {
    return applyFetchInfoPreviewWithProgress(toolId) { _, _, _ -> }
}

suspend fun EditorController.applyFetchInfoPreviewWithProgress(
    toolId: String,
    filterActive: Boolean = true,
    renames: Map<Int, String> = emptyMap(),
    deletes: Set<Int> = emptySet(),
    onProgress: (phase: String, completed: Int, total: Int) -> Unit
): Boolean {
    val preview = fetchInfoPreview ?: run {
        statusMessage = "没有可应用的抓取预览"
        return false
    }
    if (preview.toolId != toolId) {
        statusMessage = "抓取预览已变化"
        return false
    }
    if (kind != DocumentKind.Epub) {
        statusMessage = "抓取信息应用仅支持 EPUB"
        return false
    }
    statusMessage = "正在应用抓取信息..."
    return try {
        val result = applyFetchedInfoToEpub(preview, filterActive, renames, deletes, onProgress)
        val parts = buildList {
            if (preview.parameters.writeCatalog) add("标题 ${result.catalogChanged}")
            if (preview.parameters.writeIntro) add(if (result.introWritten) "简介 1" else "简介 0")
            if (preview.parameters.writeCover) add(if (result.coverWritten) "封面 1" else "封面 0")
        }
        checkReport = null
        markDocumentChanged()
        refreshChapters()
        fetchInfoPreview = null
        fetchInfoSearchChoiceRequest = null
        statusMessage = "抓取信息已应用：${parts.joinToString("；").ifBlank { "无变更" }}".let { message ->
            if (result.coverError.isBlank()) message else "$message；封面失败：${result.coverError}"
        }
        true
    } catch (error: Throwable) {
        statusMessage = "应用失败：${error.message ?: error.javaClass.simpleName}"
        false
    }
}

private suspend fun EditorController.applyFetchedInfoToEpub(
    preview: FetchInfoPreview,
    filterActive: Boolean = true,
    renames: Map<Int, String> = emptyMap(),
    deletes: Set<Int> = emptySet(),
    onProgress: (phase: String, completed: Int, total: Int) -> Unit = { _, _, _ -> }
): FetchInfoWriteResult {
    val sourceBook = epub ?: error("没有 EPUB 可应用")
    // 先在副本上完成全部写入（标题、简介、封面），全部成功后再整体替换当前书；
    // 中途任何一步抛错都不会改到原书，避免留下半成品。
    val book = sourceBook.mutableDeepCopy()
    val parameters = preview.parameters
    val info = if (filterActive) preview.filtered else preview.raw
    var catalogChanged = 0
    var introWritten = false
    var coverWritten = false
    var coverError = ""
    var touchedCurrentChapter = false
    val total = listOf(
        parameters.writeCatalog && info.catalog.isNotEmpty(),
        parameters.writeIntro && info.intro.isNotBlank(),
        parameters.writeCover && info.coverUrl.isNotBlank()
    ).count { it }.coerceAtLeast(1)
    var completed = 0
    onProgress("应用抓取信息", completed, total)
    yield()

    if (parameters.writeCatalog && info.catalog.isNotEmpty()) {
        val catalogResult = applyFetchedCatalogToEpub(
            book = book,
            parameters = parameters,
            catalog = info.catalog,
            currentChapterIndex = previewChapterIndex,
            renames = renames,
            deletes = deletes,
            onError = { message -> statusMessage = message }
        )
        catalogChanged = catalogResult.changed
        touchedCurrentChapter = catalogResult.touchedCurrentChapter
        completed += 1
        onProgress("应用目录", completed, total)
        yield()
    }

    if (parameters.writeIntro && info.intro.isNotBlank()) {
        writeFetchInfoIntroFileToEpub(book, parameters.introTargetPath, info, parameters.source)
        introWritten = true
        completed += 1
        onProgress("应用简介", completed, total)
        yield()
    }

    if (parameters.writeCover && info.coverUrl.isNotBlank()) {
        val coverResult = withContext(Dispatchers.IO) {
            runCatching {
                val request = buildFetchInfoCoverRequest(parameters, info.coverUrl)
                if (request.sameHostRedirectOnly) {
                    FetchHttpClient.getBytes(
                        request.url,
                        request.headers,
                        ::isSosadSameHostHttpsRedirect,
                        maxBytes = HTTP_IMAGE_RESPONSE_MAX_BYTES
                    )
                } else {
                    FetchHttpClient.getBytes(
                        request.url,
                        request.headers,
                        maxBytes = HTTP_IMAGE_RESPONSE_MAX_BYTES
                    )
                }
            }
        }
        coverResult
            .onSuccess { response ->
                writeCoverToEpub(book, info.coverUrl, response.bytes, response.contentType)
                coverWritten = true
            }
            .onFailure { error ->
                coverError = networkAwareErrorMessage("封面下载失败", error)
            }
        completed += 1
        onProgress("应用封面", completed, total)
        yield()
    }

    // 全部写入完成、未抛异常，才把副本整体替换为当前书，并按需刷新当前章预览。
    epub = book
    if (touchedCurrentChapter) refreshPreview()

    return FetchInfoWriteResult(
        catalogChanged = catalogChanged,
        introWritten = introWritten,
        coverWritten = coverWritten,
        coverError = coverError
    )
}
