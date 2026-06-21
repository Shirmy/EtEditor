package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.DocumentKind

fun EditorController.editableBodyText(): String {
    return when (kind) {
        DocumentKind.Epub -> {
            val chapter = epub?.chapters?.getOrNull(previewChapterIndex) ?: return ""
            htmlVisibleBodyContent(chapter.html)
        }
        DocumentKind.Txt -> {
            val document = txt ?: return ""
            if (txtPreviewMode == TXT_PREVIEW_MODE_FULL) return document.text
            if (document.chapters.isEmpty()) return document.text
            if (previewChapterIndex < 0) {
                val firstChapter = document.chapters.firstOrNull() ?: return document.text
                val end = firstChapter.startIndex
                return document.text.substring(0, end.coerceAtMost(document.text.length))
            }
            val chapter = document.chapters.getOrNull(previewChapterIndex) ?: return ""
            val start = chapter.startIndex.coerceIn(0, document.text.length)
            val end = chapter.endIndex.coerceIn(start, document.text.length)
            document.text.substring(start, end)
        }
        DocumentKind.None -> ""
    }
}

fun EditorController.editableBodyTextAt(targetKind: DocumentKind, chapterIndex: Int, previewMode: String): String {
    if (targetKind != kind) return ""
    val originalIndex = previewChapterIndex
    val originalMode = txtPreviewMode
    return try {
        previewChapterIndex = chapterIndex
        if (targetKind == DocumentKind.Txt) {
            txtPreviewMode = previewMode
        }
        editableBodyText()
    } finally {
        previewChapterIndex = originalIndex
        if (targetKind == DocumentKind.Txt) {
            txtPreviewMode = originalMode
        }
    }
}

