package com.eteditor

import com.eteditor.core.DocumentKind
import kotlinx.coroutines.yield

internal fun EditorController.titleFormatParameters(tool: EditorTool? = null): TitleFormatParameters {
    val values = if (tool == null) {
        defaultToolParameters("title_format")
    } else {
        mergedToolParameters(tool)
    }
    return buildTitleFormatParameters(
        values = values,
        modeOptions = TITLE_FORMAT_MODE_OPTIONS,
        styleOptions = TITLE_FORMAT_STYLE_OPTIONS,
        scopeOptions = TITLE_FORMAT_SCOPE_OPTIONS,
        defaultScope = TITLE_FORMAT_SCOPE_ALL,
        falseValue = BOOL_FALSE
    )
}

fun EditorController.updateTitleFormatPlanStyle(toolId: String, chapterIndex: Int, style: String) {
    if (titleFormatPlanToolId != toolId || !titleFormatPlanAllowsStyleEdit) return
    if (TITLE_FORMAT_STYLE_OPTIONS.none { it.first == style }) return
    titleFormatPlan = titleFormatPlan.map { item ->
        if (item.chapterIndex != chapterIndex) {
            item
        } else {
            val rendered = renderTitleFormat(item.prefix, item.suffix, style)
            item.copy(
                styleCode = style,
                styleName = titleFormatStyleLabel(style),
                reason = "手动选择：${titleFormatStyleLabel(style)}",
                newTitle = rendered.plainTitle
            )
        }
    }
}

internal fun EditorController.runTitleFormatTool(tool: EditorTool, manual: Boolean = false): Boolean {
    if (kind == DocumentKind.None) return false
    if (chapters.isEmpty()) {
        statusMessage = "没有可格式化的章节"
        return false
    }
    val parameters = titleFormatParameters(tool)
    val planReady = prepareTitleFormatPlan(tool, parameters)
    if (!planReady) return false
    val changed = titleFormatPlan.count { it.changed }
    if (changed <= 0) {
        statusMessage = titleFormatNoChangeMessage(titleFormatPlan)
        clearTitleFormatPlan()
        return !manual
    }

    if (parameters.preview) {
        if (manual) return true
        statusMessage = needsConfirmationMessage()
        return false
    }

    val appliedChanged = applyTitleFormatPlan(titleFormatPlan)
    return appliedChanged > 0
}

private fun EditorController.prepareTitleFormatPlan(tool: EditorTool, parameters: TitleFormatParameters): Boolean {
    val plan = buildTitleFormatPlan(parameters)
    titleFormatPlan = plan
    titleFormatPlanToolId = tool.id
    titleFormatPlanAllowsStyleEdit = false
    if (plan.isEmpty()) return false
    titleFormatPlanLogicText = titleFormatLogicText(parameters, plan)
    val changed = plan.count { it.changed }
    statusMessage = "标题格式计划：处理 ${plan.size} 章，需要修改 $changed 章"
    return true
}

private fun EditorController.buildTitleFormatPlan(parameters: TitleFormatParameters): List<TitleFormatPlanItem> {
    val result = buildTitleFormatPlanModel(
        kind = kind,
        epubChapters = epub?.chapters,
        txtDocument = txt,
        parameters = parameters,
    )
    if (result.plan.isEmpty() && result.message.isNotBlank()) statusMessage = result.message
    return result.plan
}

internal fun EditorController.applyTitleFormatPlan(plan: List<TitleFormatPlanItem>): Int {
    val renderedByIndex = plan.map { item ->
        item.chapterIndex to renderTitleFormat(item.prefix, item.suffix, item.styleCode)
    }
    val changed = when (kind) {
        DocumentKind.Epub -> applyEpubTitleFormats(renderedByIndex)
        DocumentKind.Txt -> applyTxtTitleFormats(renderedByIndex)
        DocumentKind.None -> 0
    }
    if (changed <= 0) {
        statusMessage = titleFormatNoChangeMessage(plan)
        clearTitleFormatPlan()
        return 0
    }

    checkReport = null
    markDocumentChanged()
    refreshChapters()
    statusMessage = titleFormatCompletionMessage(plan, changed)
    log(statusMessage)
    clearTitleFormatPlan()
    return changed
}

