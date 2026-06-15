package com.eteditor

import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

internal suspend fun yieldToAppUiBeforeHeavyWork() {
    yield()
    delay(120)
}

internal fun EditorController.setSaveProgress(progress: Float, text: String) {
    saveProgress = progress.coerceIn(0f, 1f)
    saveProgressText = text
    statusMessage = text
}

internal fun EditorController.setBodyOperationProgress(progress: Float, text: String) {
    bodyOperationProgress = progress.coerceIn(0f, 1f)
    bodyOperationProgressText = text
    statusMessage = text
}

fun EditorController.showStatusMessage(text: String) {
    statusMessage = text
}

internal fun EditorController.markDocumentChanged() {
    hasUnsavedChanges = true
    documentContentVersion++
}

internal suspend fun EditorController.runBusy(label: String, block: suspend () -> Unit): Boolean {
    busy = true
    statusMessage = "$label..."
    log("$label...")
    return try {
        yieldToAppUiBeforeHeavyWork()
        block()
        true
    } catch (error: Throwable) {
        statusMessage = "$label 失败：${error.message ?: error.javaClass.simpleName}"
        log("$label 失败：${error.message ?: error.javaClass.simpleName}")
        false
    } finally {
        busy = false
    }
}

fun EditorController.clearSaveFailureMessage() {
    saveFailureMessage = ""
}

internal fun EditorController.clearSaveProgress() {
    saveProgress = null
    saveProgressText = ""
}

internal fun EditorController.clearBodyOperationProgress() {
    bodyOperationProgress = null
    bodyOperationProgressText = ""
}
