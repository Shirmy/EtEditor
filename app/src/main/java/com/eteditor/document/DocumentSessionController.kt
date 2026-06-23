package com.eteditor

import android.net.Uri
import com.eteditor.core.DocumentKind
import com.eteditor.core.EpubToolkit
import com.eteditor.core.TextCodec
import com.eteditor.core.TxtDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

suspend fun EditorController.openFile(uri: Uri) {
    val name = documentDisplayName(appContext, uri)
    val extension = name.substringAfterLast('.', "").lowercase()
    val mimeType = appContext.contentResolver.getType(uri).orEmpty().lowercase()
    when {
        extension == "epub" || mimeType.contains("epub") -> openEpub(uri)
        extension == "txt" || mimeType.startsWith("text/") -> openTxt(uri)
        else -> {
            statusMessage = "只支持打开 EPUB 或 TXT"
            log("不支持的文件类型：$name")
        }
    }
}

suspend fun EditorController.openEpub(uri: Uri) = runBusy("打开 EPUB") {
    cancelTxtCatalogDetection()
    cancelTxtMoveChapterSync()
    cancelEpubWordCountCalculation()
    val name = documentDisplayName(appContext, uri).baseName("epub_modified")
    val bytes = readDocumentBytes(appContext, uri, EPUB_DOCUMENT_MAX_BYTES, "EPUB 文件")
    val fileSizeBytes = bytes.size.toLong()
    val exportOptions = epubExportOptions(epubHideSection0001FromNcx)
    val opened = withContext(Dispatchers.Default) {
        val book = EpubToolkit.parse(bytes, name, calculateChapterWordCount = false)
        OpenedEpubDocument(
            book = book,
            chapters = buildEpubChapterInfos(book, exportOptions),
            summaryMeta = buildEpubSummaryInitialMeta(fileSizeBytes = fileSizeBytes)
        )
    }
    val book = opened.book
    rememberWritableDocumentUri(appContext, uri)
    resetEpubDirectoryVisibilityDefaults()
    sourceUri = uri
    epub = book
    txt = null
    documentSessionKey++
    val sessionKey = documentSessionKey
    kind = DocumentKind.Epub
    txtSupplementedCatalogLines = emptyList()
    title = "$name.epub"
    subtitle = "EPUB | OPF: ${book.opfPath} | 章节 ${book.chapters.size}"
    epubFileSizeBytes = fileSizeBytes
    epubSummaryMeta = opened.summaryMeta
    checkReport = null
    previewChapterIndex = 0
    previewChapterCount = book.chapters.size
    previewVisibleSourceOffset = 0
    previewVisibleSourceLineOffset = 0
    previewTitle = book.chapters.firstOrNull()?.title?.ifBlank { "无标题" } ?: "EPUB 正文预览"
    previewText = ""
    previewHighlightStart = -1
    previewHighlightEnd = -1
    resetDocumentSessionRuntimeState()
    chapters = opened.chapters
    hasUnsavedChanges = false
    selectedScreen = AppScreen.Files
    statusMessage = "已打开 EPUB"
    log("EPUB 打开完成：${book.chapters.size} 个 spine HTML 章节")
    controllerScope.launch {
        delay(80)
        if (documentSessionKey == sessionKey && kind == DocumentKind.Epub && epub === book) {
            refreshPreview()
        }
    }
}