suspend fun EditorController.applyPreparedTitleFormatPlanWithProgress(
    toolId: String,
    onProgress: (completed: Int, total: Int) -> Unit
): Boolean {
    if (titleFormatPlanToolId != toolId || titleFormatPlan.isEmpty()) {
        statusMessage = "\u6ca1\u6709\u53ef\u6267\u884c\u7684\u6807\u9898\u683c\u5f0f\u8ba1\u5212"
        return false
    }
    val changed = applyTitleFormatPlanWithProgress(titleFormatPlan, onProgress)
    if (changed <= 0) {
        return true
    }
    return true
}

private suspend fun EditorController.applyTitleFormatPlanWithProgress(
    plan: List<TitleFormatPlanItem>,
    onProgress: (completed: Int, total: Int) -> Unit
): Int {
    val renderedByIndex = plan.map { item ->
        item.chapterIndex to renderTitleFormat(item.prefix, item.suffix, item.styleCode)
    }
    val total = renderedByIndex.size.coerceAtLeast(1)
    onProgress(0, total)
    yield()
    val changed = when (kind) {
        DocumentKind.Epub -> {
            val book = epub ?: return 0
            var changedCount = 0
            for ((index, pair) in renderedByIndex.withIndex()) {
                val (chapterIndex, rendered) = pair
                val chapter = book.chapters.getOrNull(chapterIndex)
                if (chapter != null) {
                    val nextHtml = updateHtmlTitleForFormat(
                        html = chapter.html,
                        plainTitle = rendered.plainTitle,
                        headingHtml = rendered.headingHtml,
                        styleCode = rendered.styleCode
                    )
                    if (chapter.title != rendered.plainTitle || chapter.html != nextHtml) {
                        chapter.title = rendered.plainTitle
                        chapter.html = nextHtml
                        chapter.wordCount = com.eteditor.core.ChapterDetector.countHtmlChars(chapter.html)
                        com.eteditor.core.updateEpubChapterHtmlEntry(book, chapter)
                        changedCount += 1
                    }
                }
                onProgress(index + 1, total)
                yield()
            }
            changedCount
        }
        DocumentKind.Txt -> {
            if (warnTxtMoveChapterSyncPending("标题格式")) return 0
            val document = txt ?: return 0
            var text = document.text
            var changedCount = 0
            val sortedRendered = renderedByIndex.sortedByDescending { it.first }
            for ((index, pair) in sortedRendered.withIndex()) {
                val (chapterIndex, rendered) = pair
                val chapter = document.chapters.getOrNull(chapterIndex)
                if (chapter != null && chapter.title != rendered.plainTitle) {
                    text = com.eteditor.core.ChapterDetector.updateTxtTitle(
                        text,
                        chapter.lineIndex,
                        rendered.plainTitle
                    )
                    changedCount += 1
                }
                onProgress(index + 1, sortedRendered.size)
                yield()
            }
            if (changedCount > 0) {
                document.text = text
                document.chapters = detectCurrentTxtChapters(text)
                applyTxtCatalogPurifyRulesAfterCatalogChange()
            }
            changedCount
        }
        DocumentKind.None -> 0
    }
    if (changed <= 0) {
        statusMessage = titleFormatNoChangeMessage(plan)
        clearTitleFormatPlan()
        return 0
    }

    checkReport = null
    markDocumentChanged()
    refreshChapters()
    statusMessage = titleFormatCompletionMessage(plan, changed)
    log(statusMessage)
    clearTitleFormatPlan()
    return changed
}

private fun EditorController.applyEpubTitleFormats(renderedByIndex: List<Pair<Int, TitleFormatRendered>>): Int {
    val book = epub ?: return 0
    return applyEpubTitleFormatsToBook(book, renderedByIndex)
}

private fun EditorController.applyTxtTitleFormats(renderedByIndex: List<Pair<Int, TitleFormatRendered>>): Int {
    if (warnTxtMoveChapterSyncPending("标题格式")) return 0
    val document = txt ?: return 0
    val changed = applyTxtTitleFormatsToDocument(document, renderedByIndex, ::detectCurrentTxtChapters)
    if (changed > 0) {
        applyTxtCatalogPurifyRulesAfterCatalogChange()
    }
    return changed
}

internal fun EditorController.clearTitleFormatPlan() {
    titleFormatPlan = emptyList()
    titleFormatPlanToolId = null
    titleFormatPlanAllowsStyleEdit = false
    titleFormatPlanLogicText = ""
}
