package com.eteditor

internal data class DirectoryEditSaveState(
    val inFlight: Boolean = false,
    val showProgress: Boolean = false,
    val message: String = ""
)

internal fun directoryEditSaveStarted() = DirectoryEditSaveState(inFlight = true)

internal fun directoryEditSaveProgress() = DirectoryEditSaveState(
    inFlight = true,
    showProgress = true
)

internal fun directoryEditSaveFailed(message: String) = DirectoryEditSaveState(
    message = message.ifBlank { "保存失败" }
)