suspend fun EditorController.openTxt(uri: Uri) = runBusy("打开 TXT") {
    cancelTxtCatalogDetection()
    cancelTxtMoveChapterSync()
    cancelEpubWordCountCalculation()
    val name = documentDisplayName(appContext, uri).baseName("txt_modified")
    val bytes = readDocumentBytes(appContext, uri, TXT_DOCUMENT_MAX_BYTES, "TXT 文件")
    val decoded = withContext(Dispatchers.Default) { TextCodec.decode(bytes) }
    rememberWritableDocumentUri(appContext, uri)
    val document = TxtDocument(
        originalName = name,
        text = decoded.first,
        encoding = decoded.second,
        chapters = emptyList()
    )
    sourceUri = uri
    txt = document
    epub = null
    epubSummaryMeta = ""
    epubFileSizeBytes = null
    documentSessionKey++
    txtHiddenCatalogLineIndices = emptySet()
    txtSupplementedCatalogLines = emptyList()
    txtAutoNumberOnSave = true
    txtChapterNumberStartAtOneOnSave = true
    kind = DocumentKind.Txt
    title = txtFilteredBookTitle(name)
    txtCatalogParsing = true
    subtitle = "${document.encoding} | ${compactByteSize(bytes.size)} | 目录识别中"
    checkReport = null
    previewChapterIndex = 0
    previewChapterCount = 0
    previewVisibleSourceOffset = 0
    previewVisibleSourceLineOffset = 0
    previewTitle = "TXT 正文预览"
    previewText = ""
    previewHighlightStart = -1
    previewHighlightEnd = -1
    txtPreviewMode = TXT_PREVIEW_MODE_CHAPTER
    resetDocumentSessionRuntimeState()
    chapters = emptyList()
    subtitle = "${document.encoding} | ${compactByteSize(bytes.size)} | 目录识别中"
    hasUnsavedChanges = false
    selectedScreen = AppScreen.Files
    statusMessage = "已打开 TXT，正在识别目录"
    log("TXT 已打开：识别编码 ${document.encoding}，目录后台识别中")
    startTxtCatalogDetection(
        document = document,
        sessionKey = documentSessionKey,
        doneLabel = "TXT 目录识别完成",
        autoPurifyAfterDetection = true,
        autoSelectChapterRules = true,
        preferPrefaceAfterDetection = true
    )
}

fun EditorController.clearDocument() {
    cancelTxtCatalogDetection()
    cancelTxtMoveChapterSync()
    cancelEpubWordCountCalculation()
    sourceUri = null
    epub = null
    txt = null
    epubSummaryMeta = ""
    epubFileSizeBytes = null
    documentSessionKey++
    txtHiddenCatalogLineIndices = emptySet()
    txtSupplementedCatalogLines = emptyList()
    kind = DocumentKind.None
    title = "未打开文件"
    subtitle = "等待打开 EPUB 或 TXT"
    chapters = emptyList()
    checkReport = null
    statusMessage = ""
    previewTitle = ""
    previewText = ""
    previewHighlightStart = -1
    previewHighlightEnd = -1
    previewChapterIndex = 0
    previewChapterCount = 0
    resetDocumentSessionRuntimeState()
    hasUnsavedChanges = false
    selectedScreen = AppScreen.Files
    log("已清空当前文档")
}

private data class OpenedEpubDocument(
    val book: com.eteditor.core.EpubBook,
    val chapters: List<com.eteditor.core.ChapterInfo>,
    val summaryMeta: String
)

private fun EditorController.resetDocumentSessionRuntimeState() {
    resetOpenDocumentRuntimeState()
    epubLongPressSplitChapter = false
    txtSupplementLongPressMode = false
    clearTextSearchState()
    clearFileRenamePlan()
    clearTitleRenamePlan()
    clearTitleFormatPlan()
    clearFetchInfoPreview()
    clearGeneratedCoverPreview()
    clearInsertChapterSourcePreview()
    resetAutomationRunRuntimeState()
    txtEnabledChapterRuleKeys = emptySet()
    txtFullPreviewCachedAnchor = null
}

private fun EditorController.resetOpenDocumentRuntimeState() {
    txtChapterRulesRefreshDeferred = false
    txtTextReplacementRefreshDeferred = false
    txtTextReplacementRefreshApplying = false
    txtBulkMoveChapterProgress = null
    txtBulkMoveChapterProgressText = ""
    resetBuiltInToolState()
}
