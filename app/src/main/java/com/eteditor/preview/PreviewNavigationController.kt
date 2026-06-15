package com.eteditor

import com.eteditor.core.DocumentKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun EditorController.previousPreviewChapter() {
    clearPreviewHighlight()
    when (kind) {
        DocumentKind.Epub -> {
            val book = epub ?: return
            if (book.chapters.isEmpty()) return
            previewChapterIndex = (previewChapterIndex - 1).coerceAtLeast(0)
            refreshPreviewWithChapterSwitchProgress()
        }
        DocumentKind.Txt -> {
            if (warnTxtMoveChapterSyncPending("\u5207\u6362\u7ae0\u8282\u9884\u89c8")) return
            val document = txt ?: return
            if (document.chapters.isEmpty()) return
            val minIndex = if (txtHasPreface()) -1 else 0
            previewChapterIndex = (previewChapterIndex - 1).coerceAtLeast(minIndex)
            refreshPreviewWithChapterSwitchProgress()
        }
        DocumentKind.None -> Unit
    }
}

fun EditorController.nextPreviewChapter() {
    clearPreviewHighlight()
    when (kind) {
        DocumentKind.Epub -> {
            val book = epub ?: return
            if (book.chapters.isEmpty()) return
            previewChapterIndex = (previewChapterIndex + 1).coerceAtMost(book.chapters.lastIndex)
            refreshPreviewWithChapterSwitchProgress()
        }
        DocumentKind.Txt -> {
            if (warnTxtMoveChapterSyncPending("\u5207\u6362\u7ae0\u8282\u9884\u89c8")) return
            val document = txt ?: return
            if (document.chapters.isEmpty()) return
            previewChapterIndex = (previewChapterIndex + 1).coerceAtMost(document.chapters.lastIndex)
            refreshPreviewWithChapterSwitchProgress()
        }
        DocumentKind.None -> Unit
    }
}

fun EditorController.selectPreviewChapter(index: Int) {
    clearPreviewHighlight()
    when (kind) {
        DocumentKind.Epub -> {
            val book = epub ?: return
            if (book.chapters.isEmpty()) return
            previewChapterIndex = index.coerceIn(0, book.chapters.lastIndex)
            refreshPreviewWithChapterSwitchProgress()
        }
        DocumentKind.Txt -> {
            if (warnTxtMoveChapterSyncPending("\u5207\u6362\u7ae0\u8282\u9884\u89c8")) return
            val document = txt ?: return
            if (txtPreviewMode != TXT_PREVIEW_MODE_CHAPTER) {
                txtPreviewMode = TXT_PREVIEW_MODE_CHAPTER
            }
            txtFullPreviewCachedAnchor = null
            if (document.chapters.isEmpty()) {
                previewChapterIndex = 0
                refreshPreviewWithChapterSwitchProgress()
                return
            }
            val minIndex = if (txtHasPreface()) -1 else 0
            previewChapterIndex = index.coerceIn(minIndex, document.chapters.lastIndex)
            refreshPreviewWithChapterSwitchProgress()
        }
        DocumentKind.None -> Unit
    }
}

private fun EditorController.refreshPreviewWithChapterSwitchProgress() {
    setBodyOperationProgress(0.15f, "切换章节：准备")
    refreshPreview()
    setBodyOperationProgress(1f, "切换章节：完成")
    controllerScope.launch {
        delay(160)
        clearBodyOperationProgress()
    }
}
