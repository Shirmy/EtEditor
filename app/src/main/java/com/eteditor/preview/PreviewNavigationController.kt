package com.eteditor

import com.eteditor.core.DocumentKind

fun EditorController.previousPreviewChapter() {
    when (kind) {
        DocumentKind.Epub -> {
            val book = epub ?: return
            if (book.chapters.isEmpty()) return
            val target = (previewChapterIndex - 1).coerceAtLeast(0)
            if (target == previewChapterIndex) return
            clearPreviewHighlight()
            previewChapterIndex = target
            refreshPreview()
        }
        DocumentKind.Txt -> {
            if (warnTxtMoveChapterSyncPending("\u5207\u6362\u7ae0\u8282\u9884\u89c8")) return
            val document = txt ?: return
            if (document.chapters.isEmpty()) return
            val minIndex = if (txtHasPreface()) -1 else 0
            val target = (previewChapterIndex - 1).coerceAtLeast(minIndex)
            if (target == previewChapterIndex) return
            clearPreviewHighlight()
            previewChapterIndex = target
            refreshPreview()
        }
        DocumentKind.None -> Unit
    }
}

fun EditorController.nextPreviewChapter() {
    when (kind) {
        DocumentKind.Epub -> {
            val book = epub ?: return
            if (book.chapters.isEmpty()) return
            val target = (previewChapterIndex + 1).coerceAtMost(book.chapters.lastIndex)
            if (target == previewChapterIndex) return
            clearPreviewHighlight()
            previewChapterIndex = target
            refreshPreview()
        }
        DocumentKind.Txt -> {
            if (warnTxtMoveChapterSyncPending("\u5207\u6362\u7ae0\u8282\u9884\u89c8")) return
            val document = txt ?: return
            if (document.chapters.isEmpty()) return
            val target = (previewChapterIndex + 1).coerceAtMost(document.chapters.lastIndex)
            if (target == previewChapterIndex) return
            clearPreviewHighlight()
            previewChapterIndex = target
            refreshPreview()
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
            refreshPreview()
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
                refreshPreview()
                return
            }
            val minIndex = if (txtHasPreface()) -1 else 0
            previewChapterIndex = index.coerceIn(minIndex, document.chapters.lastIndex)
            refreshPreview()
        }
        DocumentKind.None -> Unit
    }
}
