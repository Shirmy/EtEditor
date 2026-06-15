package com.eteditor

import com.eteditor.core.DocumentKind
import com.eteditor.core.TxtDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun EditorController.formatTxtDefault() {
    if (busy) return
    if (warnTxtMoveChapterSyncPending("格式整理")) return
    val document = txt ?: run {
        statusMessage = "请先打开 TXT"
        return
    }
    if (document.text.isBlank()) {
        statusMessage = "TXT 内容为空"
        return
    }
    val sourceText = document.text
    val chaptersSnapshot = document.chapters
    val supplementedCatalogLines = txtSupplementedCatalogLines
    val hiddenCatalogMarkers = txtHiddenCatalogMarkers(document, txtHiddenCatalogLineIndices)
    val chapterDetectionConfig = currentTxtChapterDetectionConfig()
    controllerScope.launch {
        try {
            runBusy("格式整理") {
                setSaveProgress(0.06f, "整理中：准备文本")
                val result = withContext(Dispatchers.Default) {
                    formatTxtLayoutFromCurrentCatalog(
                        text = sourceText,
                        chapters = chaptersSnapshot,
                        config = chapterDetectionConfig
                    )
                }
                if (txt !== document || document.text != sourceText) return@runBusy

                setSaveProgress(0.58f, "整理中：同步目录位置")
                val nextText = result.text
                val nextHiddenCatalogLineIndices = remapTxtHiddenCatalogLineIndices(nextText, hiddenCatalogMarkers)
                val nextSupplementedCatalogLines = remapTxtSupplementedCatalogLines(nextText, supplementedCatalogLines)
                if (txt !== document || document.text != sourceText) return@runBusy

                document.text = nextText
                txtHiddenCatalogLineIndices = nextHiddenCatalogLineIndices
                txtSupplementedCatalogLines = nextSupplementedCatalogLines
                document.chapters = result.chapters
                previewChapterIndex = previewChapterIndex.coerceAtMost(document.chapters.lastIndex.coerceAtLeast(0))
                checkReport = null
                markDocumentChanged()
                clearTextSearchState()
                setSaveProgress(0.92f, "整理中：刷新预览")
                refreshChapters()
                statusMessage = "格式整理完成：删除空行 ${result.removedBlankCount} 行，整理正文 ${result.contentLineCount} 行，章节 ${result.chapterLineCount} 行"
                setSaveProgress(1f, "整理完成")
                delay(120)
            }
        } finally {
            clearSaveProgress()
        }
    }
}

fun EditorController.refreshTxtCatalog() {
    if (busy) return
    if (warnTxtMoveChapterSyncPending("刷新目录")) return
    val document = txt ?: return
    cancelTxtCatalogDetection()
    val sessionKey = documentSessionKey
    controllerScope.launch {
        try {
            runBusy("刷新目录") {
                setSaveProgress(0.08f, "刷新中：恢复目录项")
                val restoredCount = txtHiddenCatalogLineIndices.size
                txtHiddenCatalogLineIndices = emptySet()
                val restoredSupplementedCount = restoreTxtSupplementedCatalogLines(document)
                if (sessionKey != documentSessionKey || kind != DocumentKind.Txt || txt !== document) return@runBusy

                setSaveProgress(0.36f, "刷新中：识别目录")
                val sourceText = document.text
                val config = currentTxtChapterDetectionConfig()
                val enabledKeys = txtEnabledChapterRuleKeys
                val result = withContext(Dispatchers.Default) {
                    detectTxtChaptersWithMappingsApplied(sourceText, config, enabledKeys)
                }
                if (
                    sessionKey != documentSessionKey ||
                    kind != DocumentKind.Txt ||
                    txt !== document ||
                    document.text != sourceText
                ) {
                    return@runBusy
                }

                setSaveProgress(0.68f, "刷新中：净化目录")
                if (result.text != document.text) {
                    document.text = result.text
                    markDocumentChanged()
                    clearTextSearchState()
                }
                document.chapters = result.chapters
                applyTxtCatalogPurifyRulesAfterCatalogChange()

                previewChapterIndex = previewChapterIndex.coerceAtMost(document.chapters.lastIndex.coerceAtLeast(0))
                checkReport = null
                setSaveProgress(0.90f, "刷新中：更新预览")
                refreshChapters()
                refreshPreview()
                statusMessage = buildString {
                    append("已刷新目录：${document.chapters.size} 项")
                    if (restoredCount > 0) append("，恢复移除目录 $restoredCount 项")
                    if (restoredSupplementedCount > 0) append("，移除本次补章节 $restoredSupplementedCount 项")
                    if (result.mappedTitleCount > 0) append("，映射 ${result.mappedTitleCount} 项")
                }
                setSaveProgress(1f, "刷新目录完成")
                delay(120)
            }
        } finally {
            clearSaveProgress()
        }
    }
}

