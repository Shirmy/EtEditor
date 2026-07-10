package com.eteditor

import com.eteditor.core.DocumentKind

internal enum class BodyReadOnlyActionIcon {
    Delete
}

internal enum class BodyReadOnlyActionKind(
    val title: String,
    val icon: BodyReadOnlyActionIcon? = null
) {
    Delete("删除", BodyReadOnlyActionIcon.Delete),
    Volume("分卷"),
    Split("分章"),
    Wrap("加标签")
}

internal fun bodyReadOnlyCustomActionKinds(
    kind: DocumentKind,
    splitAvailable: Boolean
): List<BodyReadOnlyActionKind> {
    return when (kind) {
        DocumentKind.Epub -> buildList {
            add(BodyReadOnlyActionKind.Delete)
            add(BodyReadOnlyActionKind.Volume)
            if (splitAvailable) add(BodyReadOnlyActionKind.Split)
            add(BodyReadOnlyActionKind.Wrap)
        }
        DocumentKind.Txt -> buildList {
            add(BodyReadOnlyActionKind.Delete)
            if (splitAvailable) add(BodyReadOnlyActionKind.Split)
        }
        DocumentKind.None -> emptyList()
    }
}

internal fun bodySelectionSourceLineIndex(
    text: String,
    selectionStart: Int,
    baseLineIndex: Int
): Int {
    val end = selectionStart.coerceIn(0, text.length)
    var lineOffset = 0
    var index = 0
    while (index < end) {
        when (text[index]) {
            '\r' -> {
                lineOffset += 1
                index += if (index + 1 < end && text[index + 1] == '\n') 2 else 1
            }
            '\n' -> {
                lineOffset += 1
                index += 1
            }
            else -> index += 1
        }
    }
    return baseLineIndex + lineOffset
}
