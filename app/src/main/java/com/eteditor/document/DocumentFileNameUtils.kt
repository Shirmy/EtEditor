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
