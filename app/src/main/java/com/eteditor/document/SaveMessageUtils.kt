package com.eteditor

import com.eteditor.core.CheckReport

internal fun buildSaveCheckFailureMessage(report: CheckReport): String {
    val errors = report.errors.takeIf { it.isNotEmpty() }
        ?: return "保存前检查未通过"
    return buildString {
        append("保存前检查未通过：")
        errors.forEachIndexed { index, error ->
            append('\n')
            append(index + 1)
            append(". ")
            append(error)
        }
    }
}

internal fun writableFileErrorMessage(error: Throwable): String {
    val reason = error.message.orEmpty()
    return when {
        reason.contains("read-only", ignoreCase = true) ->
            "当前文件位置是只读的，不能覆盖保存原文件"
        reason.contains("permission", ignoreCase = true) ||
            reason.contains("denied", ignoreCase = true) ->
            "没有写入权限，请重新从文件页打开原文件"
        reason.isNotBlank() -> reason
        else -> "文件位置不允许写入或授权已失效，请重新从文件页打开原文件"
    }
}
