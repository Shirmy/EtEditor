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
    selectionEnd: Int = selectionStart,
    baseLineIndex: Int
): Int {
    val start = selectionStart.coerceIn(0, text.length)
    val end = selectionEnd.coerceIn(start, text.length)
    val breakLength = txtLineBreakLengthAt(text, start)
    val target = if (breakLength > 0 && end >= start + breakLength) start + breakLength else start
    return baseLineIndex + countLineBreaksBefore(text, target)
}
