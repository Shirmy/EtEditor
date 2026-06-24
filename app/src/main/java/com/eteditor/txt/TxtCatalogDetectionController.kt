package com.eteditor

import com.eteditor.core.ChapterInfo
import com.eteditor.core.DocumentKind
import com.eteditor.core.TxtDocument
import com.eteditor.core.syncEpubDirectoryTitleFromHtml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun EditorController.syncTxtPreviewModeWithCatalog(document: TxtDocument) {
    val nextMode = if (document.chapters.isEmpty()) {
        TXT_PREVIEW_MODE_FULL
    } else {
        TXT_PREVIEW_MODE_CHAPTER
    }
    if (txtPreviewMode != nextMode) {
        txtPreviewMode = nextMode
        txtFullPreviewCachedAnchor = null
    }
}

internal fun EditorController.cancelTxtCatalogDetection() {
    txtCatalogParseJob?.cancel()
    txtCatalogParseJob = null
    txtCatalogParsing = false
}

internal fun EditorController.cancelTxtMoveChapterSync() {
    txtMoveChapterSyncJob?.cancel()
    txtMoveChapterSyncJob = null
    txtMoveChapterSyncRevision += 1
    txtMoveChapterSyncPending = false
    txtMoveChapterSyncWarningMessage = null
    txtBulkMoveChapterProgress = null
    txtBulkMoveChapterProgressText = ""
}

fun EditorController.dismissTxtMoveChapterSyncWarning() {
    txtMoveChapterSyncWarningMessage = null
}

fun EditorController.warnTxtMoveChapterSyncPending(action: String): Boolean {
    if (!txtMoveChapterSyncPending) return false
    val progressText = txtBulkMoveChapterProgressText.takeIf { it.isNotBlank() }
        ?.let { "当前进度：$it。" }
        .orEmpty()
    val message = "目录已先刷新，正文还在后台同步。${progressText}请等同步完成后再执行“$action”，避免目录和正文写乱。"
    txtMoveChapterSyncWarningMessage = message
    statusMessage = "章节移动未完成"
    return true
}

suspend fun EditorController.awaitTxtMoveChapterSyncBefore(action: String): Boolean {
    if (!txtMoveChapterSyncPending) return true
    val job = txtMoveChapterSyncJob
    txtMoveChapterSyncWarningMessage = null
    statusMessage = "章节移动正文同步中，等待完成后再执行“$action”。"
    job?.join()
    val ready = !warnTxtMoveChapterSyncPending(action)
    if (ready && statusMessage.startsWith("章节移动正文同步中")) {
        statusMessage = "章节移动正文同步完成，继续“$action”"
    }
    return ready
}

suspend fun EditorController.awaitPendingTxtMoveChapterSync() {
    txtMoveChapterSyncJob?.join()
}

