package com.eteditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class FetchCoverImageInfo(
    val bitmap: ImageBitmap,
    val type: String,
    val byteSize: Int,
    val width: Int,
    val height: Int
)

fun coverImageType(contentType: String, url: String): String {
    val type = contentType.substringBefore(';').trim()
    if (type.startsWith("image/", ignoreCase = true)) return type
    val extension = url.substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('.', "")
        .lowercase()
    return when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> type.ifBlank { "未知类型" }
    }
}

fun formatCoverByteSize(bytes: Int): String {
    return if (bytes >= 1024 * 1024) {
        "%.2f MB".format(bytes / 1024f / 1024f)
    } else {
        "%.1f KB".format(bytes / 1024f)
    }
}

@Composable
fun FetchInfoLine(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (value.startsWith("http")) FontFamily.Monospace else FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