private fun EditorController.restoreTxtSupplementedCatalogLines(document: TxtDocument): Int {
    val records = txtSupplementedCatalogLines
    if (records.isEmpty()) return 0
    val (nextText, restored) = restoreTxtSupplementedCatalogLinesInText(document.text, records)
    txtSupplementedCatalogLines = emptyList()
    if (restored > 0) {
        document.text = nextText
        markDocumentChanged()
        clearTextSearchState()
    }
    return restored
}

fun EditorController.txtMoveChapterBlock(sourceIndex: Int, targetIndex: Int): Boolean {
    val document = txt ?: return false
    if (warnTxtMoveChapterSyncPending("移动章节")) return false
    if (busy) {
        statusMessage = "正在处理，请稍后再移动章节"
        return false
    }
    if (txtCatalogParsing) {
        statusMessage = "目录识别中，请稍后再移动章节"
        return false
    }
    if (sourceIndex !in document.chapters.indices) return false
    if (targetIndex !in setOf(MOVE_TARGET_BOOK_START, MOVE_TARGET_BOOK_END) && sourceIndex == targetIndex) return false
    val chaptersSnapshot = document.chapters
    val source = chaptersSnapshot[sourceIndex]
    val firstStart = chaptersSnapshot.minOfOrNull { it.startIndex } ?: return false
    val textSnapshot = document.text
    if (firstStart < 0 || chaptersSnapshot.any { it.endIndex < it.startIndex || it.endIndex > textSnapshot.length }) {
        statusMessage = "目录位置已过期，请先刷新目录"
        return false
    }
    val movePlan = moveTxtChapterList(chaptersSnapshot, sourceIndex, targetIndex) ?: return false
    document.chapters = movePlan.first
    previewChapterIndex = movePlan.second.coerceAtMost(document.chapters.lastIndex.coerceAtLeast(0))
    checkReport = null
    markDocumentChanged()
    clearTextSearchState()
    refreshChapters(refreshPreview = false)
    statusMessage = "已移动目录，正文同步中：${source.title}"

    val sessionKey = documentSessionKey
    val revision = ++txtMoveChapterSyncRevision
    val config = currentTxtChapterDetectionConfig()
    val autoKeys = txtEnabledChapterRuleKeys
    val desiredChapters = document.chapters
    txtMoveChapterSyncJob?.cancel()
    txtMoveChapterSyncPending = true
    txtBulkMoveChapterProgress = 0.18f
    txtBulkMoveChapterProgressText = "移动章节：${source.title.ifBlank { "未命名章节" }}"
    txtMoveChapterSyncJob = controllerScope.launch {
        try {
            txtBulkMoveChapterProgress = 0.38f
            val result = withContext(Dispatchers.Default) {
                buildMovedTxtChapterText(
                    text = textSnapshot,
                    desiredChapters = desiredChapters,
                    firstStart = firstStart,
                    insertIndex = movePlan.second,
                    config = config,
                    autoKeys = autoKeys,
                    detectChapters = { nextText, nextConfig, nextAutoKeys ->
                        detectTxtChaptersWithConfig(nextText, nextConfig, nextAutoKeys)
                    }
                )
            }
            if (
                revision != txtMoveChapterSyncRevision ||
                sessionKey != documentSessionKey ||
                kind != DocumentKind.Txt ||
                txt !== document
            ) {
                return@launch
            }
            if (document.text != textSnapshot) {
                return@launch
            }
            txtBulkMoveChapterProgress = 0.86f
            document.text = result.text
            document.chapters = result.chapters
            previewChapterIndex = result.insertIndex.coerceAtMost(document.chapters.lastIndex.coerceAtLeast(0))
            checkReport = null
            markDocumentChanged()
            clearTextSearchState()
            refreshChapters()
            txtBulkMoveChapterProgress = 1f
            statusMessage = "已移动章节：${source.title.ifBlank { "未命名章节" }}"
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (revision == txtMoveChapterSyncRevision && sessionKey == documentSessionKey && txt === document) {
                document.chapters = detectCurrentTxtChapters(document.text)
                previewChapterIndex = previewChapterIndex.coerceAtMost(document.chapters.lastIndex.coerceAtLeast(0))
                refreshChapters()
                statusMessage = "移动章节失败：${error.message ?: error.javaClass.simpleName}"
                log(statusMessage)
            }
        } finally {
            if (revision == txtMoveChapterSyncRevision) {
                txtMoveChapterSyncJob = null
                txtMoveChapterSyncPending = false
                txtMoveChapterSyncWarningMessage = null
                txtBulkMoveChapterProgress = null
                txtBulkMoveChapterProgressText = ""
            }
        }
    }
    return true
}