internal fun EditorController.startTxtCatalogDetection(
    document: TxtDocument,
    sessionKey: Int,
    doneLabel: String = "TXT 目录重建完成",
    autoPurifyAfterDetection: Boolean = false,
    autoPurifyBodyAfterDetection: Boolean = false,
    autoPurifyCatalogAfterDetection: Boolean = false,
    applyCatalogMappings: Boolean = true,
    autoSelectChapterRules: Boolean = false,
    preferPrefaceAfterDetection: Boolean = false
) {
    txtCatalogParseJob?.cancel()
    val sourceText = document.text
    val config = currentTxtChapterDetectionConfig()
    val enabledKeys = txtEnabledChapterRuleKeys
    txtCatalogParsing = true
    val job = controllerScope.launch {
        try {
            val result = withContext(Dispatchers.Default) {
                if (autoSelectChapterRules && applyCatalogMappings) {
                    detectTxtChaptersWithAutoSelectedRules(sourceText, config) { autoKeys ->
                        detectTxtChaptersWithMappingsApplied(sourceText, config, autoKeys)
                    }
                } else if (applyCatalogMappings) {
                    detectTxtChaptersWithMappingsApplied(sourceText, config, enabledKeys)
                } else {
                    TxtCatalogDetectionResult(
                        enabledKeys = enabledKeys,
                        text = sourceText,
                        chapters = detectTxtChaptersWithConfig(sourceText, config, enabledKeys),
                        mappedTitleCount = 0
                    )
                }
            }
            val enabledRulesChanged = txtEnabledChapterRuleKeys != enabledKeys
            val staleEnabledRules = if (autoSelectChapterRules) {
                enabledRulesChanged && txtEnabledChapterRuleKeys != result.enabledKeys
            } else {
                txtEnabledChapterRuleKeys != result.enabledKeys
            }
            if (
                sessionKey != documentSessionKey ||
                kind != DocumentKind.Txt ||
                txt !== document ||
                document.text != sourceText ||
                staleEnabledRules
            ) {
                return@launch
            }
            if (autoSelectChapterRules) {
                txtEnabledChapterRuleKeys = result.enabledKeys
            }
            if (result.text != sourceText) {
                document.text = result.text
                markDocumentChanged()
                clearTextSearchState()
            }
            document.chapters = result.chapters
            previewChapterIndex = if (preferPrefaceAfterDetection && txtHasPreface()) {
                -1
            } else {
                previewChapterIndex.coerceIn(
                    if (txtHasPreface()) -1 else 0,
                    document.chapters.lastIndex.coerceAtLeast(0)
                )
            }
            checkReport = null
            txtCatalogParsing = false
            refreshChapters()
            statusMessage = buildString {
                append("$doneLabel：${document.chapters.size} 项")
                if (result.mappedTitleCount > 0) append("，映射 ${result.mappedTitleCount} 项")
            }
            log(statusMessage)
            if (autoPurifyAfterDetection) {
                delay(80)
                if (
                    sessionKey == documentSessionKey &&
                    kind == DocumentKind.Txt &&
                    txt === document
                ) {
                    applyTxtPurifyRulesAfterOpenInBackground(document, sessionKey)
                }
            } else if (autoPurifyBodyAfterDetection || autoPurifyCatalogAfterDetection) {
                applyTxtPurifyTargets(
                    applyBody = autoPurifyBodyAfterDetection,
                    applyCatalog = autoPurifyCatalogAfterDetection
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (sessionKey == documentSessionKey && txt === document) {
                statusMessage = "TXT 目录识别失败：${error.message ?: error.javaClass.simpleName}"
                log(statusMessage)
            }
        } finally {
            if (txtCatalogParseJob == coroutineContext[Job]) {
                txtCatalogParsing = false
                txtCatalogParseJob = null
            }
        }
    }
    txtCatalogParseJob = job
}

internal fun EditorController.refreshChapters(refreshPreview: Boolean = true) {
    if (kind == DocumentKind.Txt) {
        txt?.let(::syncTxtPreviewModeWithCatalog)
    }
    chapters = when (kind) {
        DocumentKind.Epub -> epub?.let { book ->
            buildEpubChapterInfos(book, epubExportOptions(epubHideSection0001FromNcx))
        }.orEmpty()
        DocumentKind.Txt -> txt?.chapters?.map { chapter ->
            val source = "第 ${chapter.lineIndex + 1} 行"
            ChapterInfo(
                index = chapter.index,
                title = chapter.title,
                wordCount = chapter.wordCount,
                source = source,
                fileName = source,
                lineNumber = chapter.lineIndex + 1,
                status = chapter.status
            )
        }.orEmpty()
        DocumentKind.None -> emptyList()
    }
    subtitle = when (kind) {
        DocumentKind.Epub -> epub?.let { "EPUB | OPF: ${it.opfPath} | 章节 ${it.chapters.size}" } ?: subtitle
        DocumentKind.Txt -> txt?.let { document ->
            if (txtCatalogParsing && document.chapters.isEmpty()) subtitle else txtSubtitle(document)
        } ?: subtitle
        DocumentKind.None -> subtitle
    }
    if (refreshPreview) {
        refreshPreview()
    }
}

internal fun EditorController.refreshEpubChapterInfoAt(
    chapterIndex: Int,
    refreshPreview: Boolean = false
) {
    val book = epub ?: return
    val chapter = book.chapters.getOrNull(chapterIndex) ?: return
    val useOwnSection0001Title = !epubExportOptions(epubHideSection0001FromNcx).hideSection0001FromNcx
    chapter.syncEpubDirectoryTitleFromHtml(useOwnSection0001Title)
    val nextInfo = ChapterInfo(
        index = chapterIndex + 1,
        title = chapter.title,
        wordCount = chapter.wordCount,
        source = chapter.path,
        fileName = chapter.path.substringAfterLast('/'),
        tocLevel = chapter.tocLevel,
        isVolume = chapter.isVolumeChapter()
    )
    val currentInfoIndex = chapters.indexOfFirst { it.index == nextInfo.index }
    if (currentInfoIndex < 0) {
        refreshChapters(refreshPreview = refreshPreview)
        return
    }
    chapters = chapters.toMutableList().also { list ->
        list[currentInfoIndex] = nextInfo
    }
    subtitle = "EPUB | OPF: ${book.opfPath} | 绔犺妭 ${book.chapters.size}"
    if (refreshPreview) {
        refreshPreview()
    }
}

internal fun buildEpubChapterInfos(
    book: com.eteditor.core.EpubBook,
    exportOptions: com.eteditor.core.EpubExportOptions
): List<ChapterInfo> {
    if (book.chapters.any { it.isVolumeChapter() }) {
        applyVolumeTocLevels(book)
    }
    val useOwnSection0001Title = !exportOptions.hideSection0001FromNcx
    val chapterInfos = book.chapters.mapIndexed { index, chapter ->
        chapter.syncEpubDirectoryTitleFromHtml(useOwnSection0001Title)
        ChapterInfo(
            index = index + 1,
            title = chapter.title,
            wordCount = chapter.wordCount,
            source = chapter.path,
            fileName = chapter.path.substringAfterLast('/'),
            tocLevel = chapter.tocLevel,
            isVolume = chapter.isVolumeChapter()
        )
    }
    return chapterInfos
}
