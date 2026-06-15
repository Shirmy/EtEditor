package com.eteditor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ToolFileButtonField(
    label: String?,
    value: String,
    onPick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 42.dp,
    displayValue: String? = null
) {
    val selected = value.isNotBlank()
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                onClick = onPick,
                shape = RoundedCornerShape(999.dp),
                color = containerColor,
                contentColor = contentColor,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .weight(1f)
                    .height(height)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    if (value.isBlank()) {
                        Text(
                            text = "选择文件",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        RequiredSuffixFileNameText(
                            text = displayValue ?: pickedFileDisplayName(value),
                            selected = true,
                            color = contentColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (value.isNotBlank()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(height),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "清除文件", modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun RequiredSuffixFileNameText(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null
) {
    val textColor = color ?: if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val weight = if (selected) FontWeight.Medium else FontWeight.Normal
    val extension = text.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { selected && it.isNotBlank() && text.substringBeforeLast('.') != text }
        ?.let { ".$it" }
        .orEmpty()
    val stem = if (extension.isBlank()) text else text.removeSuffix(extension)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stem,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = weight,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (extension.isNotBlank()) {
            Text(
                text = extension,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = weight,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

internal val INSERT_CHAPTER_SOURCE_MIME_TYPES = arrayOf(
    "application/epub+zip",
    "text/plain"
)

internal const val INSERT_CHAPTER_SOURCE_FILE_ERROR = "插入章节只支持 TXT 或 EPUB 文件"

private fun pickedFileDisplayName(value: String): String {
    val uri = runCatching { Uri.parse(value) }.getOrNull()
    val candidate = uri?.lastPathSegment
        ?: value.substringAfterLast('\\').substringAfterLast('/')
    return candidate
        .substringAfterLast(':')
        .substringAfterLast('/')
        .ifBlank { value }
}

private fun rawInsertChapterSourceDisplayName(context: Context, uri: Uri): String {
    return context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index).orEmpty() else ""
        }
        .orEmpty()
        .ifBlank { uri.lastPathSegment.orEmpty() }
        .substringAfterLast(':')
        .substringAfterLast('/')
}

private fun String.hasFileExtension(): Boolean {
    return substringAfterLast('.', missingDelimiterValue = "").isNotBlank() && contains('.')
}

internal fun isInsertChapterSourceFileAllowed(name: String, mime: String): Boolean {
    val cleanName = name.substringAfterLast(':').substringAfterLast('/')
    val lowerName = cleanName.lowercase()
    if (lowerName.endsWith(".txt") || lowerName.endsWith(".epub")) return true
    if (cleanName.hasFileExtension()) return false
    return when (mime.lowercase()) {
        "application/epub+zip",
        "text/plain" -> true
        else -> false
    }
}

internal fun insertChapterSourceFileError(context: Context, uri: Uri): String? {
    val rawName = rawInsertChapterSourceDisplayName(context, uri)
    val mime = context.contentResolver.getType(uri).orEmpty()
    return if (isInsertChapterSourceFileAllowed(rawName, mime)) {
        null
    } else {
        INSERT_CHAPTER_SOURCE_FILE_ERROR
    }
}

internal fun insertChapterSourceDisplayName(context: Context, uri: Uri): String {
    val rawName = rawInsertChapterSourceDisplayName(context, uri)
    if (rawName.substringAfterLast('.', missingDelimiterValue = "").isNotBlank() && rawName.contains('.')) {
        return rawName
    }
    val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
    val extension = when (mime) {
        "application/epub+zip" -> "epub"
        "text/plain" -> "txt"
        else -> if (mime.startsWith("text/")) "txt" else "epub"
    }
    return "${rawName.ifBlank { "source" }}.$extension"
}
