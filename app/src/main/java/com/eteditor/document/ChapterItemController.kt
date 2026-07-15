package com.eteditor

import com.eteditor.core.DocumentKind
import com.eteditor.core.EpubBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            if (result.count > 0) {
                applyTxtCatalogPurifyRulesAfterCatalogChange()
                markDocumentChanged()
                refreshChapters()
                statusMessage = "批量编辑标题：修改 ${result.count} 项"
            } else {
                statusMessage = "批量编辑标题：无需修改"
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
            statusMessage = DIRECTORY_BULK_TITLE_EDIT_BUSY_MESSAGE
            0
        }
    ) {
        val source = epub ?: return@withExclusiveOperation 0
        val sourceContentVersion = documentContentVersion
        val prepared = withContext(Dispatchers.Default) {
            val book = source.mutableStructureCopy()
            // 一次整表写回；进度路径内会按章 yield，大书更顺。
            val count = applyRenamedTitlesToEpubWithProgress(book, cleaned) { _, _ -> }
            PreparedEpubMutation(
                source = source,
                sourceContentVersion = sourceContentVersion,
                book = book,
                result = count
            )
        }
        if (!canPublishDirectoryBulkTitleEdit(epub, documentContentVersion, prepared)) {
            statusMessage = DIRECTORY_BULK_TITLE_EDIT_STALE_MESSAGE
            return@withExclusiveOperation 0
        }
        val count = prepared.result
        if (count > 0) {
            epub = prepared.book
            markDocumentChanged()
            checkReport = null
            refreshChapters()
            statusMessage = "批量编辑标题：修改 $count 项"
        } else {
            statusMessage = "批量编辑标题：无需修改"
        }
        count
    }
}

internal const val DIRECTORY_BULK_TITLE_EDIT_BUSY_MESSAGE = "正在处理，请稍后重试"
internal const val DIRECTORY_BULK_TITLE_EDIT_STALE_MESSAGE = "文档内容已变化，请重试"

/** 批量改标题写回前：书本引用与内容版本须与准备时一致。 */
internal fun canPublishDirectoryBulkTitleEdit(
    activeBook: EpubBook?,
    activeContentVersion: Int,
    prepared: PreparedEpubMutation<*>
): Boolean = preparedEpubMutationMatchesSource(activeBook, activeContentVersion, prepared)
