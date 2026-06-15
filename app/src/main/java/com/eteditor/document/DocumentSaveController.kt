package com.eteditor

import android.net.Uri
import com.eteditor.core.CheckReport
import com.eteditor.core.DocumentKind
import com.eteditor.core.EpubToolkit
import com.eteditor.core.TxtDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

fun EditorController.runSaveCheck(): CheckReport {
    val report = when (kind) {
        DocumentKind.Epub -> epub?.let { EpubToolkit.check(it, epubExportOptions(epubHideSection0001FromNcx)) }
            ?: CheckReport(listOf("请先打开 EPUB"), emptyList())
        DocumentKind.Txt -> checkTxt()
        DocumentKind.None -> CheckReport(listOf("请先打开 EPUB 或 TXT"), emptyList())
    }
    checkReport = report
    if (report.ok) {
        statusMessage = "保存前检查通过"
        log("保存前检查通过：${report.warnings.size} 条提醒")
    } else {
        statusMessage = "保存前检查未通过"
        log("保存前检查未通过：${report.errors.size} 个问题")
    }
    return report
}

suspend fun EditorController.saveToOriginal() {
    if (busy) return
    val uri = sourceUri
    if (uri == null || kind == DocumentKind.None) {
        statusMessage = "没有可覆盖保存的原文件"
        saveFailureMessage = statusMessage
        return
    }
    saveFailureMessage = ""
    try {
        val saved = runBusy("保存") {
            setSaveProgress(0.04f, "保存中：检查文件")
            val report = runSaveCheck()
            if (!report.ok) {
                saveFailureMessage = buildSaveCheckFailureMessage(report)
                statusMessage = "保存失败：保存前检查未通过"
                return@runBusy
            }
            setSaveProgress(0.12f, "保存中：准备文件")
            writeCurrentDocument(uri, ::setSaveProgress)
            val renameMessage = if (kind == DocumentKind.Txt) {
                setSaveProgress(0.94f, "保存中：写回文件名")
                renameTxtSourceToDisplayedTitle(uri)
            } else {
                null
            }
            setSaveProgress(1f, "保存完成")
            delay(120)
            hasUnsavedChanges = false
            statusMessage = renameMessage ?: "已保存"
        }
        if (!saved) {
            if (saveFailureMessage.isBlank()) {
                saveFailureMessage = statusMessage.ifBlank { "保存失败" }
            }
        }
    } finally {
        clearSaveProgress()
    }
}

private suspend fun EditorController.renameTxtSourceToDisplayedTitle(uri: Uri): String? {
    val document = txt ?: return null
    val targetBaseName = title.sanitizedSaveBaseName(document.originalName.baseName("TXT"))
    val targetFileName = "$targetBaseName.txt"
    val currentFileName = documentDisplayName(appContext, uri)
    if (currentFileName == targetFileName) return null

    val renameResult = renameDocumentFile(appContext, uri, targetFileName)
    val renamedUri = renameResult.getOrNull()
    if (renamedUri == null) {
        val reason = renameResult.exceptionOrNull()?.let { error ->
            error.message ?: error.javaClass.simpleName
        } ?: "当前文件位置不支持重命名"
        log("TXT 重命名失败：$reason")
        return "已保存，但重命名失败：$reason"
    }

    sourceUri = renamedUri
    rememberWritableDocumentUri(appContext, renamedUri)
    txt = document.copy(originalName = targetBaseName)
    documentContentVersion++
    title = targetBaseName
    return "已保存并重命名"
}

private suspend fun EditorController.writeCurrentDocument(
    uri: Uri,
    onProgress: (Float, String) -> Unit = { _, _ -> }
): Int {
    return when (kind) {
        DocumentKind.Epub -> {
            val book = epub ?: error("没有 EPUB 可保存")
            val options = epubExportOptions(epubHideSection0001FromNcx)
            onProgress(0.22f, "保存中：生成 EPUB")
            val hiddenSection0001 = EpubToolkit.hiddenSection0001Count(book, options)
            val bytes = withContext(Dispatchers.Default) {
                EpubToolkit.export(book, options)
            }
            onProgress(0.78f, "保存中：写入文件")
            writeDocumentBytes(appContext, uri, bytes)
            onProgress(0.92f, "保存中：完成写入")
            log("EPUB 已保存：${bytes.size / 1024} KB")
            hiddenSection0001
        }
        DocumentKind.Txt -> {
            onProgress(0.18f, "保存中：等待章节同步")
            awaitPendingTxtMoveChapterSync()
            val document = txt ?: error("没有 TXT 可保存")
            onProgress(
                0.42f,
                if (txtAutoNumberOnSave) "保存中：映射并重排目录标题" else "保存中：映射目录标题"
            )
            val prepared = withContext(Dispatchers.Default) {
                prepareTxtDocumentSave(
                    document = document,
                    renumberTitles = txtAutoNumberOnSave,
                    numberStartAtOne = txtChapterNumberStartAtOneOnSave
                )
            }
            applyTxtSaveChapterMappings(document, prepared.mapping)
            onProgress(0.58f, "保存中：编码文本")
            onProgress(0.80f, "保存中：写入文件")
            writeDocumentBytes(appContext, uri, prepared.bytes)
            onProgress(0.90f, "保存中：刷新目录")
            refreshTxtAfterSave(document, prepared.keepMappedCatalog)
            log("TXT 已保存：${prepared.encodingLabel}")
            0
        }
        DocumentKind.None -> {
            log("没有可保存的文件")
            0
        }
    }
}

private fun EditorController.applyTxtSaveChapterMappings(
    document: TxtDocument,
    mapped: TxtSaveChapterMappingResult
) {
    if (mapped.changedCount <= 0) return
    val currentChapters = document.chapters
    document.text = mapped.text
    documentContentVersion++
    clearTextSearchState()
    document.chapters = rebuildTxtChaptersFromSavedLines(
        text = document.text,
        chapters = currentChapters,
        config = currentTxtChapterDetectionConfig()
    )
        .ifEmpty { detectCurrentTxtChapters(document.text) }
}

private fun EditorController.refreshTxtAfterSave(
    document: TxtDocument,
    keepCurrentChapters: Boolean = false
) {
    if (!keepCurrentChapters) {
        document.chapters = detectCurrentTxtChapters(document.text)
    }
    previewChapterIndex = previewChapterIndex.coerceIn(
        if (txtHasPreface()) -1 else 0,
        document.chapters.lastIndex.coerceAtLeast(0)
    )
    checkReport = null
    refreshChapters()
    refreshPreview()
}

private fun EditorController.checkTxt(): CheckReport {
    val document = txt ?: return CheckReport(listOf("请先打开 TXT"), emptyList())
    val warnings = mutableListOf<String>()
    if (document.text.isBlank()) return CheckReport(listOf("TXT 内容为空"), emptyList())
    if (document.chapters.isEmpty()) warnings += "未识别到章节标题"
    val statusCounts = document.chapters.flatMap { it.status }.groupingBy { it }.eachCount()
    if (statusCounts.isNotEmpty()) {
        warnings += "\u76ee\u5f55\u72b6\u6001\uff1a" + statusCounts.entries.joinToString("\uff1b") { "${it.key} ${it.value}" }
    }
    return CheckReport(emptyList(), warnings)
}
