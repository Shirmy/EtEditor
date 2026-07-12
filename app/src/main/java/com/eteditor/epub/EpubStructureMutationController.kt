package com.eteditor

import com.eteditor.core.EpubBook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PreparedEpubMutation<R>(
    val source: EpubBook,
    val sourceContentVersion: Int,
    val book: EpubBook,
    val result: R
)

internal fun <R> prepareEpubMutationModel(
    source: EpubBook,
    sourceContentVersion: Int,
    mutation: (EpubBook) -> R
): PreparedEpubMutation<R> {
    val book = source.mutableStructureCopy()
    return PreparedEpubMutation(
        source = source,
        sourceContentVersion = sourceContentVersion,
        book = book,
        result = mutation(book)
    )
}

internal suspend fun <R> prepareEpubMutation(
    source: EpubBook,
    sourceContentVersion: Int,
    mutation: (EpubBook) -> R
): PreparedEpubMutation<R> = withContext(Dispatchers.Default) {
    prepareEpubMutationModel(source, sourceContentVersion, mutation)
}

internal fun preparedEpubMutationMatchesSource(
    activeBook: EpubBook?,
    activeContentVersion: Int,
    prepared: PreparedEpubMutation<*>
): Boolean = epubMutationSourceMatches(
    activeBook = activeBook,
    activeContentVersion = activeContentVersion,
    sourceBook = prepared.source,
    sourceContentVersion = prepared.sourceContentVersion
)

internal fun epubMutationSourceMatches(
    activeBook: EpubBook?,
    activeContentVersion: Int,
    sourceBook: EpubBook,
    sourceContentVersion: Int
): Boolean = activeBook === sourceBook && activeContentVersion == sourceContentVersion

internal fun EditorController.publishPreparedEpubMutation(
    prepared: PreparedEpubMutation<*>
): Boolean {
    if (!preparedEpubMutationMatchesSource(epub, documentContentVersion, prepared)) {
        statusMessage = "文档内容已变化，请重试"
        return false
    }
    epub = prepared.book
    return true
}

internal suspend fun EditorController.runEpubStructureUiOperation(
    label: String,
    action: suspend () -> Boolean
): Boolean {
    if (busy) return false
    busy = true
    val controller = this
    return try {
        coroutineScope {
            val progressJob: Job = launch {
                delay(150)
                controller.setBodyOperationProgress(0.45f, "$label：处理中")
            }
            try {
                action()
            } finally {
                progressJob.cancel()
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        statusMessage = "$label 失败：${error.message ?: error.javaClass.simpleName}"
        false
    } finally {
        clearBodyOperationProgress()
        busy = false
    }
}