fun EditorController.txtMoveChapterBlocks(sourceIndices: Set<Int>, targetIndex: Int): Boolean {
    val document = txt ?: return false
    if (warnTxtMoveChapterSyncPending("批量移动章节")) return false
    if (busy) {
        statusMessage = "正在处理，请稍后再移动章节"
        return false
    }
    if (txtCatalogParsing) {
        statusMessage = "目录识别中，请稍后再移动章节"
        return false
    }
    val chaptersSnapshot = document.chapters
    val selectedIndices = sourceIndices
        .filter { index -> index in chaptersSnapshot.indices }
        .toSortedSet()
    if (selectedIndices.isEmpty()) {
        statusMessage = "请先选择要移动的章节"
        return false
    }
    if (targetIndex !in setOf(MOVE_TARGET_BOOK_START, MOVE_TARGET_BOOK_END) && targetIndex !in chaptersSnapshot.indices) {
        return false
    }
    if (targetIndex in selectedIndices) {
        statusMessage = "移动目标不能是已选章节"
        return false
    }
    val firstStart = chaptersSnapshot.minOfOrNull { it.startIndex } ?: return false
    val textSnapshot = document.text
    if (firstStart < 0 || chaptersSnapshot.any { it.endIndex < it.startIndex || it.endIndex > textSnapshot.length }) {
        statusMessage = "目录位置已过期，请先刷新目录"
        return false
    }
    val movePlan = moveTxtChapterList(chaptersSnapshot, selectedIndices, targetIndex) ?: return false
    if (movePlan.first.map { it.startIndex } == chaptersSnapshot.map { it.startIndex }) {
        statusMessage = "章节位置没有变化"
        return false
    }
    val movedCount = selectedIndices.size
    document.chapters = movePlan.first
    previewChapterIndex = movePlan.second.coerceAtMost(document.chapters.lastIndex.coerceAtLeast(0))
    checkReport = null
    markDocumentChanged()
    clearTextSearchState()
    refreshChapters(refreshPreview = false)
    statusMessage = "已移动目录，正文同步中：$movedCount 章"

    val sessionKey = documentSessionKey
    val revision = ++txtMoveChapterSyncRevision
    val config = currentTxtChapterDetectionConfig()
    val autoKeys = txtEnabledChapterRuleKeys
    val desiredChapters = document.chapters
    txtMoveChapterSyncJob?.cancel()
    txtMoveChapterSyncPending = true
    txtBulkMoveChapterProgress = 0.18f
    txtBulkMoveChapterProgressText = "批量移动章节：$movedCount 章"
    txtMoveChapterSyncJob = controllerScope.launch {
        try {
            txtBulkMoveChapterProgress = 0.38f
            val result = withContext(Dispatchers.Default) {
                buildMovedTxtChapterText(
                    text = textSnapshot,
                    desiredChapters = desiredChapters,
                    firstStart = firstStart,
                    insertIndex = movePlan.second,
                    config = config,
                    autoKeys = autoKeys,
                    detectChapters = { nextText, nextConfig, nextAutoKeys ->
                        detectTxtChaptersWithConfig(nextText, nextConfig, nextAutoKeys)
                    }
                )
            }
            if (
                revision != txtMoveChapterSyncRevision ||
                sessionKey != documentSessionKey ||
                kind != DocumentKind.Txt ||
                txt !== document
            ) {
                return@launch
            }
            if (document.text != textSnapshot) {
                return@launch
            }
            txtBulkMoveChapterProgress = 0.86f
            document.text = result.text
            document.chapters = result.chapters
            previewChapterIndex = result.insertIndex.coerceAtMost(document.chapters.lastIndex.coerceAtLeast(0))
            checkReport = null
            markDocumentChanged()
            clearTextSearchState()
            refreshChapters()
            txtBulkMoveChapterProgress = 1f
            statusMessage = "已批量移动 $movedCount 章"
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (revision == txtMoveChapterSyncRevision && sessionKey == documentSessionKey && txt === document) {
                document.chapters = detectCurrentTxtChapters(document.text)
                previewChapterIndex = previewChapterIndex.coerceAtMost(document.chapters.lastIndex.coerceAtLeast(0))
                refreshChapters()
                statusMessage = "批量移动章节失败：${error.message ?: error.javaClass.simpleName}"
                log(statusMessage)
            }
        } finally {
            if (revision == txtMoveChapterSyncRevision) {
                txtMoveChapterSyncJob = null
                txtMoveChapterSyncPending = false
                txtMoveChapterSyncWarningMessage = null
                txtBulkMoveChapterProgress = null
                txtBulkMoveChapterProgressText = ""
            }
        }
    }
    return true
}

