package com.eteditor

import com.eteditor.core.DocumentKind

internal enum class BodyReadOnlyActionIcon {
    Cut,
    Delete,
    Edit
}

internal enum class BodyReadOnlyActionKind(
    val title: String,
    val icon: BodyReadOnlyActionIcon? = null
) {
    Cut("剪切", BodyReadOnlyActionIcon.Cut),
    Delete("删除", BodyReadOnlyActionIcon.Delete),
    Edit("编辑", BodyReadOnlyActionIcon.Edit),
    Volume("分卷"),
    Split("分章"),
    Wrap("补标签"),
    Warning("预警"),
    AuthorNote("作者有话说"),
    Annotation("注解")
}

internal fun bodyReadOnlyCustomActionKinds(
    kind: DocumentKind,
    splitAvailable: Boolean
): List<BodyReadOnlyActionKind> {
    return when (kind) {
        DocumentKind.Epub -> buildList {
            add(BodyReadOnlyActionKind.Cut)
            add(BodyReadOnlyActionKind.Delete)
            add(BodyReadOnlyActionKind.Edit)
            add(BodyReadOnlyActionKind.Volume)
            if (splitAvailable) add(BodyReadOnlyActionKind.Split)
            add(BodyReadOnlyActionKind.Wrap)
            add(BodyReadOnlyActionKind.Warning)
            add(BodyReadOnlyActionKind.AuthorNote)
            add(BodyReadOnlyActionKind.Annotation)
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
