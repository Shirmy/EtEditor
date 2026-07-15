package com.eteditor

import com.eteditor.core.DocumentKind

fun EditorController.updateChapterItem(chapterIndex: Int, fileName: String, chapterTitle: String): Boolean {
    return when (kind) {
        DocumentKind.Epub -> updateEpubChapterItem(chapterIndex, fileName, chapterTitle)
        DocumentKind.Txt -> updateTxtChapterItem(chapterIndex, chapterTitle)
        DocumentKind.None -> false
    }
}

private fun EditorController.updateTxtChapterItem(chapterIndex: Int, chapterTitle: String): Boolean {
    if (warnTxtMoveChapterSyncPending("编辑目录标题")) return false
    val document = txt ?: return false
    val result = updateTxtChapterTitleText(document, chapterIndex, chapterTitle)
    if (!result.success) {
        if (result.message.isNotBlank()) statusMessage = result.message
        return false
    }
    document.text = result.text
    document.chapters = detectCurrentTxtChapters(document.text)
    checkReport = null
    markDocumentChanged()
    applyTxtCatalogPurifyRulesAfterCatalogChange()
    refreshChapters()
    selectPreviewChapter(chapterIndex)
    return true
}

/** Applies bulk directory title edits. Indexes are 0-based chapter positions. */
internal fun EditorController.applyDirectoryBulkTitleEdits(newTitles: List<Pair<Int, String>>): Int {
    val cleaned = newTitles
        .mapNotNull { (index, title) ->
            val next = title.trim()
            if (next.isBlank()) null else index to next
        }
        .distinctBy { it.first }
    if (cleaned.isEmpty()) {
        statusMessage = "没有可写入的标题"
        return 0
    }
    return when (kind) {
        DocumentKind.Epub -> {
            val source = epub ?: return 0
            val book = source.mutableStructureCopy()
            val result = applyRenamedTitlesToEpub(book, cleaned)
            if (!result.attempted) return 0
            if (result.count > 0) {
                epub = book
                markDocumentChanged()
            }
            refreshChapters()
            statusMessage = if (result.count > 0) {
                "批量编辑标题：修改 ${result.count} 项"
            } else {
                "批量编辑标题：无需修改"
            }
            result.count
        }
        DocumentKind.Txt -> {
            if (warnTxtMoveChapterSyncPending("批量编辑标题")) return 0
            val document = txt ?: return 0
            val result = applyRenamedTitlesToTxt(document, cleaned, ::detectCurrentTxtChapters)
            if (!result.attempted) return 0
            applyTxtCatalogPurifyRulesAfterCatalogChange()
            if (result.count > 0) markDocumentChanged()
            refreshChapters()
            statusMessage = if (result.count > 0) {
                "批量编辑标题：修改 ${result.count} 项"
            } else {
                "批量编辑标题：无需修改"
            }
            result.count
        }
        DocumentKind.None -> 0
    }
}