fun EditorController.removeTxtCatalogItem(index: Int): Boolean {
    if (warnTxtMoveChapterSyncPending("移除目录项")) return false
    val document = txt ?: return false
    val chapter = document.chapters.getOrNull(index) ?: return false
    val previewAnchorOffset = currentTxtPreviewSourceAnchorOffset(document)
    txtHiddenCatalogLineIndices = txtHiddenCatalogLineIndices + chapter.lineIndex
    document.chapters = detectCurrentTxtChapters(document.text)
    restoreTxtPreviewPositionForSourceOffset(document, previewAnchorOffset)
    markDocumentChanged()
    refreshChapters(refreshPreview = false)
    refreshPreview()
    statusMessage = "已从 TXT 目录移除：${chapter.title}"
    return true
}

fun EditorController.removeTxtCatalogItems(indices: Set<Int>): Boolean {
    if (warnTxtMoveChapterSyncPending("\u6279\u91cf\u79fb\u9664\u76ee\u5f55\u9879")) return false
    val document = txt ?: return false
    val chapters = indices
        .mapNotNull { index -> document.chapters.getOrNull(index) }
        .distinctBy { chapter -> chapter.lineIndex }
    if (chapters.isEmpty()) return false
    val previewAnchorOffset = currentTxtPreviewSourceAnchorOffset(document)
    txtHiddenCatalogLineIndices = txtHiddenCatalogLineIndices + chapters.map { it.lineIndex }
    document.chapters = detectCurrentTxtChapters(document.text)
    restoreTxtPreviewPositionForSourceOffset(document, previewAnchorOffset)
    markDocumentChanged()
    refreshChapters(refreshPreview = false)
    refreshPreview()
    statusMessage = "\u5df2\u4ece TXT \u76ee\u5f55\u79fb\u9664 ${chapters.size} \u9879"
    return true
}

fun EditorController.deleteTxtChapterBlock(index: Int): Boolean {
    if (warnTxtMoveChapterSyncPending("删除章节")) return false
    val document = txt ?: return false
    val chaptersSnapshot = document.chapters
    val chapter = chaptersSnapshot.getOrNull(index) ?: return false
    val previousPreviewIndex = previewChapterIndex
    val deletion = deleteTxtChapterBlockText(
        sourceText = document.text,
        chapters = chaptersSnapshot,
        index = index,
        hiddenCatalogLineIndices = txtHiddenCatalogLineIndices,
        supplementedCatalogLines = txtSupplementedCatalogLines
    )
    if (deletion == null) {
        statusMessage = "目录位置已过期，请先刷新目录"
        return false
    }
    document.text = deletion.text
    txtHiddenCatalogLineIndices = deletion.hiddenCatalogLineIndices
    txtSupplementedCatalogLines = deletion.supplementedCatalogLines
    document.chapters = detectCurrentTxtChapters(document.text)
    previewChapterIndex = txtPreviewIndexAfterChapterDeletion(
        previousPreviewIndex = previousPreviewIndex,
        deletedIndex = index,
        remainingChapterCount = document.chapters.size
    )
    checkReport = null
    markDocumentChanged()
    clearTextSearchState()
    refreshChapters()
    refreshPreview()
    statusMessage = "\u5df2\u5220\u9664\u7ae0\u8282\uff1a${chapter.title.ifBlank { "\u672a\u547d\u540d\u7ae0\u8282" }}"
    return true
}

