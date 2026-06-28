package com.eteditor

import com.eteditor.core.DocumentKind
import kotlinx.coroutines.yield

internal fun EditorController.runFileRenameTool(tool: EditorTool, manual: Boolean): Boolean {
    if (kind != DocumentKind.Epub) {
        statusMessage = "\u6587\u4ef6\u91cd\u547d\u540d\u4ec5\u652f\u6301 EPUB"
        return false
    }
    if (chapters.isEmpty()) {
        statusMessage = "\u6ca1\u6709\u53ef\u91cd\u547d\u540d\u7684\u7ae0\u8282\u6587\u4ef6"
        return false
    }
    val parameters = fileRenameParameters(tool)
    val planReady = prepareFileRenamePlan(tool, parameters)
    if (!planReady) return false
    val changed = fileRenamePlan.count { it.changed }
    if (!manual && changed <= 0) {
        statusMessage = "\u6587\u4ef6\u91cd\u547d\u540d\uff1a\u5904\u7406 ${fileRenamePlan.size} \u4e2a\u6587\u4ef6\uff0c\u4fee\u6539 0 \u4e2a\uff0c\u65e0\u9700\u4fee\u6539"
        clearFileRenamePlan()
        return true
    }

    if (parameters.preview) {
        if (manual) return true
        statusMessage = needsConfirmationMessage()
        return false
    }

    applyFileRenamePlan(fileRenamePlan)
    return true
}

private fun EditorController.prepareFileRenamePlan(tool: EditorTool, parameters: FileRenameParameters): Boolean {
    val plan = buildFileRenamePlan(parameters)
    fileRenamePlan = plan
    fileRenamePlanToolId = tool.id
    if (plan.isEmpty()) return false
    val changed = plan.count { it.changed }
    statusMessage = "\u6587\u4ef6\u91cd\u547d\u540d\u8ba1\u5212\uff1a\u547d\u4e2d ${plan.size} \u4e2a\uff0c\u9700\u6539\u540d $changed \u4e2a"
    return true
}

private fun EditorController.buildFileRenamePlan(parameters: FileRenameParameters): List<FileRenamePlanItem> {
    val book = epub ?: run {
        statusMessage = "\u6ca1\u6709\u53ef\u91cd\u547d\u540d\u7684 EPUB"
        return emptyList()
    }
    return buildFileRenamePlanItems(
        book = book,
        parameters = parameters,
        targetIndices = epubBodyChapterIndices(book),
        onError = { message -> statusMessage = message }
    )
}

internal fun EditorController.applyFileRenamePlan(plan: List<FileRenamePlanItem>): Int {
    val source = epub ?: run {
        statusMessage = "\u6ca1\u6709\u53ef\u91cd\u547d\u540d\u7684 EPUB"
        return 0
    }
    if (plan.isEmpty()) return 0
    val book = source.mutableDeepCopy()
    val renamed = applyFileRenamePlanToEpub(book, plan)
    checkReport = null
    if (renamed > 0) {
        epub = book
        markDocumentChanged()
    }
    refreshChapters()
    if (renamed == 0) {
        statusMessage = "\u6587\u4ef6\u91cd\u547d\u540d\uff1a\u5904\u7406 ${plan.size} \u4e2a\u6587\u4ef6\uff0c\u4fee\u6539 0 \u4e2a\uff0c\u65e0\u9700\u4fee\u6539"
    } else {
        statusMessage = "\u6587\u4ef6\u91cd\u547d\u540d\u5b8c\u6210\uff1a\u5904\u7406 ${plan.size} \u4e2a\u6587\u4ef6\uff0c\u6539\u540d $renamed \u4e2a"
    }
    clearFileRenamePlan()
    return renamed
}

suspend fun EditorController.applyPreparedFileRenamePlanWithProgress(
    editorToolId: String,
    onProgress: (completed: Int, total: Int) -> Unit
): Boolean {
    if (fileRenamePlanToolId != editorToolId || fileRenamePlan.isEmpty()) {
        statusMessage = "\u6ca1\u6709\u53ef\u6267\u884c\u7684\u91cd\u547d\u540d\u8ba1\u5212"
        return false
    }
    val changed = applyFileRenamePlanWithProgress(fileRenamePlan, onProgress)
    if (changed <= 0) {
        return false
    }
    return true
}

private suspend fun EditorController.applyFileRenamePlanWithProgress(
    plan: List<FileRenamePlanItem>,
    onProgress: (completed: Int, total: Int) -> Unit
): Int {
    val source = epub ?: run {
        statusMessage = "\u6ca1\u6709\u53ef\u91cd\u547d\u540d\u7684 EPUB"
        return 0
    }
    if (plan.isEmpty()) return 0
    val book = source.mutableDeepCopy()
    val total = plan.size.coerceAtLeast(1)
    onProgress(0, total)
    yield()
    var renamed = 0
    val movedEntries = mutableListOf<Triple<com.eteditor.core.EpubChapter, FileRenamePlanItem, ByteArray?>>()
    for ((index, item) in plan.withIndex()) {
        if (item.changed) {
            val chapter = book.chapters.getOrNull(item.chapterIndex)
            if (chapter != null) {
                val bytes = book.entries.remove(item.oldPath)
                renamed += 1
                movedEntries += Triple(chapter, item, bytes)
            }
        }
        onProgress(index + 1, total)
        yield()
    }

    movedEntries.forEach { (chapter, item, bytes) ->
        val newHref = chapter.href.replaceHrefFileName(item.newFileName)
        if (bytes != null) {
            book.entries[item.newPath] = bytes
        }
        chapter.pathAliases += item.oldPath
        chapter.pathAliases += item.newPath
        chapter.path = item.newPath
        chapter.href = newHref
        book.manifest[chapter.id]?.path = item.newPath
        book.manifest[chapter.id]?.href = newHref
    }
    if (renamed > 0) {
        val renamedPaths = linkedMapOf<String, String>()
        movedEntries.forEach { (chapter, item, _) ->
            if (!item.oldPath.equals(item.newPath, ignoreCase = true)) {
                renamedPaths[normalizeEpubPath(item.oldPath).lowercase()] = item.newPath
            }
        }
        rewriteEpubBodyLinksForRenamedPaths(book, renamedPaths)
    }
    checkReport = null
    if (renamed > 0) {
        epub = book
        markDocumentChanged()
    }
    refreshChapters()
    if (renamed == 0) {
        statusMessage = "\u6587\u4ef6\u91cd\u547d\u540d\uff1a\u5904\u7406 ${plan.size} \u4e2a\u6587\u4ef6\uff0c\u4fee\u6539 0 \u4e2a\uff0c\u65e0\u9700\u4fee\u6539"
    } else {
        statusMessage = "\u6587\u4ef6\u91cd\u547d\u540d\u5b8c\u6210\uff1a\u5904\u7406 ${plan.size} \u4e2a\u6587\u4ef6\uff0c\u6539\u540d $renamed \u4e2a"
    }
    clearFileRenamePlan()
    return renamed
}

internal fun EditorController.clearFileRenamePlan() {
    fileRenamePlan = emptyList()
    fileRenamePlanToolId = null
}