fun EditorController.updateEditableBodyText(text: String): Boolean {
    return when (kind) {
        DocumentKind.Epub -> {
            val book = epub ?: return false
            val chapterIndex = previewChapterIndex
            val chapter = book.chapters.getOrNull(chapterIndex) ?: return false
            val bodyParts = htmlBodyContentParts(chapter.html)
            val currentText = bodyParts.body
            // EPUB 保存时会统一换行符为 CRLF,而 CodeEditor 的 getText() 可能返回 \n。
            // 统一换行符再比较,避免没改内容也被判定为修改而写入。
            fun normCrLf(s: String) = s.replace("\r\n", "\n").replace('\r', '\n')
            if (normCrLf(currentText) == normCrLf(text)) {
                statusMessage = "正文未修改"
                return true
            }
            chapter.html = rebuildHtmlWithBodyContent(bodyParts.prefix, text, bodyParts.suffix)
            // 编辑结果要同步进 book.entries（真正落盘的字节）并规整换行，否则只改了内存副本，
            // 保存/导出仍是旧内容——这正是其它修改 EPUB 正文的路径都会做、而此处此前遗漏的一步。
            normalizeEpubChapterLineEndingsToCrlf(book, chapter)
            chapter.wordCount = ChapterDetector.countHtmlChars(chapter.html)
            checkReport = null
            markDocumentChanged()
            clearTextSearchState()
            refreshEpubChapterInfoAt(chapterIndex, refreshPreview = false)
            statusMessage = "正文已更新"
            true
        }
        DocumentKind.Txt -> {
            if (warnTxtMoveChapterSyncPending("编辑正文")) return false
            val document = txt ?: return false
            if (txtPreviewMode == TXT_PREVIEW_MODE_FULL) {
                if (document.text == text) {
                    statusMessage = "正文未修改"
                    return true
                }
                document.text = text
                document.chapters = detectCurrentTxtChapters(document.text)
                checkReport = null
                markDocumentChanged()
                clearTextSearchState()
                refreshPreview()
                statusMessage = "全文正文已更新，正在重建目录"
                startTxtCatalogDetection(
                    document,
                    documentSessionKey,
                    "TXT 目录重建完成",
                    applyCatalogMappings = false
                )
                return true
            }
            if (document.chapters.isEmpty()) {
                if (document.text == text) {
                    statusMessage = "正文未修改"
                    return true
                }
                document.text = text
                document.chapters = detectCurrentTxtChapters(document.text)
                previewChapterIndex = 0
                checkReport = null
                markDocumentChanged()
                clearTextSearchState()
                refreshPreview()
                statusMessage = "正文已更新，正在重建目录"
                startTxtCatalogDetection(
                    document,
                    documentSessionKey,
                    "TXT 目录重建完成",
                    applyCatalogMappings = false
                )
                return true
            }
            if (previewChapterIndex < 0) {
                val firstChapter = txtFirstChapterByTextOrder(document) ?: return false
                val end = firstChapter.startIndex
                val currentText = document.text.substring(0, end.coerceAtMost(document.text.length))
                if (currentText == text) {
                    statusMessage = "正文未修改"
                    return true
                }
                document.text = document.text.replaceRange(0, end.coerceAtMost(document.text.length), text)
                document.chapters = detectCurrentTxtChapters(document.text)
                previewChapterIndex = if (txtHasPreface()) -1 else 0
                checkReport = null
                markDocumentChanged()
                clearTextSearchState()
                previewTitle = "前言"
                previewChapterCount = document.chapters.size + if (txtHasPreface()) 1 else 0
                setPreviewTextFromSource(text, -1)
                statusMessage = "前言已更新，正在重建目录"
                startTxtCatalogDetection(
                    document,
                    documentSessionKey,
                    "TXT 目录重建完成",
                    applyCatalogMappings = false
                )
                return true
            }
            val chapter = document.chapters.getOrNull(previewChapterIndex) ?: return false
            val start = chapter.startIndex.coerceIn(0, document.text.length)
            val end = chapter.endIndex.coerceIn(start, document.text.length)
            val currentText = document.text.substring(start, end)
            if (currentText == text) {
                statusMessage = "正文未修改"
                return true
            }
            document.text = document.text.replaceRange(start, end, text)
            document.chapters = detectCurrentTxtChapters(document.text)
            checkReport = null
            markDocumentChanged()
            clearTextSearchState()
            previewTitle = text.lineSequence().firstOrNull().orEmpty().trim()
                .ifBlank { chapter.title.ifBlank { "\u65e0\u6807\u9898" } }
            previewChapterCount = document.chapters.size + if (txtHasPreface()) 1 else 0
            setPreviewTextFromSource(text, previewChapterIndex)
            statusMessage = "正文已更新，正在重建目录"
            startTxtCatalogDetection(
                document,
                documentSessionKey,
                "TXT 目录重建完成",
                applyCatalogMappings = false
            )
            true
        }
        DocumentKind.None -> false
    }
}

fun EditorController.updateEditableBodyTextAt(
    targetKind: DocumentKind,
    chapterIndex: Int,
    previewMode: String,
    text: String,
    showNoChangeMessage: Boolean = true
): Boolean {
    if (targetKind != kind) return false
    val originalIndex = previewChapterIndex
    val originalMode = txtPreviewMode
    val originalStatus = statusMessage
    val shouldRefreshPreview = targetKind != DocumentKind.Epub || originalIndex == chapterIndex
    val current = editableBodyTextAt(targetKind, chapterIndex, previewMode)
    val changed = current != text
    if (!changed && !showNoChangeMessage) return true
    return try {
        previewChapterIndex = chapterIndex
        if (targetKind == DocumentKind.Txt) {
            txtPreviewMode = previewMode
        }
        updateEditableBodyText(text).also {
            if (!showNoChangeMessage && !changed) {
                statusMessage = originalStatus
            }
        }
    } finally {
        previewChapterIndex = originalIndex
        if (targetKind == DocumentKind.Txt) {
            txtPreviewMode = originalMode
        }
        if (shouldRefreshPreview) {
            refreshPreview()
        }
    }
}
