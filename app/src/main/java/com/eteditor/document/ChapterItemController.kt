package com.eteditor

import com.eteditor.core.DocumentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

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

/**
 * Applies bulk directory title edits. Indexes are 0-based chapter positions.
 * EPUB path uses exclusive busy lock, background mutation, and source version check.
 */
internal suspend fun EditorController.applyDirectoryBulkTitleEdits(newTitles: List<Pair<Int, String>>): Int {
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
        DocumentKind.Epub -> applyDirectoryBulkTitleEditsToEpub(cleaned)
        DocumentKind.Txt -> {
            // 产品当前只接 EPUB 批量改标题；TXT 写回保留以防误调，但不走 UI 入口。
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

private suspend fun EditorController.applyDirectoryBulkTitleEditsToEpub(
    cleaned: List<Pair<Int, String>>
): Int {
    return withExclusiveOperation(
        isBusy = { busy },
        setBusy = { busy = it },
        onBusy = {
            statusMessage = "正在处理，请稍后重试"
            0
        }
    ) {
        val source = epub ?: return@withExclusiveOperation 0
        val sourceContentVersion = documentContentVersion
        val prepared = withContext(Dispatchers.Default) {
            val book = source.mutableStructureCopy()
            var count = 0
            cleaned.forEachIndexed { index, pair ->
                val result = applyRenamedTitlesToEpub(book, listOf(pair))
                count += result.count
                if (index % 8 == 7) yield()
            }
            PreparedEpubMutation(
                source = source,
                sourceContentVersion = sourceContentVersion,
                book = book,
                result = count
            )
        }
        if (!preparedEpubMutationMatchesSource(epub, documentContentVersion, prepared)) {
            statusMessage = "文档内容已变化，请重试"
            return@withExclusiveOperation 0
        }
        val count = prepared.result
        if (count > 0) {
            epub = prepared.book
            markDocumentChanged()
        }
        checkReport = null
        refreshChapters()
        statusMessage = if (count > 0) {
            "批量编辑标题：修改 $count 项"
        } else {
            "批量编辑标题：无需修改"
        }
        count
    }
}
