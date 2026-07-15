package com.eteditor

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

internal fun displayName(contentResolver: ContentResolver, uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            val name = cursor.getString(index).orEmpty()
            if (name.isNotBlank()) return name
        }
    }
    return uri.lastPathSegment.orEmpty()
}

internal fun String.baseName(fallback: String): String {
    return substringAfterLast('/').substringBeforeLast('.').ifBlank { fallback }
}

internal fun String.sanitizedSaveBaseName(fallback: String): String {
    val withoutExtension = trim()
        .let { value ->
            when {
                value.endsWith(".txt", ignoreCase = true) -> value.dropLast(4)
                value.endsWith(".epub", ignoreCase = true) -> value.dropLast(5)
                else -> value
            }
        }
    return withoutExtension
        .map { char ->
            if (char.code < 32 || char in "\\/:*?\"<>|") '_' else char
        }
        .joinToString("")
        .trim()
        .trim('.')
        .ifBlank { fallback }
}

/** Target display base name + file name used when TXT save renames the source document. */
internal data class TxtSaveRenameTarget(
    val baseName: String,
    val fileName: String
)

internal fun resolveTxtSaveRenameTarget(
    displayedTitle: String,
    originalName: String
): TxtSaveRenameTarget {
    val baseName = displayedTitle.sanitizedSaveBaseName(originalName.baseName("TXT"))
    return TxtSaveRenameTarget(
        baseName = baseName,
        fileName = "$baseName.txt"
    )
}

internal fun shouldRenameTxtAfterSave(
    currentFileName: String?,
    targetFileName: String
): Boolean = currentFileName != targetFileName

/**
 * User-facing status after content write. null means keep the generic "已保存" path.
 * failureReason non-null means rename failed after a successful content write.
 */
internal fun txtRenameAfterSaveMessage(
    renamed: Boolean,
    failureReason: String? = null
): String? {
    if (failureReason != null) {
        return "已保存，但重命名失败：$failureReason"
    }
    if (!renamed) return null
    return "已保存并重命名"
}
