package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.DocumentKind
import kotlinx.coroutines.yield

internal fun EditorController.titleRenameParameters(tool: EditorTool? = null): TitleRenameParameters {
    val values = if (tool == null) {
        defaultToolParameters("chapter_title_rename")
    } else {
        mergedToolParameters(tool)
    }
    return buildTitleRenameParameters(
        values = values,
        toolScopes = TOOL_SCOPES,
        falseValue = BOOL_FALSE
    )
}

internal fun EditorController.runTitleRenameTool(tool: EditorTool, manual: Boolean): Boolean {
    if (kind == DocumentKind.None || chapters.isEmpty()) {
        statusMessage = "没有可重命名的章节"
        return false
    }
    statusMessage = ""
    val parameters = titleRenameParameters(tool)
    val planReady = prepareTitleRenamePlan(tool, parameters)
    if (!planReady) return false
    val previewChanged = titleRenamePlan.count { it.changed }
    if (!manual && previewChanged <= 0) {
        statusMessage = "标题重命名：处理 ${titleRenamePlan.size} 章，修改 0 章，无需修改"
        clearTitleRenamePlan()
        return true
    }

    if (parameters.preview) {
        if (manual) return true
        statusMessage = needsConfirmationMessage()
        return false
    }

    val appliedChanged = applyTitleRenamePlan(titleRenamePlan)
    return appliedChanged > 0
}

private fun EditorController.prepareTitleRenamePlan(tool: EditorTool, parameters: TitleRenameParameters): Boolean {
    val plan = buildTitleRenamePlan(parameters)
    titleRenamePlan = plan
    titleRenamePlanToolId = tool.id
    if (plan.isEmpty()) return false
    val changed = plan.count { it.changed }
    statusMessage = "标题重命名计划：处理 ${plan.size} 章，需要修改 $changed 章"
    return true
}

private fun EditorController.buildTitleRenamePlan(parameters: TitleRenameParameters): List<TitleRenamePlanItem> {
    val result = buildTitleRenamePlanModel(
        kind = kind,
        epubChapters = epub?.chapters,
        txtDocument = txt,
        chapters = chapters,
        currentIndex = previewChapterIndex,
        parameters = parameters,
    )
    if (result.plan.isEmpty() && result.message.isNotBlank()) statusMessage = result.message
    return result.plan
}

internal fun EditorController.applyTitleRenamePlan(plan: List<TitleRenamePlanItem>): Int {
    val changedTitles = plan
        .filter { it.changed }
        .map { it.chapterIndex to it.newTitle }
    if (changedTitles.isEmpty()) {
        statusMessage = "标题重命名：处理 ${plan.size} 章，修改 0 章，无需修改"
        clearTitleRenamePlan()
        return 0
    }
    val changed = applyTitlesToIndices(changedTitles)
    checkReport = null
    if (changed > 0) markDocumentChanged()
    refreshChapters()
    statusMessage = if (changed > 0) {
        "标题重命名完成：处理 ${plan.size} 章，修改 $changed 章"
    } else {
        "标题重命名：处理 ${plan.size} 章，修改 0 章，无需修改"
    }
    log(statusMessage)
    clearTitleRenamePlan()
    return changed
}

suspend fun EditorController.applyPreparedTitleRenamePlanWithProgress(
    toolId: String,
    onProgress: (completed: Int, total: Int) -> Unit
): Boolean {
    if (titleRenamePlanToolId != toolId || titleRenamePlan.isEmpty()) {
        statusMessage = "\u6ca1\u6709\u53ef\u6267\u884c\u7684\u6807\u9898\u8ba1\u5212"
        return false
    }
    val changed = applyTitleRenamePlanWithProgress(titleRenamePlan, onProgress)
    return changed > 0
}

private suspend fun EditorController.applyTitleRenamePlanWithProgress(
    plan: List<TitleRenamePlanItem>,
    onProgress: (completed: Int, total: Int) -> Unit
): Int {
    val changedTitles = plan
        .filter { it.changed }
        .map { it.chapterIndex to it.newTitle }
    val total = plan.size.coerceAtLeast(1)
    onProgress(0, total)
    yield()
    if (changedTitles.isEmpty()) {
        onProgress(total, total)
        statusMessage = "标题重命名：处理 ${plan.size} 章，修改 0 章，无需修改"
        clearTitleRenamePlan()
        return 0
    }
    val changed = when (kind) {
        DocumentKind.Epub -> {
            val book = epub ?: return 0
            var count = 0
            for ((index, pair) in changedTitles.withIndex()) {
                val (chapterIndex, rawTitle) = pair
                val title = ChapterDetector.cleanTitle(rawTitle)
                val chapter = book.chapters.getOrNull(chapterIndex)
                if (chapter != null && title.isNotBlank()) {
                    chapter.title = title
                    chapter.html = ChapterDetector.updateHtmlTitle(chapter.html, title)
                    chapter.wordCount = ChapterDetector.countHtmlChars(chapter.html)
                    count += 1
                }
                onProgress(index + 1, changedTitles.size)
                yield()
            }
            count
        }
        DocumentKind.Txt -> {
            if (warnTxtMoveChapterSyncPending("写回标题")) return 0
            val document = txt ?: return 0
            var text = document.text
            var count = 0
            val sortedTitles = changedTitles.sortedByDescending { it.first }
            for ((index, pair) in sortedTitles.withIndex()) {
                val (chapterIndex, rawTitle) = pair
                val title = ChapterDetector.cleanTitle(rawTitle)
                val chapter = document.chapters.getOrNull(chapterIndex)
                if (chapter != null && title.isNotBlank()) {
                    text = ChapterDetector.updateTxtTitle(text, chapter.lineIndex, title)
                    count += 1
                }
                onProgress(index + 1, sortedTitles.size)
                yield()
            }
            document.text = text
            document.chapters = detectCurrentTxtChapters(text)
            count
        }
        DocumentKind.None -> 0
    }
    checkReport = null
    if (changed > 0) markDocumentChanged()
    refreshChapters()
    statusMessage = if (changed > 0) {
        "标题重命名完成：处理 ${plan.size} 章，修改 $changed 章"
    } else {
        "标题重命名：处理 ${plan.size} 章，修改 0 章，无需修改"
    }
    log(statusMessage)
    clearTitleRenamePlan()
    return changed
}

private fun EditorController.applyTitlesToIndices(newTitles: List<Pair<Int, String>>): Int {
    when (kind) {
        DocumentKind.Epub -> {
            val book = epub ?: return 0
            val result = applyRenamedTitlesToEpub(book, newTitles)
            if (!result.attempted) return 0
            if (result.count > 0) markDocumentChanged()
            refreshChapters()
            return result.count
        }
        DocumentKind.Txt -> {
            if (warnTxtMoveChapterSyncPending("写回标题")) return 0
            val document = txt ?: return 0
            val result = applyRenamedTitlesToTxt(document, newTitles, ::detectCurrentTxtChapters)
            if (!result.attempted) return 0
            applyTxtCatalogPurifyRulesAfterCatalogChange()
            if (result.count > 0) markDocumentChanged()
            refreshChapters()
            return result.count
        }
        DocumentKind.None -> return 0
    }
}

internal fun EditorController.clearTitleRenamePlan() {
    titleRenamePlan = emptyList()
    titleRenamePlanToolId = null
}
