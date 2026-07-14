package com.eteditor

internal data class TxtRenameSaveTransition<T>(
    val activeSource: T,
    val baseName: String
)

internal fun <T> resolveTxtRenameSaveTransition(
    renamed: T?,
    targetBaseName: String,
    canContinueSaving: (T) -> Boolean
): Result<TxtRenameSaveTransition<T>> {
    val active = renamed
        ?: return Result.failure(IllegalStateException("改名后没有返回新文件位置"))
    if (!canContinueSaving(active)) {
        return Result.failure(IllegalStateException("改名后的文件无法继续访问，请重新打开该文件"))
    }
    return Result.success(
        TxtRenameSaveTransition(
            activeSource = active,
            baseName = targetBaseName
        )
    )
}
