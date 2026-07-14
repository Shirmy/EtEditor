package com.eteditor

internal data class TxtRenameSaveTransition<T>(
    val activeSource: T,
    val baseName: String
)

internal fun <T> resolveTxtRenameSaveTransition(
    renamed: T?,
    targetBaseName: String
): Result<TxtRenameSaveTransition<T>> {
    val active = renamed
        ?: return Result.failure(IllegalStateException("改名后没有返回新文件位置"))
    return Result.success(
        TxtRenameSaveTransition(
            activeSource = active,
            baseName = targetBaseName
        )
    )
}