fun EditorController.deleteTxtChapterBlocks(indices: Set<Int>): Boolean {
    if (warnTxtMoveChapterSyncPending("批量删除章节")) return false
    val document = txt ?: return false
    val chaptersSnapshot = document.chapters
    val selectedIndices = indices
        .filter { index -> index in chaptersSnapshot.indices }
        .distinct()
        .sorted()
    if (selectedIndices.isEmpty()) return false

    val deletion = deleteTxtChapterBlocksText(
        sourceText = document.text,
        chapters = chaptersSnapshot,
        selectedIndices = selectedIndices,
        hiddenCatalogLineIndices = txtHiddenCatalogLineIndices,
        supplementedCatalogLines = txtSupplementedCatalogLines
    )
    if (deletion == null) {
        statusMessage = "目录位置已过期，请先刷新目录"
        return false
    }
    val previousPreviewIndex = previewChapterIndex
    document.text = deletion.text
    txtHiddenCatalogLineIndices = deletion.hiddenCatalogLineIndices
    txtSupplementedCatalogLines = deletion.supplementedCatalogLines
    document.chapters = detectCurrentTxtChapters(document.text)
    previewChapterIndex = txtPreviewIndexAfterChapterBlocksDeletion(
        previousPreviewIndex = previousPreviewIndex,
        deletedIndices = deletion.deletedIndices,
        remainingChapterCount = document.chapters.size
    )
    checkReport = null
    markDocumentChanged()
    clearTextSearchState()
    refreshChapters()
    refreshPreview()
    statusMessage = "已批量删除 ${deletion.deletedCount} 章"
    return true
}

fun EditorController.txtLineText(lineIndex: Int): String {
    val document = txt ?: return ""
    val lines = document.text.split('\n').map { it.removeSuffix("\r") }
    return lines.getOrNull(lineIndex).orEmpty()
}

fun EditorController.suggestTxtSupplementChapterNumber(lineIndex: Int): String {
    val document = txt ?: return ""
    val lines = document.text.split('\n').map { it.removeSuffix("\r") }
    val line = lines.getOrNull(lineIndex)?.trim().orEmpty()
    return suggestTxtSupplementChapterNumberForLine(lineIndex, line, document.chapters)
}

fun EditorController.supplementTxtChapterLine(lineIndex: Int, chapterNumber: String): Boolean {
    if (warnTxtMoveChapterSyncPending("补章节")) return false
    val document = txt ?: return false
    val lines = document.text.split('\n').map { it.removeSuffix("\r") }.toMutableList()
    if (lineIndex !in lines.indices) {
        statusMessage = "行号无效"
        return false
    }
    val line = lines.getOrNull(lineIndex)?.trim().orEmpty()
    if (line.isBlank()) {
        statusMessage = "当前行为空，不能补章节"
        return false
    }
    val rawNumber = chapterNumber.trim()
    val number = normalizeManualChapterNumber(rawNumber)
    if (number.isBlank()) {
        statusMessage = if (rawNumber.isBlank()) "请输入章节号" else "章节号只能输入数字或中文数字"
        return false
    }

    txtHiddenCatalogLineIndices = txtHiddenCatalogLineIndices - lineIndex
    val alreadyChapter = hasManualChapterPrefix(line)
    if (!alreadyChapter) {
        val originalLine = lines[lineIndex]
        val supplementedLine = "第${number}章$line"
        lines[lineIndex] = supplementedLine
        txtSupplementedCatalogLines = txtSupplementedCatalogLines
            .filterNot { it.lineIndex == lineIndex }
            .plus(
                TxtSupplementedCatalogLine(
                    lineIndex = lineIndex,
                    originalLine = originalLine,
                    supplementedLine = supplementedLine
                )
            )
        document.text = lines.joinToString("\n")
        markDocumentChanged()
        clearTextSearchState()
    } else {
        txtSupplementedCatalogLines = txtSupplementedCatalogLines
            .filterNot { it.lineIndex == lineIndex }
            .plus(
                TxtSupplementedCatalogLine(
                    lineIndex = lineIndex,
                    originalLine = lines[lineIndex],
                    supplementedLine = lines[lineIndex]
                )
            )
    }
    document.chapters = detectCurrentTxtChapters(document.text)
    applyTxtCatalogPurifyRulesAfterCatalogChange()
    previewChapterIndex = document.chapters
        .indexOfFirst { it.lineIndex == lineIndex }
        .takeIf { it >= 0 }
        ?: previewChapterIndex.coerceAtMost(document.chapters.lastIndex.coerceAtLeast(0))
    checkReport = null
    refreshChapters()
    statusMessage = if (alreadyChapter) {
        "当前行已有章节号，已刷新目录"
    } else {
        "已补章节：第${number}章$line"
    }
    return true
}
